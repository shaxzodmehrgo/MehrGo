package uz.teamwork.mehrgodriver.common

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract test for the lenient [AppGson] that fixes crash A / crash B.
 * Verifies (1) malformed numeric fields NO LONGER throw, (2) valid data still parses exactly,
 * (3) serialisation (used by GpsBatchSocketChannel + request bodies) is byte-identical to stock Gson.
 */
class AppGsonTest {

    private val gson: Gson = AppGson.gson

    // Mirrors the DriverEarningsSummary.by_day shape the crash-B agent flagged: primitive
    // (non-null Kotlin) numeric leaves inside a list.
    data class Row(
        val earned: Long,
        val distanceKm: Double,
        val ordersCount: Int,
        val rate: Float,
        val flag: Boolean
    )

    data class Wrap(val by_day: List<Row>)

    // Nullable (boxed) leaves.
    data class Nullable(val total: Int?, val price: Double?, val active: Boolean?)

    @Test
    fun emptyStringNumeric_coercesToDefault_noThrow() {
        val json =
            """{"by_day":[{"earned":"","distanceKm":"","ordersCount":"","rate":"","flag":""}]}"""
        val r = gson.fromJson(json, Wrap::class.java).by_day[0]
        assertEquals(0L, r.earned)
        assertEquals(0.0, r.distanceKm, 0.0)
        assertEquals(0, r.ordersCount)
        assertEquals(0f, r.rate)
        assertEquals(false, r.flag)
    }

    @Test
    fun nullNumeric_onNonNullField_coercesToDefault_noThrow() {
        val json =
            """{"by_day":[{"earned":null,"distanceKm":null,"ordersCount":null,"rate":null,"flag":null}]}"""
        val r = gson.fromJson(json, Wrap::class.java).by_day[0]
        assertEquals(0L, r.earned)
        assertEquals(0.0, r.distanceKm, 0.0)
        assertEquals(0, r.ordersCount)
    }

    @Test
    fun validValues_parseExactly() {
        val json =
            """{"by_day":[{"earned":25000,"distanceKm":12.5,"ordersCount":3,"rate":1.5,"flag":true}]}"""
        val r = gson.fromJson(json, Wrap::class.java).by_day[0]
        assertEquals(25000L, r.earned)
        assertEquals(12.5, r.distanceKm, 0.0)
        assertEquals(3, r.ordersCount)
        assertEquals(1.5f, r.rate)
        assertEquals(true, r.flag)
    }

    @Test
    fun numericAsString_stillParses_legacyBehaviourKept() {
        val json =
            """{"by_day":[{"earned":"25000","distanceKm":"12.5","ordersCount":"3","rate":"1.5","flag":"true"}]}"""
        val r = gson.fromJson(json, Wrap::class.java).by_day[0]
        assertEquals(25000L, r.earned)
        assertEquals(12.5, r.distanceKm, 0.0)
        assertEquals(3, r.ordersCount)
        assertEquals(true, r.flag)
    }

    @Test
    fun nullableFields_keepNull_onBlankOrNull() {
        val json = """{"total":"","price":null,"active":""}"""
        val n = gson.fromJson(json, Nullable::class.java)
        assertNull(n.total)
        assertNull(n.price)
        assertNull(n.active)
    }

    @Test
    fun serialisation_isByteIdenticalToStock_intsHaveNoDecimal() {
        // GpsBatchSocketChannel + request bodies rely on this exact shape.
        val out = gson.toJson(Row(25000L, 12.5, 3, 1.5f, true))
        assertEquals(
            """{"earned":25000,"distanceKm":12.5,"ordersCount":3,"rate":1.5,"flag":true}""",
            out
        )
    }

    @Test
    fun serialisation_stockGson_parity() {
        // The same object must serialise identically under stock Gson and AppGson.
        val row = Row(25000L, 12.0, 3, 2f, false)
        assertEquals(Gson().toJson(row), gson.toJson(row))
    }
}
