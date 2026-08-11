<?php
namespace app\controllers;

use app\models\ParentUser;
use app\models\RouteTrip;
use app\models\Subscription;
use app\models\Track;
use app\services\NotifyService;
use app\services\TelegramService;
use Yii;

/**
 * Telegram-бот для родителей: привязка чата (/start <token>), живая геолокация автобуса,
 * уведомления по событиям рейса (через NotifyService). Вебхук защищён секретом в URL.
 *
 * Настройка вебхука (однократно, с сервера):
 *   php -r "require 'vendor/autoload.php'; ..."  либо curl setWebhook на
 *   https://<host>/api/tg/webhook/<secret>  (secret = TelegramController::secret()).
 */
class TelegramController extends BaseApiController
{
    /** POST /api/tg/webhook/<secret> — приём апдейтов Telegram. */
    public function actionWebhook($secret = '')
    {
        if (!hash_equals(self::secret(), (string)$secret)) {
            $this->abort(404, 'not found');
        }
        if (!TelegramService::enabled()) {
            return $this->ok(['skipped' => 'tg_disabled']);
        }
        $u = $this->body();
        if (!is_array($u)) {
            return $this->ok(['ok' => true]);
        }
        if (isset($u['callback_query'])) {
            $this->onCallback($u['callback_query']);
            return $this->ok(['ok' => true]);
        }
        $msg = $u['message'] ?? $u['edited_message'] ?? null;
        if (is_array($msg)) {
            $this->onMessage($msg);
        }
        return $this->ok(['ok' => true]);
    }

    private function onMessage(array $msg): void
    {
        $chatId = $msg['chat']['id'] ?? null;
        $text = trim((string)($msg['text'] ?? ''));
        if ($chatId === null) {
            return;
        }
        if (strpos($text, '/start') === 0) {
            $this->bind($chatId, trim(substr($text, 6)), $msg);
            return;
        }
        if ($text === '/where' || mb_stripos($text, 'автобус') !== false) {
            $this->sendWhere($chatId);
            return;
        }
        if ($text === '/stop' || mb_stripos($text, 'отключить') !== false) {
            TelegramService::sendMessage($chatId, 'Ок, разовые запросы остановлены. Уведомления и живая геолокация на старте рейса продолжат приходить. Полностью отвязать — в приложении.');
            return;
        }
        TelegramService::sendMessage(
            $chatId,
            'MaktabGo 🚌 Напишите «Где автобус», чтобы получить живую геолокацию рейса. Привязка — по кнопке «Подключить Telegram» в приложении.',
            $this->kb()
        );
    }

    private function bind($chatId, string $payload, array $msg): void
    {
        $parent = self::parentFromToken($payload);
        if (!$parent) {
            TelegramService::sendMessage($chatId, 'Не удалось привязать чат: ссылка недействительна. Откройте «Подключить Telegram» в приложении MaktabGo ещё раз.');
            return;
        }
        $parent->tg_chat_id = (int)$chatId;
        $parent->save(false);
        $name = trim((string)($msg['from']['first_name'] ?? '')) ?: 'родитель';
        TelegramService::sendMessage(
            $chatId,
            "Готово, {$name}! ✅ Telegram привязан к MaktabGo.\n\nТеперь вы получаете уведомления на каждом этапе рейса, а на старте — <b>живую геолокацию автобуса</b> прямо здесь.\n\nВ любой момент напишите «Где автобус».",
            $this->kb()
        );
        $route = self::activeRoute($parent);
        if ($route) {
            $this->sendWhere($chatId, $parent, $route);
        }
    }

    private function sendWhere($chatId, ?ParentUser $parent = null, ?RouteTrip $route = null): void
    {
        $parent = $parent ?: ParentUser::findOne(['tg_chat_id' => $chatId]);
        if (!$parent) {
            TelegramService::sendMessage($chatId, 'Сначала привяжите чат по ссылке из приложения.');
            return;
        }
        $route = $route ?: self::activeRoute($parent);
        if (!$route) {
            TelegramService::sendMessage($chatId, 'Сейчас активного рейса нет. Как только водитель выедет — живая геолокация придёт сюда автоматически.');
            return;
        }
        $last = Track::find()->where(['route_id' => $route->id])->orderBy('id DESC')->one();
        if (!$last) {
            TelegramService::sendMessage($chatId, 'Рейс идёт, но геолокация ещё не поступила. Попробуйте через минуту.');
            return;
        }
        $res = TelegramService::sendLocation($chatId, (float)$last->lat, (float)$last->lon, 3600);
        $mid = $res['result']['message_id'] ?? null;
        if ($mid) {
            NotifyService::registerLive($route, $chatId, (int)$mid);
        }
        $speed = $last->speed !== null ? (int)round((float)$last->speed) . ' км/ч' : '—';
        TelegramService::sendMessage($chatId, "🚌 Автобус в пути. Скорость: {$speed}. Точка выше обновляется в реальном времени.");
    }

    private function onCallback(array $cb): void
    {
        TelegramService::answerCallback($cb['id'] ?? '');
        $chatId = $cb['message']['chat']['id'] ?? ($cb['from']['id'] ?? null);
        if ($chatId !== null && ($cb['data'] ?? '') === 'where') {
            $this->sendWhere($chatId);
        }
    }

    private function kb(): array
    {
        return [
            'keyboard' => [[['text' => '📍 Где автобус']], [['text' => '🔕 Отключить']]],
            'resize_keyboard' => true,
        ];
    }

    // ---- helpers ----

    private static function activeRoute(ParentUser $parent): ?RouteTrip
    {
        $subs = Subscription::find()->where(['parent_id' => $parent->id])->andWhere(['not', ['route_id' => null]])->all();
        foreach ($subs as $s) {
            $r = RouteTrip::findOne($s->route_id);
            if ($r && $r->status === RouteTrip::STATUS_RUNNING) {
                return $r;
            }
        }
        return null;
    }

    /** Подписанный токен привязки для deep-link. */
    public static function tokenFor(ParentUser $parent): string
    {
        return 'p' . $parent->id . '_' . substr(hash_hmac('sha256', 'p' . $parent->id, self::salt()), 0, 12);
    }

    private static function parentFromToken(string $payload): ?ParentUser
    {
        if (!preg_match('/^p(\d+)_([a-f0-9]{12})$/', $payload, $m)) {
            return null;
        }
        $parent = ParentUser::findOne((int)$m[1]);
        if (!$parent) {
            return null;
        }
        $expect = substr(hash_hmac('sha256', 'p' . $parent->id, self::salt()), 0, 12);
        return hash_equals($expect, $m[2]) ? $parent : null;
    }

    private static function salt(): string
    {
        return (string)(Yii::$app->params['admin']['token'] ?? (getenv('COOKIE_KEY') ?: 'maktabgo'));
    }

    public static function secret(): string
    {
        $s = (string)(getenv('TG_WEBHOOK_SECRET') ?: '');
        return $s !== '' ? $s : substr(hash('sha256', 'tgwh:' . self::salt()), 0, 24);
    }
}
