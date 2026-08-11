<?php
namespace app\controllers;

use app\models\Driver;
use app\models\ParentUser;
use Yii;
use yii\web\Controller;
use yii\web\Response;

/**
 * Базовый контроллер API: JSON-ответы, CORS, разбор Bearer-токена.
 */
class BaseApiController extends Controller
{
    public $enableCsrfValidation = false;

    public function beforeAction($action)
    {
        Yii::$app->response->format = Response::FORMAT_JSON;
        if (Yii::$app->request->isOptions) {
            Yii::$app->response->statusCode = 204;
            Yii::$app->end();
        }
        return parent::beforeAction($action);
    }

    protected function body()
    {
        return Yii::$app->request->bodyParams;
    }

    protected function param($key, $default = null)
    {
        return Yii::$app->request->bodyParams[$key] ?? Yii::$app->request->get($key, $default);
    }

    protected function bearer(): ?string
    {
        $h = Yii::$app->request->headers->get('Authorization');
        if ($h && preg_match('/Bearer\s+(.+)/i', $h, $m)) {
            return trim($m[1]);
        }
        return null;
    }

    protected function currentParent(): ?ParentUser
    {
        $token = $this->bearer();
        return $token ? ParentUser::findOne(['auth_key' => $token]) : null;
    }

    protected function currentDriver(): ?Driver
    {
        $token = $this->bearer();
        return $token ? Driver::findOne(['auth_key' => $token]) : null;
    }

    protected function requireParent(): ParentUser
    {
        $p = $this->currentParent();
        if (!$p) {
            $this->abort(401, 'Не авторизован (родитель)');
        }
        return $p;
    }

    protected function requireDriver(): Driver
    {
        $d = $this->currentDriver();
        if (!$d) {
            $this->abort(401, 'Не авторизован (водитель)');
        }
        return $d;
    }

    protected function abort(int $code, string $message)
    {
        Yii::$app->response->statusCode = $code;
        Yii::$app->response->data = ['ok' => false, 'error' => $message];
        Yii::$app->end();
    }

    protected function ok(array $data = []): array
    {
        return array_merge(['ok' => true], $data);
    }
}
