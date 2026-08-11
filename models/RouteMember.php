<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property int $route_id
 * @property int $subscription_id
 * @property int $child_id
 * @property int $pickup_seq
 * @property float $pickup_lat
 * @property float $pickup_lon
 * @property string $status
 */
class RouteMember extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_route_member';
    }

    public function getChild()
    {
        return $this->hasOne(Child::class, ['id' => 'child_id']);
    }

    public function getSubscription()
    {
        return $this->hasOne(Subscription::class, ['id' => 'subscription_id']);
    }
}
