<?php
namespace app\commands;

use app\services\TelegramService;
use yii\console\Controller;
use yii\console\ExitCode;
use Yii;

/**
 * Настройка Telegram-бота родителей (одноразово, с сервера).
 *   php yii telegram/set-webhook                    — зарегистрировать вебхук
 *   php yii telegram/set-webhook --base=https://maktabgo.uz
 *   php yii telegram/info                           — показать секрет/бота/путь вебхука
 */
class TelegramController extends Controller
{
    /** База сайта для вебхука (иначе params['web_base'] / env WEB_BASE / https://maktabgo.uz). */
    public $base;

    public function options($actionID)
    {
        return ['base'];
    }

    public function actionSetWebhook()
    {
        if (!TelegramService::enabled()) {
            $this->stderr("TELEGRAM_BOT_TOKEN не задан в params.php — бот выключен.\n");
            return ExitCode::CONFIG;
        }
        $base = rtrim($this->base ?: (string)(Yii::$app->params['web_base'] ?? (getenv('WEB_BASE') ?: 'https://maktabgo.uz')), '/');
        $secret = \app\controllers\TelegramController::secret();
        $url = $base . '/api/tg/webhook/' . $secret;
        $res = TelegramService::setWebhook($url);
        $this->stdout("Webhook URL: $url\n");
        $this->stdout('Ответ Telegram: ' . json_encode($res, JSON_UNESCAPED_UNICODE) . "\n");
        $this->stdout('Бот: @' . TelegramService::botUsername() . "\n");
        return ExitCode::OK;
    }

    public function actionInfo()
    {
        $this->stdout('Бот: @' . TelegramService::botUsername() . "\n");
        $secret = \app\controllers\TelegramController::secret();
        $this->stdout('Секрет вебхука: ' . $secret . "\n");
        $this->stdout('Путь вебхука: /api/tg/webhook/' . $secret . "\n");
        $this->stdout('Deep-link (пример): https://t.me/' . TelegramService::botUsername() . "?start=p1_<подпись>\n");
        return ExitCode::OK;
    }
}
