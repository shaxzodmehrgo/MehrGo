<?php
namespace app\services;

use app\models\Driver;
use app\models\Otp;
use app\models\ParentUser;
use Yii;

/**
 * OTP-коды входа: SMS (Eskiz) + Telegram (если чат привязан). Заменяет демо-код 1111.
 * Если ни SMS, ни Telegram не настроены/не привязаны — dev-режим: код возвращается в ответе
 * (фронт автозаполняет), чтобы демо продолжало работать без кред.
 */
class OtpService
{
    public static function normalizePhone($raw): string
    {
        $p = preg_replace('/[^\d+]/', '', (string)$raw);
        return $p !== '' ? $p : '';
    }

    private static function ttl(): int
    {
        return (int)(Yii::$app->params['otp']['ttl'] ?? 300);
    }

    private static function length(): int
    {
        return (int)(Yii::$app->params['otp']['code_length'] ?? 4);
    }

    private static function genCode(): string
    {
        $len = self::length();
        $min = (int)str_pad('1', $len, '0');       // 1000
        $max = (int)str_pad('9', $len, '9');       // 9999
        return (string)random_int($min, $max);
    }

    /** Найти tg_chat_id по телефону (client/driver). */
    private static function chatId(string $phone, string $role): ?int
    {
        $m = $role === 'driver'
            ? Driver::findOne(['phone' => $phone])
            : ParentUser::findOne(['phone' => $phone]);
        return $m && $m->tg_chat_id ? (int)$m->tg_chat_id : null;
    }

    /**
     * Запросить код. Возвращает ['sent_sms'=>b,'sent_tg'=>b,'dev_code'=>?string,'channel'=>string].
     */
    public static function request(string $phone, string $role = 'client'): array
    {
        $phone = self::normalizePhone($phone);
        $code = self::genCode();
        $now = time();

        // гасим прежние неиспользованные коды этого телефона+роли
        Otp::updateAll(['used' => true], ['phone' => $phone, 'role' => $role, 'used' => false]);

        $otp = new Otp();
        $otp->phone = $phone;
        $otp->code = $code;
        $otp->role = $role;
        $otp->expires_at = $now + self::ttl();
        $otp->used = false;
        $otp->attempts = 0;
        $otp->created_at = $now;

        $text = self::messageText($code);
        $sentSms = false; $sentTg = false; $channel = 'dev';

        if (SmsService::enabled()) {
            $sentSms = SmsService::send($phone, $text);
            if ($sentSms) { $channel = 'sms'; }
        }
        $chat = self::chatId($phone, $role);
        if ($chat && TelegramService::enabled()) {
            $r = TelegramService::sendMessage($chat, $text);
            $sentTg = (bool)($r['ok'] ?? false);
            if ($sentTg) { $channel = $sentSms ? 'sms+tg' : 'telegram'; }
        }

        $otp->channel = $channel;
        $otp->save(false);

        $devReturn = (bool)(Yii::$app->params['otp']['dev_return_code'] ?? true);
        $devCode = (!$sentSms && !$sentTg && $devReturn) ? $code : null;

        return ['sent_sms' => $sentSms, 'sent_tg' => $sentTg, 'channel' => $channel, 'dev_code' => $devCode];
    }

    /** Проверить код. */
    public static function verify(string $phone, string $code, string $role = 'client'): bool
    {
        $phone = self::normalizePhone($phone);
        $code = trim((string)$code);
        $otp = Otp::find()
            ->where(['phone' => $phone, 'role' => $role, 'used' => false])
            ->orderBy('id DESC')->one();
        if (!$otp) {
            return false;
        }
        $otp->attempts = (int)$otp->attempts + 1;
        if ($otp->attempts > 6 || $otp->expires_at < time()) {
            $otp->used = true; $otp->save(false);
            return false;
        }
        if (!hash_equals((string)$otp->code, $code)) {
            $otp->save(false);
            return false;
        }
        $otp->used = true;
        $otp->save(false);
        return true;
    }

    private static function messageText(string $code): string
    {
        $brand = Yii::$app->params['brand'] ?? 'Birga';
        return "$brand: kod tasdiqlash / код подтверждения: $code";
    }
}
