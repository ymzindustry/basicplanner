package com.ymz.basicplanner.model

import androidx.compose.runtime.Composable
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDate
import java.time.LocalDateTime
import androidx.compose.ui.res.stringResource
import com.ymz.basicplanner.model.module.ModuleAttachment
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.R

private val idGenerator = AtomicLong(1L)

fun nextId(): Long = idGenerator.getAndIncrement()

enum class ItemType {
    STRING,
    INT,
    FLOAT,
    VIDEO, 
    IMAGE, 
    BOOLEAN
}


@Serializable
data class ItemFamily(
    val id: Long = nextId(),
    val name: String,
    val description: String,
    val itemType: ItemType,
    val defaultValue: String = ""
)


@Serializable
data class Item(
    val id: Long = nextId(),
    val activeProgramId: Long?,
    val entityId: Long,
    val itemFamilyId: Long, 
    var value: String
    
)

enum class EntityKind { 
    MOVEMENT,
    EXERCISE,
    SESSION,
    DAY,
    WEEK,
    BLOCK,
    PROGRAM
}

enum class SubEntityKind { 
    MOVEMENT,
    EXERCISE,
    SESSION,
    DAY,
    WEEK,
    BLOCK,
    PROGRAM
}

@Composable
fun EntityKind.getLabel(): String {
    return when (this) {
        EntityKind.MOVEMENT -> stringResource(id = R.string.entity_kind_movement)
        EntityKind.EXERCISE -> stringResource(id = R.string.entity_kind_exercise)
        EntityKind.SESSION -> stringResource(id = R.string.entity_kind_session)
        EntityKind.DAY -> stringResource(id = R.string.entity_kind_day)
        EntityKind.WEEK -> stringResource(id = R.string.entity_kind_week)
        EntityKind.BLOCK -> stringResource(id = R.string.entity_kind_block)
        EntityKind.PROGRAM -> stringResource(id = R.string.entity_kind_program)
    }
}

@Composable
fun SubEntityKind.getLabel(): String {
    return when (this) {
        SubEntityKind.MOVEMENT -> stringResource(id = R.string.sub_entity_kind_none)
        SubEntityKind.EXERCISE -> stringResource(id = R.string.sub_entity_kind_set_singular)
        SubEntityKind.SESSION -> stringResource(id = R.string.sub_entity_kind_exercise_singular)
        SubEntityKind.DAY -> stringResource(id = R.string.sub_entity_kind_session_singular)
        SubEntityKind.WEEK -> stringResource(id = R.string.sub_entity_kind_day_singular)
        SubEntityKind.BLOCK -> stringResource(id = R.string.sub_entity_kind_element_singular)
        SubEntityKind.PROGRAM -> stringResource(id = R.string.sub_entity_kind_block_singular)
    }
}

@Composable
fun InterpolationStrategy.getLabel(): String {
    return when (this) {
        InterpolationStrategy.DAY -> stringResource(id = R.string.interpolation_strategy_day)
        InterpolationStrategy.WEEK -> stringResource(id = R.string.interpolation_strategy_week)
        InterpolationStrategy.BLOCK -> stringResource(id = R.string.interpolation_strategy_block)
        InterpolationStrategy.CUSTOM -> stringResource(id = R.string.interpolation_strategy_custom)
        InterpolationStrategy.CONST -> stringResource(id = R.string.interpolation_strategy_const)
    }
}

fun truncate(text: String, maxLength: Int = 20): String {
    return if (text.length > maxLength) text.take(maxLength) + "..." else text
}

interface BaseEntity {
    val id: Long
    var name: String
    var owner: String?


    fun computeNameString(movements: List<MovementEntity>, unknownText: String, maxLength: Int = 35, strRestDay: String = ""): String {
        return when (this) {
            is ExerciseEntity -> {
                val rawParentName = movements.find { it.id == this.movementId }?.name ?: unknownText
                val parentName = truncate(rawParentName, maxLength)

                if (this.name.isNotBlank()) {
                    val variantName = truncate(this.name, maxLength)
                    "$parentName^$variantName"
                } else {
                    truncate(parentName, maxLength)
                }
            }
            is DayEntity -> if (this.id == CreationState.REST_DAY_ID) strRestDay else truncate(this.name, maxLength)
            else -> truncate(this.name, maxLength)
        }
    }
}

@Serializable
data class MovementEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null
) : BaseEntity

@Serializable
data class SetEntry(
    val id: Long = nextId(),
    val sets: Int,
    val reps: Int,
    val intensity: Int,
    val rest: Int,
    /** Direct weight (kg) for a set added during a live session. Non-null marks the set as "added" (deletable, uses this kg instead of baseRm × intensity%). */
    val weightKg: Int? = null,
    var finished: Boolean? = null
)

/** A set row is only usable once its four values are filled: sets, reps, intensity (%), rest. */
fun SetEntry.isComplete(): Boolean = sets > 0 && reps > 0 && intensity > 0 && rest > 0

/** An exercise can only be saved when it has at least one set row and every row is complete. */
fun List<SetEntry>.allComplete(): Boolean = isNotEmpty() && all { it.isComplete() }

@Serializable
data class ExerciseEntity(
    override val id: Long,
    val movementId: Long,
    override var name: String,
    override var owner: String? = null,
    val entries: MutableList<SetEntry> = mutableListOf(),

    var finished: Boolean? = null
) : BaseEntity

@Serializable
data class SessionEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null,
    val exerciseIds: MutableList<Long> = mutableListOf(),
    @Contextual var date: LocalDateTime? = null,
    var finished: Boolean? = null,
    
    var baseRms: Map<Long, Int>? = null,
    var percent: Float = 0f,
    var adjustmentPercent: Int = 0
) : BaseEntity

@Serializable
data class TimedSessionEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null,
    @Contextual var time: LocalTime,
    val sessionId: Long
) : BaseEntity

@Serializable
data class DayEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null,
    val timedSessionIds: MutableList<Long> = mutableListOf(),
    var adjustmentPercent: Int = 0,
    val subAdjustments: MutableList<Int> = mutableListOf()
) : BaseEntity

@Serializable
data class WeekEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null,
    val dayIds: MutableList<Long> = mutableListOf(),
    var adjustmentPercent: Int = 0,
    val subAdjustments: MutableList<Int> = mutableListOf()
) : BaseEntity

@Serializable
data class BlockEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null,
    val weekIds: MutableList<Long> = mutableListOf(),
    var adjustmentPercent: Int = 0,
    val subBlockIds: MutableList<Long> = mutableListOf(),
    val subAdjustments: MutableList<Int> = mutableListOf(),
    /**
     * Block depth in the tree: 0 = a block sitting directly in a program, 1 = its sub-blocks,
     * and so on. Derived from the hierarchy by [CreationState.recomputeBlockLevels] — never typed
     * in by hand, and never a constraint on what a block may contain. null = not attached to a
     * program yet.
     */
    var level: Int? = null
) : BaseEntity

@Serializable
data class ProgramEntity(
    override val id: Long = nextId(),
    override var name: String,
    override var owner: String? = null,
    val blockIds: MutableList<Long> = mutableListOf(),
    var adjustmentPercent: Int = 0,
    val subAdjustments: MutableList<Int> = mutableListOf()
) : BaseEntity

enum class InterpolationStrategy {
    DAY,
    WEEK,
    BLOCK,
    CUSTOM,
    CONST
}

@Serializable
data class RmSettings(
    val start: Int,
    val target: Int,
    val strategy: InterpolationStrategy,
    /** For BLOCK strategy: which block level to interpolate across (0 = top-level blocks). */
    val blockLevel: Int = 0
)

@Serializable
data class ActiveProgram(
    val id: Long = nextId(),
    val programId: Long,
    @Contextual var startDate: LocalDate,
    var name: String,
    
    val rmSettings: Map<Long, RmSettings> = emptyMap(),
    val exerciseDailyRms: Map<Long, List<Int>> = emptyMap(),
    var requiresUpdate: Boolean = false,
    val sessionIds: MutableList<Long> = mutableListOf()
)

data class TempSlot(
    val time: LocalTime,
    var session: SessionEntity
)

@Serializable
data class AppState(
    val movements: List<MovementEntity> = emptyList(), 
    val exercises: List<ExerciseEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val timedSessions: List<TimedSessionEntity> = emptyList(),
    val days: List<DayEntity> = emptyList(),
    val weeks: List<WeekEntity> = emptyList(),
    val blocks: List<BlockEntity> = emptyList(),
    val programs: List<ProgramEntity> = emptyList(),
    val activePrograms: List<ActiveProgram> = emptyList(),
    val selectedActiveProgramId: Long? = null,
    val nextId: Long = 1L,
    val currentUserName: String = "User",
    val itemFamilies: List<ItemFamily> = emptyList(),
    val items: List<Item> = emptyList(),
    val performanceEntries: List<PerformanceEntry> = emptyList(),
    val hasSeededGeneratedData: Boolean = false,
    /** Python modules and where they are plugged in (per program). */
    val modules: List<ModuleDefinition> = emptyList(),
    val moduleAttachments: List<ModuleAttachment> = emptyList()
)