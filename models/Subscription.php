<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property int $parent_id
 * @property int $child_id
 * @property float $home_lat
 * @property float $home_lon
 * @property string $home_address
 * @property int $school_id
 * @property int $days_mask
 * @property string $vehicle_options
 * @property string $mode
 * @property float $distance_km
 * @property int $monthly_price
 * @property string $status
 * @property int $route_id
 * @property int $created_at
 */
class Subscription extends ActiveRecord
{
    const STATUS_FORMING = 'forming';
    const STATUS_ACTIVE = 'active';
    const STATUS_PAUSED = 'paused';
    const STATUS_CANCELLED = 'cancelled';

    const MODE_PRIVATE = 'private';
    const MODE_SHARED = 'shared';

    public static function tableName()
    {
        return 'shuttle_subscription';
    }

    public function getChild()
    {
        return $this->hasOne(Child::class, ['id' => 'child_id']);
    }

    public function getSchool()
    {
        return $this->hasOne(School::class, ['id' => 'school_id']);
    }

    public function getRoute()
    {
        return $this->hasOne(RouteTrip::class, ['id' => 'route_id']);
    }

    /** @return string[] список приемлемых классов авто */
    public function vehicleOptionsArray()
    {
        return array_values(array_filter(array_map('trim', explode(',', (string)$this->vehicle_options))));
    }
}
