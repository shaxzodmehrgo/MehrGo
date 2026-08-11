package uz.teamwork.mehrgodriver.common.location_utils

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil

object LocationUtils {
    fun findNearestLocationIndex(location: LatLng, routePath: List<LatLng>): Pair<Int, LatLng>? {
        var nearestLocationIndex: Int? = null
        var nearestLocation: LatLng? = null
        var minDistance = Double.MAX_VALUE

        for (i in 0 until routePath.size - 1) {
            val segmentStart = routePath[i]
            val segmentEnd = routePath[i + 1]
            val closestPoint = findClosestPointOnSegment(location, segmentStart, segmentEnd)
            val distance = SphericalUtil.computeDistanceBetween(location, closestPoint)
            if (distance < minDistance) {
                minDistance = distance
                nearestLocationIndex = i
                nearestLocation = closestPoint
            }
        }

        return if (nearestLocationIndex != null && nearestLocation != null) {
            Pair(nearestLocationIndex, nearestLocation)
        } else {
            null
        }
    }

    private fun findClosestPointOnSegment(location: LatLng, start: LatLng, end: LatLng): LatLng {
        val segmentVector = LatLng(end.latitude - start.latitude, end.longitude - start.longitude)
        val pointVector =
            LatLng(location.latitude - start.latitude, location.longitude - start.longitude)
        val segmentLength =
            Math.sqrt((segmentVector.latitude * segmentVector.latitude) + (segmentVector.longitude * segmentVector.longitude))
        val unitSegmentVector =
            LatLng(segmentVector.latitude / segmentLength, segmentVector.longitude / segmentLength)
        val dotProduct =
            (pointVector.latitude * unitSegmentVector.latitude) + (pointVector.longitude * unitSegmentVector.longitude)
        val closestPoint = if (dotProduct < 0) {
            start
        } else if (dotProduct > segmentLength) {
            end
        } else {
            LatLng(
                start.latitude + unitSegmentVector.latitude * dotProduct,
                start.longitude + unitSegmentVector.longitude * dotProduct
            )
        }
        return closestPoint
    }
}