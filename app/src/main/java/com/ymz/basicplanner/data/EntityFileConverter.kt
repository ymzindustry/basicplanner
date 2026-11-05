package com.ymz.basicplanner.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ymz.basicplanner.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Paquet de données pour l'exportation incluant l'arborescence complète des entités.
 */

@Serializable
data class EntityExportPackage(
    val mainEntityId: Long,
    val kind: EntityKind,
    val movements: List<MovementEntity> = emptyList(), 
    val exercises: List<ExerciseEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val timedSessions: List<TimedSessionEntity> = emptyList(),
    val days: List<DayEntity> = emptyList(),
    val weeks: List<WeekEntity> = emptyList(),
    val blocks: List<BlockEntity> = emptyList(),
    val programs: List<ProgramEntity> = emptyList()
)

object EntityFileConverter {

    /**
     * Configuration JSON utilisant les sérialiseurs personnalisés pour les dates.
     */
    private val exportJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        serializersModule = SerializersModule {
            contextual(LocalTime::class, LocalTimeAsStringSerializer)
            contextual(LocalDate::class, LocalDateAsStringSerializer)
            contextual(LocalDateTime::class, LocalDateTimeAsStringSerializer)
        }
    }

    fun exportToBpentFile(context: Context, entity: BaseEntity, state: CreationState): File {
        val pkg = createExportPackage(entity, state)
        val content = exportJson.encodeToString(pkg)

        val fileName = "${entity.name.replace(" ", "_").replace("/", "_")}.bpent"
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        return file
    }


    private fun createExportPackage(entity: BaseEntity, state: CreationState): EntityExportPackage {
        
        val movements = mutableSetOf<MovementEntity>()
        val exercises = mutableSetOf<ExerciseEntity>()
        val sessions = mutableSetOf<SessionEntity>()
        val timedSessions = mutableSetOf<TimedSessionEntity>()
        val days = mutableSetOf<DayEntity>()
        val weeks = mutableSetOf<WeekEntity>()
        val blocks = mutableSetOf<BlockEntity>()
        val programs = mutableSetOf<ProgramEntity>()

        fun collect(e: BaseEntity) {
            when (e) {
                is ProgramEntity -> {
                    programs.add(e)
                    e.blockIds.forEach { id -> state.blocks.find { it.id == id }?.let { collect(it) } }
                }
                is BlockEntity -> {
                    blocks.add(e)
                    e.weekIds.forEach { id -> state.weeks.find { it.id == id }?.let { collect(it) } }
                }
                is WeekEntity -> {
                    weeks.add(e)
                    e.dayIds.forEach { id ->
                        if (id != CreationState.REST_DAY_ID) state.days.find { it.id == id }?.let { collect(it) }
                    }
                }
                is DayEntity -> {
                    days.add(e)
                    e.timedSessionIds.forEach { tsId ->
                        state.timedSessions.find { it.id == tsId }?.let { ts ->
                            timedSessions.add(ts)
                            state.sessions.find { it.id == ts.sessionId }?.let { collect(it) }
                        }
                    }
                }
                is SessionEntity -> {
                    sessions.add(e)
                    e.exerciseIds.forEach { id -> state.exercises.find { it.id == id }?.let { collect(it) } }
                }
                is ExerciseEntity -> {
                    exercises.add(e)
                    
                    state.movements.find { it.id == e.movementId }?.let { movements.add(it) }
                }
            }
        }

        collect(entity)

        return EntityExportPackage(
            mainEntityId = entity.id,
            kind = when(entity) {
                is ProgramEntity -> EntityKind.PROGRAM
                is BlockEntity -> EntityKind.BLOCK
                is WeekEntity -> EntityKind.WEEK
                is DayEntity -> EntityKind.DAY
                is SessionEntity -> EntityKind.SESSION
                else -> EntityKind.EXERCISE
            },
            movements = movements.toList(), 
            exercises = exercises.toList(),
            sessions = sessions.toList(),
            timedSessions = timedSessions.toList(),
            days = days.toList(),
            weeks = weeks.toList(),
            blocks = blocks.toList(),
            programs = programs.toList()
        )
    }



    fun importFromBpentFile(file: File, state: CreationState) {
        val content = file.readText()
        val pkg = exportJson.decodeFromString<EntityExportPackage>(content)

        val idMap = mutableMapOf<Long, Long>()
        idMap[CreationState.REST_DAY_ID] = CreationState.REST_DAY_ID

        
        pkg.movements.forEach { importedMov ->
            
            val existing = state.movements.find { it.name.equals(importedMov.name, ignoreCase = true) }

            if (existing != null) {
                
                idMap[importedMov.id] = existing.id
            } else {
                
                val newId = state.newId()
                idMap[importedMov.id] = newId
                state.movements.add(importedMov.copy(
                    id = newId
                ))
            }
        }

        
        (pkg.exercises + pkg.sessions + pkg.timedSessions + pkg.days + pkg.weeks + pkg.blocks + pkg.programs)
            .forEach { idMap[it.id] = state.newId() }

        
        pkg.exercises.forEach { ex ->
            
            val mappedMovementId = idMap[ex.movementId] ?: ex.movementId 

            val newEx = ex.copy(
                id = idMap[ex.id]!!,
                movementId = mappedMovementId
            )
            
            state.exercises.add(newEx)
        }

        pkg.sessions.forEach { sess ->
            state.sessions.add(sess.copy(
                id = idMap[sess.id]!!,
                exerciseIds = sess.exerciseIds.mapNotNull { idMap[it] }.toMutableList()
            ))
        }

        pkg.timedSessions.forEach { ts ->
            state.timedSessions.add(ts.copy(
                id = idMap[ts.id]!!,
                sessionId = idMap[ts.sessionId]!!
            ))
        }

        pkg.days.forEach { d ->
            state.days.add(d.copy(
                id = idMap[d.id]!!,
                timedSessionIds = d.timedSessionIds.mapNotNull { idMap[it] }.toMutableList()
            ))
        }

        pkg.weeks.forEach { w ->
            state.weeks.add(w.copy(
                id = idMap[w.id]!!,
                dayIds = w.dayIds.map { idMap[it] ?: CreationState.REST_DAY_ID }.toMutableList()
            ))
        }

        pkg.blocks.forEach { b ->
            state.blocks.add(b.copy(
                id = idMap[b.id]!!,
                weekIds = b.weekIds.mapNotNull { idMap[it] }.toMutableList()
            ))
        }

        pkg.programs.forEach { p ->
            state.programs.add(p.copy(
                id = idMap[p.id]!!,
                blockIds = p.blockIds.mapNotNull { idMap[it] }.toMutableList()
            ))
        }

        // An exported package may still carry the old auto-created wrappers: unwrap them so the
        // imported program uses the flat hierarchy too.
        state.migrateAutoWrappers()
    }


    fun saveBpentToDownloads(context: Context, sourceFile: File): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
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
            
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val destinationFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destinationFile, overwrite = true)
            return null
        }
    }
}