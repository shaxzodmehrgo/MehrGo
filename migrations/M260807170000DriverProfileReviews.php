<?php
namespace app\migrations;

use yii\db\Migration;

/** Профиль водителя (для показа родителю) + отзывы. Схема shuttle, только ADD/CREATE. */
class M260807170000DriverProfileReviews extends Migration
{
    public function safeUp()
    {
        $this->addColumn('shuttle_driver', 'photo_url', $this->string());
        $this->addColumn('shuttle_driver', 'profession', $this->string(120));
        $this->addColumn('shuttle_driver', 'experience_years', $this->integer()->notNull()->defaultValue(0));
        $this->addColumn('shuttle_driver', 'rating', $this->decimal(3, 2)->notNull()->defaultValue(5.00));
        $this->addColumn('shuttle_driver', 'rides_count', $this->integer()->notNull()->defaultValue(0));
        $this->addColumn('shuttle_driver', 'id_verified', $this->boolean()->notNull()->defaultValue(false));

        $this->createTable('shuttle_review', [
            'id' => $this->primaryKey(),
            'route_id' => $this->integer(),
            'driver_id' => $this->integer()->notNull(),
            'parent_id' => $this->integer(),
            'stars' => $this->integer()->notNull()->defaultValue(5),
            'text' => $this->text(),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_review_driver', 'shuttle_review', 'driver_id');
    }

    public function safeDown()
    {
        $this->dropTable('shuttle_review');
        foreach (['photo_url', 'profession', 'experience_years', 'rating', 'rides_count', 'id_verified'] as $c) {
            $this->dropColumn('shuttle_driver', $c);
        }
    }
}
