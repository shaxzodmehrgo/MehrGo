package uz.teamwork.mehrgodriver.common.location_utils

import com.google.android.gms.maps.model.LatLng

object PolygonUtils {
    fun isPointInPolygon(polygon: List<LatLng>, point: LatLng): Boolean {
        var intersectCount = 0
        for (j in 0 until polygon.size - 1) {
            if (rayCastIntersect(point, polygon[j], polygon[j + 1])) {
                intersectCount++
            }
        }
        return intersectCount % 2 == 1 // odd count means inside polygon
    }

    private fun rayCastIntersect(point: LatLng, vertA: LatLng, vertB: LatLng): Boolean {
        val aY = vertA.latitude
        val bY = vertB.latitude
        val aX = vertA.longitude
        val bX = vertB.longitude
        val pY = point.latitude
        val pX = point.longitude
        if (aY > pY && bY > pY || aY < pY && bY < pY || aX < pX && bX < pX) {
            return false
        }
        val m = (aY - bY) / (aX - bX)
        val bee = -aX * m + aY
        val x = (pY - bee) / m
        return x > pX
    }
}