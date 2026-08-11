<?php
namespace app\services;

use app\models\RouteMember;
use app\models\RouteTrip;
use app\models\School;
use app\models\Subscription;
use Yii;

/**
 * Пулинг маршрутов Birga.
 *   1) заявки status=forming, mode=shared, по школам;
 *   2) гео-кластеризация домов; места считаются по СУММЕ детей (children_count);
 *   3) категория посадки от класса авто: легковой — от двери; фургон — точки сбора;
 *   4) если детей больше вместимости — кластер делится на несколько рейсов (излишек → новый рейс);
 *   5) рейс active при сумме детей >= min_children.
 */
class MatchingService
{
    private $cfg;
    private $pickup;
    private $pricing;
    private $routing;

    public function __construct()
    {
        $this->cfg = Yii::$app->params['matching'];
        $this->pickup = Yii::$app->params['pickup'];
        $this->pricing = new PricingService();
        $this->routing = new RoutingService();
    }

    public function runAll(): array
    {
        $schoolIds = Subscription::find()
            ->select('school_id')->distinct()
            ->where(['status' => Subscription::STATUS_FORMING, 'mode' => Subscription::MODE_SHARED])
            ->column();
        $result = [];
        foreach ($schoolIds as $sid) {
            $result[] = $this->runForSchool((int)$sid);
        }
        return ['schools' => $result];
    }

    public function runForSchool(int $schoolId): array
    {
        $school = School::findOne($schoolId);
        if (!$school) {
            return ['school_id' => $schoolId, 'error' => 'school not found'];
        }

        foreach (RouteTrip::find()->where(['school_id' => $schoolId, 'status' => RouteTrip::STATUS_FORMING])->all() as $r) {
            RouteMember::deleteAll(['route_id' => $r->id]);
            Subscription::updateAll(['route_id' => null], ['route_id' => $r->id, 'status' => Subscription::STATUS_FORMING]);
            $r->delete();
        }

        $subs = Subscription::find()->where([
            'school_id' => $schoolId,
            'status' => Subscription::STATUS_FORMING,
            'mode' => Subscription::MODE_SHARED,
        ])->all();

        if (!$subs) {
            return ['school_id' => $schoolId, 'clusters' => 0, 'routes' => []];
        }

        // «Только с началкой» не смешиваем с остальными.
        $elem = array_values(array_filter($subs, fn($s) => (bool)$s->opt_elementary));
        $rest = array_values(array_filter($subs, fn($s) => !$s->opt_elementary));
        $routes = []; $clusterCount = 0;
        foreach ([$rest, $elem] as $pool) {
            if (!$pool) continue;
            foreach ($this->clusterByHome($pool) as $cluster) {
                $clusterCount++;
                foreach ($this->buildRoutesForCluster($school, $cluster) as $r) {
                    $routes[] = $r;
                }
            }
        }
        return ['school_id' => $schoolId, 'school' => $school->name, 'clusters' => $clusterCount, 'routes' => $routes];
    }

    /** Жадная кластеризация по близости домов. @param Subscription[] $subs */
    private function clusterByHome(array $subs): array
    {
        $radius = (float)$this->cfg['cluster_radius_m'];
        $maxCap = $this->pricing->maxCapacity() - 1;
        $clusters = [];
        foreach ($subs as $sub) {
            $best = null; $bestDist = INF;
            foreach ($clusters as $i => $cl) {
                if ($cl['children'] >= $maxCap) {
                    continue;
                }
                $d = Geo::haversineMeters($cl['centroid'][0], $cl['centroid'][1], $sub->home_lat, $sub->home_lon);
                if ($d <= $radius && $d < $bestDist) { $bestDist = $d; $best = $i; }
            }
            if ($best === null) {
                $clusters[] = [
                    'centroid' => [(float)$sub->home_lat, (float)$sub->home_lon],
                    'items' => [$sub],
                    'children' => (int)$sub->children_count,
                ];
            } else {
                $clusters[$best]['items'][] = $sub;
                $clusters[$best]['children'] += (int)$sub->children_count;
                $pts = array_map(fn($s) => [(float)$s->home_lat, (float)$s->home_lon], $clusters[$best]['items']);
                $clusters[$best]['centroid'] = Geo::centroid($pts);
            }
        }
        return array_map(fn($c) => $c['items'], $clusters);
    }

    /**
     * Собрать один или несколько рейсов из кластера (излишек → новый рейс).
     * @param Subscription[] $items
     * @return array список сводок рейсов
     */
    private function buildRoutesForCluster(School $school, array $items): array
    {
        $total = array_sum(array_map(fn($s) => (int)$s->children_count, $items));
        $vehicleClass = $this->pickVehicleClass($items, $total);
        $capacity = $this->pricing->childCapacityOf($vehicleClass);

        // Упорядочить заявки по маршруту (nearest-neighbour к школе) и разложить по вместимости.
        $ordered = $this->orderStops($items, $school);
        $groups = [];
        $cur = []; $curCnt = 0;
        foreach ($ordered as $s) {
            $cnt = (int)$s->children_count;
            if ($curCnt + $cnt > $capacity && $cur) {
                $groups[] = $cur; $cur = []; $curCnt = 0;
            }
            $cur[] = $s; $curCnt += $cnt;
        }
        if ($cur) {
            $groups[] = $cur;
        }

        $out = [];
        foreach ($groups as $group) {
            $out[] = $this->buildRoute($school, $group, $vehicleClass);
        }
        return $out;
    }

    /** @param Subscription[] $items уже упорядоченные, влезающие в один автомобиль */
    private function buildRoute(School $school, array $items, string $vehicleClass): array
    {
        $total = array_sum(array_map(fn($s) => (int)$s->children_count, $items));
        $capacity = $this->pricing->childCapacityOf($vehicleClass);
        $minChildren = (int)$this->cfg['min_children_default'];
        // Забор от двери только для малого легкового (4-мест); остальные — точки сбора.
        $pickupMode = $vehicleClass === 'car4' ? 'door' : 'points';

        if ($pickupMode === 'door') {
            $points = array_map(fn($s) => [
                'lat' => (float)$s->home_lat, 'lon' => (float)$s->home_lon, 'members' => [$s],
            ], $items);
        } else {
            $points = $this->makePickupPoints($items);
        }
        $points = $this->orderPoints($points, $school);

        $seq = array_map(fn($p) => [$p['lat'], $p['lon']], $points);
        $seq[] = [(float)$school->lat, (float)$school->lon];
        $route = $this->routing->routeMeters($seq);

        $centroid = Geo::centroid(array_map(fn($s) => [(float)$s->home_lat, (float)$s->home_lon], $items));
        $status = $total >= $minChildren ? RouteTrip::STATUS_ACTIVE : RouteTrip::STATUS_FORMING;

        $rt = new RouteTrip();
        $rt->school_id = $school->id;
        $rt->direction = 'am';
        $rt->vehicle_class = $vehicleClass;
        $rt->capacity = $capacity;
        $rt->min_children = $minChildren;
        $rt->current_children = $total;
        $rt->status = $status;
        $rt->pickup_mode = $pickupMode;
        $rt->start_date = $this->nextMonday();
        $rt->polyline = $route['polyline'];
        $rt->distance_km = round($route['distance'] / 1000, 2);
        $rt->centroid_lat = $centroid[0];
        $rt->centroid_lon = $centroid[1];
        $rt->created_at = time();
        $rt->save(false);

        // Fair-split для совместного ЛЕГКОВОГО: доля семьи ∝ (её расстояние × детей),
        // сумма долей всех семей = стоимость машины по всему маршруту. Фургоны — прежняя модель.
        $isLight = $this->pricing->categoryOf($vehicleClass) === 'light';
        $cLeg = 0.0; $sumW = 0.0; $memberDist = [];
        if ($isLight) {
            $cLeg = $this->pricing->carCostLeg((float)$rt->distance_km, $vehicleClass);
            foreach ($items as $s) {
                $d = (float)($s->distance_km ?: $this->routing->distanceKm($s->home_lat, $s->home_lon, $school->lat, $school->lon));
                $memberDist[$s->id] = $d;
                $sumW += $d * (int)$s->children_count;
            }
        }

        $pi = 1;
        foreach ($points as $p) {
            foreach ($p['members'] as $s) {
                $walk = $pickupMode === 'door' ? 0
                    : (int)round(Geo::haversineMeters($s->home_lat, $s->home_lon, $p['lat'], $p['lon']));

                $m = new RouteMember();
                $m->route_id = $rt->id;
                $m->subscription_id = $s->id;
                $m->child_id = $s->child_id;
                $m->children_count = (int)$s->children_count;
                $m->pickup_seq = $pi;
                $m->pickup_lat = $p['lat'];
                $m->pickup_lon = $p['lon'];
                $m->home_lat = $s->home_lat;
                $m->home_lon = $s->home_lon;
                $m->pickup_type = $pickupMode === 'door' ? 'door' : 'point';
                $m->walk_m = $walk;
                $m->status = 'assigned';
                $m->save(false);

                $s->route_id = $rt->id;
                if ($isLight) {
                    $w = $memberDist[$s->id] * (int)$s->children_count;
                    $s->monthly_price = $this->pricing->fairShareMonthly(
                        $cLeg, $w, $sumW, $vehicleClass, $s->direction ?: 'both', (int)$s->days_mask
                    ); // доля семьи (вес уже учитывает число детей)
                } else {
                    $dist = (float)($s->distance_km ?: $this->routing->distanceKm($s->home_lat, $s->home_lon, $school->lat, $school->lon));
                    $calc = $this->pricing->calcMonthly($dist, (int)$s->days_mask, $vehicleClass, Subscription::MODE_SHARED, $total, null, null, $s->direction ?: 'both');
                    $s->monthly_price = $calc['monthly'] * (int)$s->children_count; // на семью
                }
                $s->status = $status === RouteTrip::STATUS_ACTIVE ? Subscription::STATUS_ACTIVE : Subscription::STATUS_FORMING;
                $s->save(false);
            }
            $pi++;
        }

        return [
            'route_id' => $rt->id,
            'vehicle_class' => $vehicleClass,
            'capacity' => $capacity,
            'children' => $total,
            'min_children' => $minChildren,
            'status' => $status,
            'pickup_mode' => $pickupMode,
            'pickup_points' => count($points),
            'start_date' => $rt->start_date,
            'need_more' => max(0, $minChildren - $total),
            'distance_km' => (float)$rt->distance_km,
            'provider' => $route['provider'],
        ];
    }

    /** Общие точки сбора для фургонов. @param Subscription[] $items */
    private function makePickupPoints(array $items): array
    {
        $radius = (float)$this->pickup['point_radius_m'];
        $groupSize = (int)$this->pickup['point_group_size'];
        $points = [];
        foreach ($items as $s) {
            $best = null; $bestD = INF;
            foreach ($points as $i => $p) {
                $cnt = array_sum(array_map(fn($x) => (int)$x->children_count, $p['members']));
                if ($cnt >= $groupSize) continue;
                $d = Geo::haversineMeters($p['lat'], $p['lon'], $s->home_lat, $s->home_lon);
                if ($d <= $radius && $d < $bestD) { $bestD = $d; $best = $i; }
            }
            if ($best === null) {
                $points[] = ['lat' => (float)$s->home_lat, 'lon' => (float)$s->home_lon, 'members' => [$s]];
            } else {
                $points[$best]['members'][] = $s;
                $pts = array_map(fn($x) => [(float)$x->home_lat, (float)$x->home_lon], $points[$best]['members']);
                $c = Geo::centroid($pts);
                $points[$best]['lat'] = $c[0];
                $points[$best]['lon'] = $c[1];
            }
        }
        return $points;
    }

    /** Класс авто: пересечение приемлемых у всех; наименьшая вместимость под спрос. */
    private function pickVehicleClass(array $items, int $total): string
    {
        $vehicles = $this->pricing->vehicles();
        $sets = array_map(fn($s) => $s->vehicleOptionsArray(), $items);
        $common = array_shift($sets) ?: [];
        foreach ($sets as $set) {
            $common = array_values(array_intersect($common, $set));
        }
        if (!$common) {
            $common = [];
            foreach ($items as $s) $common = array_merge($common, $s->vehicleOptionsArray());
            $common = array_values(array_unique($common));
        }
        $common = array_values(array_filter($common, fn($c) => isset($vehicles[$c])));
        if (!$common) return 'van10';
        usort($common, fn($a, $b) => $vehicles[$a]['seats'] <=> $vehicles[$b]['seats']);
        foreach ($common as $c) {
            if (($vehicles[$c]['seats'] - 1) >= $total) return $c; // места − переднее
        }
        return end($common);
    }

    /** @param Subscription[] $items @return Subscription[] */
    private function orderStops(array $items, School $school): array
    {
        if (count($items) <= 1) return $items;
        usort($items, function ($a, $b) use ($school) {
            $da = Geo::haversineMeters($a->home_lat, $a->home_lon, $school->lat, $school->lon);
            $db = Geo::haversineMeters($b->home_lat, $b->home_lon, $school->lat, $school->lon);
            return $db <=> $da;
        });
        $ordered = [array_shift($items)];
        while ($items) {
            $last = end($ordered);
            $bestIdx = 0; $bestDist = INF;
            foreach ($items as $i => $s) {
                $d = Geo::haversineMeters($last->home_lat, $last->home_lon, $s->home_lat, $s->home_lon);
                if ($d < $bestDist) { $bestDist = $d; $bestIdx = $i; }
            }
            $ordered[] = $items[$bestIdx];
            array_splice($items, $bestIdx, 1);
        }
        return $ordered;
    }

    private function orderPoints(array $points, School $school): array
    {
        if (count($points) <= 1) return $points;
        usort($points, function ($a, $b) use ($school) {
            $da = Geo::haversineMeters($a['lat'], $a['lon'], $school->lat, $school->lon);
            $db = Geo::haversineMeters($b['lat'], $b['lon'], $school->lat, $school->lon);
            return $db <=> $da;
        });
        $ordered = [array_shift($points)];
        while ($points) {
            $last = end($ordered);
            $bestIdx = 0; $bestDist = INF;
            foreach ($points as $i => $p) {
                $d = Geo::haversineMeters($last['lat'], $last['lon'], $p['lat'], $p['lon']);
                if ($d < $bestDist) { $bestDist = $d; $bestIdx = $i; }
            }
            $ordered[] = $points[$bestIdx];
            array_splice($points, $bestIdx, 1);
        }
        return $ordered;
    }

    private function nextMonday(): string
    {
        $t = time();
        $dow = (int)date('N', $t);
        $add = $dow === 1 ? 0 : (8 - $dow);
        return date('Y-m-d', strtotime("+$add day", $t));
    }
}
