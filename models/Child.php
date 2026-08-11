<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property int $parent_id
 * @property string $name
 * @property string $grade
 * @property int $created_at
 */
class Child extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_child';
    }
}
