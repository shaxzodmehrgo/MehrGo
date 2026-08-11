<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property int $route_id
 * @property float $lat
 * @property float $lon
 * @property int $recorded_at
 */
class Track extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_track';
    }
}
