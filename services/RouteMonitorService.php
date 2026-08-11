<?php
namespace app\services;

use app\models\Driver;
use app\models\RouteTrip;
use app\models\School;
use app\models\Track;
use Yii;

/**
 * Мониторинг рейса из потока /track (одна точка правды на бэкенде):
 *  - скорость (из соседних точек или из приложения),
 *  - превышение скорости → алерт диспетчеру + предупреждение водителю,
 *  - прогноз опоздания к школе → алерт диспетчеру и родителям.
 * Всё аддитивно. Off-route/ожидание — след. итерация (нужна полилиния Yandex).
 */
class RouteMonitorService
{
    /** @return string[] предупреждения ВОДИТЕЛЮ (показать в приложении в ответе /track). */
    public static function onTrack(RouteTrip $route, Driver $driver, $appSpeedKmh = null): array
    {
        $cfg = Yii::$app->params['monitor'] ?? [];
        $limit = (float)($cfg['speed_limit_kmh'] ?? 80);
        $lateMargin = (int)($cfg['late_margin_min'] ?? 5);

        $pts = Track::find()->where(['route_id' => $route->id])
            ->orderBy(['recorded_at' => SORT_DESC, 'id' => SORT_DESC])->limit(2)->all();
        if (!$pts) {
            return [];
        }
        $cur = $pts[0];
        $warnings = [];

        $speed = $appSpeedKmh !== null && $appSpeedKmh !== '' ? (float)$appSpeedKmh : null;
        if ($speed === null && count($pts) >= 2) {
            $prev = $pts[1];
            $dt = (int)$cur->recorded_at - (int)$prev->recorded_at;
            if ($dt > 0) {
                $km = self::haversine($prev->lat, $prev->lon, $cur->lat, $cur->lon);
                $speed = $km / ($dt / 3600.0);
            }
        }
        if ($speed !== null && $speed >= 0) {
            $cur->speed = round(min($speed, 9999.99), 2); // не переполнять decimal(6,2)
            $cur->save(false);
            if ($speed > $limit && $speed < 400) {        // >400 км/ч = GPS-глюк, не алертим
                $warnings[] = 'Снизьте скорость — превышение (' . round($speed) . ' км/ч)';
                self::alert($route, 'speeding', 'Водитель #' . $driver->id . ' превышает скорость: ' . round($speed) . ' км/ч', false);
            }
        }

        $school = School::findOne($route->school_id);
        if ($school && !empty($school->start_time) && $speed !== null && $speed > 5
            && $route->status === RouteTrip::STATUS_RUNNING) {
            $routing = new RoutingService();
            $distKm = $routing->distanceKm($cur->lat, $cur->lon, $school->lat, $school->lon);
            $etaSec = (int)(($distKm / max($speed, 1)) * 3600);
            $arrival = time() + $etaSec;
            $target = strtotime(date('Y-m-d') . ' ' . $school->start_time);
            if ($target && $target < time()) {
                $target = strtotime('+1 day', $target); // ближайшее наступление времени школы (не «сегодня в прошлом»)
            }
            if ($target && $arrival > $target + $lateMargin * 60) {
                $lateMin = (int)round(($arrival - $target) / 60);
                self::alert($route, 'late', 'Возможное опоздание к «' . $school->name . '» на ~' . $lateMin . ' мин', true);
            }
        }
        return $warnings;
    }

    private static function alert(RouteTrip $route, string $type, string $text, bool $notifyParents): void
    {
        $ttl = $type === 'speeding' ? 120 : 300;
        $recent = (new \yii\db\Query())->from('shuttle_event')
            ->where(['route_id' => $route->id, 'type' => 'alert_' . $type])
            ->andWhere(['>', 'created_at', time() - $ttl])->exists();
        if ($recent) {
            return;
        }
        Yii::$app->db->createCommand()->insert('shuttle_event', [
            'subscription_id' => null, 'route_id' => $route->id,
            'type' => 'alert_' . $type, 'text' => $text, 'created_at' => time(),
        ])->execute();
        $chat = Yii::$app->params['dispatcher']['tg_chat_id'] ?? '';
        if ($chat && TelegramService::enabled()) {
            TelegramService::sendMessage($chat, '⚠ ' . $text);
        }
        if ($notifyParents) {
            NotifyService::routeStage($route, 'alert_' . $type, $text);
        }
    }

    private static function haversine($lat1, $lon1, $lat2, $lon2): float
    {
        $r = 6371.0;
        $dLat = deg2rad($lat2 - $lat1);
        $dLon = deg2rad($lon2 - $lon1);
        $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLon / 2) ** 2;
        return $r * 2 * atan2(sqrt($a), sqrt(1 - $a));
    }
}
