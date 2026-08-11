<?php
namespace app\services;

use Yii;

/**
 * Telegram Bot API (по образцу common\services\TelegramService такси).
 * Токен серверно: params['telegram']['bot_token'] (env TELEGRAM_BOT_TOKEN). НЕ хардкодим.
 */
class TelegramService
{
    private const BASE = 'https://api.telegram.org/bot';

    public static function enabled(): bool
    {
        return !empty(Yii::$app->params['telegram']['bot_token']);
    }

    public static function token(): string
    {
        return (string)(Yii::$app->params['telegram']['bot_token'] ?? '');
    }

    public static function username(): string
    {
        return (string)(Yii::$app->params['telegram']['bot_username'] ?? '');
    }

    /** Универсальный вызов Bot API. */
    public static function api(string $method, array $data = []): ?array
    {
        if (!self::enabled()) {
            return null;
        }
        $ch = curl_init(self::BASE . self::token() . '/' . $method);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query($data),
        ]);
        $raw = curl_exec($ch);
        curl_close($ch);
        return is_string($raw) ? json_decode($raw, true) : null;
    }

    public static function sendMessage($chatId, string $text, ?array $replyMarkup = null): ?array
    {
        $data = ['chat_id' => $chatId, 'text' => $text, 'parse_mode' => 'HTML', 'disable_web_page_preview' => true];
        if ($replyMarkup !== null) {
            $data['reply_markup'] = json_encode($replyMarkup);
        }
        return self::api('sendMessage', $data);
    }

    /** Локация; при $livePeriod>0 — «живая» точка, которую можно двигать editMessageLiveLocation. */
    public static function sendLocation($chatId, float $lat, float $lon, int $livePeriod = 0, ?array $replyMarkup = null): ?array
    {
        $data = ['chat_id' => $chatId, 'latitude' => $lat, 'longitude' => $lon];
        if ($livePeriod > 0) {
            $data['live_period'] = $livePeriod;
        }
        if ($replyMarkup !== null) {
            $data['reply_markup'] = json_encode($replyMarkup);
        }
        return self::api('sendLocation', $data);
    }

    public static function editLiveLocation($chatId, $messageId, float $lat, float $lon): ?array
    {
        return self::api('editMessageLiveLocation', [
            'chat_id' => $chatId,
            'message_id' => $messageId,
            'latitude' => $lat,
            'longitude' => $lon,
        ]);
    }

    public static function stopLiveLocation($chatId, $messageId): ?array
    {
        return self::api('stopMessageLiveLocation', ['chat_id' => $chatId, 'message_id' => $messageId]);
    }

    public static function answerCallback($callbackId, string $text = ''): ?array
    {
        return self::api('answerCallbackQuery', ['callback_query_id' => $callbackId, 'text' => $text]);
    }

    public static function setWebhook(string $url): ?array
    {
        return self::api('setWebhook', ['url' => $url]);
    }

    /** @username бота — из конфига или через getMe (кэш на сутки), для deep-link t.me/<bot>?start=... */
    public static function botUsername(): string
    {
        $cfg = self::username();
        if ($cfg !== '') {
            return ltrim($cfg, '@');
        }
        $cached = Yii::$app->cache->get('tg_bot_username');
        if (is_string($cached) && $cached !== '') {
            return $cached;
        }
        $me = self::api('getMe');
        $u = $me['result']['username'] ?? '';
        if ($u !== '') {
            Yii::$app->cache->set('tg_bot_username', $u, 86400);
        }
        return (string)$u;
    }
}
