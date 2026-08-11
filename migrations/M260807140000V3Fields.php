<?php
namespace app\migrations;

use yii\db\Migration;

/**
 * v3: направление поездки, класс ребёнка, доп-опции и нужды, время учёбы школы, tg-slug.
 */
class M260807140000V3Fields extends Migration
{
    public function safeUp()
    {
        // Подписка
        $this->addColumn('shuttle_subscription', 'direction', $this->string(4)->notNull()->defaultValue('both')); // both|am|pm
        $this->addColumn('shuttle_subscription', 'grade_band', $this->string(12));   // 1-4|5-7|8-11
        $this->addColumn('shuttle_subscription', 'opt_teacher', $this->boolean()->notNull()->defaultValue(false));
        $this->addColumn('shuttle_subscription', 'opt_child_seat', $this->boolean()->notNull()->defaultValue(false));
        $this->addColumn('shuttle_subscription', 'opt_elementary', $this->boolean()->notNull()->defaultValue(false));
        $this->addColumn('shuttle_subscription', 'needs', $this->text());

        // Школа
        $this->addColumn('shuttle_school', 'start_time', $this->string(5));  // '08:00'
        $this->addColumn('shuttle_school', 'end_time', $this->string(5));    // '13:00'
        $this->addColumn('shuttle_school', 'tg_slug', $this->string(64));
    }

    public function safeDown()
    {
        foreach (['direction', 'grade_band', 'opt_teacher', 'opt_child_seat', 'opt_elementary', 'needs'] as $c) {
            $this->dropColumn('shuttle_subscription', $c);
        }
        foreach (['start_time', 'end_time', 'tg_slug'] as $c) {
            $this->dropColumn('shuttle_school', $c);
        }
    }
}
