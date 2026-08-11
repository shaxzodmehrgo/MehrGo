<?php
namespace app\controllers;

use app\models\Attendance;
use app\models\RouteTrip;
use app\models\School;
use app\models\Subscription;
use app\models\Track;
use app\services\CancellationService;
use app\services\Geo;
use app\services\MatchingService;
use app\services\PricingService;
use app\services\RoutingService;
use Yii;

class SubscriptionController extends BaseApiController
{
    /** POST /api/calc — живой расчёт: варианты авто по местам, мин-детей на опцию, семейная сумма. */
    public function actionCalc()
    {
        $homeLat = (float)$this->param('home_lat');
        $homeLon = (float)$this->param('home_lon');
        $schoolId = (int)$this->param('school_id');
        $daysMask = (int)($this->param('days_mask') ?: Yii::$app->params['days']['default_mask']);
        $childrenCount = max(1, (int)($this->param('children_count') ?: 1));
        $options = $this->vehicleOptions($this->param('vehicle_options'));

        $school = School::findOne($schoolId);
        if (!$school) {
            $this->abort(422, 'Школа не найдена');
        }

        $pricing = new PricingService();
        $distance = (new RoutingService())->distanceKm($homeLat, $homeLon, $school->lat, $school->lon);

        $minChildren = (int)Yii::$app->params['matching']['min_children_default'];
        $nearbyForming = $this->countNearbyForming($school->id, $homeLat, $homeLon);
        $personalClass = Yii::$app->params['pricing']['personal_class'] ?? 'car7';
        $direction = in_array($this->param('direction'), ['am', 'pm'], true) ? $this->param('direction') : 'both';

        $variants = [];

        // Личный водитель (выделенная машина, забор от двери)
        $pc = $pricing->calcMonthly($distance, $daysMask, $personalClass, Subscription::MODE_PRIVATE, null, null, null, $direction);
        $capP = $pricing->childCapacityOf($personalClass);
        $routesP = (int)ceil($childrenCount / max(1, $capP));
        $variants[] = [
            'mode' => 'private',
            'vehicle_class' => $personalClass,
            'seats' => $pricing->capacityOf($personalClass),
            'child_capacity' => $capP,
            'category' => 'light',
            'pickup' => 'door',
            'car_monthly' => $pc['monthly'],
            'per_child_monthly' => null,
            'family_monthly' => $pc['monthly'] * $routesP,
            'min_children' => 1,
            'capacity' => $capP,
            'routes_needed' => $routesP,
            'school_days' => $pc['school_days'],
        ];

        // Совместные варианты по выбранным местам
        foreach ($options as $veh) {
            $cap = $pricing->childCapacityOf($veh);
            $occ = min($cap, max($minChildren, $nearbyForming + $childrenCount));
            $sc = $pricing->calcMonthly($distance, $daysMask, $veh, Subscription::MODE_SHARED, $occ, null, null, $direction);
            $variants[] = [
                'mode' => 'shared',
                'vehicle_class' => $veh,
                'seats' => $pricing->capacityOf($veh),
                'child_capacity' => $cap,
                'category' => $pricing->categoryOf($veh),
                'pickup' => $veh === 'car4' ? 'door' : 'points',
                'per_child_monthly' => $sc['monthly'],
                'car_monthly' => null,
                'family_monthly' => $sc['monthly'] * $childrenCount,
                'min_children' => $minChildren,
                'capacity' => $cap,
                'routes_needed' => (int)ceil($childrenCount / max(1, $cap)),
                'pool_discount' => $sc['pool_discount'],
                'effective_occupancy' => $occ,
                'school_days' => $sc['school_days'],
            ];
        }

        return $this->ok([
            'distance_km' => $distance,
            'children_count' => $childrenCount,
            'direction' => $direction,
            'school' => ['id' => $school->id, 'name' => $school->name, 'lat' => (float)$school->lat, 'lon' => (float)$school->lon],
            'pickup_time' => $this->computePickupTime($school, $distance),
            'min_children' => $minChildren,
            'nearby_forming' => $nearbyForming,
            'need_more' => max(0, $minChildren - ($nearbyForming + $childrenCount)),
            'variants' => $variants,
        ]);
    }

    /** POST /api/subscriptions — создать заявку и запустить пулинг. */
    public function actionCreate()
    {
        $parent = $this->requireParent();
        $schoolId = (int)$this->param('school_id');
        $homeLat = (float)$this->param('home_lat');
        $homeLon = (float)$this->param('home_lon');
        $childrenCount = max(1, (int)($this->param('children_count') ?: 1));
        $mode = $this->param('mode') === Subscription::MODE_PRIVATE ? Subscription::MODE_PRIVATE : Subscription::MODE_SHARED;
        $daysMask = (int)($this->param('days_mask') ?: Yii::$app->params['days']['default_mask']);
        $options = $this->vehicleOptions($this->param('vehicle_options'));
        $direction = in_array($this->param('direction'), ['am', 'pm'], true) ? $this->param('direction') : 'both';

        $school = School::findOne($schoolId);
        if (!$school) {
            $this->abort(422, 'Школа не найдена');
        }

        $pricing = new PricingService();
        $distance = (new RoutingService())->distanceKm($homeLat, $homeLon, $school->lat, $school->lon);
        $personalClass = Yii::$app->params['pricing']['personal_class'] ?? 'car7';

        // Дни: фургонные совместные рейсы — только Пн–Пт; легковой/личный — до 7.
        $hasVan = false;
        foreach ($options as $o) {
            if ($pricing->categoryOf($o) === 'van') {
                $hasVan = true;
            }
        }
        if ($mode === Subscription::MODE_SHARED && $hasVan) {
            $daysMask &= (int)Yii::$app->params['days']['van_fixed_mask'];
        }

        $sub = new Subscription();
        $sub->parent_id = $parent->id;
        $sub->child_id = null;
        $sub->children_count = $childrenCount;
        $sub->home_lat = $homeLat;
        $sub->home_lon = $homeLon;
        $sub->home_address = $this->param('home_address');
        $sub->school_id = $school->id;
        $sub->days_mask = $daysMask ?: (int)Yii::$app->params['days']['van_fixed_mask'];
        $sub->vehicle_options = implode(',', $options ?: ['van10']);
        $sub->mode = $mode;
        $sub->direction = $direction;
        $sub->grade_band = $this->param('grade_band');
        $sub->opt_teacher = (bool)$this->param('opt_teacher');
        $sub->opt_child_seat = (bool)$this->param('opt_child_seat');
        $sub->opt_elementary = (bool)$this->param('opt_elementary');
        $sub->needs = $this->param('needs');
        $sub->distance_km = $distance;
        $sub->status = Subscription::STATUS_FORMING;
        $sub->created_at = time();

        if ($mode === Subscription::MODE_PRIVATE) {
            $calc = $pricing->calcMonthly($distance, $sub->days_mask, $personalClass, Subscription::MODE_PRIVATE, null, null, null, $direction);
            $routes = (int)ceil($childrenCount / max(1, $pricing->childCapacityOf($personalClass)));
            $sub->monthly_price = $calc['monthly'] * $routes;
            $sub->status = Subscription::STATUS_ACTIVE; // личный водитель активен сразу
        } else {
            $minChildren = (int)Yii::$app->params['matching']['min_children_default'];
            $calc = $pricing->calcMonthly($distance, $sub->days_mask, $options[0] ?? 'van10', Subscription::MODE_SHARED, $minChildren, null, null, $direction);
            $sub->monthly_price = $calc['monthly'] * $childrenCount;
        }
        $sub->save(false);

        $matching = null;
        if ($mode === Subscription::MODE_SHARED) {
            $matching = (new MatchingService())->runForSchool($school->id);
            $sub->refresh();
        }

        return $this->ok([
            'subscription' => $this->serializeSubscription($sub),
            'matching' => $matching,
        ]);
    }

    public function actionIndex()
    {
        $parent = $this->requireParent();
        $subs = Subscription::find()->where(['parent_id' => $parent->id])->orderBy('id DESC')->all();
        return $this->ok(['subscriptions' => array_map(fn($s) => $this->serializeSubscription($s), $subs)]);
    }

    public function actionView($id)
    {
        $parent = $this->requireParent();
        $sub = Subscription::findOne(['id' => (int)$id, 'parent_id' => $parent->id]);
        if (!$sub) {
            $this->abort(404, 'Подписка не найдена');
        }
        return $this->ok(['subscription' => $this->serializeSubscription($sub, true)]);
    }

    public function actionCalendar($id)
    {
        $parent = $this->requireParent();
        $sub = Subscription::findOne(['id' => (int)$id, 'parent_id' => $parent->id]);
        if (!$sub) {
            $this->abort(404, 'Подписка не найдена');
        }
        $year = (int)($this->param('year') ?: date('Y'));
        $month = (int)($this->param('month') ?: date('n'));
        $daysInMonth = (int)date('t', mktime(0, 0, 0, $month, 1, $year));

        $att = [];
        foreach (Attendance::find()->where(['subscription_id' => $sub->id])->all() as $a) {
            $att[$a->date] = $a;
        }

        $days = [];
        for ($d = 1; $d <= $daysInMonth; $d++) {
            $ts = mktime(0, 0, 0, $month, $d, $year);
            $date = date('Y-m-d', $ts);
            $n = (int)date('N', $ts);
            $active = (bool)($sub->days_mask & (1 << ($n - 1)));
            $a = $att[$date] ?? null;
            $days[] = [
                'date' => $date,
                'weekday' => $n,
                'scheduled' => $active,
                'status' => $a ? $a->status : ($active ? 'planned' : 'off'),
                'charge_percent' => $a ? $a->charge_percent : null,
                'charge_amount' => $a ? $a->charge_amount : null,
            ];
        }
        return $this->ok(['year' => $year, 'month' => $month, 'days' => $days]);
    }

    public function actionDayOff($id)
    {
        $parent = $this->requireParent();
        $sub = Subscription::findOne(['id' => (int)$id, 'parent_id' => $parent->id]);
        if (!$sub) {
            $this->abort(404, 'Подписка не найдена');
        }
        $date = $this->param('date');
        if (!$date || !strtotime($date)) {
            $this->abort(422, 'Укажите дату (Y-m-d)');
        }
        $result = (new CancellationService())->applyDayOff($sub, date('Y-m-d', strtotime($date)));
        return $this->ok(['result' => $result]);
    }

    public function actionTrack($id)
    {
        $parent = $this->requireParent();
        $sub = Subscription::findOne(['id' => (int)$id, 'parent_id' => $parent->id]);
        if (!$sub || !$sub->route_id) {
            $this->abort(404, 'Рейс не назначен');
        }
        $route = RouteTrip::findOne($sub->route_id);
        $last = Track::find()->where(['route_id' => $sub->route_id])->orderBy('id DESC')->one();
        $stops = $route ? \app\controllers\DriverController::groupStops($route) : [];
        $school = $route ? School::findOne($route->school_id) : null;

        $position = null;
        if ($last) {
            $position = [
                'lat' => (float)$last->lat, 'lon' => (float)$last->lon, 'at' => (int)$last->recorded_at,
                'speed' => $last->speed !== null ? (float)$last->speed : null,
                'bearing' => $last->bearing !== null ? (float)$last->bearing : null,
            ];
        }

        // Следующая цель: первая невзятая точка, иначе школа.
        $picked = 0;
        $next = null;
        foreach ($stops as $p) {
            if ($this->pointDone($p)) {
                $picked++;
            } elseif ($next === null) {
                $next = ['lat' => $p['lat'], 'lon' => $p['lon'], 'label' => 'Точка ' . $p['seq'], 'seq' => $p['seq'], 'type' => 'stop'];
            }
        }
        if ($next === null && $school) {
            $next = ['lat' => (float)$school->lat, 'lon' => (float)$school->lon, 'label' => $school->name, 'type' => 'school'];
        }

        // ETA до следующей цели (расстояние / скорость, минимум 20 км/ч).
        $eta = null;
        if ($position && $next) {
            $km = Geo::haversineMeters($position['lat'], $position['lon'], $next['lat'], $next['lon']) / 1000.0;
            $kmh = ($position['speed'] && $position['speed'] > 5) ? $position['speed'] : 20;
            $eta = (int)max(1, ceil($km / $kmh * 60));
        }

        return $this->ok([
            'route_status' => $route ? $route->status : null,
            'position' => $position,
            'driver' => $route && $route->driver_id ? $this->driverInfo($route) : null,
            'events' => $this->routeEvents($sub),
            'stops' => array_map(fn($p) => [
                'seq' => $p['seq'], 'lat' => $p['lat'], 'lon' => $p['lon'],
                'children' => $p['children_total'], 'done' => $this->pointDone($p),
            ], $stops),
            'school' => $school ? ['lat' => (float)$school->lat, 'lon' => (float)$school->lon, 'name' => $school->name] : null,
            'home' => ['lat' => (float)$sub->home_lat, 'lon' => (float)$sub->home_lon],
            'polyline' => $route ? $route->polyline : null,
            'next' => $next,
            'eta_min' => $eta,
            'picked' => $picked,
            'total' => count($stops),
        ]);
    }

    /** GET /api/subscriptions/{id}/tg-link — deep-link привязки Telegram-бота к родителю. */
    public function actionTgLink($id)
    {
        $parent = $this->requireParent();
        $sub = Subscription::findOne(['id' => (int)$id, 'parent_id' => $parent->id]);
        if (!$sub) {
            $this->abort(404, 'Подписка не найдена');
        }
        if (!\app\services\TelegramService::enabled()) {
            return $this->ok(['enabled' => false]);
        }
        $bot = \app\services\TelegramService::botUsername();
        $token = \app\controllers\TelegramController::tokenFor($parent);
        return $this->ok([
            'enabled' => true,
            'bot' => $bot,
            'link' => $bot ? ('https://t.me/' . $bot . '?start=' . $token) : null,
            'linked' => !empty($parent->tg_chat_id),
        ]);
    }

    /** Точка забора «сделана», если все дети на ней забраны/высажены. */
    private function pointDone(array $p): bool
    {
        if (empty($p['pickups'])) {
            return false;
        }
        foreach ($p['pickups'] as $pu) {
            $st = $pu['status'] ?? '';
            if ($st !== 'picked' && $st !== 'dropped') {
                return false;
            }
        }
        return true;
    }

    /** POST /api/subscriptions/{id}/review {stars,text} — оценка водителя родителем. */
    public function actionReview($id)
    {
        $parent = $this->requireParent();
        $sub = Subscription::findOne(['id' => (int)$id, 'parent_id' => $parent->id]);
        if (!$sub || !$sub->route_id) {
            $this->abort(404, 'Рейс не назначен');
        }
        $route = RouteTrip::findOne($sub->route_id);
        if (!$route || !$route->driver_id) {
            $this->abort(422, 'У рейса нет водителя');
        }
        $stars = (int)$this->param('stars');
        if ($stars < 1 || $stars > 5) {
            $this->abort(422, 'stars: 1..5');
        }
        $text = trim((string)$this->param('text'));
        Yii::$app->db->createCommand()->insert('shuttle_review', [
            'route_id' => (int)$route->id,
            'driver_id' => (int)$route->driver_id,
            'parent_id' => (int)$parent->id,
            'stars' => $stars,
            'text' => $text !== '' ? $text : null,
            'created_at' => time(),
        ])->execute();
        $avg = (new \yii\db\Query())->from('shuttle_review')->where(['driver_id' => (int)$route->driver_id])->average('stars');
        $cnt = (new \yii\db\Query())->from('shuttle_review')->where(['driver_id' => (int)$route->driver_id])->count();
        $d = \app\models\Driver::findOne($route->driver_id);
        if ($d) { $d->rating = round((float)$avg, 2); $d->save(false); }
        return $this->ok([
            'review' => ['stars' => $stars, 'text' => $text],
            'driver_rating' => round((float)$avg, 2),
            'reviews_count' => (int)$cnt,
        ]);
    }

    // ---- helpers ----

    private function vehicleOptions($raw): array
    {
        $arr = is_array($raw) ? $raw : array_map('trim', explode(',', (string)$raw));
        $known = array_keys(Yii::$app->params['pricing']['vehicles']);
        return array_values(array_filter($arr, fn($v) => in_array($v, $known, true)));
    }

    private function countNearbyForming(int $schoolId, float $lat, float $lon): int
    {
        $radius = (float)Yii::$app->params['matching']['cluster_radius_m'];
        $subs = Subscription::find()->where([
            'school_id' => $schoolId,
            'status' => Subscription::STATUS_FORMING,
            'mode' => Subscription::MODE_SHARED,
        ])->all();
        $count = 0;
        foreach ($subs as $s) {
            if (Geo::haversineMeters($lat, $lon, $s->home_lat, $s->home_lon) <= $radius) {
                $count += (int)$s->children_count;
            }
        }
        return $count;
    }

    private function serializeSubscription(Subscription $sub, bool $withRoute = false): array
    {
        $school = School::findOne($sub->school_id);
        $data = [
            'id' => $sub->id,
            'children_count' => (int)$sub->children_count,
            'school' => $school ? ['id' => $school->id, 'name' => $school->name, 'lat' => (float)$school->lat, 'lon' => (float)$school->lon] : null,
            'home' => ['lat' => (float)$sub->home_lat, 'lon' => (float)$sub->home_lon, 'address' => $sub->home_address],
            'mode' => $sub->mode,
            'direction' => $sub->direction,
            'grade_band' => $sub->grade_band,
            'options' => [
                'teacher' => (bool)$sub->opt_teacher,
                'child_seat' => (bool)$sub->opt_child_seat,
                'elementary' => (bool)$sub->opt_elementary,
            ],
            'needs' => $sub->needs,
            'days_mask' => (int)$sub->days_mask,
            'vehicle_options' => $sub->vehicleOptionsArray(),
            'distance_km' => (float)$sub->distance_km,
            'monthly_price' => (int)$sub->monthly_price,
            'pickup_time' => $school ? $this->computePickupTime($school, (float)$sub->distance_km) : null,
            'status' => $sub->status,
            'route_id' => $sub->route_id ? (int)$sub->route_id : null,
        ];
        if ($sub->route_id) {
            $route = RouteTrip::findOne($sub->route_id);
            if ($route) {
                $data['route'] = $this->serializeRoute($route, $withRoute);
            }
            $data['events'] = $this->routeEvents($sub);
        }
        return $data;
    }

    private function serializeRoute(RouteTrip $route, bool $full = false): array
    {
        $out = [
            'id' => $route->id,
            'status' => $route->status,
            'vehicle_class' => $route->vehicle_class,
            'seats' => (int)$route->capacity,
            'capacity' => (int)$route->capacity,
            'current_children' => (int)$route->current_children,
            'min_children' => (int)$route->min_children,
            'need_more' => max(0, (int)$route->min_children - (int)$route->current_children),
            'distance_km' => (float)$route->distance_km,
            'pickup_mode' => $route->pickup_mode,
            'start_date' => $route->start_date,
            'has_driver' => (bool)$route->driver_id,
        ];
        if ($full) {
            $out['stops'] = \app\controllers\DriverController::groupStops($route);
            $out['polyline'] = $route->polyline;
            $out['school'] = ($s = School::findOne($route->school_id)) ? ['lat' => (float)$s->lat, 'lon' => (float)$s->lon, 'name' => $s->name] : null;
            $out['driver'] = $route->driver_id ? $this->driverInfo($route) : null;
        }
        return $out;
    }

    private function driverInfo(RouteTrip $route): ?array
    {
        $d = \app\models\Driver::findOne($route->driver_id);
        if (!$d) {
            return null;
        }
        $card = $d->card();
        $card['reviews'] = (new \yii\db\Query())
            ->select(['stars', 'text', 'created_at'])
            ->from('shuttle_review')
            ->where(['driver_id' => $d->id])
            ->orderBy(['id' => SORT_DESC])->limit(5)->all();
        return $card;
    }

    /** Лента событий рейса для родителя: свои (accepted/started/picked/completed) + алерты рейса (late). */
    private function routeEvents(Subscription $sub): array
    {
        if (!$sub->route_id) {
            return [];
        }
        return (new \yii\db\Query())
            ->select(['type', 'text', 'created_at'])
            ->from('shuttle_event')
            ->where(['or',
                ['subscription_id' => $sub->id],
                ['and', ['route_id' => (int)$sub->route_id], ['subscription_id' => null]],
            ])
            ->orderBy(['id' => SORT_DESC])->limit(10)->all();
    }

    /** Во сколько ребёнку выйти (подача) = начало уроков − время в пути − буфер. */
    private function computePickupTime(School $school, float $distanceKm): ?string
    {
        if (empty($school->start_time)) {
            return null;
        }
        $avgKmh = 25;
        $travelMin = (int)ceil($distanceKm / $avgKmh * 60) + 10; // +10 мин буфер
        $ts = strtotime($school->start_time) - $travelMin * 60;
        return $ts ? date('H:i', $ts) : null;
    }
}
