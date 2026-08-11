<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property string $name
 * @property float $lat
 * @property float $lon
 * @property string $address
 */
class School extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_school';
    }
}
