package com.ymz.basicplanner.model

import java.time.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class PerformanceEntry(
    val id: Long,
    val movementId: Long,
    val activeProgramId: Long? = null,
    @Contextual val date: LocalDate,
    val weightKg: Double,
    val reps: Int = 1,
    val sessionId: Long? = null,
    val note: String = "",
    /** Exercise (variant) that produced this entry: "Deadlift Medium" vs "Deadlift Wide". */
    val exerciseId: Long? = null,
    /** Variant name as it was when recorded, kept so the filter still works if the exercise is
     *  renamed or deleted; the live name wins while the exercise exists. */
    val variant: String = ""
)

data class PerformancePoint(
    val date: LocalDate,
    val value: Double
)

data class DatedItemValue(
    val date: LocalDate,
    val value: String
)

fun resolveDatedItems(app: AppState, activeProgramId: Long): Map<Long, List<DatedItemValue>> {
    val activeSessionIds = app.activePrograms
        .find { it.id == activeProgramId }
        ?.sessionIds
        .orEmpty()
        .toSet()
    val sessions = app.sessions.filter { it.id in activeSessionIds }

    val sessionByExerciseId = mutableMapOf<Long, SessionEntity>()
    val sessionByEntryId = mutableMapOf<Long, SessionEntity>()
    for (session in sessions) {
        for (exerciseId in session.exerciseIds) {
            sessionByExerciseId[exerciseId] = session
            val exercise = app.exercises.find { it.id == exerciseId } ?: continue
            for (entry in exercise.entries) {
                sessionByEntryId[entry.id] = session
            }
        }
    }

    val result = mutableMapOf<Long, MutableList<DatedItemValue>>()
    for (item in app.items) {
        if (item.activeProgramId != null && item.activeProgramId != activeProgramId) continue
        val session = when {
            sessionByExerciseId.containsKey(item.entityId) -> sessionByExerciseId[item.entityId]
            sessionByEntryId.containsKey(item.entityId) -> sessionByEntryId[item.entityId]
            else -> sessions.find { it.id == item.entityId }
        } ?: continue
        val date = session.date?.toLocalDate() ?: continue
        result.getOrPut(item.itemFamilyId) { mutableListOf() }
            .add(DatedItemValue(date = date, value = item.value))
    }

    return result.mapValues { (_, list) -> list.sortedBy { it.date } }
}

fun scaledBaseRm(base: Int, percent: Int): Int {
    return kotlin.math.round(base * (1.0 + percent / 100.0)).toInt()
}

/** Exercises planned on one weekday slot, flat layout aware (day / session / bare exercise). */
private fun exercisesInWeekSlot(app: AppState, slotId: Long): List<ExerciseEntity> = when {
    slotId == CreationState.REST_DAY_ID || slotId == CreationState.EMPTY_DAY_ID -> emptyList()
    app.exercises.any { it.id == slotId } -> app.exercises.filter { it.id == slotId }
    app.sessions.any { it.id == slotId } ->
        app.sessions.filter { it.id == slotId }
            .flatMap { session -> session.exerciseIds.mapNotNull { id -> app.exercises.find { it.id == id } } }
    app.days.any { it.id == slotId } ->
        app.days.filter { it.id == slotId }
            .flatMap { day ->
                day.timedSessionIds
                    .mapNotNull { tsId -> app.timedSessions.find { it.id == tsId }?.sessionId }
                    .flatMap { sessionId -> app.sessions.find { it.id == sessionId }?.exerciseIds ?: emptyList() }
            }
            .mapNotNull { id -> app.exercises.find { it.id == id } }
    else -> emptyList()
}

/** Runs [visit] for every week of a program, whether it sits in a block or directly in it. */
private fun forEachWeekOfProgram(app: AppState, program: ProgramEntity, visit: (week: WeekEntity, isFirst: Boolean, weekIdx: Int) -> Unit) {
    fun walkBlock(block: BlockEntity) {
        block.weekIds.forEachIndexed { weekIdx, weekId ->
            app.weeks.find { it.id == weekId }?.let { visit(it, false, weekIdx) }
        }
        block.subBlockIds.forEach { subId -> app.blocks.find { it.id == subId }?.let { walkBlock(it) } }
    }
    program.blockIds.forEachIndexed { idx, id ->
        val block = app.blocks.find { it.id == id }
        if (block != null) {
            block.weekIds.forEachIndexed { weekIdx, weekId ->
                app.weeks.find { it.id == weekId }?.let { visit(it, idx == 0 && weekIdx == 0, weekIdx) }
            }
            block.subBlockIds.forEach { subId -> app.blocks.find { it.id == subId }?.let { walkBlock(it) } }
        } else {
            app.weeks.find { it.id == id }?.let { visit(it, idx == 0, 0) }
        }
    }
}

/**
 * Total adjustment (%) that applies on [date]: every ancestor's own adjustment plus the
 * per-child adjustment its parent carries for the branch leading to that date
 * (program → block → week → day slot). Missing a level's [subAdjustments] entry silently
 * drops the percentage the UI displays on that row.
 */
fun effectiveAdjustmentPercent(
    app: AppState,
    activeProgramId: Long,
    date: LocalDate
): Int {
    val ap = app.activePrograms.find { it.id == activeProgramId } ?: return 0
    val program = app.programs.find { it.id == ap.programId } ?: return 0

    var currentDate = ap.startDate
    var found: Int? = null

    /** Walks [week]'s seven slots; [acc] already carries every ancestor, including the
     *  per-child adjustment that put this week in its block. */
    fun visitWeek(week: WeekEntity, acc: Int, isFirstWeekOfProgram: Boolean) {
        if (found != null) return
        val weekPct = acc + week.adjustmentPercent
        val startDayIndex = if (isFirstWeekOfProgram) ap.startDate.dayOfWeek.value - 1 else 0
        for (i in startDayIndex until 7) {
            if (currentDate == date) {
                val dayId = week.dayIds.getOrNull(i)
                val slotPct = if (dayId != null && dayId != CreationState.REST_DAY_ID) {
                    (app.days.find { it.id == dayId }?.adjustmentPercent ?: 0) +
                        week.subAdjustments.getOrElse(i) { 0 }
                } else {
                    0
                }
                found = weekPct + slotPct
                return
            }
            currentDate = currentDate.plusDays(1)
        }
    }

    fun visitBlock(block: BlockEntity, acc: Int, isFirstBlockOfProgram: Boolean) {
        val blockPct = acc + block.adjustmentPercent
        block.subBlockIds.forEachIndexed { sIdx, subId ->
            if (found != null) return
            app.blocks.find { it.id == subId }?.let { sub ->
                visitBlock(sub, blockPct + block.subAdjustments.getOrElse(sIdx) { 0 }, false)
            }
        }
        block.weekIds.forEachIndexed { weekIdx, weekId ->
            if (found != null) return@forEachIndexed
            app.weeks.find { it.id == weekId }?.let { week ->
                visitWeek(
                    week = week,
                    acc = blockPct + block.subAdjustments.getOrElse(weekIdx) { 0 },
                    isFirstWeekOfProgram = isFirstBlockOfProgram && weekIdx == 0
                )
            }
        }
    }

    program.blockIds.forEachIndexed { idx, blockId ->
        if (found != null) return@forEachIndexed
        val programPct = program.adjustmentPercent + program.subAdjustments.getOrElse(idx) { 0 }
        val block = app.blocks.find { it.id == blockId }
        if (block != null) {
            visitBlock(block, programPct, idx == 0)
        } else {
            // A week sitting directly in the program carries no block adjustment.
            app.weeks.find { it.id == blockId }?.let { week ->
                visitWeek(week, programPct, idx == 0)
            }
        }
    }
    return found ?: program.adjustmentPercent
}

/** Planned reps of [movementId], optionally narrowed to one [variant] (exercise name). */
fun plannedRepsForMovement(
    app: AppState,
    activeProgramId: Long,
    movementId: Long,
    variant: String? = null
): List<Int> {
    val ap = app.activePrograms.find { it.id == activeProgramId } ?: return emptyList()
    val program = app.programs.find { it.id == ap.programId } ?: return emptyList()

    val repsSet = mutableSetOf<Int>()
    var currentDate = ap.startDate

    fun walkWeek(week: WeekEntity, isFirstBlock: Boolean, weekIdx: Int) {
        val startDayIndex = if (isFirstBlock && weekIdx == 0) {
            ap.startDate.dayOfWeek.value - 1
        } else {
            0
        }
        for (i in startDayIndex until 7) {
            val slotId = week.dayIds.getOrNull(i)
            if (slotId != null) {
                val wantedVariant = variant?.trim()?.takeIf { it.isNotEmpty() }
                exercisesInWeekSlot(app, slotId)
                    .filter { it.movementId == movementId }
                    .filter { wantedVariant == null || it.name.trim().equals(wantedVariant, ignoreCase = true) }
                    .flatMap { exercise -> exercise.entries }
                    .forEach { repsSet.add(it.reps) }
            }
            currentDate = currentDate.plusDays(1)
        }
    }

    fun walk(block: BlockEntity, isFirstBlock: Boolean) {
        block.subBlockIds.forEach { subId -> app.blocks.find { it.id == subId }?.let { walk(it, false) } }
        block.weekIds.forEachIndexed { weekIdx, weekId ->
            app.weeks.find { it.id == weekId }?.let { walkWeek(it, isFirstBlock, weekIdx) }
        }
    }

    program.blockIds.forEachIndexed { idx, blockId ->
        val block = app.blocks.find { it.id == blockId }
        if (block != null) {
            walk(block, idx == 0)
        } else {
            app.weeks.find { it.id == blockId }?.let { walkWeek(it, idx == 0, 0) }
        }
    }
    return repsSet.sorted()
}

/**
 * First and last day the active program covers, so the dashboard can draw a date axis from the
 * very first day of the program to its very last one (weeks count 7 days; the first week starts
 * on [ActiveProgram.startDate]'s weekday, so its leading days are outside the program).
 */
/** A stretch of the plan drawn as one band on the dashboard: a block at some level, or a week. */
data class PlanSegment(
    val id: Long,
    val name: String,
    val start: LocalDate,
    val end: LocalDate
)

/**
 * The program cut into bands for the chart: the blocks sitting at [level] (0 = blocks directly in
 * the program), or the weeks when [level] is null. Bands follow the same date walk as the goal
 * curve, so a band's span is exactly the days it covers.
 */
fun programSegments(app: AppState, activeProgramId: Long, level: Int?): List<PlanSegment> {
    val ap = app.activePrograms.find { it.id == activeProgramId } ?: return emptyList()
    val program = app.programs.find { it.id == ap.programId } ?: return emptyList()

    var cursor = ap.startDate
    var firstWeekSeen = false
    val segments = mutableListOf<PlanSegment>()

    /** Paints one week's days and returns the band it covers. */
    fun walkWeek(week: WeekEntity): PlanSegment {
        // The program's first week starts on the activation weekday, the later ones on Monday.
        val startDayIndex = if (!firstWeekSeen) ap.startDate.dayOfWeek.value - 1 else 0
        firstWeekSeen = true
        val days = 7 - startDayIndex
        val start = cursor
        val end = cursor.plusDays((days - 1).toLong())
        cursor = cursor.plusDays(days.toLong())
        return PlanSegment(id = week.id, name = week.name, start = start, end = end)
    }

    fun walkBlock(block: BlockEntity, depth: Int) {
        val start = cursor
        block.subBlockIds.forEach { subId -> app.blocks.find { it.id == subId }?.let { walkBlock(it, depth + 1) } }
        block.weekIds.forEach { weekId ->
            val week = app.weeks.find { it.id == weekId } ?: return@forEach
            val band = walkWeek(week)
            if (level == null) segments += band
        }
        val end = cursor.minusDays(1)
        // One band per *occurrence*: a block listed twice in a program shows as two sections, even
        // though both entries point at the same block id.
        if (level != null && depth == level && !end.isBefore(start)) {
            segments += PlanSegment(id = block.id, name = block.name, start = start, end = end)
        }
    }

    program.blockIds.forEach { blockId ->
        val block = app.blocks.find { it.id == blockId }
        if (block != null) {
            walkBlock(block, 0)
        } else {
            app.weeks.find { it.id == blockId }?.let { week ->
                val band = walkWeek(week)
                if (level == null) segments += band
            }
        }
    }

    return segments.sortedBy { it.start }
}

fun programDateRange(app: AppState, activeProgramId: Long): Pair<LocalDate, LocalDate>? {
    val ap = app.activePrograms.find { it.id == activeProgramId } ?: return null
    val program = app.programs.find { it.id == ap.programId } ?: return null

    var days = 0
    fun addWeek(isFirstWeek: Boolean) {
        val startDayIndex = if (isFirstWeek) ap.startDate.dayOfWeek.value - 1 else 0
        days += 7 - startDayIndex
    }

    fun walk(block: BlockEntity, isFirstBlock: Boolean) {
        block.subBlockIds.forEach { subId -> app.blocks.find { it.id == subId }?.let { walk(it, false) } }
        block.weekIds.forEachIndexed { weekIdx, weekId ->
            if (app.weeks.any { it.id == weekId }) {
                addWeek(isFirstBlock && weekIdx == 0)
            }
        }
    }

    program.blockIds.forEachIndexed { idx, blockId ->
        val block = app.blocks.find { it.id == blockId }
        if (block != null) {
            walk(block, idx == 0)
        } else if (app.weeks.any { it.id == blockId }) {
            addWeek(idx == 0)
        }
    }

    if (days == 0) return null
    return ap.startDate to ap.startDate.plusDays((days - 1).toLong())
}

/** Every planned slot of [movementId] in the active program, keyed by the date it falls on. */
private fun movementSlotsByDate(
    app: AppState,
    activeProgramId: Long,
    movementId: Long
): Map<LocalDate, List<ExerciseEntity>> {
    val ap = app.activePrograms.find { it.id == activeProgramId } ?: return emptyMap()
    val program = app.programs.find { it.id == ap.programId } ?: return emptyMap()

    val exercisesByDate = mutableMapOf<LocalDate, List<ExerciseEntity>>()
    var currentDate = ap.startDate

    fun walkWeek(week: WeekEntity, isFirstBlock: Boolean, weekIdx: Int) {
        val startDayIndex = if (isFirstBlock && weekIdx == 0) {
            ap.startDate.dayOfWeek.value - 1
        } else {
            0
        }
        for (i in startDayIndex until 7) {
            val slotId = week.dayIds.getOrNull(i)
            if (slotId != null) {
                val slotExercises = exercisesInWeekSlot(app, slotId)
                if (slotExercises.any { it.movementId == movementId }) {
                    exercisesByDate[currentDate] = slotExercises
                }
            }
            currentDate = currentDate.plusDays(1)
        }
    }

    fun walk(block: BlockEntity, isFirstBlock: Boolean) {
        block.subBlockIds.forEach { subId ->
            app.blocks.find { it.id == subId }?.let { walk(it, false) }
        }
        block.weekIds.forEachIndexed { weekIdx, weekId ->
            app.weeks.find { it.id == weekId }?.let { walkWeek(it, isFirstBlock, weekIdx) }
        }
    }

    program.blockIds.forEachIndexed { idx, blockId ->
        val block = app.blocks.find { it.id == blockId }
        if (block != null) {
            walk(block, idx == 0)
        } else {
            app.weeks.find { it.id == blockId }?.let { walkWeek(it, idx == 0, 0) }
        }
    }

    return exercisesByDate
}

/** Variant names ("Medium", "Wide", ...) of [movementId] that the active program actually plans. */
fun plannedVariantsForMovement(
    app: AppState,
    activeProgramId: Long,
    movementId: Long
): List<String> {
    return movementSlotsByDate(app, activeProgramId, movementId).values
        .flatten()
        .filter { it.movementId == movementId }
        .map { it.name.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
}

/**
 * Planned weights for [movementId], optionally narrowed to one [variant] (exercise name).
 * An empty or null variant means "every variant of the movement".
 */
fun goalSeriesForMovement(
    app: AppState,
    activeProgramId: Long,
    movementId: Long,
    reps: Int,
    variant: String? = null
): List<PerformancePoint> {
    val ap = app.activePrograms.find { it.id == activeProgramId } ?: return emptyList()
    val baseRmList = ap.exerciseDailyRms[movementId] ?: return emptyList()
    val exercisesByDate = movementSlotsByDate(app, activeProgramId, movementId)
    val wantedVariant = variant?.trim()?.takeIf { it.isNotEmpty() }

    val points = mutableListOf<PerformancePoint>()
    for ((date, slotExercises) in exercisesByDate) {
        val dayIndex = java.time.temporal.ChronoUnit.DAYS.between(ap.startDate, date).toInt()
        val baseRm = baseRmList.getOrNull(dayIndex) ?: 0
        if (baseRm <= 0) continue

        val adjustmentPct = effectiveAdjustmentPercent(app, activeProgramId, date)
        val adjustedBaseRm = scaledBaseRm(baseRm, adjustmentPct)

        val plannedWeights = slotExercises
            .filter { it.movementId == movementId }
            .filter { wantedVariant == null || it.name.trim().equals(wantedVariant, ignoreCase = true) }
            .flatMap { exercise -> exercise.entries.filter { it.reps == reps } }
            .map { entry -> kotlin.math.ceil(adjustedBaseRm * (entry.intensity / 100.0)).toDouble() }

        val maxWeight = plannedWeights.maxOrNull() ?: continue
        points.add(PerformancePoint(date = date, value = maxWeight))
    }

    return points.sortedBy { it.date }
}

/** Variant label used by the dashboard filter: the exercise's live name, falling back to the name
 *  captured on the entry, and finally to "" (untagged entries, e.g. imported or hand-added). */
fun PerformanceEntry.variantName(app: AppState): String {
    val live = exerciseId?.let { id -> app.exercises.find { it.id == id }?.name?.trim() }
    if (!live.isNullOrBlank()) return live
    return variant.trim()
}

fun List<PerformanceEntry>.filterByReps(reps: Int): List<PerformanceEntry> {
    return filter { it.reps == reps }
}

fun List<PerformanceEntry>.seriesForMovement(
    movementId: Long,
    activeProgramId: Long?
): List<PerformancePoint> {
    if (activeProgramId == null) return emptyList()
    return filter { it.movementId == movementId && it.activeProgramId == activeProgramId }
        .sortedBy { it.date }
        .map { PerformancePoint(date = it.date, value = it.weightKg) }
}

fun List<PerformancePoint>.personalBest(): PerformancePoint? = maxByOrNull { it.value }

fun List<PerformancePoint>.latest(): PerformancePoint? = maxByOrNull { it.date }

fun List<PerformancePoint>.progressPercentSinceStart(): Double? {
    if (isEmpty()) return null
    val start = first().value
    if (start == 0.0) return null
    val end = last().value
    return ((end - start) / start) * 100.0
}

fun shouldSeedGeneratedData(state: AppState): Boolean {
    return !state.hasSeededGeneratedData &&
        state.movements.isEmpty() &&
        state.exercises.isEmpty() &&
        state.programs.isEmpty() &&
        state.activePrograms.isEmpty() &&
        state.performanceEntries.isEmpty()
}

object DemoSeedData {
    private data class DemoCurve(
        val movementName: String,
        val startWeight: Double,
        val endWeight: Double,
        val intervalDays: Long = 4L
    )

    private val curves = listOf(
        DemoCurve("Barbell Bench Press", 150.0, 200.0),
        DemoCurve("Back Squat", 180.0, 240.0),
        DemoCurve("Deadlift", 220.0, 280.0),
        DemoCurve("Overhead Press", 80.0, 100.0)
    )

    fun performanceEntries(
        movementIds: Map<String, Long>,
        endDate: LocalDate,
        pointsPerMovement: Int = 8,
        activeProgramId: Long? = null
    ): List<PerformanceEntry> {
        var nextGeneratedId = -1L
        return curves.mapNotNull { curve ->
            val movementId = movementIds[curve.movementName] ?: return@mapNotNull null
            val weights = interpolateLinear(
                start = curve.startWeight,
                end = curve.endWeight,
                count = pointsPerMovement
            )
            val startDate = endDate.minusDays(curve.intervalDays * (weights.size - 1))
            weights.mapIndexed { index, weight ->
                PerformanceEntry(
                    id = nextGeneratedId--,
                    movementId = movementId,
                    activeProgramId = activeProgramId,
                    date = startDate.plusDays(curve.intervalDays * index),
                    weightKg = weight,
                    note = "Generated demo"
                )
            }
        }.flatten()
    }

    private fun interpolateLinear(start: Double, end: Double, count: Int): List<Double> {
        if (count <= 1) return listOf(end)
        return (0 until count).map { index ->
            val ratio = index.toDouble() / (count - 1)
            roundToHalf(start + ((end - start) * ratio))
        }
    }

    private fun roundToHalf(value: Double): Double = kotlin.math.round(value * 2.0) / 2.0
}

private fun entryWeightKg(entry: SetEntry, adjustedBase: Int?): Double? {
    entry.weightKg?.let { return it.toDouble() }
    if (adjustedBase == null) return null
    return kotlin.math.ceil(adjustedBase * (entry.intensity / 100.0)).toDouble()
}

fun bestPerformanceFromSets(
    entries: List<SetEntry>,
    baseRm: Int?,
    adjustmentPct: Int = 0
): Pair<Double, Int>? {
    val adjustedBase = baseRm?.let { scaledBaseRm(it, adjustmentPct) }
    val finishedEntries = entries.filter { it.finished == true }
    if (finishedEntries.isEmpty()) return null
    val best = finishedEntries.maxByOrNull { entry ->
        entryWeightKg(entry, adjustedBase) ?: 0.0
    } ?: return null
    val weight = entryWeightKg(best, adjustedBase) ?: return null
    return weight to best.reps
}

fun bestWeightPerReps(
    entries: List<SetEntry>,
    baseRm: Int?,
    adjustmentPct: Int = 0
): Map<Int, Double> {
    val adjustedBase = baseRm?.let { scaledBaseRm(it, adjustmentPct) }
    return entries
        .filter { it.finished == true }
        .groupBy { it.reps }
        .mapValues { (_, list) ->
            list.maxOf { entry ->
                entryWeightKg(entry, adjustedBase) ?: 0.0
            }
        }
        .filterValues { it > 0.0 }
}

fun sessionBestPerformanceEntries(
    session: SessionEntity,
    exercises: List<ExerciseEntity>,
    activeProgramId: Long?,
    adjustmentPct: Int = 0
): List<PerformanceEntry> {
    val result = mutableListOf<PerformanceEntry>()
    val date = session.date?.toLocalDate() ?: return result
    val baseRms = session.baseRms ?: emptyMap()
    for (exerciseId in session.exerciseIds) {
        val exercise = exercises.find { it.id == exerciseId } ?: continue
        val baseRm = baseRms[exercise.movementId]
        val bestByReps = bestWeightPerReps(exercise.entries.toList(), baseRm, adjustmentPct)
        for ((reps, weight) in bestByReps) {
            result.add(
                PerformanceEntry(
                    id = 0,
                    movementId = exercise.movementId,
                    activeProgramId = activeProgramId,
                    date = date,
                    weightKg = weight,
                    reps = reps,
                    sessionId = session.id,
                    note = "${reps}×${weight.toInt()}kg auto",
                    exerciseId = exercise.id,
                    variant = exercise.name.trim()
                )
            )
        }
    }
    return result
}
