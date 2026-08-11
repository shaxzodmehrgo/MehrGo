<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property string $phone
 * @property string $name
 * @property string $auth_key
 * @property int $created_at
 */
class ParentUser extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_parent';
    }

    public function getChildren()
    {
        return $this->hasMany(Child::class, ['parent_id' => 'id']);
    }
}
