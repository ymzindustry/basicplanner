package com.ymz.basicplanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.ymz.basicplanner.model.AppState
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.LocalDate
import java.time.LocalDateTime

object LocalTimeAsStringSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())    
    }

    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.parse(decoder.decodeString())
    }
}

object LocalDateAsStringSerializer : KSerializer<java.time.LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: java.time.LocalDate) {
        encoder.encodeString(value.toString()) 
    }

    override fun deserialize(decoder: Decoder): java.time.LocalDate {
        return java.time.LocalDate.parse(decoder.decodeString())
    }
}
object LocalDateTimeAsStringSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString()) 
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }
}

private val appJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    serializersModule = SerializersModule {

        contextual(LocalTime::class, LocalTimeAsStringSerializer)
        contextual(LocalDate::class, LocalDateAsStringSerializer)
        contextual(LocalDateTime::class, LocalDateTimeAsStringSerializer)
    }
}

object AppStateSerializer : Serializer<AppState> {

    override val defaultValue: AppState = AppState()

    override suspend fun readFrom(input: InputStream): AppState {
        return try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) {
                defaultValue
            } else {
                appJson.decodeFromString(AppState.serializer(), text)
            }
        } catch (e: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        val text = appJson.encodeToString(AppState.serializer(), t)
        output.write(text.encodeToByteArray())
    }
}

private val Context.appStateDataStore: DataStore<AppState> by dataStore(
    fileName = "app_state.json",
    serializer = AppStateSerializer
)

object AppStateStore {

    suspend fun save(context: Context, state: AppState) {
        context.appStateDataStore.updateData { state }
    }

    suspend fun load(context: Context): AppState {
        return context.appStateDataStore.data.first()
    }

    suspend fun clear(context: Context) {
        save(context, AppState())
    }
}
