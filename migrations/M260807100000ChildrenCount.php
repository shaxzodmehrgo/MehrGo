<?php
namespace app\migrations;

use yii\db\Migration;

/**
 * Дети указываются количеством (без ФИО). Добавляем children_count;
 * child_id делаем необязательным (обратная совместимость).
 */
class M260807100000ChildrenCount extends Migration
{
    public function safeUp()
    {
        $this->addColumn('shuttle_subscription', 'children_count', $this->integer()->notNull()->defaultValue(1));
        $this->addColumn('shuttle_route_member', 'children_count', $this->integer()->notNull()->defaultValue(1));
        // child_id больше не обязателен
        $this->execute('ALTER TABLE shuttle_subscription ALTER COLUMN child_id DROP NOT NULL');
        $this->execute('ALTER TABLE shuttle_route_member ALTER COLUMN child_id DROP NOT NULL');
    }

    public function safeDown()
    {
        $this->dropColumn('shuttle_route_member', 'children_count');
        $this->dropColumn('shuttle_subscription', 'children_count');
    }
}
