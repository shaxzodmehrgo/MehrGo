<?php
namespace app\controllers;

use app\models\School;

class SchoolController extends BaseApiController
{
    public function actionIndex()
    {
        $schools = School::find()->orderBy('name')->all();
        $labels = ['state' => 'Гос. школа', 'private' => 'Частная школа', 'center' => 'Учебный центр'];
        $bot = 'mehrgoshuttlebot';
        return $this->ok([
            'schools' => array_map(fn(School $s) => [
                'id' => $s->id, 'name' => $s->name,
                'lat' => (float)$s->lat, 'lon' => (float)$s->lon, 'address' => $s->address,
                'type' => $s->type, 'type_label' => $labels[$s->type] ?? $s->type,
                'partner' => (bool)$s->partner,
                'start_time' => $s->start_time, 'end_time' => $s->end_time,
                'tg_slug' => $s->tg_slug,
                'tg_link' => $s->tg_slug ? "https://t.me/$bot?start={$s->tg_slug}" : null,
            ], $schools),
        ]);
    }
}
