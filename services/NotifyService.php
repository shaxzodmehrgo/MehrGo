<?php
namespace app\services;

use app\models\ParentUser;
use app\models\RouteMember;
use app\models\RouteTrip;
use app\models\Subscription;
use app\models\Track;
use Yii;

/**
 * Уведомления родителям об этапах рейса. Пишет событие в shuttle_event (для показа в web)
 * и шлёт Telegram, если у родителя привязан tg_chat_id. Аддитивно, ничего не ломает.
 *
 * Живая геолокация в Telegram: на старте рейса каждому родителю с tg_chat_id
 * отправляется live-локация (sendLocation live_period), её message_id кладём в кэш;
 * на каждой точке трека — editMessageLiveLocation; на завершении — stopMessageLiveLocation.
 */
class NotifyService
{
    private const LIVE_PERIOD = 3600;      // 1 ч живой геолокации на старт
    private const CACHE_TTL   = 14400;     // 4 ч хранить привязки live-сообщений

    public static function routeStage(RouteTrip $route, string $type, string $text): void
    {
        $members = RouteMember::find()->where(['route_id' => $route->id])->all();
        $notifiedParents = [];
        foreach ($members as $m) {
            $sub = Subscription::findOne($m->subscription_id);
            if (!$sub) {
                continue;
            }
            Yii::$app->db->createCommand()->insert('shuttle_event', [
                'subscription_id' => $sub->id,
                'route_id' => $route->id,
                'type' => $type,
                'text' => $text,
                'created_at' => time(),
            ])->execute();

            if (in_array($sub->parent_id, $notifiedParents, true)) {
                continue;
            }
            $notifiedParents[] = $sub->parent_id;
            $parent = ParentUser::findOne($sub->parent_id);
            if ($parent && !empty($parent->tg_chat_id) && TelegramService::enabled()) {
                TelegramService::sendMessage(
                    $parent->tg_chat_id,
                    self::emoji($type) . ' ' . $text,
                    self::mapButton()
                );
            }
        }

        if ($type === 'started') {
            self::startLive($route);
        }
        if (in_array($type, ['completed', 'done', 'cancelled'], true)) {
            self::stopLive($route);
        }
    }

    /** На старте: включить живую геолокацию всем родителям рейса (у кого есть tg_chat_id). */
    public static function startLive(RouteTrip $route): void
    {
        if (!TelegramService::enabled()) {
            return;
        }
        [$lat, $lon] = self::currentPos($route);
        if ($lat === null) {
            return;
        }
        $entries = [];
        foreach (self::parentsOfRoute($route) as $parent) {
            if (empty($parent->tg_chat_id)) {
                continue;
            }
            $res = TelegramService::sendLocation($parent->tg_chat_id, $lat, $lon, self::LIVE_PERIOD);
            $mid = $res['result']['message_id'] ?? null;
            if ($mid) {
                $entries[] = ['chat_id' => (string)$parent->tg_chat_id, 'message_id' => (int)$mid];
                TelegramService::sendMessage(
                    $parent->tg_chat_id,
                    '📍 Живая геолокация автобуса включена — точка на карте выше обновляется в реальном времени.'
                );
            }
        }
        if ($entries) {
            Yii::$app->cache->set(self::key($route->id), $entries, self::CACHE_TTL);
        }
    }

    /** На каждой точке трека: подвинуть все live-сообщения этого рейса. */
    public static function pushLive(RouteTrip $route, float $lat, float $lon): void
    {
        if (!TelegramService::enabled()) {
            return;
        }
        $entries = Yii::$app->cache->get(self::key($route->id));
        if (!is_array($entries) || !$entries) {
            return;
        }
        foreach ($entries as $e) {
            try {
                TelegramService::editLiveLocation($e['chat_id'], $e['message_id'], $lat, $lon);
            } catch (\Throwable $ex) {
                // не роняем трек из-за телеги
            }
        }
    }

    /** На завершении/отмене: остановить живые локации. */
    public static function stopLive(RouteTrip $route): void
    {
        $entries = Yii::$app->cache->get(self::key($route->id));
        if (is_array($entries) && TelegramService::enabled()) {
            foreach ($entries as $e) {
                try {
                    TelegramService::stopLiveLocation($e['chat_id'], $e['message_id']);
                } catch (\Throwable $ex) {
                }
            }
        }
        Yii::$app->cache->delete(self::key($route->id));
    }

    /** Разовая live-локация по запросу из бота (/where). Регистрируем, чтобы трек её двигал. */
    public static function registerLive(RouteTrip $route, $chatId, int $messageId): void
    {
        $entries = Yii::$app->cache->get(self::key($route->id));
        if (!is_array($entries)) {
            $entries = [];
        }
        foreach ($entries as $e) {
            if ((string)$e['chat_id'] === (string)$chatId) {
                return; // уже есть live для этого чата
            }
        }
        $entries[] = ['chat_id' => (string)$chatId, 'message_id' => $messageId];
        Yii::$app->cache->set(self::key($route->id), $entries, self::CACHE_TTL);
    }

    // ---- helpers ----

    public static function parentsOfRoute(RouteTrip $route): array
    {
        $ids = [];
        $out = [];
        foreach (RouteMember::find()->where(['route_id' => $route->id])->all() as $m) {
            $sub = Subscription::findOne($m->subscription_id);
            if (!$sub || in_array($sub->parent_id, $ids, true)) {
                continue;
            }
            $ids[] = $sub->parent_id;
            $p = ParentUser::findOne($sub->parent_id);
            if ($p) {
                $out[] = $p;
            }
        }
        return $out;
    }

    private static function currentPos(RouteTrip $route): array
    {
        $last = Track::find()->where(['route_id' => $route->id])->orderBy('id DESC')->one();
        if ($last) {
            return [(float)$last->lat, (float)$last->lon];
        }
        if ($route->driver_id) {
            $d = \app\models\Driver::findOne($route->driver_id);
            if ($d && $d->cur_lat) {
                return [(float)$d->cur_lat, (float)$d->cur_lon];
            }
        }
        $s = \app\models\School::findOne($route->school_id);
        if ($s && $s->lat) {
            return [(float)$s->lat, (float)$s->lon];
        }
        return [null, null];
    }

    private static function key(int $routeId): string
    {
        return 'tglive:' . $routeId;
    }

    private static function mapButton(): ?array
    {
        $base = self::webBase();
        if ($base === '') {
            return null;
        }
        return ['inline_keyboard' => [[['text' => '🗺 Открыть карту', 'url' => $base]]]];
    }

    private static function webBase(): string
    {
        $b = (string)(Yii::$app->params['web_base'] ?? getenv('WEB_BASE') ?: 'https://maktabgo.uz');
        return rtrim($b, '/');
    }

    private static function emoji(string $type): string
    {
        return [
            'accepted' => '✅', 'started' => '🚌', 'picked' => '🧒',
            'arrived' => '📍', 'completed' => '🏁', 'done' => '🏁',
            'alert_speeding' => '⚠️', 'alert_late' => '⏰', 'alert_offroute' => '🛰️',
        ][$type] ?? '🔔';
    }
}
