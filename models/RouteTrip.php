<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * Совместный рейс (маршрут). Таблица shuttle_route.
 *
 * @property int $id
 * @property int $school_id
 * @property string $direction
 * @property string $vehicle_class
 * @property int $capacity
 * @property int $min_children
 * @property int $current_children
 * @property int $driver_id
 * @property string $status
 * @property string $polyline
 * @property float $distance_km
 * @property float $centroid_lat
 * @property float $centroid_lon
 * @property int $created_at
 */
class RouteTrip extends ActiveRecord
{
    const STATUS_FORMING = 'forming';
    const STATUS_ACTIVE = 'active';
    const STATUS_RUNNING = 'running';
    const STATUS_DONE = 'done';
    const STATUS_CANCELLED = 'cancelled';

    public static function tableName()
    {
        return 'shuttle_route';
    }

    public function getMembers()
    {
        return $this->hasMany(RouteMember::class, ['route_id' => 'id']);
    }

    public function getSchool()
    {
        return $this->hasOne(School::class, ['id' => 'school_id']);
    }

    public function getDriver()
    {
        return $this->hasOne(Driver::class, ['id' => 'driver_id']);
    }
}
