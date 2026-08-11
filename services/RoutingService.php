<?php
namespace app\services;

use Yii;

/**
 * Абстракция маршрутизации: расстояние/ETA/полилиния.
 * Провайдеры: yandex (основной), osrm (route.teamwork.uz — резерв), haversine (офлайн-оценка).
 * Точка переключения — params['routing']['provider'].
 */
class RoutingService
{
    private $cfg;

    public function __construct($cfg = null)
    {
        $this->cfg = $cfg ?: Yii::$app->params['routing'];
    }

    /** Расстояние по дороге между двумя точками, км. */
    public function distanceKm($fromLat, $fromLon, $toLat, $toLon)
    {
        $legs = $this->routeMeters([[$fromLat, $fromLon], [$toLat, $toLon]]);
        return round($legs['distance'] / 1000, 2);
    }

    /**
     * Маршрут по последовательности точек.
     * @param array $points [[lat,lon], ...]
     * @return array ['distance' => метры, 'duration' => сек, 'polyline' => string|null, 'provider' => string]
     */
    public function routeMeters(array $points)
    {
        $provider = $this->cfg['provider'] ?? 'haversine';
        try {
            if ($provider === 'osrm') {
                return $this->osrm($points);
            }
            if ($provider === 'yandex' && !empty($this->cfg['yandex_key'])) {
                return $this->yandex($points);
            }
        } catch (\Throwable $e) {
            Yii::warning('Routing provider failed, fallback to haversine: ' . $e->getMessage());
        }
        return $this->haversine($points);
    }

    private function haversine(array $points)
    {
        $factor = $this->cfg['road_factor'] ?? 1.35;
        $meters = 0.0;
        for ($i = 1; $i < count($points); $i++) {
            $meters += Geo::haversineMeters(
                $points[$i - 1][0], $points[$i - 1][1],
                $points[$i][0], $points[$i][1]
            );
        }
        $meters *= $factor;
        return [
            'distance' => $meters,
            'duration' => (int)round($meters / (30 * 1000 / 3600)), // ~30 км/ч
            'polyline' => null,
            'provider' => 'haversine',
        ];
    }

    private function osrm(array $points)
    {
        $coords = implode(';', array_map(fn($p) => $p[1] . ',' . $p[0], $points)); // lon,lat
        $url = rtrim($this->cfg['osrm_url'], '/') . "/route/v1/driving/$coords?overview=full&geometries=polyline";
        $json = $this->httpGet($url);
        $data = json_decode($json, true);
        if (empty($data['routes'][0])) {
            throw new \RuntimeException('OSRM: no route');
        }
        $r = $data['routes'][0];
        return [
            'distance' => (float)$r['distance'],
            'duration' => (int)$r['duration'],
            'polyline' => $r['geometry'] ?? null,
            'provider' => 'osrm',
        ];
    }

    private function yandex(array $points)
    {
        // Yandex Distance Matrix / Router (HTTP). Требует коммерческого ключа.
        // Реализация вызова оставлена под ключ заказчика; при ошибке — fallback.
        $origins = $points[0][0] . ',' . $points[0][1];
        $dest = end($points);
        $destinations = $dest[0] . ',' . $dest[1];
        $url = 'https://api.routing.yandex.net/v2/distancematrix'
            . '?apikey=' . urlencode($this->cfg['yandex_key'])
            . "&origins=$origins&destinations=$destinations&mode=driving";
        $json = $this->httpGet($url);
        $data = json_decode($json, true);
        $row = $data['rows'][0]['elements'][0] ?? null;
        if (!$row || ($row['status'] ?? '') !== 'OK') {
            throw new \RuntimeException('Yandex: no matrix element');
        }
        return [
            'distance' => (float)$row['distance']['value'],
            'duration' => (int)$row['duration']['value'],
            'polyline' => null,
            'provider' => 'yandex',
        ];
    }

    private function httpGet($url)
    {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 8,
            CURLOPT_HTTPHEADER => ['Accept: application/json'],
        ]);
        $res = curl_exec($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);
        if ($res === false || $code >= 400) {
            throw new \RuntimeException("HTTP $code $err");
        }
        return $res;
    }
}
