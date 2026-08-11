<?php
namespace app\migrations;

use yii\db\Migration;

/** События рейса для уведомлений родителю (показ в web). Схема shuttle. */
class M260807180000Events extends Migration
{
    public function safeUp()
    {
        $this->createTable('shuttle_event', [
            'id' => $this->primaryKey(),
            'subscription_id' => $this->integer(),
            'route_id' => $this->integer(),
            'type' => $this->string(20)->notNull(),
            'text' => $this->string(255),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_event_sub', 'shuttle_event', 'subscription_id');
    }

    public function safeDown()
    {
        $this->dropTable('shuttle_event');
    }
}
