<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * Предзапись водителя на маршрут (несколько кандидатов на рейс).
 *
 * @property int $id
 * @property int $route_id
 * @property int $driver_id
 * @property string $status
 * @property int $created_at
 */
class RouteDriver extends ActiveRecord
{
    const STATUS_INTERESTED = 'interested';
    const STATUS_ASSIGNED = 'assigned';
    const STATUS_DECLINED = 'declined';

    public static function tableName()
    {
        return 'shuttle_route_driver';
    }

    public function getDriver()
    {
        return $this->hasOne(Driver::class, ['id' => 'driver_id']);
    }
}
