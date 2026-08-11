<?php
namespace app\controllers;

use Yii;

/**
 * Гео-прокси для фронта. Ключи Yandex НЕ уходят в браузер:
 *  - Геосаджест (Suggest API) вызывается серверно с params['yandex']['suggest_key'];
 *    фронт бьёт в /api/geo/suggest и получает уже нормализованные подсказки.
 * JS-ключ карты отдаётся отдельно (/api/config) — он referer-ограничен на birga.mehrgo.uz.
 */
class GeoController extends BaseApiController
{
    /** GET /api/geo/suggest?text=...&ll=<lon,lat> — подсказки адресов (прокси Yandex Geosuggest). */
    public function actionSuggest()
    {
        $text = trim((string)$this->param('text', ''));
        if ($text === '' || mb_strlen($text) < 2) {
            return $this->ok(['items' => []]);
        }
        $key = Yii::$app->params['yandex']['suggest_key'] ?? '';
        if (!$key) {
            // Ключ ещё не настроен — фронт молча остаётся на ручном вводе + маркере.
            return $this->ok(['items' => [], 'note' => 'suggest_key_not_set']);
        }

        // Центр окна поиска: пришедший ll (lon,lat) или центр Ташкента.
        $ll = (string)$this->param('ll', '69.2401,41.2995');
        if (!preg_match('/^-?\d+(\.\d+)?,-?\d+(\.\d+)?$/', $ll)) {
            $ll = '69.2401,41.2995';
        }

        $query = http_build_query([
            'apikey' => $key,
            'text' => $text,
            'lang' => 'ru_RU',
            'results' => 7,
            'll' => $ll,
            'spn' => '0.7,0.7',
            'types' => 'geo,street,house,district,locality',
            'print_address' => 1,
        ]);
        $url = 'https://suggest-maps.yandex.ru/v1/suggest?' . $query;

        try {
            $data = json_decode($this->httpGet($url), true);
        } catch (\Throwable $e) {
            Yii::warning('Geosuggest failed: ' . $e->getMessage());
            return $this->ok(['items' => [], 'note' => 'upstream_error']);
        }

        $items = [];
        foreach (($data['results'] ?? []) as $r) {
            $items[] = [
                'title' => $r['title']['text'] ?? '',
                'subtitle' => $r['subtitle']['text'] ?? '',
                'address' => $r['address']['formatted_address'] ?? null,
                'distance' => $r['distance']['value'] ?? null,
            ];
        }
        return $this->ok(['items' => $items]);
    }

    /**
     * GET /api/geo/geocode?text=... — адрес → координаты (прокси HTTP-Геокодера Yandex).
     * Серверный запасной путь: фронт сперва пробует ymaps.geocode (JS API), при неудаче — сюда.
     */
    public function actionGeocode()
    {
        $text = trim((string)$this->param('text', ''));
        if ($text === '') {
            return $this->ok(['found' => false]);
        }
        $key = Yii::$app->params['yandex']['geocoder_key'] ?? '';
        if (!$key) {
            return $this->ok(['found' => false, 'note' => 'geocoder_key_not_set']);
        }
        $query = http_build_query([
            'apikey' => $key,
            'format' => 'json',
            'geocode' => $text,
            'lang' => 'ru_RU',
            'results' => 1,
            'bbox' => '68.90,41.05~69.65,41.60', // окно Ташкента
            'rspn' => 1,
        ]);
        $url = 'https://geocode-maps.yandex.ru/1.x/?' . $query;
        try {
            $data = json_decode($this->httpGet($url), true);
        } catch (\Throwable $e) {
            Yii::warning('Geocode failed: ' . $e->getMessage());
            return $this->ok(['found' => false, 'note' => 'upstream_error']);
        }
        $obj = $data['response']['GeoObjectCollection']['featureMember'][0]['GeoObject'] ?? null;
        if (!$obj) {
            return $this->ok(['found' => false]);
        }
        $pos = explode(' ', trim($obj['Point']['pos'] ?? '')); // "lon lat"
        if (count($pos) < 2) {
            return $this->ok(['found' => false]);
        }
        return $this->ok([
            'found' => true,
            'lat' => (float)$pos[1],
            'lon' => (float)$pos[0],
            'address' => $obj['metaDataProperty']['GeocoderMetaData']['text'] ?? $text,
        ]);
    }

    private function httpGet(string $url): string
    {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 6,
            CURLOPT_HTTPHEADER => ['Accept: application/json'],
        ]);
        $res = curl_exec($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        curl_close($ch);
        if ($res === false || $code >= 400) {
            throw new \RuntimeException("HTTP $code $err");
        }
        return $res;
    }
}
