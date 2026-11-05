package com.ymz.basicplanner.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.ymz.basicplanner.model.*
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import com.ymz.basicplanner.R

object ExcelExporter {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM")

    private fun getColLetter(index: Int): String {
        var i = index
        var res = ""
        while (i >= 0) {
            res = ('A'.code + (i % 26)).toChar() + res
            i = (i / 26) - 1
        }
        return res
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun exportActiveProgram(
        context: Context,
        activeProgram: ActiveProgram,
        program: ProgramEntity,
        creationState: CreationState
    ): Uri? {
        val workbook = XSSFWorkbook()
        val trainingSheet = workbook.createSheet(context.getString(R.string.excel_sheet_training_plan))
        val baseSheet = workbook.createSheet(context.getString(R.string.excel_sheet_base_database))

        val sessionStyle = workbook.createCellStyle().apply {
            wrapText = true
            verticalAlignment = VerticalAlignment.TOP
        }
        val weekHeaderStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            val font = workbook.createFont().apply { bold = true }
            setFont(font)
        }


        
        val movementIds = activeProgram.exerciseDailyRms.keys.toList().sorted()
        val duration = activeProgram.exerciseDailyRms.values.firstOrNull()?.size ?: 0
        val dateToIndexMap = mutableMapOf<LocalDate, Int>()

        val baseHeader = baseSheet.createRow(0)
        baseHeader.createCell(0).setCellValue(context.getString(R.string.excel_column_date))

        
        movementIds.forEachIndexed { i, mId ->
            val movementName = creationState.movements.find { it.id == mId }?.name ?: context.getString(R.string.excel_unknown_movement)
            baseHeader.createCell(i + 1).setCellValue(movementName)
        }

        for (dayIdx in 0 until duration) {
            val date = activeProgram.startDate.plusDays(dayIdx.toLong())
            val row = baseSheet.createRow(dayIdx + 1)
            row.createCell(0).setCellValue(date.toString())

            
            movementIds.forEachIndexed { colIdx, mId ->
                val value = activeProgram.exerciseDailyRms[mId]?.getOrNull(dayIdx) ?: 0
                row.createCell(colIdx + 1).setCellValue(value.toDouble())
            }

            dateToIndexMap[date] = dayIdx + 2
        }

        var currentRow = 0
        var weekCounter = 1

        var weekMondayDate = activeProgram.startDate.with(DayOfWeek.MONDAY)

        program.blockIds.forEachIndexed { _, blockId ->
            val block = creationState.blocks.find { it.id == blockId } ?: return@forEachIndexed
            block.weekIds.forEachIndexed { _, weekId ->
                val week = creationState.weeks.find { it.id == weekId } ?: return@forEachIndexed

                val weekEndDate = weekMondayDate.plusDays(6)
                val weekLabel = "${context.getString(R.string.excel_week_prefix)} $weekCounter (${weekMondayDate.format(dateFormatter)}${context.getString(R.string.excel_date_range_separator)}${weekEndDate.format(dateFormatter)})"
                val weekHeaderRow = trainingSheet.createRow(currentRow++)
                weekHeaderRow.createCell(0).apply {
                    setCellValue(weekLabel)
                    cellStyle = weekHeaderStyle
                }
                trainingSheet.addMergedRegion(CellRangeAddress(currentRow - 1, currentRow - 1, 0, 6))

                val daysHeaderRow = trainingSheet.createRow(currentRow++)
                listOf(
                    context.getString(R.string.day_monday_short),
                    context.getString(R.string.day_tuesday_short),
                    context.getString(R.string.day_wednesday_short),
                    context.getString(R.string.day_thursday_short),
                    context.getString(R.string.day_friday_short),
                    context.getString(R.string.day_saturday_short),
                    context.getString(R.string.day_sunday_short)
                ).forEachIndexed { i, day ->
                    daysHeaderRow.createCell(i).setCellValue(day)
                }

                val contentRow = trainingSheet.createRow(currentRow++)
                contentRow.heightInPoints = 160f

                for (dayIdx in 0..6) {
                    val cell = contentRow.createCell(dayIdx)
                    cell.cellStyle = sessionStyle
                    val dateForCell = weekMondayDate.plusDays(dayIdx.toLong())
                    val dayNum = dateForCell.dayOfMonth.toString()
                    val dayId = week.dayIds.getOrNull(dayIdx)


                    if (!dateForCell.isBefore(activeProgram.startDate) && dateToIndexMap.containsKey(dateForCell) && dayId != null && dayId != CreationState.REST_DAY_ID) {
                        val dayEntity = creationState.days.find { it.id == dayId }
                        val daySessions = dayEntity?.timedSessionIds?.mapNotNull { tsId ->
                            creationState.timedSessions.find { it.id == tsId }
                        }?.sortedBy { it.time } ?: emptyList()

                        val formulaParts = mutableListOf<String>()
                        formulaParts.add("\"$dayNum\"")
                        formulaParts.add("\" \"")

                        daySessions.forEach { ts ->
                            val sessionEntity = creationState.sessions.find { it.id == ts.sessionId }
                            formulaParts.add("\"[${ts.time}] ${sessionEntity?.name ?: ""}\"")
                            formulaParts.add("\"${context.getString(R.string.excel_session_separator)}\"")

                            sessionEntity?.exerciseIds?.forEach { exId ->
                                val ex = creationState.exercises.find { it.id == exId }

                                if (ex != null) {
                                    
                                    val exoColIdx = movementIds.indexOf(ex.movementId)

                                    if (exoColIdx != -1) {
                                        val baseRowNum = dateToIndexMap[dateForCell] ?: 2
                                        val baseRef = baseSheet.getRow(baseRowNum).getCell(exoColIdx + 1).numericCellValue

                                        
                                        val mName = creationState.movements.find { it.id == ex.movementId }?.name ?: context.getString(R.string.excel_movement_unknown_short)
                                        val displayLabel = if (ex.name.isBlank()) mName else "$mName ${context.getString(R.string.excel_variant_name_format).format(ex.name)}"

                                        ex.entries.forEach { entry ->
                                            val line = "\"$displayLabel${context.getString(R.string.excel_exercise_label_separator)} ${entry.sets} ${context.getString(R.string.excel_sets_reps_separator)} ${entry.reps} ${context.getString(R.string.excel_reps_intensity_separator)} \" & " +
                                                    "ROUND($baseRef * ${entry.intensity / 100.0}, 0) & " +
                                                    "\" ${context.getString(R.string.excel_kg_rest_separator)} ${entry.rest} ${context.getString(R.string.excel_seconds_suffix)}\""

                                            formulaParts.add(line)
                                        }
                                    }
                                }
                            }
                            formulaParts.add("\" \"")
                        }

                        if (formulaParts.size > 2) {
                            cell.cellFormula = formulaParts.joinToString(" & CHAR(10) & ")
                        } else {
                            cell.setCellValue("$dayNum\n${context.getString(R.string.excel_rest_day_label)}")
                        }
                    } else {
                        cell.setCellValue("$dayNum\n${context.getString(R.string.excel_rest_day_label)}")
                    }
                }

                currentRow += 2
                weekMondayDate = weekMondayDate.plusDays(7)
                weekCounter++
            }
        }


        for (i in 0..6) trainingSheet.setColumnWidth(i, 256 * 35)
        for (i in 0..movementIds.size) baseSheet.setColumnWidth(i, 256 * 15)

        val ownerName = program.owner ?: context.getString(R.string.excel_unknown_movement)
        val finalFileName = "${activeProgram.name}${context.getString(R.string.excel_filename_from_prefix)}$ownerName".replace(" ", context.getString(R.string.excel_filename_space_replacement))
        return saveWorkbookToDownloads(context, workbook, "$finalFileName.xlsx")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWorkbookToDownloads(context: Context, workbook: XSSFWorkbook, fileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { os -> workbook.write(os) }
            workbook.close()
        }
        return uri
    }
}
