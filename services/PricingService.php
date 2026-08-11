<?php
namespace app\services;

use Yii;

/**
 * Движок ценообразования Birga — помесячная подписка.
 *
 *   trip_cost = base_fare + distance_km * rate_km * vehicle_factor   (весь автомобиль, 1 поездка)
 *   личный:   monthly = trip_cost * personal_premium * trips * учебные_дни   (на семью, вся машина)
 *   совместный: per_child = (trip_cost / occupancy) * (1 - pool_discount);
 *               monthly_на_ребёнка = per_child * trips * учебные_дни
 *
 * Отмена дня (возврат клиенту = 100 - charge):
 *   личный 100% возврат · совместный легковой 60% · совместный фургон 0%.
 */
class PricingService
{
    private $p;
    private $c;

    public function __construct($pricing = null, $cancellation = null)
    {
        $this->p = $pricing ?: Yii::$app->params['pricing'];
        $this->c = $cancellation ?: Yii::$app->params['cancellation'];
    }

    // ---- каталог авто ----

    public function vehicles(): array
    {
        return $this->p['vehicles'];
    }

    public function capacityOf(string $class): int
    {
        return (int)($this->p['vehicles'][$class]['seats'] ?? 4);
    }

    /** Полезных мест для детей: одно (переднее) не для ребёнка. */
    public function childCapacityOf(string $class): int
    {
        return max(1, $this->capacityOf($class) - 1);
    }

    /**
     * Коэффициент направления: туда-обратно = 1.0; одна сторона —
     * личный/4-мест = 0.5 (реально в 2 раза меньше поездок),
     * 7-мест/фургон = 0.8 (машина всё равно едет в обе стороны).
     */
    public function directionFactor(string $mode, string $vehicleClass, string $direction): float
    {
        if ($direction === 'both') {
            return 1.0;
        }
        return ($mode === 'private' || $vehicleClass === 'car4') ? 0.5 : 0.8;
    }

    public function factorOf(string $class): float
    {
        return (float)($this->p['vehicles'][$class]['factor'] ?? 1.0);
    }

    public function categoryOf(string $class): string
    {
        return (string)($this->p['vehicles'][$class]['category'] ?? 'van');
    }

    public function maxCapacity(): int
    {
        $max = 0;
        foreach ($this->p['vehicles'] as $v) {
            $max = max($max, (int)$v['seats']);
        }
        return $max ?: 20;
    }

    // ---- стоимость ----

    /** Стоимость одной поездки (весь автомобиль, фургонный тариф с factor), сум. */
    public function tripCost(float $distanceKm, string $vehicleClass): float
    {
        return $this->p['base_fare'] + $distanceKm * $this->p['rate_km'] * $this->factorOf($vehicleClass);
    }

    /**
     * Стоимость машины за ОДНУ поездку по расстоянию, сум.
     *  - легковой (car4/car7) и личный: private_base + km * private_rate_km  (без factor);
     *  - фургон: базовый tripCost (base_fare + km * rate_km * factor).
     * Для совместного легкового это стоимость машины по ВСЕМУ маршруту (делится между семьями).
     */
    public function carCostLeg(float $distanceKm, string $vehicleClass): float
    {
        if ($this->categoryOf($vehicleClass) === 'light') {
            return $this->p['private_base'] + $distanceKm * $this->p['private_rate_km'];
        }
        return $this->tripCost($distanceKm, $vehicleClass);
    }

    /**
     * Доля семьи в совместном ЛЕГКОВОМ рейсе (fair-split по расстоянию×детей).
     * share = cLeg * w / sumW; месяц = round(share * trips * dirFactor * учебные_дни).
     * $cLeg — стоимость машины за поездку по всему маршруту; $w — вес семьи (км*детей);
     * $sumW — сумма весов всех семей рейса. Сумма долей всех семей = стоимость машины.
     */
    public function fairShareMonthly(
        float $cLeg, float $w, float $sumW, string $vehicleClass,
        string $direction, int $daysMask, ?int $year = null, ?int $month = null
    ): int {
        $share = $sumW > 0 ? $cLeg * ($w / $sumW) : $cLeg;
        $daily = $share * (int)$this->p['trips_per_day']
            * $this->directionFactor('shared', $vehicleClass, $direction);
        return $this->round($daily * $this->schoolDaysInMonth($daysMask, $year, $month));
    }

    public function poolDiscount(int $occupancy, int $capacity): float
    {
        if ($capacity <= 1 || $occupancy <= 1) {
            return 0.0;
        }
        $fill = min(1.0, $occupancy / $capacity);
        return round(($this->p['pool_discount_max'] ?? 0.25) * $fill, 4);
    }

    public function schoolDaysInMonth(int $daysMask, ?int $year = null, ?int $month = null): int
    {
        $year = $year ?: (int)date('Y');
        $month = $month ?: (int)date('n');
        $daysInMonth = (int)date('t', mktime(0, 0, 0, $month, 1, $year));
        $count = 0;
        for ($d = 1; $d <= $daysInMonth; $d++) {
            $n = (int)date('N', mktime(0, 0, 0, $month, $d, $year));
            if ($daysMask & (1 << ($n - 1))) {
                $count++;
            }
        }
        return $count;
    }

    public function daysPerWeek(int $daysMask): int
    {
        $c = 0;
        for ($i = 0; $i < 7; $i++) {
            if ($daysMask & (1 << $i)) {
                $c++;
            }
        }
        return $c;
    }

    /**
     * Расчёт подписки (на единицу: на семью для личного, на ребёнка для совместного).
     */
    public function calcMonthly(
        float $distanceKm,
        int $daysMask,
        string $vehicleClass,
        string $mode,
        ?int $occupancy = null,
        ?int $year = null,
        ?int $month = null,
        string $direction = 'both'
    ): array {
        $trips = (int)$this->p['trips_per_day'];
        $schoolDays = $this->schoolDaysInMonth($daysMask, $year, $month);
        $capacity = $this->capacityOf($vehicleClass);
        $category = $this->categoryOf($vehicleClass);
        $tripCost = $this->carCostLeg($distanceKm, $vehicleClass); // машина/поездка (легковой тариф без factor)

        if ($mode === 'private') {
            $effOcc = 1;
            $discount = 0.0;
            $perUnitTrip = $tripCost; // на семью, вся машина
        } elseif ($category === 'light') {
            // Совместный легковой: делим стоимость машины на число детей — это и есть выгода пула.
            // (оценка для калькулятора; фактическая доля семьи считается по маршруту, fairShareMonthly)
            $effOcc = ($occupancy && $occupancy > 0)
                ? $occupancy
                : (int)(Yii::$app->params['matching']['min_children_default'] ?? 3);
            $discount = 0.0;
            $perUnitTrip = $tripCost / $effOcc; // на ребёнка
        } else {
            // Совместный фургон: прежняя модель (factor + pool_discount).
            $effOcc = ($occupancy && $occupancy > 0)
                ? $occupancy
                : (int)(Yii::$app->params['matching']['min_children_default'] ?? 3);
            $tripCost = $this->tripCost($distanceKm, $vehicleClass);
            $discount = $this->poolDiscount($effOcc, $capacity);
            $perUnitTrip = ($tripCost / $effOcc) * (1 - $discount); // на ребёнка
        }

        $dirFactor = $this->directionFactor($mode, $vehicleClass, $direction);
        $daily = $perUnitTrip * $trips * $dirFactor;
        $monthlyRaw = $daily * $schoolDays;

        return [
            'mode' => $mode,
            'vehicle_class' => $vehicleClass,
            'category' => $this->categoryOf($vehicleClass),
            'direction' => $direction,
            'direction_factor' => $dirFactor,
            'seats' => $capacity,
            'child_capacity' => $this->childCapacityOf($vehicleClass),
            'distance_km' => round($distanceKm, 2),
            'trip_cost' => (int)round($tripCost),
            'trips_per_day' => $trips,
            'effective_occupancy' => $effOcc,
            'capacity' => $capacity,
            'pool_discount' => $discount,
            'per_child_trip' => (int)round($perUnitTrip),
            'daily' => (int)round($daily),
            'school_days' => $schoolDays,
            'days_per_week' => $this->daysPerWeek($daysMask),
            'monthly' => $this->round($monthlyRaw),
            'currency' => Yii::$app->params['currency'] ?? 'UZS',
        ];
    }

    public function round($amount): int
    {
        $to = (int)($this->p['round_to'] ?? 1000);
        if ($to <= 0) {
            return (int)round($amount);
        }
        return (int)(round($amount / $to) * $to);
    }

    /**
     * Удержание при отмене дня по категории авто.
     * @param string $mode private|shared
     * @param string $vehicleClass класс авто (для категории)
     * @param int $dailyAmount дневная стоимость (на единицу подписки)
     */
    public function cancellationCharge(string $mode, string $vehicleClass, int $dailyAmount): array
    {
        $charge = $this->c['charge'];
        if ($mode === 'private') {
            $pct = (int)$charge['private'];
        } elseif ($this->categoryOf($vehicleClass) === 'light') {
            $pct = (int)$charge['shared_light'];
        } else {
            $pct = (int)$charge['shared_van'];
        }
        return [
            'charge_percent' => $pct,
            'refund_percent' => 100 - $pct,
            'charge_amount' => $this->round($dailyAmount * $pct / 100),
        ];
    }
}
