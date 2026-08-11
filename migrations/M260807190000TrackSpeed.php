<?php
namespace app\migrations;

use yii\db\Migration;

/** Телематика: скорость/точность/курс в точках трека (мониторинг рейса). Схема shuttle. */
class M260807190000TrackSpeed extends Migration
{
    public function safeUp()
    {
        $this->addColumn('shuttle_track', 'speed', $this->decimal(6, 2));
        $this->addColumn('shuttle_track', 'accuracy', $this->decimal(6, 2));
        $this->addColumn('shuttle_track', 'bearing', $this->decimal(6, 2));
    }

    public function safeDown()
    {
        foreach (['bearing', 'accuracy', 'speed'] as $c) {
            $this->dropColumn('shuttle_track', $c);
        }
    }
}
