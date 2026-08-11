<?php
namespace app\models;

use yii\db\ActiveRecord;

/**
 * Баланс-леджер (по образцу common\models\Balance такси, но lean).
 * amount: + приход, − расход. balance_after — снимок баланса после операции.
 *
 * @property int $id
 * @property string $actor      client|driver
 * @property int $actor_id
 * @property string $type       topup|charge|withdraw|earn|refund
 * @property int $amount
 * @property int $balance_after
 * @property int $route_id
 * @property string $note
 * @property string $created_by
 * @property int $created_at
 */
class Transaction extends ActiveRecord
{
    const ACTOR_CLIENT = 'client';
    const ACTOR_DRIVER = 'driver';

    const TYPE_TOPUP = 'topup';       // пополнение баланса
    const TYPE_CHARGE = 'charge';     // списание с клиента за рейс
    const TYPE_WITHDRAW = 'withdraw'; // вывод средств водителю
    const TYPE_EARN = 'earn';         // начисление водителю за рейс
    const TYPE_REFUND = 'refund';     // возврат

    public static function tableName()
    {
        return 'shuttle_transaction';
    }
}
