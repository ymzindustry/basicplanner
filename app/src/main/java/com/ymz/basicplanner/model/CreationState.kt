package com.ymz.basicplanner.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil

/** A week plus the ordered path of block indices from the program root (index 0 = first block level). */
private data class WeekPath(val week: WeekEntity, val blockIndices: List<Int>)

class CreationState {


    var currentUserName by mutableStateOf("Yumzi")
    val movements: SnapshotStateList<MovementEntity> = mutableStateListOf()
    val exercises: SnapshotStateList<ExerciseEntity> = mutableStateListOf()
    val sessions: SnapshotStateList<SessionEntity> = mutableStateListOf()
    val timedSessions: SnapshotStateList<TimedSessionEntity> = mutableStateListOf()
    val days: SnapshotStateList<DayEntity> = mutableStateListOf()
    val weeks: SnapshotStateList<WeekEntity> = mutableStateListOf()
    val blocks: SnapshotStateList<BlockEntity> = mutableStateListOf()
    val programs: SnapshotStateList<ProgramEntity> = mutableStateListOf()

    val activePrograms: SnapshotStateList<ActiveProgram> = mutableStateListOf()
    val items: SnapshotStateList<Item> = mutableStateListOf()
    val itemFamilies: SnapshotStateList<ItemFamily> = mutableStateListOf()
    val performanceEntries: SnapshotStateList<PerformanceEntry> = mutableStateListOf()
    val modules: SnapshotStateList<com.ymz.basicplanner.model.module.ModuleDefinition> = mutableStateListOf()
    val moduleAttachments: SnapshotStateList<com.ymz.basicplanner.model.module.ModuleAttachment> = mutableStateListOf()
    var selectedActiveProgramId: Long? = null
    var hasSeededGeneratedData by mutableStateOf(false)
    val dataVersion = mutableStateOf(0)

    companion object {
        const val REST_DAY_ID: Long = -1L

        /** Marker stored in an unfilled weekday slot (no exercise/session picked yet). */
        const val EMPTY_DAY_ID: Long = Long.MIN_VALUE
    }
    private var nextId = 1L
    fun newId(): Long = nextId++

    /** Returns the deepest block level available in this program (0 = top-level blocks only). */
    fun maxBlockLevel(program: ProgramEntity): Int {
        return flattenWeeks(program).maxOfOrNull { it.blockIndices.size - 1 } ?: -1
    }

    fun createAndHangItem(entityId: Long, activeProgramId: Long?, family: ItemFamily, value: String) {
        val newItem = Item(
            id = newId(),
            activeProgramId = activeProgramId,
            entityId = entityId,
            itemFamilyId = family.id,
            value = value
        )
        items.add(newItem)
    }

    fun createAndHangItem(entity: BaseEntity, activeProgram: ActiveProgram?, family: ItemFamily, value: String) {
        createAndHangItem(entity.id, activeProgram?.id, family, value)
    }

    fun getItemsForEntity(entityId: Long, activeProgramId: Long?): List<Item> {
        return items.filter { it.entityId == entityId && it.activeProgramId == activeProgramId }
    }
    fun updateHangedItem(item: Item, newValue: String) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item.copy(value = newValue)
        }
    }

    fun deleteHangedItem(item: Item) {
        items.remove(item)
    }
    
    fun createItemFamily(name: String, description: String, itemType: ItemType, defaultValue: String): ItemFamily {
        val newFamily = ItemFamily(
            id = newId(),
            name = name,
            description = description,
            itemType = itemType,
            defaultValue = defaultValue
        )
        itemFamilies.add(newFamily)
        return newFamily
    }

    fun updateItemFamily(itemFamily: ItemFamily, newName: String, newDescription: String, newItemType: ItemType, newDefaultValue: String) {
        val index = itemFamilies.indexOfFirst { it.id == itemFamily.id }
        if (index != -1) {
            itemFamilies[index] = itemFamily.copy(name = newName, description = newDescription, itemType = newItemType, defaultValue = newDefaultValue)
        }
    }
    
    fun deleteItemFamily(itemFamily: ItemFamily) {
        
        items.removeAll { it.itemFamilyId == itemFamily.id }
        
        itemFamilies.remove(itemFamily)
    }

    fun addPerformanceEntry(
        movementId: Long,
        activeProgramId: Long?,
        date: LocalDate,
        weightKg: Double,
        note: String = "",
        reps: Int = 1
    ): PerformanceEntry {
        val entry = PerformanceEntry(
            id = newId(),
            movementId = movementId,
            activeProgramId = activeProgramId,
            date = date,
            weightKg = weightKg,
            note = note,
            reps = reps
        )
        performanceEntries.add(entry)
        return entry
    }

    fun deletePerformanceEntry(entry: PerformanceEntry) {
        performanceEntries.removeAll { it.id == entry.id }
    }

    fun recordSessionBestPerformances(sessionId: Long, activeProgramId: Long?) {
        val session = sessions.find { it.id == sessionId } ?: return
        val exercises = session.exerciseIds.mapNotNull { exId ->
            this.exercises.find { it.id == exId }
        }
        val adjustmentPct = if (activeProgramId != null && session.date != null) {
            session.adjustmentPercent + effectiveAdjustmentPercent(
                toAppState(),
                activeProgramId,
                session.date!!.toLocalDate()
            )
        } else {
            session.adjustmentPercent
        }
        val entries = sessionBestPerformanceEntries(
            session = session,
            exercises = exercises,
            activeProgramId = activeProgramId,
            adjustmentPct = adjustmentPct
        )
        val sessionDate = session.date?.toLocalDate()
        performanceEntries.removeAll { it.note.contains("auto", true) && it.date == sessionDate }
        performanceEntries.addAll(
            entries.map { it.copy(id = newId()) }
        )
    }

    fun performancesForMovement(movementId: Long, activeProgramId: Long?): List<PerformanceEntry> {
        if (activeProgramId == null) return emptyList()
        return performanceEntries
            .filter { it.movementId == movementId && it.activeProgramId == activeProgramId }
            .sortedBy { it.date }
    }

    fun createEntity(kind: EntityKind, name: String, selectedChildIds: List<Long> = emptyList()): BaseEntity {
        val childIds = selectedChildIds
        val entity = when (kind) {
            EntityKind.MOVEMENT -> MovementEntity(newId(), name, currentUserName)
            EntityKind.EXERCISE -> {
                val movementId = childIds.firstOrNull() ?: -1L
                ExerciseEntity(newId(), movementId, name, currentUserName)
            }
            EntityKind.SESSION  -> SessionEntity(newId(), name, currentUserName, childIds.toMutableList())
            EntityKind.DAY      -> DayEntity(newId(), name, currentUserName, childIds.toMutableList())
            EntityKind.WEEK     -> WeekEntity(newId(), name, currentUserName, childIds.toMutableList())
            EntityKind.BLOCK    -> {
                val subBlockIds = childIds.filter { childId ->
                    blocks.any { block -> block.id == childId }
                }
                val weekIds = childIds.filter { it !in subBlockIds }
                BlockEntity(
                    id = newId(),
                    name = name,
                    owner = currentUserName,
                    weekIds = weekIds.toMutableList(),
                    subBlockIds = subBlockIds.toMutableList()
                )
            }
            EntityKind.PROGRAM  -> ProgramEntity(newId(), name, currentUserName, childIds.toMutableList())
        }
        when (kind) {
            EntityKind.MOVEMENT -> movements.add(entity as MovementEntity)
            EntityKind.EXERCISE -> exercises.add(entity as ExerciseEntity)
            EntityKind.SESSION  -> sessions.add(entity as SessionEntity)
            EntityKind.DAY      -> days.add(entity as DayEntity)
            EntityKind.WEEK     -> weeks.add(entity as WeekEntity)
            EntityKind.BLOCK    -> blocks.add(entity as BlockEntity)
            EntityKind.PROGRAM  -> programs.add(entity as ProgramEntity)
        }
        bumpDataVersion()
        return entity
    }

    private fun bumpDataVersion() {
        dataVersion.value++
    }

    fun copyEntity(kind: EntityKind, entity: BaseEntity) {
        val newName = "(Copy) ${entity.name}"
        when (kind) {
            EntityKind.MOVEMENT -> {
                val original = entity as MovementEntity
                movements.add(original.copy(id = newId(), name = newName))
            }
            EntityKind.EXERCISE -> {
                val original = entity as ExerciseEntity
                val newExercise = original.copy(
                    id = newId(),
                    name = newName,
                    entries = original.entries.map { it.copy(id = newId()) }.toMutableList()
                )
                exercises.add(newExercise)
            }
            EntityKind.SESSION -> {
                val original = entity as SessionEntity
                sessions.add(original.copy(
                    id = newId(),
                    name = newName,
                    exerciseIds = original.exerciseIds.toMutableList()
                ))
            }
            EntityKind.DAY -> {
                val original = entity as DayEntity
                val newTimedSessionIds = original.timedSessionIds.mapNotNull { tsId ->
                    timedSessions.find { it.id == tsId }?.let {
                        val newTs = it.copy(id = newId())
                        timedSessions.add(newTs)
                        newTs.id
                    }
                }
                days.add(original.copy(
                    id = newId(),
                    name = newName,
                    timedSessionIds = newTimedSessionIds.toMutableList(),
                    subAdjustments = original.subAdjustments.toMutableList()
                ))
            }
            EntityKind.WEEK -> {
                val original = entity as WeekEntity
                weeks.add(original.copy(
                    id = newId(),
                    name = newName,
                    dayIds = original.dayIds.toMutableList(),
                    subAdjustments = original.subAdjustments.toMutableList()
                ))
            }
            EntityKind.BLOCK -> {
                val original = entity as BlockEntity
                blocks.add(original.copy(
                    id = newId(),
                    name = newName,
                    weekIds = original.weekIds.toMutableList(),
                    subAdjustments = original.subAdjustments.toMutableList(),
                    level = original.level
                ))
            }
            EntityKind.PROGRAM -> {
                val original = entity as ProgramEntity
                programs.add(original.copy(
                    id = newId(),
                    name = newName,
                    blockIds = original.blockIds.toMutableList(),
                    subAdjustments = original.subAdjustments.toMutableList()
                ))
            }
        }
    }

    fun getEntities(kind: EntityKind): List<BaseEntity> = when (kind) {
        EntityKind.MOVEMENT -> movements
        EntityKind.EXERCISE -> exercises
        EntityKind.SESSION  -> sessions
        EntityKind.DAY      -> days
        EntityKind.WEEK     -> weeks
        EntityKind.BLOCK    -> blocks
        EntityKind.PROGRAM  -> programs
    }

    fun availableChildren(kind: EntityKind): List<BaseEntity> = when (kind) {
        EntityKind.MOVEMENT -> emptyList()
        EntityKind.EXERCISE -> movements
        EntityKind.SESSION  -> exercises
        EntityKind.DAY      -> sessions
        EntityKind.WEEK     -> days + sessions + exercises
        EntityKind.BLOCK    -> weeks + blocks
        EntityKind.PROGRAM  -> blocks + weeks
    }

    /** Helper for the edit dialog to display a child entity's name. */
    fun exerciseDisplayName(ex: ExerciseEntity): String {
        val mName = movements.find { it.id == ex.movementId }?.name ?: ""
        return if (ex.name.isNotBlank()) "$mName ${ex.name}" else mName
    }

    fun nameOf(parentKind: EntityKind, childId: Long): String? {
        return when (parentKind) {
            EntityKind.EXERCISE -> (movements.find { it.id == childId })?.name
            EntityKind.DAY -> (sessions.find { it.id == childId })?.name
            EntityKind.SESSION -> (exercises.find { it.id == childId })?.let { exerciseDisplayName(it) }
            EntityKind.WEEK -> (days.find { it.id == childId })?.name
                ?: (sessions.find { it.id == childId })?.name
                ?: (exercises.find { it.id == childId })?.let { exerciseDisplayName(it) }
            EntityKind.BLOCK -> (weeks.find { it.id == childId })?.name ?: (blocks.find { it.id == childId })?.name
            EntityKind.PROGRAM -> (blocks.find { it.id == childId })?.name ?: (weeks.find { it.id == childId })?.name
            else -> null
        }
    }

    fun childrenOf(kind: EntityKind, parent: BaseEntity): List<BaseEntity> = when (kind) {
        
        EntityKind.MOVEMENT -> emptyList()

        
        EntityKind.EXERCISE -> {
            val ex = parent as ExerciseEntity
            val movement = movements.find { it.id == ex.movementId }
            if (movement != null) listOf(movement) else emptyList()
        }
        EntityKind.SESSION -> {
            val s = parent as SessionEntity
            s.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
        }
        EntityKind.DAY -> {
            val d = parent as DayEntity
            d.timedSessionIds
                .mapNotNull { id -> timedSessions.find { it.id == id } }
                .sortedBy { it.time }
        }
        EntityKind.WEEK -> {
            val w = parent as WeekEntity
            // A weekday slot may hold a day, a session or a single exercise: no wrapper is created
            // around a bare session/exercise, so the slot always shows the entity's live name.
            w.dayIds.mapNotNull { id -> resolveWeekSlot(id) }
        }
        EntityKind.BLOCK -> {
            val b = parent as BlockEntity
            val subBlocks = b.subBlockIds.mapNotNull { id -> blocks.find { it.id == id } }
            val weeksList = b.weekIds.mapNotNull { id -> weeks.find { it.id == id } }
            subBlocks + weeksList
        }
        EntityKind.PROGRAM -> {
            val p = parent as ProgramEntity
            // A program may hold blocks and (nesting-free) weeks directly, without a wrapper block.
            p.blockIds.mapNotNull { id -> blocks.find { it.id == id } ?: weeks.find { it.id == id } }
        }
    }



    /**
     * Dropping a single exercise into a week slot makes the app auto-create a day, a timed session
     * and a session around it (negative ids), all named after the exercise's display name at that
     * moment. Those are snapshots, so they are refreshed whenever the exercise changes: the program
     * then shows the exercise's live movement and variant.
     */
    private fun syncExerciseWrapperNames(exercise: ExerciseEntity) {
        val display = exerciseDisplayName(exercise)
        if (display.isBlank()) return
        weeks.forEach { week ->
            week.dayIds.forEach { slotId ->
                val day = days.find { it.id == slotId } ?: return@forEach
                if (day.id >= 0) return@forEach
                val timed = day.timedSessionIds
                    .mapNotNull { tsId -> timedSessions.find { it.id == tsId } }
                    .singleOrNull() ?: return@forEach
                val session = sessions.find { it.id == timed.sessionId } ?: return@forEach
                if (session.id >= 0) return@forEach
                if (session.exerciseIds != listOf(exercise.id)) return@forEach
                if (session.name != display) session.name = display
                if (timed.name != display) timed.name = display
                if (day.name != display) day.name = display
            }
        }
        bumpDataVersion()
    }

    /** Same idea for a whole session dropped into a week slot: the wrapper day follows its name. */
    private fun syncSessionWrapperNames(session: SessionEntity) {
        if (session.name.isBlank()) return
        weeks.forEach { week ->
            week.dayIds.forEach { slotId ->
                val day = days.find { it.id == slotId } ?: return@forEach
                if (day.id >= 0) return@forEach
                val timed = day.timedSessionIds
                    .mapNotNull { tsId -> timedSessions.find { it.id == tsId } }
                    .singleOrNull() ?: return@forEach
                if (timed.sessionId != session.id) return@forEach
                if (timed.name != session.name) timed.name = session.name
                if (day.name != session.name) day.name = session.name
            }
        }
        bumpDataVersion()
    }

    /** And for a week dropped into a program: the wrapper block follows the week's name. */
    private fun syncWeekWrapperNames(week: WeekEntity) {
        if (week.name.isBlank()) return
        blocks.filter { it.id < 0 && it.subBlockIds.isEmpty() && it.weekIds == listOf(week.id) }
            .forEach { if (it.name != week.name) it.name = week.name }
        bumpDataVersion()
    }

    /**
     * Writes one weekday slot of a week: the list is padded to 7 slots with rest days first, so the
     * position always means the same weekday (index 0 = Monday). Replacing an occupied slot simply
     * overwrites its entity.
     */
    fun assignToWeekDay(slots: MutableList<Long>, dayIndex: Int, childId: Long) {
        while (slots.size <= dayIndex) slots.add(EMPTY_DAY_ID)
        slots[dayIndex] = childId
    }

    /**
     * The exercise to use when a movement is dropped straight into a program (a weekday slot) while
     * no exercise exists for it yet: reuses the blank-variant exercise of that movement, otherwise
     * creates it. The user fills the variant and the sets afterwards.
     */
    /**
     * The exercise of a movement for a given variant name: reuses the matching exercise, otherwise
     * creates it. Picking a movement in the program therefore always produces a real exercise.
     */
    fun exerciseForMovement(movement: MovementEntity, variantName: String): ExerciseEntity {
        val wanted = variantName.trim()
        val existing = exercises.firstOrNull {
            it.movementId == movement.id && it.name == wanted && isPlanLevel(it)
        }
        if (existing != null) return existing
        return createEntity(EntityKind.EXERCISE, wanted, listOf(movement.id)) as ExerciseEntity
    }

    fun exerciseForMovement(movement: MovementEntity): ExerciseEntity {
        val existing = exercises.firstOrNull {
            it.movementId == movement.id && it.name.isBlank() && isPlanLevel(it)
        }
        if (existing != null) return existing
        return createEntity(EntityKind.EXERCISE, "", listOf(movement.id)) as ExerciseEntity
    }

    /** Ids of the throw-away copies owned by session instances (sessions with negative ids). */
    private fun instanceCopyIds(): Set<Long> =
        sessions.filter { it.id < 0 }.flatMap { it.exerciseIds }.toSet()

    /**
     * True for the entities the user manages in the lists and pickers, false for the copies a
     * session instance owns. Exercises are matched against the instance copies instead of their id
     * sign, so a plan exercise can never become invisible just because its id is unusual.
     */
    fun isPlanLevel(entity: BaseEntity): Boolean = when (entity) {
        is ExerciseEntity -> entity.id !in instanceCopyIds()
        else -> entity.id >= 0
    }

    /** Resolves one weekday slot of a week: an empty/rest day, a day, a session or an exercise. */
    fun resolveWeekSlot(id: Long): BaseEntity? = when {
        // An unfilled weekday and an explicit rest day both render as an empty day.
        id == REST_DAY_ID || id == EMPTY_DAY_ID -> DayEntity(id = REST_DAY_ID, name = "", owner = null)
        else -> days.find { it.id == id }
            ?: sessions.find { it.id == id }
            ?: exercises.find { it.id == id }
    }

    /** Kind of a week slot, for the tree renderer. */
    fun weekSlotKind(entity: BaseEntity): EntityKind = when (entity) {
        is DayEntity -> EntityKind.DAY
        is SessionEntity -> EntityKind.SESSION
        is ExerciseEntity -> EntityKind.EXERCISE
        else -> EntityKind.DAY
    }

    /**
     * Older versions wrapped a bare exercise/session dropped in a week slot in a throw-away day,
     * and a week dropped in a program in a throw-away block, naming the wrapper after the child.
     * Those wrappers are removed here so the hierarchy stays flat (idempotent: only negative-id
     * containers created by the app are considered, never entities the user made by hand).
     */
    fun migrateAutoWrappers() {
        weeks.forEach { week ->
            val migrated = week.dayIds.map { slotId ->
                if (slotId == REST_DAY_ID) return@map slotId
                val day = days.find { it.id == slotId } ?: return@map slotId
                if (day.id >= 0) return@map slotId
                val timed = day.timedSessionIds
                    .mapNotNull { tsId -> timedSessions.find { it.id == tsId } }
                    .singleOrNull() ?: return@map slotId
                val session = sessions.find { it.id == timed.sessionId } ?: return@map slotId
                if (session.id >= 0) return@map slotId
                if (session.exerciseIds.size == 1) session.exerciseIds.first() else session.id
            }
            if (migrated != week.dayIds) {
                week.dayIds.clear()
                week.dayIds.addAll(migrated)
            }
        }
        programs.forEach { program ->
            val migrated = program.blockIds.map { blockId ->
                val block = blocks.find { it.id == blockId } ?: return@map blockId
                if (block.id >= 0 || block.subBlockIds.isNotEmpty() || block.weekIds.size != 1) {
                    return@map blockId
                }
                block.weekIds.first()
            }
            if (migrated != program.blockIds) {
                program.blockIds.clear()
                program.blockIds.addAll(migrated)
            }
        }
        bumpDataVersion()
    }

    fun updateEntity(
        kind: EntityKind,
        entity: BaseEntity,
        newName: String,
        selectedChildIds: List<Long>?
    ) {
        entity.name = newName
        var entityToSave = entity
        val childIds = selectedChildIds
        if (childIds != null) {
            when (kind) {
                EntityKind.MOVEMENT -> Unit
                EntityKind.EXERCISE -> {
                    
                    if (entity is ExerciseEntity && childIds.isNotEmpty()) {
                        val newMovementId = childIds.first()
                        
                        entityToSave = entity.copy(movementId = newMovementId)
                        entityToSave.name = newName
                        // Containers auto-created around this exercise must follow its new name.
                        syncExerciseWrapperNames(entityToSave as ExerciseEntity)
                    }
                }
                EntityKind.SESSION -> {
                    (entity as SessionEntity).exerciseIds.clear()
                    entity.exerciseIds.addAll(childIds)
                    if (entity is SessionEntity) syncSessionWrapperNames(entity)
                }
                EntityKind.DAY -> {
                    (entity as DayEntity).timedSessionIds.clear()
                    entity.timedSessionIds.addAll(childIds)
                }
                EntityKind.WEEK -> {
                    (entity as WeekEntity).dayIds.clear()
                    entity.dayIds.addAll(childIds)
                    if (entity is WeekEntity) syncWeekWrapperNames(entity)
                }
                EntityKind.BLOCK -> {
                    val b = entity as BlockEntity
                    // A block may not contain itself nor one of its own ancestors: that is a loop.
                    val ancestors = blockAncestors(b.id)
                    val subBlockIds = childIds.filter { childId ->
                        childId != b.id && childId !in ancestors && blocks.any { block -> block.id == childId }
                    }
                    val weekIds = childIds.filter { it !in subBlockIds && it !in ancestors && it != b.id }
                    b.weekIds.clear()
                    b.weekIds.addAll(weekIds)
                    b.subBlockIds.clear()
                    b.subBlockIds.addAll(subBlockIds)
                }
                EntityKind.PROGRAM -> {
                    (entity as ProgramEntity).blockIds.clear()
                    entity.blockIds.addAll(childIds)
                }
            }
        }

        
        val list = getEntities(kind) as? SnapshotStateList<BaseEntity>
        
        val index = list?.indexOfFirst { it.id == entity.id } ?: -1

        if (index != -1 && list != null) {
            
            list[index] = entityToSave
        }

        recomputeBlockLevels()
        refreshActiveProgramsStatus()
        bumpDataVersion()

    }

    fun blockDescendants(blockId: Long): Set<Long> {
        val result = mutableSetOf<Long>()
        fun visit(id: Long) {
            blocks.find { it.id == id }?.subBlockIds?.forEach { childId ->
                if (result.add(childId)) visit(childId)
            }
        }
        visit(blockId)
        return result
    }

    /** Blocks that (transitively) contain [blockId]; putting one of them inside it makes a loop. */
    fun blockAncestors(blockId: Long): Set<Long> {
        val result = mutableSetOf<Long>()
        fun visit(id: Long) {
            blocks.forEach { candidate ->
                if (candidate.id != blockId && id in candidate.subBlockIds && result.add(candidate.id)) {
                    visit(candidate.id)
                }
            }
        }
        visit(blockId)
        return result
    }

    /**
     * Drops child links that would close a loop (a block containing one of its own ancestors).
     * A loop makes every tree walk — and every expansion in the UI — recurse forever, which is a
     * hard crash, so broken data is repaired on load instead of being rendered.
     */
    fun breakBlockCycles() {
        val onPath = mutableSetOf<Long>()
        fun visit(block: BlockEntity) {
            onPath.add(block.id)
            val kept = block.subBlockIds.filter { childId ->
                val child = blocks.find { it.id == childId }
                if (child == null || childId in onPath) {
                    false
                } else {
                    visit(child)
                    true
                }
            }
            if (kept.size != block.subBlockIds.size) {
                block.subBlockIds.clear()
                block.subBlockIds.addAll(kept)
            }
            onPath.remove(block.id)
        }
        programs.forEach { program ->
            program.blockIds.forEach { id -> blocks.find { it.id == id }?.let { visit(it) } }
        }
    }

    /**
     * Re-derives every block's [BlockEntity.level] from the tree: a block directly in a program is
     * level 0, its sub-blocks level 1, and so on. Levels are therefore authored by the hierarchy
     * itself — any nesting depth and any mix of weeks/blocks is legal — and stale values (stored
     * bottom-up by older builds, or left behind by a move) are corrected on load and after edits.
     * Cycles are guarded against, so a malformed tree cannot hang the traversal.
     */
    fun recomputeBlockLevels() {
        val depths = mutableMapOf<Long, Int>()
        fun walk(blockId: Long, depth: Int) {
            if (depths.containsKey(blockId)) return
            val block = blocks.find { it.id == blockId } ?: return
            depths[blockId] = depth
            block.subBlockIds.forEach { subId -> walk(subId, depth + 1) }
        }
        programs.forEach { program -> program.blockIds.forEach { id -> walk(id, 0) } }
        // Blocks outside every program carry no level (null) instead of a stale number.
        blocks.forEach { block -> block.level = depths[block.id] }
    }

    fun deleteEntity(kind: EntityKind, entity: BaseEntity) {
        val id = entity.id
        bumpDataVersion()
        items.removeAll { it.entityId == id }
        when (kind) {
            EntityKind.MOVEMENT -> {
                movements.remove(entity)
                exercises.removeAll { it.movementId == id }
            }
            EntityKind.EXERCISE -> {
                exercises.remove(entity)
                sessions.forEach { it.exerciseIds.removeAll { exId -> exId == id } }
            }
            EntityKind.SESSION -> {
                val timedSessionIdsToDelete = timedSessions.filter { it.sessionId == id }.map { it.id }
                val wrapperDaysToDelete = days.filter { day ->
                    day.id < 0 && day.timedSessionIds.any { it in timedSessionIdsToDelete }
                }

                sessions.remove(entity)
                timedSessions.removeAll { it.sessionId == id }
                days.forEach { it.timedSessionIds.removeAll(timedSessionIdsToDelete) }

                wrapperDaysToDelete.forEach { deleteEntity(EntityKind.DAY, it) }
            }
            EntityKind.DAY -> {
                days.remove(entity)
                weeks.forEach { it.dayIds.removeAll { dayId -> dayId == id } }
            }
            EntityKind.WEEK -> {
                if (entity is WeekEntity) {
                    val wrapperDaysToDelete = entity.dayIds
                        .mapNotNull { dayId -> days.find { it.id == dayId } }
                        .filter { it.id < 0 }
                    wrapperDaysToDelete.forEach { deleteEntity(EntityKind.DAY, it) }
                }
                val wrapperBlocksToDelete = blocks.filter { block ->
                    block.id < 0 && block.weekIds.contains(id)
                }

                weeks.remove(entity)
                blocks.forEach { it.weekIds.removeAll { weekId -> weekId == id } }

                wrapperBlocksToDelete.forEach { deleteEntity(EntityKind.BLOCK, it) }
            }
            EntityKind.BLOCK -> {
                val toDelete = mutableListOf<BlockEntity>()
                fun collect(b: BlockEntity) {
                    toDelete.add(b)
                    b.subBlockIds.forEach { subId -> blocks.find { it.id == subId }?.let { collect(it) } }
                }
                collect(entity as BlockEntity)
                val idsToDelete = toDelete.map { it.id }.toSet()
                toDelete.forEach { blocks.remove(it) }
                blocks.forEach { it.subBlockIds.removeAll(idsToDelete) }
                programs.forEach { it.blockIds.removeAll(idsToDelete) }
                items.removeAll { it.entityId in idsToDelete }
            }
            EntityKind.PROGRAM -> {
                if (entity is ProgramEntity) {
                    val wrapperBlocksToDelete = entity.blockIds
                        .mapNotNull { blockId -> blocks.find { it.id == blockId } }
                        .filter { it.id < 0 }
                    wrapperBlocksToDelete.forEach { deleteEntity(EntityKind.BLOCK, it) }
                }
                programs.remove(entity as ProgramEntity)
                activePrograms.removeAll { it.programId == id }
            }
        }
        recomputeBlockLevels()
        refreshActiveProgramsStatus()
    }

    fun getActiveProgramForSession(sessionId: Long): ActiveProgram? {
        return activePrograms.find { it.sessionIds.contains(sessionId) }
    }
    
    /**
     * Every exercise planned on one weekday of a week. A slot may hold a day (its sessions), a bare
     * session or a single exercise directly, which is why the flat layout is resolved instead of
     * assuming a day -> timed session -> session chain.
     */
    fun exercisesInWeekSlot(slotId: Long): List<ExerciseEntity> = when (val entity = resolveWeekSlot(slotId)) {
        is ExerciseEntity -> listOf(entity)
        is SessionEntity -> entity.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
        is DayEntity -> entity.timedSessionIds
            .mapNotNull { tsId -> timedSessions.find { it.id == tsId }?.sessionId }
            .flatMap { sessionId -> sessions.find { it.id == sessionId }?.exerciseIds ?: emptyList() }
            .mapNotNull { id -> exercises.find { it.id == id } }
        else -> emptyList()
    }

    /** The weeks a program plans, whether they sit in a block or directly in the program. */
    fun weekIdsOfProgram(program: ProgramEntity): List<Long> {
        fun collect(block: BlockEntity): List<Long> =
            block.subBlockIds.flatMap { subId -> blocks.find { it.id == subId }?.let { collect(it) } ?: emptyList() } +
                block.weekIds
        return program.blockIds.flatMap { id ->
            blocks.find { it.id == id }?.let { collect(it) } ?: listOf(id)
        }
    }

    fun getUniqueMovementIdsForProgram(program: ProgramEntity): List<Long> {
        val movementIds = mutableSetOf<Long>()
        weekIdsOfProgram(program).forEach { weekId ->
            val week = weeks.find { it.id == weekId } ?: return@forEach
            week.dayIds.forEach { slotId ->
                exercisesInWeekSlot(slotId).forEach { movementIds.add(it.movementId) }
            }
        }
        return movementIds.toList().sorted()
    }


    /** Flatten the block hierarchy into an ordered list of WeekPath by depth-first traversal. */
    private fun flattenWeeks(program: ProgramEntity): List<WeekPath> {
        val result = mutableListOf<WeekPath>()
        val counters = mutableMapOf<Int, Int>() // level -> next block index

        fun walkBlock(block: BlockEntity, level: Int, path: MutableList<Int>) {
            val idx = counters.getOrPut(level) { 0 }
            counters[level] = idx + 1
            path.add(idx)

            block.weekIds.forEach { weekId ->
                weeks.find { it.id == weekId }?.let { w -> result.add(WeekPath(w, path.toList())) }
            }
            block.subBlockIds.forEach { subId ->
                blocks.find { it.id == subId }?.let { walkBlock(it, level + 1, path) }
            }

            path.removeAt(path.size - 1)
        }

        program.blockIds.forEach { bid ->
            val block = blocks.find { it.id == bid }
            if (block != null) {
                walkBlock(block, 0, mutableListOf())
            } else {
                // A week can sit directly in the program (no wrapper block).
                weeks.find { it.id == bid }?.let { w -> result.add(WeekPath(w, listOf(0))) }
            }
        }
        return result
    }

    private fun calculateSingleExerciseRms(
        program: ProgramEntity,
        settings: RmSettings,
        startDate: LocalDate,
        existingList: List<Int>? = null
    ): List<Int> {
        
        val weekPaths = flattenWeeks(program)
        val totalWeeks = weekPaths.size
        val totalDays = totalWeeks * 7
        if (totalDays == 0) return emptyList()

        
        if (settings.strategy == InterpolationStrategy.CONST) {
            return List(totalDays) { settings.start }
        }

        
        
        if (settings.strategy == InterpolationStrategy.CUSTOM && existingList != null && existingList.isNotEmpty()) {
            val oldStartVal = existingList.first().toDouble()
            val oldEndVal = existingList.last().toDouble()
            val oldRange = oldEndVal - oldStartVal
            val newRange = (settings.target - settings.start).toDouble()

            
            val projectedList = if (oldRange == 0.0) {
                
                List(existingList.size) {
                    
                    val ratio = it.toDouble() / (existingList.size - 1).coerceAtLeast(1)
                    ceil(settings.start + (ratio * newRange)).toInt().coerceAtLeast(0)
                }
            } else {
                existingList.map { oldVal ->
                    val ratio = (oldVal - oldStartVal) / oldRange
                    
                    
                    ceil(settings.start + (ratio * newRange)).toInt().coerceAtLeast(0)
                }
            }

            
            return if (projectedList.size == totalDays) {
                projectedList
            } else if (projectedList.size < totalDays) {
                
                val lastVal = projectedList.last()
                projectedList + List(totalDays - projectedList.size) { lastVal }
            } else {
                
                projectedList.take(totalDays)
            }
        }

        
        val result = mutableListOf<Int>()
        val diff = settings.target - settings.start
        var cursorDate = startDate.with(java.time.DayOfWeek.MONDAY)

        // For BLOCK strategy, resolve the block index at the chosen block level for each week.
        val blockIdxAtLevel: List<Int> = if (settings.strategy == InterpolationStrategy.BLOCK) {
            weekPaths.map { path -> path.blockIndices.getOrElse(settings.blockLevel) { path.blockIndices.last() } }
        } else emptyList()
        val totalBlocksAtLevel = if (blockIdxAtLevel.isNotEmpty()) (blockIdxAtLevel.maxOrNull() ?: 0) + 1 else 0

        weekPaths.forEachIndexed { weekIdx, path ->
            repeat(7) { dayInWeek ->
                val dayIdx = weekIdx * 7 + dayInWeek
                val ratio = when (settings.strategy) {
                    InterpolationStrategy.BLOCK -> if (totalBlocksAtLevel > 1) blockIdxAtLevel[weekIdx].toDouble() / (totalBlocksAtLevel - 1) else 1.0
                    InterpolationStrategy.WEEK -> if (totalWeeks > 1) weekIdx.toDouble() / (totalWeeks - 1) else 1.0
                    else -> if (totalDays > 1) dayIdx.toDouble() / (totalDays - 1) else 1.0
                }

                val value = ceil(settings.start + (diff * ratio)).toInt().coerceAtLeast(0)

                if (!cursorDate.isBefore(startDate)) {
                    result.add(value)
                }

                cursorDate = cursorDate.plusDays(1)
            }
        }

        
        return if (result.size > totalDays) result.take(totalDays)
        else if (result.size < totalDays) result + List(totalDays - result.size) { result.lastOrNull() ?: settings.target }
        else result
    }

    fun startProgram(
        program: ProgramEntity,
        startDate: LocalDate,
        name: String = program.name,
        
        rmSettings: Map<Long, RmSettings> = emptyMap()
    ): ActiveProgram {

        val dailyRms = rmSettings.mapValues { (movementId, settings) ->
            
            val list = calculateSingleExerciseRms(program, settings, startDate)
            if (list.isEmpty()) {

            } else if (list.contains(0)) {

            } else {

            }

            list
        }

        val active = ActiveProgram(
            id = newId(),
            programId = program.id,
            startDate = startDate,
            name = name,
            rmSettings = rmSettings,
            exerciseDailyRms = dailyRms
        )
        activePrograms.add(active)
        return active
    }

    fun updateActiveProgram(
        activeProgram: ActiveProgram,
        newName: String,
        newStartDate: LocalDate,
        newRmSettings: Map<Long, RmSettings>
    ) {
        val index = activePrograms.indexOfFirst { it.id == activeProgram.id }
        if (index == -1) return

        val program = programs.find { it.id == activeProgram.programId } ?: return
        val oldAp = activePrograms[index]
        
        val updatedDailyRms = oldAp.exerciseDailyRms.toMutableMap()
        val finalRmSettings = mutableMapOf<Long, RmSettings>()
        
        updatedDailyRms.keys.retainAll(newRmSettings.keys)

        newRmSettings.forEach { (movementId, newSetting) ->
            val oldSetting = oldAp.rmSettings[movementId]


            if (newSetting.strategy == InterpolationStrategy.CUSTOM) {
                
                if (oldSetting != null) {
                    finalRmSettings[movementId] = oldSetting
                } else {
                    
                    finalRmSettings[movementId] = newSetting
                }

                
                
            }

            
            
            else {
                
                finalRmSettings[movementId] = newSetting

                
                if (oldSetting != newSetting || oldAp.startDate != newStartDate) {
                    updatedDailyRms[movementId] = calculateSingleExerciseRms(
                        program = program,
                        settings = newSetting,
                        startDate = newStartDate,
                        existingList = null 
                    )
                }
            }
        }

        activePrograms[index] = oldAp.copy(
            name = newName,
            startDate = newStartDate,
            rmSettings = finalRmSettings, 
            exerciseDailyRms = updatedDailyRms,
            requiresUpdate = false
        )
    }

    fun refreshActiveProgramsStatus() {
        for (i in activePrograms.indices) {
            val ap = activePrograms[i]
            val program = programs.find { it.id == ap.programId } ?: continue

            
            // Count total days across the whole (possibly nested) hierarchy.
            val newTotalDays = flattenWeeks(program).size * 7

            val actualMovementIds = getUniqueMovementIdsForProgram(program).toSet()
            val updatedRms = ap.exerciseDailyRms.toMutableMap()
            val updatedSettings = ap.rmSettings.toMutableMap()
            var structureChanged = false

            
            val keysToRemove = updatedSettings.keys.filter { it !in actualMovementIds }
            if (keysToRemove.isNotEmpty()) {
                structureChanged = true
                keysToRemove.forEach { key -> updatedRms.remove(key); updatedSettings.remove(key) }
            }

            
            
            val newMovementIds = actualMovementIds.filter { it !in updatedSettings }
            if (newMovementIds.isNotEmpty()) {
                structureChanged = true
                newMovementIds.forEach { mId ->
                    updatedSettings[mId] = RmSettings(start = 0, target = 0, strategy = InterpolationStrategy.CONST)
                    updatedRms[mId] = List(newTotalDays) { 0 }
                }
            }

            
            updatedRms.forEach { (mId, list) ->
                if (list.size != newTotalDays) {
                    structureChanged = true
                    val settings = updatedSettings[mId]

                    
                    val safeSettings = settings ?: RmSettings(0, 0, InterpolationStrategy.CONST).also {
                        updatedSettings[mId] = it
                    }

                    
                    if (safeSettings.strategy == InterpolationStrategy.CUSTOM) {
                        
                        if (list.size < newTotalDays) {
                            
                            val lastVal = list.lastOrNull() ?: safeSettings.target
                            updatedRms[mId] = list + List(newTotalDays - list.size) { lastVal }
                        } else {
                            
                            updatedRms[mId] = list.take(newTotalDays)
                        }
                    } else {
                        
                        
                        updatedRms[mId] = calculateSingleExerciseRms(
                            program = program,
                            settings = safeSettings,
                            startDate = ap.startDate,
                            existingList = null 
                        )
                    }
                }
            }

            if (ap.requiresUpdate || structureChanged) {
                activePrograms[i] = ap.copy(
                    requiresUpdate = false,
                    exerciseDailyRms = updatedRms,
                    rmSettings = updatedSettings
                )
            }
        }
    }

    fun createSessionInstance(
        originalSession: SessionEntity,
        
        dailyRms: Map<Long, Int>,
        sessionDate: java.time.LocalDateTime,
        activeProgramId: Long
    ): SessionEntity {

        val newSessionId = -newId()
        val newExerciseIds = mutableListOf<Long>()

        originalSession.exerciseIds.forEach { originalExId ->
            val originalEx = exercises.find { it.id == originalExId }
            if (originalEx != null) {
                val newExId = -newId()


                val newEntries = mutableListOf<SetEntry>()

                originalEx.entries.forEach { entry ->
                    repeat(entry.sets) {

                        newEntries.add(
                            entry.copy(
                                id = newId(),
                                sets = 1,
                                finished = false
                            )
                        )
                    }
                }


                val newEx = originalEx.copy(
                    id = newExId,
                    entries = newEntries,
                    finished = false
                )

                exercises.add(newEx)
                newExerciseIds.add(newExId)
            }
        }

        val newSessionInstance = originalSession.copy(
            id = newSessionId,
            exerciseIds = newExerciseIds,
            date = sessionDate,
            finished = false,
            baseRms = dailyRms 
        )

        sessions.add(newSessionInstance)

        val activeProgramIndex = activePrograms.indexOfFirst { it.id == activeProgramId }
        if (activeProgramIndex != -1) {
            activePrograms[activeProgramIndex].sessionIds.add(newSessionId)
        }

        return newSessionInstance
    }


    fun deleteActiveProgram(activeProgram: ActiveProgram) {
        val sessionsToDeleteIds = activeProgram.sessionIds
        if (sessionsToDeleteIds.isNotEmpty()) {
            val sessionsToDelete = sessions.filter { it.id in sessionsToDeleteIds }
            val exercisesToDeleteIds = sessionsToDelete.flatMap { it.exerciseIds }

            if (exercisesToDeleteIds.isNotEmpty()) {
                exercises.removeAll { it.id in exercisesToDeleteIds }
            }

            sessions.removeAll { it.id in sessionsToDeleteIds }
        }
        items.removeAll { it.activeProgramId == activeProgram.id }
        activePrograms.remove(activeProgram)
    }

    fun toAppState(): AppState {
        return AppState(
            movements = movements.toList(),
            exercises = exercises.toList(),
            sessions = sessions.toList(),
            timedSessions = timedSessions.toList(),
            days = days.toList(),
            weeks = weeks.toList(),
            blocks = blocks.toList(),
            programs = programs.toList(),
            activePrograms = activePrograms.toList(),
            itemFamilies = itemFamilies.toList(),
            items = items.toList(),
            selectedActiveProgramId = selectedActiveProgramId,
            nextId = nextId,
            currentUserName = currentUserName,
            performanceEntries = performanceEntries.toList(),
            hasSeededGeneratedData = hasSeededGeneratedData,
            modules = modules.toList(),
            moduleAttachments = moduleAttachments.toList()
        )
    }

    fun loadFrom(app: AppState) {
        movements.clear(); movements.addAll(app.movements) 
        exercises.clear(); exercises.addAll(app.exercises)
        sessions.clear(); sessions.addAll(app.sessions)
        timedSessions.clear(); timedSessions.addAll(app.timedSessions)
        days.clear(); days.addAll(app.days)
        weeks.clear(); weeks.addAll(app.weeks)
        blocks.clear(); blocks.addAll(app.blocks)
        programs.clear(); programs.addAll(app.programs)
        itemFamilies.clear(); itemFamilies.addAll(app.itemFamilies)
        activePrograms.clear(); activePrograms.addAll(app.activePrograms)
        items.clear(); items.addAll(app.items)
        performanceEntries.clear(); performanceEntries.addAll(app.performanceEntries)
        modules.clear(); modules.addAll(app.modules)
        moduleAttachments.clear(); moduleAttachments.addAll(app.moduleAttachments)
        if (app.hasSeededGeneratedData) {
            performanceEntries.removeAll { it.note == "Generated demo" }
        }
        val legacyPerformanceOwnerId = app.selectedActiveProgramId ?: app.activePrograms.firstOrNull()?.id
        if (legacyPerformanceOwnerId != null) {
            for (index in performanceEntries.indices) {
                val entry = performanceEntries[index]
                if (entry.activeProgramId == null) {
                    performanceEntries[index] = entry.copy(activeProgramId = legacyPerformanceOwnerId)
                }
            }
        }
        // Entries recorded before variants existed carry no exercise id: recover it from the
        // session that produced them when that session planned exactly one exercise of the movement.
        for (index in performanceEntries.indices) {
            val entry = performanceEntries[index]
            if (entry.exerciseId != null || entry.sessionId == null) continue
            val session = sessions.find { it.id == entry.sessionId } ?: continue
            val candidates = session.exerciseIds
                .mapNotNull { id -> exercises.find { it.id == id } }
                .filter { it.movementId == entry.movementId }
            if (candidates.size == 1) {
                performanceEntries[index] = entry.copy(
                    exerciseId = candidates.first().id,
                    variant = candidates.first().name.trim()
                )
            }
        }
        migrateAutoWrappers()
        breakBlockCycles()
        recomputeBlockLevels()
        selectedActiveProgramId = app.selectedActiveProgramId
        currentUserName = app.currentUserName
        hasSeededGeneratedData = app.hasSeededGeneratedData
        nextId = app.nextId
    }

    fun reset() {
        movements.clear(); exercises.clear(); sessions.clear(); timedSessions.clear() 
        days.clear(); weeks.clear(); blocks.clear(); programs.clear(); activePrograms.clear()
        itemFamilies.clear()
        items.clear()
        performanceEntries.clear()
        selectedActiveProgramId = null
        hasSeededGeneratedData = false
        nextId = 1L
    }


    fun populateWithSampleProgram() {

        
        val mBench = createEntity(EntityKind.MOVEMENT, "Barbell Bench Press")
        val mOhp = createEntity(EntityKind.MOVEMENT, "Overhead Press")
        val mDeadlift = createEntity(EntityKind.MOVEMENT, "Deadlift")
        val mPullUp = createEntity(EntityKind.MOVEMENT, "Weighted Pull-ups")
        val mRow = createEntity(EntityKind.MOVEMENT, "Barbell Row")
        val mSquat = createEntity(EntityKind.MOVEMENT, "Back Squat")
        val mLunge = createEntity(EntityKind.MOVEMENT, "Walking Lunges")

        
        val benchPress = createEntity(EntityKind.EXERCISE, "Classic", listOf(mBench.id)) as ExerciseEntity
        benchPress.entries += SetEntry(sets = 1, reps = 8, intensity = 75, rest = 120)
        benchPress.entries += SetEntry(sets = 1, reps = 5, intensity = 85, rest = 150)
        benchPress.entries += SetEntry(sets = 1, reps = 5, intensity = 90, rest = 180)

        val ohp = createEntity(EntityKind.EXERCISE, "Hard", listOf(mOhp.id)) as ExerciseEntity
        ohp.entries += SetEntry(sets = 1, reps = 6, intensity = 80, rest = 180)
        ohp.entries += SetEntry(sets = 1, reps = 6, intensity = 80, rest = 180)
        ohp.entries += SetEntry(sets = 1, reps = 4, intensity = 90, rest = 240)

        val deadlift = createEntity(EntityKind.EXERCISE, "Soft", listOf(mDeadlift.id)) as ExerciseEntity
        deadlift.entries += SetEntry(sets = 1, reps = 5, intensity = 85, rest = 240)
        deadlift.entries += SetEntry(sets = 1, reps = 5, intensity = 90, rest = 240)
        deadlift.entries += SetEntry(sets = 1, reps = 3, intensity = 95, rest = 300)

        val pullUps = createEntity(EntityKind.EXERCISE, "", listOf(mPullUp.id)) as ExerciseEntity
        pullUps.entries += SetEntry(sets = 1, reps = 8, intensity = 0, rest = 120)
        pullUps.entries += SetEntry(sets = 1, reps = 6, intensity = 0, rest = 150)
        pullUps.entries += SetEntry(sets = 1, reps = 6, intensity = 0, rest = 150)

        val barbellRow = createEntity(EntityKind.EXERCISE, "", listOf(mRow.id)) as ExerciseEntity
        barbellRow.entries += SetEntry(sets = 1, reps = 8, intensity = 70, rest = 90)
        barbellRow.entries += SetEntry(sets = 1, reps = 8, intensity = 75, rest = 90)

        val squat = createEntity(EntityKind.EXERCISE, "Easy", listOf(mSquat.id)) as ExerciseEntity
        squat.entries += SetEntry(sets = 1, reps = 5, intensity = 80, rest = 180)
        squat.entries += SetEntry(sets = 1, reps = 5, intensity = 85, rest = 200)
        squat.entries += SetEntry(sets = 1, reps = 3, intensity = 90, rest = 240)

        val lunges = createEntity(EntityKind.EXERCISE, "", listOf(mLunge.id)) as ExerciseEntity
        lunges.entries += SetEntry(sets = 1, reps = 12, intensity = 0, rest = 90)
        lunges.entries += SetEntry(sets = 1, reps = 10, intensity = 0, rest = 90)
        lunges.entries += SetEntry(sets = 1, reps = 10, intensity = 0, rest = 120)

        
        val sessionPushA = createEntity(EntityKind.SESSION, "Push A",
            listOf(benchPress.id, ohp.id)) as SessionEntity

        val sessionPullA = createEntity(EntityKind.SESSION, "Pull A",
            listOf(deadlift.id, pullUps.id, barbellRow.id)) as SessionEntity

        val sessionLegsA = createEntity(EntityKind.SESSION, "Legs A",
            listOf(squat.id, lunges.id)) as SessionEntity

        val sessionFullBodyA = createEntity(EntityKind.SESSION, "Full Body A",
            listOf(squat.id, benchPress.id, barbellRow.id)) as SessionEntity

        val sessionFullBodyB = createEntity(EntityKind.SESSION, "Full Body B",
            listOf(deadlift.id, ohp.id, pullUps.id)) as SessionEntity

        
        val defaultTime = LocalTime.of(18, 0)

        fun makeDay(dayName: String, session: SessionEntity): DayEntity {
            val ts = TimedSessionEntity(
                id = newId(),
                name = session.name,
                time = defaultTime, 
                sessionId = session.id,
                owner = currentUserName
            )
            timedSessions += ts
            return createEntity(EntityKind.DAY, dayName, listOf(ts.id)) as DayEntity
        }

        
        
        

        
        
        val sbdLoadDaySquatFocus = makeDay("SBD Load - Squat Focus", sessionLegsA) 
        val sbdLoadDayBenchFocus = makeDay("SBD Load - Bench Focus", sessionPushA) 
        val sbdLoadDayDeadliftFocus = makeDay("SBD Load - Deadlift Focus", sessionPullA) 

        
        
        val sbdDeloadDay1 = makeDay("SBD Deload - Light Full Body 1", sessionFullBodyA) 
        val sbdDeloadDay2 = makeDay("SBD Deload - Light Full Body 2", sessionFullBodyB) 

        

        
        val sbdLoadWeek = createEntity(EntityKind.WEEK, "SBD Load Week",
            listOf(
                sbdLoadDaySquatFocus.id,    
                REST_DAY_ID,                
                sbdLoadDayBenchFocus.id,    
                REST_DAY_ID,                
                sbdLoadDayDeadliftFocus.id, 
                REST_DAY_ID,                
                REST_DAY_ID                 
            ))

        
        val sbdDeloadWeek = createEntity(EntityKind.WEEK, "SBD Deload Week",
            listOf(
                sbdDeloadDay1.id,           
                REST_DAY_ID,                
                REST_DAY_ID,                
                sbdDeloadDay2.id,           
                REST_DAY_ID,                
                REST_DAY_ID,                
                REST_DAY_ID                 
            ))

        

        
        val sbdLoadBlock = createEntity(EntityKind.BLOCK, "SBD Load Phase (3 Weeks)",
            listOf(
                sbdLoadWeek.id,
                sbdLoadWeek.id,
                sbdLoadWeek.id
            ))

        
        val sbdDeloadBlock = createEntity(EntityKind.BLOCK, "SBD Deload Phase (1 Week)",
            listOf(
                sbdDeloadWeek.id
            ))

        

        createEntity(EntityKind.PROGRAM, "SBD 4-Week Load/Deload Cycle",
            listOf(
                sbdLoadBlock.id,
                sbdDeloadBlock.id
            ))

        
        
        
    }



    fun populateWithGeneratedData(referenceDate: LocalDate = LocalDate.now()) {
        populateWithSampleProgram()
        val program = programs.lastOrNull() ?: return

        val movementIdsByName = movements.associate { it.name to it.id }
        val rmSettings = getUniqueMovementIdsForProgram(program).associateWith { movementId ->
            val movementName = movements.find { it.id == movementId }?.name.orEmpty()
            val (start, target) = when (movementName) {
                "Barbell Bench Press" -> 150 to 200
                "Back Squat" -> 180 to 240
                "Deadlift" -> 220 to 280
                "Overhead Press" -> 80 to 100
                "Weighted Pull-ups" -> 20 to 40
                "Barbell Row" -> 90 to 130
                else -> 0 to 0
            }
            RmSettings(
                start = start,
                target = target,
                strategy = InterpolationStrategy.WEEK
            )
        }

        val activeProgram = startProgram(
            program = program,
            startDate = referenceDate.minusDays(7),
            name = "Demo ${program.name}",
            rmSettings = rmSettings
        )
        selectedActiveProgramId = activeProgram.id

        val rpeFamily = createItemFamily("RPE", "Rate of perceived exertion", ItemType.INT, "7")
        val noteFamily = createItemFamily("Note", "Session note", ItemType.STRING, "")

        val demoPlan = listOf(
            "Push A" to 6L,
            "Legs A" to 4L,
            "Pull A" to 2L,
            "Push A" to 1L,
            "Pull A" to 0L
        )
        for ((sessionName, daysAgo) in demoPlan) {
            val template = sessions.find { it.name == sessionName } ?: continue
            val sessionDate = referenceDate.minusDays(daysAgo).atTime(18, 0)
            val dayIndex = (7 - daysAgo).toInt()
            val dailyRms = activeProgram.exerciseDailyRms.mapValues { (_, list) ->
                list.getOrNull(dayIndex) ?: list.firstOrNull() ?: 0
            }
            val instance = createSessionInstance(
                originalSession = template,
                dailyRms = dailyRms,
                sessionDate = sessionDate,
                activeProgramId = activeProgram.id
            )

            instance.exerciseIds.forEachIndexed { exerciseIndex, exId ->
                val ex = exercises.find { it.id == exId } ?: return@forEachIndexed
                val finishedPattern = when (exerciseIndex % 3) {
                    0 -> listOf(true, true, false)
                    1 -> listOf(true, false)
                    else -> listOf(true, true, true)
                }
                ex.entries.forEachIndexed { entryIndex, entry ->
                    ex.entries[entryIndex] = entry.copy(
                        finished = finishedPattern.getOrNull(entryIndex) ?: true
                    )
                }
                createAndHangItem(ex.id, activeProgram.id, rpeFamily, (6 + (exerciseIndex % 4)).toString())
                createAndHangItem(
                    ex.id,
                    activeProgram.id,
                    noteFamily,
                    "Solid session, movement ${exerciseIndex + 1}"
                )
                ex.entries.forEachIndexed { entryIndex, entry ->
                    createAndHangItem(
                        entry.id,
                        activeProgram.id,
                        rpeFamily,
                        (7 + (entryIndex % 3)).toString()
                    )
                }
            }

            createAndHangItem(instance.id, activeProgram.id, noteFamily, "Demo $sessionName")

            val instanceExercises = instance.exerciseIds.mapNotNull { exId -> exercises.find { it.id == exId } }
            val totalSets = instanceExercises.sumOf { it.entries.size }
            val completedSets = instanceExercises.sumOf { it.entries.count { it.finished == true } }
            if (totalSets > 0) {
                val instanceIndex = sessions.indexOfFirst { it.id == instance.id }
                if (instanceIndex != -1) {
                    sessions[instanceIndex] = sessions[instanceIndex].copy(
                        percent = completedSets.toFloat() / totalSets
                    )
                }
            }

            recordSessionBestPerformances(instance.id, activeProgram.id)
        }

        hasSeededGeneratedData = true
    }

}