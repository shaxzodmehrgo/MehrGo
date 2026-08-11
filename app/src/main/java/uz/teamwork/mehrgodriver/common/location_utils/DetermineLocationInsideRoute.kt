package uz.teamwork.mehrgodriver.common.location_utils

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil

object DetermineLocationInsideRoute {
    fun isLocationInsideRoute(
        location: LatLng,
        routePath: List<LatLng>,
        toleranceMeters: Double = 100.0
    ): Boolean {
        for (i in 0 until routePath.size - 1) {
            val segmentStart = routePath[i]
            val segmentEnd = routePath[i + 1]
            val distanceToSegment = distanceToSegment(location, segmentStart, segmentEnd)
            if (distanceToSegment <= toleranceMeters) {
                return true
            }
        }
        return false
    }

    private fun distanceToSegment(location: LatLng, start: LatLng, end: LatLng): Double {
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
        return SphericalUtil.computeDistanceBetween(location, closestPoint)
    }

    // You can work these methods if need without "SphericalUtil" class in library
    fun computeDistanceBetween(from: LatLng, to: LatLng): Double {
        return computeAngleBetween(from, to) * 6371009 // Earth radius
    }

    private fun computeAngleBetween(from: LatLng, to: LatLng): Double {
        return distanceRadians(
            Math.toRadians(from.latitude), Math.toRadians(from.longitude),
            Math.toRadians(to.latitude), Math.toRadians(to.longitude)
        )
    }

    private fun distanceRadians(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        return arcHav(havDistance(lat1, lat2, lng1 - lng2))
    }

    private fun arcHav(x: Double): Double {
        return 2 * Math.asin(Math.sqrt(x))
    }

    private fun havDistance(lat1: Double, lat2: Double, dLng: Double): Double {
        return hav(lat1 - lat2) + hav(dLng) * Math.cos(lat1) * Math.cos(lat2)
    }

    private fun hav(x: Double): Double {
        val sinHalf = Math.sin(x * 0.5)
        return sinHalf * sinHalf
    }
}