<?php
namespace app\services;

use app\models\Driver;
use app\models\ParentUser;
use app\models\RouteMember;
use app\models\RouteTrip;
use app\models\Subscription;
use app\models\Transaction;

/**
 * Балансы клиентов/водителей + реестр транзакций (lean-порт common\models\Balance такси).
 * amount передаём положительным; знак определяется типом. balance_after — снимок.
 */
class BalanceService
{
    private static function model(string $actor, int $id)
    {
        return $actor === Transaction::ACTOR_DRIVER
            ? Driver::findOne($id)
            : ParentUser::findOne($id);
    }

    /** Применить операцию к балансу и записать транзакцию. */
    public static function apply(string $actor, int $id, string $type, int $amount, string $note = '', ?int $routeId = null, string $by = 'system'): array
    {
        $m = self::model($actor, $id);
        if (!$m) {
            return ['ok' => false, 'error' => 'actor not found'];
        }
        $amount = abs((int)$amount);
        $sign = in_array($type, [Transaction::TYPE_CHARGE, Transaction::TYPE_WITHDRAW], true) ? -1 : 1;
        $delta = $sign * $amount;

        if ($type === Transaction::TYPE_WITHDRAW && ((int)$m->balance + $delta) < 0) {
            return ['ok' => false, 'error' => 'Недостаточно средств на балансе'];
        }

        $m->balance = (int)$m->balance + $delta;
        $m->save(false);

        $txn = new Transaction();
        $txn->actor = $actor;
        $txn->actor_id = $id;
        $txn->type = $type;
        $txn->amount = $delta;
        $txn->balance_after = (int)$m->balance;
        $txn->route_id = $routeId;
        $txn->note = $note !== '' ? mb_substr($note, 0, 255) : null;
        $txn->created_by = $by;
        $txn->created_at = time();
        $txn->save(false);

        return ['ok' => true, 'balance' => (int)$m->balance, 'txn_id' => $txn->id];
    }

    public static function topup(string $actor, int $id, int $amount, string $note = '', string $by = 'admin'): array
    {
        return self::apply($actor, $id, Transaction::TYPE_TOPUP, $amount, $note ?: 'Пополнение', null, $by);
    }

    public static function withdraw(string $actor, int $id, int $amount, string $note = '', string $by = 'admin'): array
    {
        return self::apply($actor, $id, Transaction::TYPE_WITHDRAW, $amount, $note ?: 'Вывод средств', null, $by);
    }

    public static function chargeManual(string $actor, int $id, int $amount, string $note = '', string $by = 'admin'): array
    {
        return self::apply($actor, $id, Transaction::TYPE_CHARGE, $amount, $note ?: 'Ручное списание', null, $by);
    }

    /**
     * Авто-списание за завершённый рейс: с каждого клиента — его доля за поездку (fair-split),
     * водителю — начисление суммой. Возвращает сводку. Идемпотентность гарантирует вызов из complete.
     */
    public static function chargeRouteComplete(RouteTrip $route): array
    {
        $pricing = new PricingService();
        $members = RouteMember::find()->where(['route_id' => $route->id])->all();
        if (!$members) {
            return ['ok' => true, 'charged' => [], 'driver_earn' => 0];
        }
        $isLight = $pricing->categoryOf($route->vehicle_class) === 'light';
        $cLeg = $pricing->carCostLeg((float)$route->distance_km, $route->vehicle_class);

        // веса
        $sumW = 0.0; $totalChildren = 0; $rows = [];
        foreach ($members as $m) {
            $sub = Subscription::findOne($m->subscription_id);
            if (!$sub) { continue; }
            $ch = (int)$m->children_count;
            $d = (float)($sub->distance_km ?: 0);
            $w = $isLight ? ($d * $ch) : $ch; // фургон — пропорционально детям
            $rows[] = ['sub' => $sub, 'w' => $w, 'children' => $ch];
            $sumW += $w;
            $totalChildren += $ch;
        }
        if ($sumW <= 0) {
            return ['ok' => true, 'charged' => [], 'driver_earn' => 0];
        }

        $charged = []; $driverEarn = 0;
        foreach ($rows as $r) {
            $share = (int)round($cLeg * ($r['w'] / $sumW));
            if ($share <= 0) { continue; }
            $res = self::apply(
                Transaction::ACTOR_CLIENT, (int)$r['sub']->parent_id, Transaction::TYPE_CHARGE,
                $share, 'Рейс #' . $route->id, $route->id, 'system'
            );
            if ($res['ok']) {
                $driverEarn += $share;
                $charged[] = ['parent_id' => (int)$r['sub']->parent_id, 'amount' => $share, 'balance' => $res['balance']];
            }
        }

        if ($route->driver_id && $driverEarn > 0) {
            self::apply(Transaction::ACTOR_DRIVER, (int)$route->driver_id, Transaction::TYPE_EARN,
                $driverEarn, 'Рейс #' . $route->id, $route->id, 'system');
        }

        return ['ok' => true, 'charged' => $charged, 'driver_earn' => $driverEarn, 'car_cost' => (int)round($cLeg)];
    }
}
