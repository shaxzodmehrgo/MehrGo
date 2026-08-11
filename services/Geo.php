<?php
namespace app\services;

/**
 * Геометрия на сфере: расстояния и центроиды.
 */
class Geo
{
    const EARTH_R = 6371000.0; // метры

    /** Гаверсинус, метры между двумя точками. */
    public static function haversineMeters($lat1, $lon1, $lat2, $lon2)
    {
        $dLat = deg2rad($lat2 - $lat1);
        $dLon = deg2rad($lon2 - $lon1);
        $a = sin($dLat / 2) ** 2
            + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLon / 2) ** 2;
        $c = 2 * atan2(sqrt($a), sqrt(1 - $a));
        return self::EARTH_R * $c;
    }

    /**
     * Центроид набора точек.
     * @param array $points [[lat,lon], ...]
     * @return array [lat, lon]
     */
    public static function centroid(array $points)
    {
        $n = count($points);
        if ($n === 0) {
            return [0.0, 0.0];
        }
        $lat = 0.0;
        $lon = 0.0;
        foreach ($points as $p) {
            $lat += $p[0];
            $lon += $p[1];
        }
        return [$lat / $n, $lon / $n];
    }
}
