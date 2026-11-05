package com.ymz.basicplanner.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.ymz.basicplanner.R
import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.effectiveAdjustmentPercent
import com.ymz.basicplanner.model.scaledBaseRm
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class JsonExport(
    val programName: String,
    val days: List<JsonDay>
)

@Serializable
data class JsonDay(
    val date: String,
    val dayName: String = "",
    val sessions: List<JsonSession>
)

@Serializable
data class JsonSession(
    val name: String,
    val time: String,
    val exercises: List<JsonExercise>,
    val items: Map<String, String> = emptyMap()
)

@Serializable
data class JsonExercise(
    val name: String,
    val variant: String = "",
    val sets: List<JsonSet>,
    val items: Map<String, String> = emptyMap()
)

@Serializable
data class JsonSet(
    val index: Int,
    val reps: Int,
    val intensity: Int,
    val rest: Int,
    val finished: Boolean,
    val weightKg: Double? = null,
    val items: Map<String, String> = emptyMap()
)

object JsonExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private data class HierarchyInfo(val blockName: String, val weekName: String, val dayName: String)

    fun exportFullJson(context: Context, state: CreationState, activeProgram: ActiveProgram) {
        val program = state.programs.find { it.id == activeProgram.programId } ?: return
        val export = buildExportData(state.toAppState(), program, activeProgram)
        val jsonText = json.encodeToString(JsonExport.serializer(), export)
        val fileName = "${program.name.replace(" ", "_").replace("/", "_")}_full.json"
        val file = File(context.cacheDir, fileName)
        file.writeText(jsonText)
        val uri = saveJsonToDownloads(context, file)
        if (uri != null) {
            showExportNotification(context, fileName, uri)
        }
    }

    fun buildExportData(app: AppState, program: ProgramEntity, activeProgram: ActiveProgram): JsonExport {
        val hierarchy = buildHierarchyMap(app, program, activeProgram)

        val sessions = activeProgram.sessionIds
            .mapNotNull { id -> app.sessions.find { it.id == id } }

        val sessionsByDate = sessions
            .filter { it.date != null }
            .groupBy { it.date!!.toLocalDate() }

        val days = sessionsByDate.map { (date, daySessions) ->
            val dayName = hierarchy[date]?.dayName ?: ""
            JsonDay(
                date = date.toString(),
                dayName = dayName,
                sessions = daySessions
                    .sortedBy { it.date }
                    .map { session -> toJsonSession(app, activeProgram.id, session) }
            )
        }.sortedBy { it.date }

        return JsonExport(
            programName = program.name,
            days = days
        )
    }

    private fun toJsonSession(
        app: AppState,
        activeProgramId: Long,
        session: com.ymz.basicplanner.model.SessionEntity
    ): JsonSession {
        val adjustmentPct = if (session.date != null) {
            session.adjustmentPercent + effectiveAdjustmentPercent(app, activeProgramId, session.date!!.toLocalDate())
        } else {
            session.adjustmentPercent
        }
        val exercises = session.exerciseIds.mapNotNull { exId ->
            val exercise = app.exercises.find { it.id == exId } ?: return@mapNotNull null
            val movement = app.movements.find { it.id == exercise.movementId }
            val baseRm = session.baseRms?.get(exercise.movementId)

            JsonExercise(
                name = movement?.name ?: exercise.name,
                variant = if (exercise.name.isNotBlank()) exercise.name else "",
                sets = exercise.entries.mapIndexed { index, entry ->
                    JsonSet(
                        index = index + 1,
                        reps = entry.reps,
                        intensity = entry.intensity,
                        rest = entry.rest,
                        finished = entry.finished == true,
                        weightKg = entry.weightKg?.toDouble() ?: baseRm?.let {
                            kotlin.math.ceil(scaledBaseRm(it, adjustmentPct) * (entry.intensity / 100.0)).toDouble()
                        },
                        items = itemsFor(app, activeProgramId, entry.id)
                    )
                },
                items = itemsFor(app, activeProgramId, exercise.id)
            )
        }

        return JsonSession(
            name = session.name,
            time = session.date?.toLocalTime()?.toString() ?: "",
            exercises = exercises,
            items = itemsFor(app, activeProgramId, session.id)
        )
    }

    private fun itemsFor(app: AppState, activeProgramId: Long, entityId: Long): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (item in app.items) {
            if (item.entityId != entityId) continue
            if (item.activeProgramId != null && item.activeProgramId != activeProgramId) continue
            val family = app.itemFamilies.find { it.id == item.itemFamilyId } ?: continue
            result[family.name] = item.value
        }
        return result
    }

    private fun buildHierarchyMap(
        app: AppState,
        program: ProgramEntity,
        activeProgram: ActiveProgram
    ): Map<LocalDate, HierarchyInfo> {
        val result = mutableMapOf<LocalDate, HierarchyInfo>()
        var currentDate = activeProgram.startDate
        program.blockIds.forEach { blockId ->
            val block = app.blocks.find { it.id == blockId } ?: return@forEach
            block.weekIds.forEach { weekId ->
                val week = app.weeks.find { it.id == weekId } ?: return@forEach
                for (i in 0 until 7) {
                    val date = currentDate.plusDays(i.toLong())
                    val dayIndex = date.dayOfWeek.value - 1
                    val dayId = week.dayIds.getOrNull(dayIndex)
                    val day: DayEntity = if (dayId != null && dayId != CreationState.REST_DAY_ID) {
                        app.days.find { it.id == dayId }
                            ?: DayEntity(id = CreationState.REST_DAY_ID, name = "Rest Day")
                    } else {
                        DayEntity(id = CreationState.REST_DAY_ID, name = "Rest Day")
                    }
                    result[date] = HierarchyInfo(block.name, week.name, day.name)
                }
                currentDate = currentDate.plusWeeks(1)
            }
        }
        return result
    }

    private fun saveJsonToDownloads(context: Context, sourceFile: File): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            return uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val destinationFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destinationFile, overwrite = true)
            return null
        }
    }

    private fun showExportNotification(context: Context, fileName: String, uri: Uri) {
        val channelId = "export_channel"
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Exports",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/json")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("Export complete")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}
