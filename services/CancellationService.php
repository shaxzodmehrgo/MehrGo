<?php
namespace app\services;

use app\models\Attendance;
use app\models\RouteTrip;
use app\models\Subscription;
use Yii;

/**
 * Отмена дня и возврат по категории авто:
 *   личный водитель — 100% возврат; совместный легковой (4/7) — 60%; совместный фургон (9/10/20) — 0%.
 */
class CancellationService
{
    private $pricing;

    public function __construct(?PricingService $pricing = null)
    {
        $this->pricing = $pricing ?: new PricingService();
    }

    public function applyDayOff(Subscription $sub, string $date): array
    {
        $ts = strtotime($date . ' 00:00:00');
        $year = (int)date('Y', $ts);
        $month = (int)date('n', $ts);

        $schoolDays = max(1, $this->pricing->schoolDaysInMonth((int)$sub->days_mask, $year, $month));
        $daily = (int)round(((int)$sub->monthly_price) / $schoolDays);

        $vehicleClass = $this->vehicleClassOf($sub);
        $charge = $this->pricing->cancellationCharge($sub->mode, $vehicleClass, $daily);

        $att = Attendance::findOne(['subscription_id' => $sub->id, 'date' => $date]) ?: new Attendance();
        $att->subscription_id = $sub->id;
        $att->date = $date;
        $att->status = Attendance::STATUS_CANCELLED;
        $att->charge_percent = $charge['charge_percent'];
        $att->charge_amount = $charge['charge_amount'];
        if ($att->isNewRecord) {
            $att->created_at = time();
        }
        $att->save(false);

        return [
            'date' => $date,
            'mode' => $sub->mode,
            'vehicle_class' => $vehicleClass,
            'daily_amount' => $daily,
            'charge_percent' => $charge['charge_percent'],
            'refund_percent' => $charge['refund_percent'],
            'charge_amount' => $charge['charge_amount'],
            'refund_amount' => $daily - $charge['charge_amount'],
        ];
    }

    private function vehicleClassOf(Subscription $sub): string
    {
        if ($sub->route_id) {
            $route = RouteTrip::findOne($sub->route_id);
            if ($route && $route->vehicle_class) {
                return $route->vehicle_class;
            }
        }
        if ($sub->mode === Subscription::MODE_PRIVATE) {
            return Yii::$app->params['pricing']['personal_class'] ?? 'car7';
        }
        $opts = $sub->vehicleOptionsArray();
        return $opts[0] ?? 'car7';
    }
}
