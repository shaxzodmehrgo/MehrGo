<?php
namespace app\controllers;

use app\models\ParentUser;
use app\services\OtpService;
use Yii;

/**
 * OTP-авторизация родителя. Реальный код через SMS (Eskiz) + Telegram (OtpService).
 * Если провайдеры не настроены — dev-режим (код возвращается в ответе). Контракт как в такси.
 */
class AuthController extends BaseApiController
{
    public function actionOtp()
    {
        $phone = $this->normalizePhone($this->param('phone'));
        if (!$phone) {
            $this->abort(422, 'Укажите телефон');
        }
        $r = OtpService::request($phone, 'client');
        $out = ['phone' => $phone, 'channel' => $r['channel'], 'message' => 'Код отправлен'];
        if ($r['dev_code'] !== null) {
            $out['dev_code'] = $r['dev_code'];
            $out['message'] = 'Код (демо, провайдер не настроен): ' . $r['dev_code'];
        }
        return $this->ok($out);
    }

    public function actionVerify()
    {
        $phone = $this->normalizePhone($this->param('phone'));
        $code = (string)$this->param('code');
        $name = $this->param('name');

        if (!$phone) {
            $this->abort(422, 'Укажите телефон');
        }
        if (!OtpService::verify($phone, $code, 'client')) {
            $this->abort(401, 'Неверный или просроченный код');
        }

        $parent = ParentUser::findOne(['phone' => $phone]);
        if (!$parent) {
            $parent = new ParentUser();
            $parent->phone = $phone;
            $parent->name = $name ?: 'Родитель';
            $parent->created_at = time();
        } elseif ($name) {
            $parent->name = $name;
        }
        $parent->auth_key = Yii::$app->security->generateRandomString(40);
        $parent->save(false);

        return $this->ok([
            'token' => $parent->auth_key,
            'parent' => ['id' => $parent->id, 'phone' => $parent->phone, 'name' => $parent->name],
        ]);
    }

    private function normalizePhone($raw): ?string
    {
        $p = preg_replace('/[^\d+]/', '', (string)$raw);
        return $p !== '' ? $p : null;
    }
}
