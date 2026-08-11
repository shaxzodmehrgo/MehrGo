<?php
namespace app\services;

use Yii;

/**
 * Отправка SMS через Eskiz (notify.eskiz.uz) — порт лин-версии из такси (common/modules/sms/smses/EskizSms).
 * Креды серверно: params['sms'] (env ESKIZ_EMAIL / ESKIZ_PASSWORD / ESKIZ_FROM). Токен кэшируется.
 */
class SmsService
{
    private const BASE = 'https://notify.eskiz.uz/api/';

    public static function enabled(): bool
    {
        $c = Yii::$app->params['sms'] ?? [];
        return !empty($c['eskiz_email']) && !empty($c['eskiz_password']);
    }

    public static function normalizePhone($phone): string
    {
        return preg_replace('/\D/', '', (string)$phone);
    }

    private static function cfg(string $k, $def = ''): string
    {
        return (string)(Yii::$app->params['sms'][$k] ?? $def);
    }

    /** Логин в Eskiz → токен (кэш 20 дней). */
    private static function fetchToken(): ?string
    {
        $res = self::http('POST', self::BASE . 'auth/login', [
            'email' => self::cfg('eskiz_email'),
            'password' => self::cfg('eskiz_password'),
        ]);
        $token = $res['body']['data']['token'] ?? null;
        if ($token) {
            Yii::$app->cache->set('eskiz_token', $token, 20 * 24 * 3600);
        }
        return $token;
    }

    private static function token(): ?string
    {
        $t = Yii::$app->cache->get('eskiz_token');
        return $t ?: self::fetchToken();
    }

    /** Отправить SMS. Возвращает true при успехе. */
    public static function send(string $phone, string $text): bool
    {
        if (!self::enabled()) {
            return false;
        }
        $phone = self::normalizePhone($phone);
        $token = self::token();
        if (!$token) {
            return false;
        }
        $payload = [
            'mobile_phone' => $phone,
            'message' => $text,
            'from' => self::cfg('eskiz_from', '4546'),
        ];
        $res = self::http('POST', self::BASE . 'message/sms/send', $payload, $token);
        if ($res['code'] === 401) { // токен протух — обновить и повторить
            $token = self::fetchToken();
            if (!$token) {
                return false;
            }
            $res = self::http('POST', self::BASE . 'message/sms/send', $payload, $token);
        }
        if ($res['code'] < 200 || $res['code'] >= 300) {
            Yii::warning('Eskiz send failed: ' . json_encode($res['body']));
            return false;
        }
        return true;
    }

    private static function http(string $method, string $url, array $data, ?string $bearer = null): array
    {
        $ch = curl_init($url);
        $headers = ['Accept: application/json'];
        if ($bearer) {
            $headers[] = 'Authorization: Bearer ' . $bearer;
        }
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_POSTFIELDS => http_build_query($data),
            CURLOPT_HTTPHEADER => $headers,
        ]);
        $raw = curl_exec($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        return ['code' => $code, 'body' => is_string($raw) ? json_decode($raw, true) : null];
    }
}
