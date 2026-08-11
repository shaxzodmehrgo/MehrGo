<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property string $phone
 * @property string $code
 * @property string $channel
 * @property string $role
 * @property int $expires_at
 * @property bool $used
 * @property int $attempts
 * @property int $created_at
 */
class Otp extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_otp';
    }
}
