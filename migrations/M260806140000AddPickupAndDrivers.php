<?php
namespace app\migrations;

use yii\db\Migration;

/**
 * Доработки: типы школ (кураторский список), точки посадки для крупных авто,
 * дата старта рейса и предзапись водителей на маршрут.
 */
class M260806140000AddPickupAndDrivers extends Migration
{
    public function safeUp()
    {
        // Школа: тип + признак партнёра (кураторский список Б-точек)
        $this->addColumn('shuttle_school', 'type', $this->string(16)->notNull()->defaultValue('state')); // state|private|center
        $this->addColumn('shuttle_school', 'partner', $this->boolean()->notNull()->defaultValue(true));

        // Рейс: режим посадки + дата старта
        $this->addColumn('shuttle_route', 'pickup_mode', $this->string(8)->notNull()->defaultValue('door')); // door|points
        $this->addColumn('shuttle_route', 'start_date', $this->date());

        // Участник рейса: сохраняем дом ребёнка отдельно от точки посадки + пешая дистанция
        $this->addColumn('shuttle_route_member', 'home_lat', $this->double());
        $this->addColumn('shuttle_route_member', 'home_lon', $this->double());
        $this->addColumn('shuttle_route_member', 'pickup_type', $this->string(8)->notNull()->defaultValue('door')); // door|point
        $this->addColumn('shuttle_route_member', 'walk_m', $this->integer()->notNull()->defaultValue(0));

        // Предзапись водителей на маршрут (несколько кандидатов; сокет не нужен)
        $this->createTable('shuttle_route_driver', [
            'id' => $this->primaryKey(),
            'route_id' => $this->integer()->notNull(),
            'driver_id' => $this->integer()->notNull(),
            'status' => $this->string(12)->notNull()->defaultValue('interested'), // interested|assigned|declined
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_rd_unique', 'shuttle_route_driver', ['route_id', 'driver_id'], true);
        $this->createIndex('idx_rd_driver', 'shuttle_route_driver', 'driver_id');
    }

    public function safeDown()
    {
        $this->dropTable('shuttle_route_driver');
        $this->dropColumn('shuttle_route_member', 'walk_m');
        $this->dropColumn('shuttle_route_member', 'pickup_type');
        $this->dropColumn('shuttle_route_member', 'home_lon');
        $this->dropColumn('shuttle_route_member', 'home_lat');
        $this->dropColumn('shuttle_route', 'start_date');
        $this->dropColumn('shuttle_route', 'pickup_mode');
        $this->dropColumn('shuttle_school', 'partner');
        $this->dropColumn('shuttle_school', 'type');
    }
}
