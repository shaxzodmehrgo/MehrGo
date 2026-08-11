package uz.teamwork.mehrgodriver.common

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * The ONE Gson for the whole app: both Retrofit converters (ApiService + RouteApiService),
 * the error-body parser injected into every use case, the socket parser and GPS-batch
 * serialisation all share this instance.
 *
 * Why: backends in this fleet intermittently send numeric fields as "", null, or the wrong
 * JSON type ("total": "", "waiting_cost": null, "distance_km": ""). Stock Gson throws
 * JsonSyntaxException on those; because the use-case funnels only catch HttpException/IOException
 * that RuntimeException escaped the coroutine and hard-crashed the app off the OkHttp async
 * thread (crash B), or crashed the tracking service off the LocalBroadcast dispatch (crash A).
 * These leaf adapters coerce a bad value instead of throwing:
 *   - non-null Kotlin fields (primitive int/long/double/float/boolean) -> 0 / 0L / 0.0 / 0f / false
 *   - nullable Kotlin fields  (boxed Integer/Long/Double/Float/Boolean) -> null
 * Valid values — including numeric strings like "5" that Gson already accepted — parse exactly
 * as before. Only inputs that USED TO CRASH change behaviour. Serialisation (write) is unchanged.
 */
object AppGson {

    val gson: Gson by lazy {
        GsonBuilder()
            // primitives: non-null Kotlin fields, must never be null -> numeric/false default
            .registerTypeAdapter(Integer.TYPE, IntAdapter(0))
            .registerTypeAdapter(java.lang.Long.TYPE, LongAdapter(0L))
            .registerTypeAdapter(java.lang.Double.TYPE, DoubleAdapter(0.0))
            .registerTypeAdapter(java.lang.Float.TYPE, FloatAdapter(0f))
            .registerTypeAdapter(java.lang.Boolean.TYPE, BoolAdapter(false))
            // boxed: nullable Kotlin fields -> keep null semantics on missing/blank/garbage
            .registerTypeAdapter(Integer::class.java, IntAdapter(null))
            .registerTypeAdapter(java.lang.Long::class.java, LongAdapter(null))
            .registerTypeAdapter(java.lang.Double::class.java, DoubleAdapter(null))
            .registerTypeAdapter(java.lang.Float::class.java, FloatAdapter(null))
            .registerTypeAdapter(java.lang.Boolean::class.java, BoolAdapter(null))
            .create()
    }

    private class IntAdapter(private val default: Int?) : TypeAdapter<Int?>() {
        override fun read(reader: JsonReader): Int? {
            val raw = readRaw(reader)?.trim()
            if (raw.isNullOrEmpty()) return default
            return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: default
        }

        override fun write(out: JsonWriter, value: Int?) {
            if (value == null) out.nullValue() else out.value(value.toLong())
        }
    }

    private class LongAdapter(private val default: Long?) : TypeAdapter<Long?>() {
        override fun read(reader: JsonReader): Long? {
            val raw = readRaw(reader)?.trim()
            if (raw.isNullOrEmpty()) return default
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong() ?: default
        }

        override fun write(out: JsonWriter, value: Long?) {
            if (value == null) out.nullValue() else out.value(value)
        }
    }

    private class DoubleAdapter(private val default: Double?) : TypeAdapter<Double?>() {
        override fun read(reader: JsonReader): Double? {
            val raw = readRaw(reader)?.trim()
            if (raw.isNullOrEmpty()) return default
            return raw.toDoubleOrNull() ?: default
        }

        override fun write(out: JsonWriter, value: Double?) {
            if (value == null) out.nullValue() else out.value(value)
        }
    }

    private class FloatAdapter(private val default: Float?) : TypeAdapter<Float?>() {
        override fun read(reader: JsonReader): Float? {
            val raw = readRaw(reader)?.trim()
            if (raw.isNullOrEmpty()) return default
            return raw.toFloatOrNull() ?: default
        }

        override fun write(out: JsonWriter, value: Float?) {
            if (value == null) out.nullValue() else out.value(value.toDouble())
        }
    }

    private class BoolAdapter(private val default: Boolean?) : TypeAdapter<Boolean?>() {
        override fun read(reader: JsonReader): Boolean? {
            if (reader.peek() == JsonToken.BOOLEAN) return reader.nextBoolean()
            val raw = readRaw(reader)?.trim()?.lowercase()
            return when (raw) {
                null, "" -> default
                "true", "1", "1.0" -> true
                "false", "0", "0.0" -> false
                else -> default
            }
        }

        override fun write(out: JsonWriter, value: Boolean?) {
            if (value == null) out.nullValue() else out.value(value)
        }
    }

    /** NUMBER/STRING -> raw text; NULL -> null; BOOLEAN -> "true"/"false"; anything else skipped -> null. */
    private fun readRaw(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull(); null
        }

        JsonToken.NUMBER, JsonToken.STRING -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        else -> {
            reader.skipValue(); null
        }
    }
}
