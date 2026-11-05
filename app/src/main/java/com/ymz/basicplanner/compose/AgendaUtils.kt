package com.ymz.basicplanner.compose

import androidx.compose.ui.geometry.Offset
import com.ymz.basicplanner.model.BaseEntity
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.ProgramEntity
import java.time.LocalDate

/** One planned calendar slot: the entity sitting in that week weekday (day, session or exercise). */
data class AgendaSlot(
    val kind: EntityKind,
    val entity: BaseEntity,
    val labels: List<String>
)

private fun restSlot() = AgendaSlot(
    kind = EntityKind.DAY,
    entity = DayEntity(id = CreationState.REST_DAY_ID, name = "", owner = null),
    labels = emptyList()
)

/**
 * Maps every programmed date to the entity planned for it. A weekday slot of a week holds a day,
 * a session or a single exercise — never a wrapper — so the calendar always shows live names.
 */
fun buildProgramCalendar(
    program: ProgramEntity,
    startDate: LocalDate,
    creationState: CreationState
): Map<LocalDate, AgendaSlot> {
    val slots = mutableMapOf<LocalDate, AgendaSlot>()
    var currentDate = startDate

    fun slotFor(weekId: Long, index: Int): AgendaSlot? {
        val week = creationState.weeks.find { it.id == weekId } ?: return null
        val slotId = week.dayIds.getOrNull(index) ?: return null
        if (slotId == CreationState.REST_DAY_ID || slotId == CreationState.EMPTY_DAY_ID) return null
        val entity = creationState.resolveWeekSlot(slotId) ?: return null
        val labels = when (entity) {
            is DayEntity -> entity.timedSessionIds.mapNotNull { tsId ->
                creationState.timedSessions.find { it.id == tsId }?.name
            }
            is SessionEntity -> listOf(entity.name)
            is ExerciseEntity -> listOf(creationState.exerciseDisplayName(entity))
            else -> emptyList()
        }
        return AgendaSlot(creationState.weekSlotKind(entity), entity, labels.filter { it.isNotBlank() })
    }

    fun processWeek(weekId: Long, isFirst: Boolean, weekIdx: Int) {
        val week = creationState.weeks.find { it.id == weekId } ?: return
        val startDayIndex = if (isFirst && weekIdx == 0) startDate.dayOfWeek.value - 1 else 0
        for (i in startDayIndex until 7) {
            slots[currentDate] = slotFor(week.id, i) ?: restSlot()
            currentDate = currentDate.plusDays(1)
        }
    }

    fun processBlock(block: BlockEntity, isFirst: Boolean) {
        block.subBlockIds.forEach { subId ->
            creationState.blocks.find { it.id == subId }?.let { processBlock(it, false) }
        }
        block.weekIds.forEachIndexed { weekIdx, weekId -> processWeek(weekId, isFirst, weekIdx) }
    }

    program.blockIds.forEach { id ->
        val block = creationState.blocks.find { it.id == id }
        if (block != null) {
            processBlock(block, true)
        } else {
            // A week may sit directly in a program (no wrapper block).
            creationState.weeks.find { it.id == id }?.let { processWeek(it.id, true, 0) }
        }
    }
    return slots
}

fun calculateDateFromGrid(offset: Offset, gridSize: androidx.compose.ui.unit.IntSize, visibleDates: List<LocalDate>): LocalDate? {
    val colWidth = gridSize.width / 7f
    val rowHeight = gridSize.height / 6f
    val col = (offset.x / colWidth).toInt().coerceIn(0, 6)
    val row = (offset.y / rowHeight).toInt().coerceIn(0, 5)
    val index = row * 7 + col
    return visibleDates.getOrNull(index)
}

enum class DayCellMode {
    SEQUENCE,
    AGENDA
}