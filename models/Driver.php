<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * @property int $id
 * @property string $phone
 * @property string $name
 * @property string $auth_key
 * @property string $vehicle_class
 * @property int $capacity
 * @property string $plate
 * @property bool $online
 * @property float $cur_lat
 * @property float $cur_lon
 * @property int $created_at
 */
class Driver extends ActiveRecord
{
    public static function tableName()
    {
        return 'shuttle_driver';
    }

    /** Карточка водителя для показа родителю (после миграции профиля). */
    public function card(): array
    {
        return [
            'id' => (int)$this->id,
            'name' => $this->name,
            'photo_url' => $this->photo_url,
            'profession' => $this->profession,
            'experience_years' => (int)$this->experience_years,
            'rating' => (float)($this->rating ?? 5.0),
            'rides_count' => (int)$this->rides_count,
            'id_verified' => (bool)$this->id_verified,
            'vehicle_class' => $this->vehicle_class,
            'car_model' => $this->car_model,
            'car_color' => $this->car_color,
            'plate' => $this->plate,
        ];
    }
}
