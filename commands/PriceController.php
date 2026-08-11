<?php
namespace app\commands;

use app\services\PricingService;
use yii\console\Controller;

class PriceController extends Controller
{
    public function actionTest()
    {
        $svc = new PricingService();
        $distance = 5.0;
        $daysMask = 31;
        $year = 2026; $month = 9;
        $sd = $svc->schoolDaysInMonth($daysMask, $year, $month);

        echo "=== Дистанция {$distance} км, Пн–Пт, сентябрь 2026 (учебных дней: {$sd}) ===\n";
        echo "Легковой тариф: 20 000 + 4 000/км за поездку (в одну сторону). Round-trip = ×2.\n\n";

        foreach ([
            ['private', 'car7', null],
            ['shared', 'car7', 3],
            ['shared', 'car7', 6],
            ['shared', 'van10', 3],
            ['shared', 'van10', 10],
        ] as [$mode, $veh, $occ]) {
            $r = $svc->calcMonthly($distance, $daysMask, $veh, $mode, $occ, $year, $month);
            printf(
                "%-8s %-6s occ=%-4s машина/поездка=%8s  /ед/поездка=%7s  MONTHLY=%10s  (disc %.0f%%)\n",
                $r['mode'], $r['vehicle_class'], (string)$r['effective_occupancy'],
                number_format($r['trip_cost'], 0, '.', ' '),
                number_format($r['per_child_trip'], 0, '.', ' '),
                number_format($r['monthly'], 0, '.', ' '),
                $r['pool_discount'] * 100
            );
        }

        echo "\n=== Fair-split (совместный легковой car7): маршрут 12 км, 3 семьи ===\n";
        $cLeg = $svc->carCostLeg(12.0, 'car7');
        echo "Стоимость машины/поездку по маршруту 12 км: " . number_format($cLeg, 0, '.', ' ') . " сум\n";
        $fams = [['1-я (забрали первой)', 10.0, 1], ['2-я', 6.0, 1], ['3-я (забрали последней)', 2.0, 1]];
        $sumW = 0.0;
        foreach ($fams as [$n, $d, $ch]) { $sumW += $d * $ch; }
        $sumMonthly = 0;
        foreach ($fams as [$n, $d, $ch]) {
            $w = $d * $ch;
            $m = $svc->fairShareMonthly($cLeg, $w, $sumW, 'car7', 'both', $daysMask, $year, $month);
            $sumMonthly += $m;
            printf("  %-26s %4.0f км → доля/поездку %8s  MONTHLY=%10s\n",
                $n, $d, number_format($cLeg * $w / $sumW, 0, '.', ' '), number_format($m, 0, '.', ' '));
        }
        printf("  ИТОГО по рейсу за месяц: %s сум (оператор получает стоимость машины)\n\n",
            number_format($sumMonthly, 0, '.', ' '));

        echo "\n=== Отмена дня (дневная стоимость 12 000) ===\n";
        foreach ([['private', 'car7'], ['shared', 'car7'], ['shared', 'van10'], ['shared', 'van20']] as [$mode, $veh]) {
            $c = $svc->cancellationCharge($mode, $veh, 12000);
            printf("%-8s %-6s → удержано %d%%, возврат %d%% = %s сум возврат\n",
                $mode, $veh, $c['charge_percent'], $c['refund_percent'],
                number_format(12000 - $c['charge_amount'], 0, '.', ' '));
        }
        return 0;
    }
}
