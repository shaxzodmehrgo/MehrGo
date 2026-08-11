<?php
namespace app\migrations;

use yii\db\Migration;

/**
 * Фаза 1-3: OTP-коды, балансы (клиент/водитель), реестр транзакций, поля регистрации водителя.
 * Всё в схеме shuttle (такси не трогаем).
 */
class M260807160000BillingOtp extends Migration
{
    public function safeUp()
    {
        // --- Балансы ---
        $this->addColumn('shuttle_parent', 'balance', $this->bigInteger()->notNull()->defaultValue(0));
        $this->addColumn('shuttle_parent', 'tg_chat_id', $this->bigInteger());
        $this->addColumn('shuttle_driver', 'balance', $this->bigInteger()->notNull()->defaultValue(0));
        $this->addColumn('shuttle_driver', 'tg_chat_id', $this->bigInteger());
        // --- Регистрация/верификация водителя ---
        $this->addColumn('shuttle_driver', 'verified', $this->boolean()->notNull()->defaultValue(false));
        $this->addColumn('shuttle_driver', 'status', $this->string(12)->notNull()->defaultValue('pending')); // pending|active|blocked
        $this->addColumn('shuttle_driver', 'car_model', $this->string(64));
        $this->addColumn('shuttle_driver', 'car_color', $this->string(32));
        // существующие (демо) водители — уже проверены, чтобы вход не сломался; новые = pending
        $this->update('shuttle_driver', ['verified' => true, 'status' => 'active']);

        // --- OTP-коды ---
        $this->createTable('shuttle_otp', [
            'id' => $this->primaryKey(),
            'phone' => $this->string(20)->notNull(),
            'code' => $this->string(8)->notNull(),
            'channel' => $this->string(10), // sms|telegram|dev
            'role' => $this->string(8)->notNull()->defaultValue('client'), // client|driver
            'expires_at' => $this->integer()->notNull(),
            'used' => $this->boolean()->notNull()->defaultValue(false),
            'attempts' => $this->integer()->notNull()->defaultValue(0),
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_otp_phone', 'shuttle_otp', ['phone', 'used']);

        // --- Реестр транзакций (баланс-леджер) ---
        $this->createTable('shuttle_transaction', [
            'id' => $this->primaryKey(),
            'actor' => $this->string(8)->notNull(),      // client|driver
            'actor_id' => $this->integer()->notNull(),
            'type' => $this->string(12)->notNull(),      // topup|charge|withdraw|earn|refund
            'amount' => $this->bigInteger()->notNull(),  // + приход, − расход
            'balance_after' => $this->bigInteger(),
            'route_id' => $this->integer(),
            'note' => $this->string(255),
            'created_by' => $this->string(40),           // admin|system|<token>
            'created_at' => $this->integer()->notNull(),
        ]);
        $this->createIndex('idx_txn_actor', 'shuttle_transaction', ['actor', 'actor_id']);
    }

    public function safeDown()
    {
        $this->dropTable('shuttle_transaction');
        $this->dropTable('shuttle_otp');
        foreach (['car_color', 'car_model', 'status', 'verified', 'tg_chat_id', 'balance'] as $c) {
            $this->dropColumn('shuttle_driver', $c);
        }
        $this->dropColumn('shuttle_parent', 'tg_chat_id');
        $this->dropColumn('shuttle_parent', 'balance');
    }
}
