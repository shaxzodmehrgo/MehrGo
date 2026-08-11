<?php
namespace app\migrations;

use yii\db\Migration;

/**
 * MaktabGo initial schema. Все таблицы создаются в изолированной схеме `shuttle`
 * (search_path выставлен в config/db.php). Таблицы такси НЕ затрагиваются.
 */
class M260806120000Init extends Migration
{
    public function safeUp()
    {
        // Родитель
        $this->createTable('shuttle_parent', [
            'id' => $this->primaryKey(),
            'phone' => $this->string(20)->notNull()->unique(),
            'name' => $this->string(120),
            'auth_key' => $this->string(64),
            'created_at' => $this->integer()->notNull(),
        ]);

        // Ребёнок
        $this->createTable('shuttle_child', [
            'id' => $this->primaryKey(),
            'parent_id' => $this->integer()->notNull(),
            'name' => $this->string(120)->notNull(),
            'grade' => $this->string(20),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_child_parent', 'shuttle_child', 'parent_id');

        // Школа
        $this->createTable('shuttle_school', [
            'id' => $this->primaryKey(),
            'name' => $this->string(200)->notNull(),
            'lat' => $this->double()->notNull(),
            'lon' => $this->double()->notNull(),
            'address' => $this->string(300),
        ]);

        // Водитель + его автомобиль
        $this->createTable('shuttle_driver', [
            'id' => $this->primaryKey(),
            'phone' => $this->string(20)->notNull()->unique(),
            'name' => $this->string(120),
            'auth_key' => $this->string(64),
            'vehicle_class' => $this->string(16)->notNull()->defaultValue('van10'), // light|van7|van9|van10|van20
            'capacity' => $this->integer()->notNull()->defaultValue(10),
            'plate' => $this->string(20),
            'online' => $this->boolean()->notNull()->defaultValue(false),
            'cur_lat' => $this->double(),
            'cur_lon' => $this->double(),
            'created_at' => $this->integer()->notNull(),
        ]);

        // Подписка (заявка родителя на маршрут)
        $this->createTable('shuttle_subscription', [
            'id' => $this->primaryKey(),
            'parent_id' => $this->integer()->notNull(),
            'child_id' => $this->integer()->notNull(),
            'home_lat' => $this->double()->notNull(),
            'home_lon' => $this->double()->notNull(),
            'home_address' => $this->string(300),
            'school_id' => $this->integer()->notNull(),
            'days_mask' => $this->integer()->notNull()->defaultValue(31), // Пн..Пт = 1+2+4+8+16
            'vehicle_options' => $this->string(80)->notNull()->defaultValue('van10'), // csv
            'mode' => $this->string(10)->notNull()->defaultValue('shared'), // private|shared
            'distance_km' => $this->decimal(8, 2),
            'monthly_price' => $this->integer(),
            'status' => $this->string(12)->notNull()->defaultValue('forming'), // forming|active|paused|cancelled
            'route_id' => $this->integer(),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_sub_parent', 'shuttle_subscription', 'parent_id');
        $this->createIndex('idx_sub_school', 'shuttle_subscription', 'school_id');
        $this->createIndex('idx_sub_status', 'shuttle_subscription', 'status');

        // Маршрут (совместный рейс)
        $this->createTable('shuttle_route', [
            'id' => $this->primaryKey(),
            'school_id' => $this->integer()->notNull(),
            'direction' => $this->string(4)->notNull()->defaultValue('am'), // am|pm
            'vehicle_class' => $this->string(16)->notNull(),
            'capacity' => $this->integer()->notNull(),
            'min_children' => $this->integer()->notNull()->defaultValue(3),
            'current_children' => $this->integer()->notNull()->defaultValue(0),
            'driver_id' => $this->integer(),
            'status' => $this->string(12)->notNull()->defaultValue('forming'), // forming|active|running|done|cancelled
            'polyline' => $this->text(),
            'distance_km' => $this->decimal(8, 2),
            'centroid_lat' => $this->double(),
            'centroid_lon' => $this->double(),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_route_school', 'shuttle_route', 'school_id');
        $this->createIndex('idx_route_status', 'shuttle_route', 'status');

        // Участник маршрута (ребёнок в рейсе)
        $this->createTable('shuttle_route_member', [
            'id' => $this->primaryKey(),
            'route_id' => $this->integer()->notNull(),
            'subscription_id' => $this->integer()->notNull(),
            'child_id' => $this->integer()->notNull(),
            'pickup_seq' => $this->integer()->notNull()->defaultValue(0),
            'pickup_lat' => $this->double()->notNull(),
            'pickup_lon' => $this->double()->notNull(),
            'status' => $this->string(12)->notNull()->defaultValue('assigned'), // assigned|picked|dropped
        ]);
        $this->createIndex('idx_member_route', 'shuttle_route_member', 'route_id');
        $this->createIndex('idx_member_sub', 'shuttle_route_member', 'subscription_id');

        // Посещаемость / списания по дням
        $this->createTable('shuttle_attendance', [
            'id' => $this->primaryKey(),
            'subscription_id' => $this->integer()->notNull(),
            'date' => $this->date()->notNull(),
            'status' => $this->string(12)->notNull()->defaultValue('planned'), // planned|cancelled|completed
            'charge_percent' => $this->integer()->notNull()->defaultValue(100),
            'charge_amount' => $this->integer()->notNull()->defaultValue(0),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_att_sub_date', 'shuttle_attendance', ['subscription_id', 'date'], true);

        // Трек машины для живого отображения
        $this->createTable('shuttle_track', [
            'id' => $this->primaryKey(),
            'route_id' => $this->integer()->notNull(),
            'lat' => $this->double()->notNull(),
            'lon' => $this->double()->notNull(),
            'recorded_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_track_route', 'shuttle_track', 'route_id');
    }

    public function safeDown()
    {
        foreach ([
            'shuttle_track', 'shuttle_attendance', 'shuttle_route_member',
            'shuttle_route', 'shuttle_subscription', 'shuttle_driver',
            'shuttle_school', 'shuttle_child', 'shuttle_parent',
        ] as $t) {
            $this->dropTable($t);
        }
    }
}
