<?php
namespace app\controllers;

use Yii;
use yii\web\Controller;
use yii\web\Response;

class SiteController extends Controller
{
    public $enableCsrfValidation = false;

    public function beforeAction($action)
    {
        Yii::$app->response->format = Response::FORMAT_JSON;
        return parent::beforeAction($action);
    }

    public function actionHealth()
    {
        return [
            'ok' => true,
            'brand' => Yii::$app->params['brand'],
            'time' => date('c'),
            'db' => $this->dbStatus(),
        ];
    }

    /**
     * Публичная конфигурация фронта. Наружу отдаётся ТОЛЬКО JS-ключ карты
     * (он referer-ограничен на birga.mehrgo.uz). Suggest/Distance-ключи остаются серверно.
     */
    public function actionConfig()
    {
        $p = Yii::$app->params;
        return [
            'ok' => true,
            'brand' => $p['brand'],
            'yandex_js_key' => $p['yandex']['js_key'] ?? '',
            'route_provider' => $p['routing']['provider'] ?? 'haversine',
            'map_center' => ['lat' => 41.2995, 'lon' => 69.2401],
        ];
    }

    public function actionPreflight()
    {
        Yii::$app->response->statusCode = 204;
        return null;
    }

    public function actionError()
    {
        $exception = Yii::$app->errorHandler->exception;
        Yii::$app->response->statusCode = $exception && method_exists($exception, 'getStatusCode')
            ? $exception->getStatusCode() : 500;
        return [
            'ok' => false,
            'error' => $exception ? $exception->getMessage() : 'Unknown error',
        ];
    }

    private function dbStatus()
    {
        try {
            Yii::$app->db->createCommand('SELECT 1')->queryScalar();
            return 'connected';
        } catch (\Throwable $e) {
            return 'error: ' . $e->getMessage();
        }
    }
}
