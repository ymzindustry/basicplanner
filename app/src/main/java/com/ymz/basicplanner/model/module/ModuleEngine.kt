package com.ymz.basicplanner.model.module

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.effectiveAdjustmentPercent
import com.ymz.basicplanner.model.scaledBaseRm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.ceil

/** One entity that may feed a module input: the target itself, or an ancestor in the tree. */
data class ModuleInputCandidate(
    val entityId: Long,
    val kindLabel: String,
    val name: String
)

/**
 * Resolves which items may feed a module attached to [kind]/[targetId]: the target itself plus
 * everything above it (exercise → session → day → week → block → program). Children are never
 * allowed — a module must not read from something it is meant to shape.
 */
fun moduleInputCandidates(app: AppState, kind: ModuleTargetKind, targetId: Long): List<ModuleInputCandidate> {
    val result = mutableListOf<ModuleInputCandidate>()

    // Kotlin local functions cannot be used before their declaration, so order matters here:
    // day → day-of-session → session → exercise.
    fun addDay(dayId: Long) {
        val day = app.days.find { it.id == dayId } ?: return
        result += ModuleInputCandidate(day.id, "Day", day.name)
        val week = app.weeks.find { dayId in it.dayIds }
        if (week != null) {
            result += ModuleInputCandidate(week.id, "Week", week.name)
            val block = app.blocks.find { week.id in it.weekIds }
            if (block != null) {
                result += ModuleInputCandidate(block.id, "Block", block.name)
                val program = app.programs.find { p -> block.id in p.blockIds || block.id in programBlockIds(app, p) }
                if (program != null) result += ModuleInputCandidate(program.id, "Program", program.name)
            } else {
                val program = app.programs.find { week.id in it.blockIds }
                if (program != null) result += ModuleInputCandidate(program.id, "Program", program.name)
            }
        }
    }

    fun addDayOfSession(sessionId: Long) {
        val timed = app.timedSessions.filter { it.sessionId == sessionId }.map { it.id }.toSet()
        val day = app.days.find { d -> d.timedSessionIds.any { it in timed } }
        if (day != null) addDay(day.id)
    }

    fun addSession(sessionId: Long) {
        val session = app.sessions.find { it.id == sessionId } ?: return
        result += ModuleInputCandidate(session.id, "Session", session.name)
        addDayOfSession(sessionId)
    }

    fun addExercise(exerciseId: Long) {
        val ex = app.exercises.find { it.id == exerciseId } ?: return
        val movement = app.movements.find { it.id == ex.movementId }?.name ?: "?"
        result += ModuleInputCandidate(ex.id, "Exercise", if (ex.name.isBlank()) movement else "$movement ${ex.name}")
    }

    when (kind) {
        ModuleTargetKind.SET -> {
            val ex = app.exercises.find { exercise -> exercise.entries.any { it.id == targetId } }
            if (ex != null) {
                addExercise(ex.id)
                app.sessions.find { s -> ex.id in s.exerciseIds }?.let { addSession(it.id) }
            }
        }
        ModuleTargetKind.EXERCISE -> {
            addExercise(targetId)
            app.sessions.filter { targetId in it.exerciseIds }.forEach { addSession(it.id) }
        }
        ModuleTargetKind.SESSION -> addSession(targetId)
        ModuleTargetKind.DAY -> addDay(targetId)
    }
    return result.distinctBy { it.entityId }
}

private const val KIND_LABEL_SET = "Set"

/** Every block id of a program, roots and nested ones. */
private fun programBlockIds(app: AppState, program: com.ymz.basicplanner.model.ProgramEntity): Set<Long> {
    val ids = mutableSetOf<Long>()
    fun walk(id: Long) {
        val block = app.blocks.find { it.id == id } ?: return
        if (ids.add(id)) block.subBlockIds.forEach { walk(it) }
    }
    program.blockIds.forEach { walk(it) }
    return ids
}

@Serializable
private data class ModuleOutputItem(
    val set_id: Long? = null,
    val reps_delta: Int? = null,
    val percent_delta: Double? = null,
    val kg_delta: Double? = null,
    val rest_delta: Int? = null
)

@Serializable
private data class ModuleOutputWrapper(val sets: List<ModuleOutputItem> = emptyList())

/**
 * Runs the modules attached to one program for one session and returns, per set entry, the summed
 * deltas. Modules never mutate the plan: the caller applies the deltas to a display/runtime copy.
 *
 * Order is broadest first (day → session → exercise → set) then by attachment id, and every module
 * sees the *plan* values, so the result does not depend on execution order — deltas simply add up.
 */
class ModuleEngine(private val executor: ModuleExecutor) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun evaluate(app: AppState, activeProgramId: Long, sessionId: Long): ModuleOutcome {
        val ap = app.activePrograms.find { it.id == activeProgramId } ?: return ModuleOutcome()
        val program = app.programs.find { it.id == ap.programId } ?: return ModuleOutcome()
        val session = app.sessions.find { it.id == sessionId } ?: return ModuleOutcome()

        val attachments = app.moduleAttachments
            .filter { it.programId == program.id }
            .sortedWith(compareBy({ breadth(it.targetKind) }, { it.id }))
        if (attachments.isEmpty()) return ModuleOutcome()

        val deltaBySet = mutableMapOf<Long, SetAdjustment>()
        val applied = mutableListOf<Long>()
        val issues = mutableListOf<ModuleRunIssue>()

        attachments.forEach { attachment ->
            val module = app.modules.find { it.id == attachment.moduleId } ?: return@forEach
            val affected = affectedSets(app, attachment, session.id)
            if (affected.isEmpty()) return@forEach

            val context = buildContext(app, ap, session, attachment, affected)
            val output = try {
                executor.run(module, json.encodeToString(ModuleContext.serializer(), context))
            } catch (t: Throwable) {
                issues += ModuleRunIssue(module.id, module.name, t.message ?: t::class.simpleName ?: "error")
                return@forEach
            }

            val items = try {
                parseOutput(output)
            } catch (t: Throwable) {
                issues += ModuleRunIssue(module.id, module.name, "invalid output: ${t.message ?: ""}")
                return@forEach
            }

            val affectedIds = affected.map { it.second.id }.toSet()
            var changed = false
            items.forEach { item ->
                val setId = item.set_id ?: return@forEach
                if (setId !in affectedIds) return@forEach
                val current = deltaBySet[setId] ?: SetAdjustment()
                val updated = SetAdjustment(
                    repsDelta = current.repsDelta + (item.reps_delta ?: 0),
                    percentDelta = current.percentDelta + (item.percent_delta ?: 0.0),
                    kgDelta = current.kgDelta + (item.kg_delta ?: 0.0),
                    restDelta = current.restDelta + (item.rest_delta ?: 0)
                )
                if (updated != current) {
                    deltaBySet[setId] = updated
                    changed = true
                }
            }
            if (changed) applied += module.id
        }

        return ModuleOutcome(
            adjustments = deltaBySet.filterValues { !it.isIdentity() },
            applied = applied.distinct(),
            issues = issues
        )
    }

    /** Context for the test bench: a module run by hand without opening a session. */
    fun previewContext(app: AppState, activeProgramId: Long, sessionId: Long): ModuleContext? {
        val ap = app.activePrograms.find { it.id == activeProgramId } ?: return null
        val session = app.sessions.find { it.id == sessionId } ?: return null
        val attachment = ModuleAttachment(
            id = 0,
            moduleId = 0,
            programId = ap.programId,
            targetKind = ModuleTargetKind.SESSION,
            targetId = session.id
        )
        return buildContext(app, ap, session, attachment, affectedSets(app, attachment, session.id))
    }

    private fun parseOutput(output: String): List<ModuleOutputItem> {
        val trimmed = output.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "None" || trimmed == "null") return emptyList()
        return if (trimmed.startsWith("{")) {
            json.decodeFromString(ModuleOutputWrapper.serializer(), trimmed).sets
        } else {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ModuleOutputItem.serializer()), trimmed)
        }
    }

    private fun breadth(kind: ModuleTargetKind): Int = when (kind) {
        ModuleTargetKind.DAY -> 0
        ModuleTargetKind.SESSION -> 1
        ModuleTargetKind.EXERCISE -> 2
        ModuleTargetKind.SET -> 3
    }

    /** The plan sets an attachment covers inside [sessionId]. */
    fun affectedSets(app: AppState, attachment: ModuleAttachment, sessionId: Long): List<Pair<ExerciseEntity, SetEntry>> {
        val session = app.sessions.find { it.id == sessionId } ?: return emptyList()
        val sessionExercises = session.exerciseIds.mapNotNull { id -> app.exercises.find { it.id == id } }

        val exercises = when (attachment.targetKind) {
            ModuleTargetKind.SET -> sessionExercises.filter { ex -> ex.entries.any { it.id == attachment.targetId } }
            ModuleTargetKind.EXERCISE -> sessionExercises.filter { it.id == attachment.targetId }
            ModuleTargetKind.SESSION -> if (session.id == attachment.targetId) sessionExercises else emptyList()
            ModuleTargetKind.DAY -> if (dayOfSession(app, sessionId) == attachment.targetId) sessionExercises else emptyList()
        }
        return exercises.flatMap { ex -> ex.entries.map { ex to it } }
            .filter { (_, entry) -> attachment.targetKind != ModuleTargetKind.SET || entry.id == attachment.targetId }
    }

    /** The Day entity the session sits under, if any. */
    fun dayOfSession(app: AppState, sessionId: Long): Long? {
        val timedIds = app.timedSessions.filter { it.sessionId == sessionId }.map { it.id }.toSet()
        return app.days.find { day -> day.timedSessionIds.any { it in timedIds } }?.id
    }

    private fun buildContext(
        app: AppState,
        ap: ActiveProgram,
        session: com.ymz.basicplanner.model.SessionEntity,
        attachment: ModuleAttachment,
        affected: List<Pair<ExerciseEntity, SetEntry>>
    ): ModuleContext {
        val families = app.itemFamilies.associateBy { it.id }
        val items = mutableMapOf<String, String>()
        attachment.bindings.forEach { binding ->
            app.items
                .filter { it.entityId == binding.entityId }
                .filter { it.activeProgramId == null || it.activeProgramId == ap.id }
                .forEach { item ->
                    val name = families[item.itemFamilyId]?.name ?: binding.specName
                    items[name] = item.value
                }
        }

        val date = session.date?.toLocalDate() ?: LocalDate.now()
        val adjustmentPct = effectiveAdjustmentPercent(app, ap.id, date)
        val sets = affected.map { (exercise, entry) ->
            ModuleSetSnapshot(
                id = entry.id,
                exerciseId = exercise.id,
                exercise = exercise.name,
                movement = app.movements.find { it.id == exercise.movementId }?.name ?: "",
                reps = entry.reps,
                intensity = entry.intensity,
                rest = entry.rest,
                weight_kg = plannedWeightKg(ap, session, exercise, entry, adjustmentPct)
            )
        }

        return ModuleContext(
            date = date.toString(),
            program_id = ap.programId,
            target = ModuleTargetInfo(
                kind = attachment.targetKind.name.lowercase(),
                id = attachment.targetId,
                name = targetName(app, attachment)
            ),
            items = items,
            sets = sets
        )
    }

    private fun targetName(app: AppState, attachment: ModuleAttachment): String = when (attachment.targetKind) {
        ModuleTargetKind.SET -> "Set ${attachment.targetId}"
        ModuleTargetKind.EXERCISE -> app.exercises.find { it.id == attachment.targetId }?.name ?: ""
        ModuleTargetKind.SESSION -> app.sessions.find { it.id == attachment.targetId }?.name ?: ""
        ModuleTargetKind.DAY -> app.days.find { it.id == attachment.targetId }?.name ?: ""
    }

    private fun plannedWeightKg(
        ap: ActiveProgram,
        session: com.ymz.basicplanner.model.SessionEntity,
        exercise: ExerciseEntity,
        entry: SetEntry,
        adjustmentPct: Int
    ): Double {
        entry.weightKg?.let { return it.toDouble() }
        val base = session.baseRms?.get(exercise.movementId)
            ?: ap.exerciseDailyRms[exercise.movementId]?.firstOrNull()
            ?: 0
        if (base <= 0) return 0.0
        return ceil(scaledBaseRm(base, adjustmentPct) * (entry.intensity / 100.0))
    }
}
