<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property int $subscription_id
 * @property string $date
 * @property string $status
 * @property int $charge_percent
 * @property int $charge_amount
 * @property int $created_at
 */
class Attendance extends ActiveRecord
{
    const STATUS_PLANNED = 'planned';
    const STATUS_CANCELLED = 'cancelled';
    const STATUS_COMPLETED = 'completed';

    public static function tableName()
    {
        return 'shuttle_attendance';
    }
}
