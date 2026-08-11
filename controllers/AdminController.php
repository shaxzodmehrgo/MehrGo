<?php
namespace app\controllers;

use app\models\Driver;
use app\models\ParentUser;
use app\models\RouteMember;
use app\models\RouteTrip;
use app\models\School;
use app\models\Subscription;
use app\models\Track;
use app\models\Transaction;
use app\services\BalanceService;
use app\services\MatchingService;
use Yii;

/**
 * Диспетчерский дашборд + служебные эндпоинты.
 * Доступ закрыт токеном: если задан params['admin']['token'] (env ADMIN_TOKEN),
 * все /api/admin/* требуют заголовок X-Admin-Token или ?key=. Пока токен не задан —
 * эндпоинты отдают 503 (чтобы данные клиентов/биллинга не висели открыто на проде).
 */
class AdminController extends BaseApiController
{
    public function beforeAction($action)
    {
        if (!parent::beforeAction($action)) {
            return false;
        }
        $token = (string)(Yii::$app->params['admin']['token'] ?? '');
        if ($token === '') {
            $this->abort(503, 'Админ-доступ не настроен: задайте ADMIN_TOKEN на сервере');
        }
        $given = Yii::$app->request->headers->get('X-Admin-Token') ?: (string)Yii::$app->request->get('key');
        if (!is_string($given) || !hash_equals($token, $given)) {
            $this->abort(401, 'Неверный админ-токен');
        }
        return true;
    }

    /** POST /api/admin/run-matching — ручной пулинг всех школ. */
    public function actionRunMatching()
    {
        return $this->ok(['matching' => (new MatchingService())->runAll()]);
    }

    /** GET /api/admin/dashboard — единая сводка для дашборда. */
    public function actionDashboard()
    {
        $schools = School::find()->orderBy('name')->all();
        $drivers = Driver::find()->orderBy('name')->all();
        $parents = ParentUser::find()->orderBy('name')->all();
        $subs = Subscription::find()->orderBy('id DESC')->all();
        $routes = RouteTrip::find()->orderBy('id')->all();

        $schoolName = [];
        foreach ($schools as $s) { $schoolName[$s->id] = $s->name; }
        $parentById = [];
        foreach ($parents as $p) { $parentById[$p->id] = $p; }
        $driverById = [];
        foreach ($drivers as $d) { $driverById[$d->id] = $d; }

        // --- Заявки + биллинг ---
        $subsOut = []; $mrrActive = 0; $mrrForming = 0; $byStatus = []; $bySchool = [];
        $subCountByParent = []; $mrrByParent = []; $subCountBySchool = [];
        foreach ($subs as $s) {
            $par = $parentById[$s->parent_id] ?? null;
            $price = (int)$s->monthly_price;
            $subsOut[] = [
                'id' => $s->id,
                'parent' => $par ? $par->name : null,
                'phone' => $par ? $par->phone : null,
                'school' => $schoolName[$s->school_id] ?? null,
                'mode' => $s->mode,
                'direction' => $s->direction,
                'children' => (int)$s->children_count,
                'distance_km' => (float)$s->distance_km,
                'monthly_price' => $price,
                'status' => $s->status,
                'route_id' => $s->route_id ? (int)$s->route_id : null,
                'address' => $s->home_address,
            ];
            $byStatus[$s->status] = ($byStatus[$s->status] ?? 0) + 1;
            if ($s->status === Subscription::STATUS_ACTIVE) { $mrrActive += $price; }
            if ($s->status === Subscription::STATUS_FORMING) { $mrrForming += $price; }
            $sn = $schoolName[$s->school_id] ?? '—';
            if (!isset($bySchool[$sn])) { $bySchool[$sn] = ['subs' => 0, 'active' => 0, 'mrr' => 0]; }
            $bySchool[$sn]['subs']++;
            if ($s->status === Subscription::STATUS_ACTIVE) { $bySchool[$sn]['active']++; $bySchool[$sn]['mrr'] += $price; }
            $subCountByParent[$s->parent_id] = ($subCountByParent[$s->parent_id] ?? 0) + 1;
            $mrrByParent[$s->parent_id] = ($mrrByParent[$s->parent_id] ?? 0) + $price;
            $subCountBySchool[$s->school_id] = ($subCountBySchool[$s->school_id] ?? 0) + 1;
        }

        // --- Рейсы ---
        $routesOut = []; $routeStatus = [];
        foreach ($routes as $r) {
            $dr = $r->driver_id ? ($driverById[$r->driver_id] ?? null) : null;
            $routesOut[] = [
                'id' => $r->id,
                'school' => $schoolName[$r->school_id] ?? null,
                'status' => $r->status,
                'vehicle_class' => $r->vehicle_class,
                'children' => (int)$r->current_children,
                'capacity' => (int)$r->capacity,
                'min_children' => (int)$r->min_children,
                'distance_km' => (float)$r->distance_km,
                'pickup_mode' => $r->pickup_mode,
                'start_date' => $r->start_date,
                'driver' => $dr ? $dr->name : null,
                'stops' => (int)RouteMember::find()->where(['route_id' => $r->id])->count(),
            ];
            $routeStatus[$r->status] = ($routeStatus[$r->status] ?? 0) + 1;
        }

        // --- Водители ---
        $driversOut = []; $onlineCount = 0;
        foreach ($drivers as $d) {
            if ($d->online) { $onlineCount++; }
            $curRoute = null;
            foreach ($routes as $r) {
                if ($r->driver_id == $d->id && in_array($r->status, [RouteTrip::STATUS_ACTIVE, RouteTrip::STATUS_RUNNING], true)) {
                    $curRoute = $r; break;
                }
            }
            $driversOut[] = [
                'id' => $d->id, 'name' => $d->name, 'phone' => $d->phone,
                'vehicle_class' => $d->vehicle_class, 'capacity' => (int)$d->capacity, 'plate' => $d->plate,
                'car_model' => $d->car_model, 'car_color' => $d->car_color,
                'online' => (bool)$d->online,
                'verified' => (bool)$d->verified, 'status' => $d->status,
                'balance' => (int)$d->balance,
                'lat' => $d->cur_lat ? (float)$d->cur_lat : null,
                'lon' => $d->cur_lon ? (float)$d->cur_lon : null,
                'route_id' => $curRoute ? $curRoute->id : null,
                'route_status' => $curRoute ? $curRoute->status : null,
            ];
        }

        // --- Клиенты ---
        $parentsOut = [];
        foreach ($parents as $p) {
            $parentsOut[] = [
                'id' => $p->id, 'name' => $p->name, 'phone' => $p->phone,
                'subs' => $subCountByParent[$p->id] ?? 0,
                'monthly' => $mrrByParent[$p->id] ?? 0,
                'balance' => (int)$p->balance,
            ];
        }

        // --- Последние транзакции ---
        $txns = array_map(fn(Transaction $tx) => [
            'id' => $tx->id, 'actor' => $tx->actor, 'actor_id' => $tx->actor_id,
            'type' => $tx->type, 'amount' => (int)$tx->amount, 'balance_after' => (int)$tx->balance_after,
            'route_id' => $tx->route_id ? (int)$tx->route_id : null, 'note' => $tx->note,
            'created_by' => $tx->created_by, 'created_at' => (int)$tx->created_at,
        ], Transaction::find()->orderBy('id DESC')->limit(100)->all());

        // --- Школы (точки Б) ---
        $schoolsOut = [];
        foreach ($schools as $s) {
            $schoolsOut[] = [
                'id' => $s->id, 'name' => $s->name, 'type' => $s->type, 'address' => $s->address,
                'lat' => (float)$s->lat, 'lon' => (float)$s->lon, 'partner' => (bool)$s->partner,
                'subs' => $subCountBySchool[$s->id] ?? 0,
                'start_time' => $s->start_time, 'end_time' => $s->end_time,
            ];
        }

        return $this->ok([
            'overview' => [
                'subscriptions_total' => count($subs),
                'subscriptions_by_status' => $byStatus,
                'routes_total' => count($routes),
                'routes_by_status' => $routeStatus,
                'drivers_total' => count($drivers),
                'drivers_online' => $onlineCount,
                'parents_total' => count($parents),
                'schools_total' => count($schools),
                'mrr_active' => $mrrActive,
                'mrr_forming_potential' => $mrrForming,
            ],
            'billing' => ['mrr_active' => $mrrActive, 'mrr_forming_potential' => $mrrForming, 'by_school' => $bySchool, 'by_status' => $byStatus],
            'schools' => $schoolsOut,
            'drivers' => $driversOut,
            'parents' => $parentsOut,
            'subscriptions' => $subsOut,
            'routes' => $routesOut,
        ]);
    }

    /** GET /api/admin/live — лёгкий эндпоинт для карты (частый поллинг). */
    public function actionLive()
    {
        $out = ['drivers' => [], 'routes' => []];
        foreach (Driver::find()->where(['online' => true])->all() as $d) {
            if ($d->cur_lat) {
                $out['drivers'][] = [
                    'id' => $d->id, 'name' => $d->name, 'vehicle_class' => $d->vehicle_class,
                    'lat' => (float)$d->cur_lat, 'lon' => (float)$d->cur_lon,
                ];
            }
        }
        foreach (RouteTrip::find()->where(['in', 'status', [RouteTrip::STATUS_ACTIVE, RouteTrip::STATUS_RUNNING]])->orderBy('id')->all() as $r) {
            $school = School::findOne($r->school_id);
            $last = Track::find()->where(['route_id' => $r->id])->orderBy('id DESC')->one();
            $grp = self::groupStops($r);
            $picked = 0;
            $next = null;
            foreach ($grp as $p) {
                if (self::pointDone($p)) {
                    $picked++;
                } elseif ($next === null) {
                    $next = ['lat' => $p['lat'], 'lon' => $p['lon'], 'label' => 'Точка ' . $p['seq']];
                }
            }
            if ($next === null && $school) {
                $next = ['lat' => (float)$school->lat, 'lon' => (float)$school->lon, 'label' => $school->name];
            }
            $drv = $r->driver_id ? Driver::findOne($r->driver_id) : null;
            $eta = null;
            if ($last && $next) {
                $km = self::hv((float)$last->lat, (float)$last->lon, $next['lat'], $next['lon']) / 1000.0;
                $kmh = ($last->speed && (float)$last->speed > 5) ? (float)$last->speed : 20;
                $eta = (int)max(1, ceil($km / $kmh * 60));
            }
            $out['routes'][] = [
                'id' => $r->id, 'status' => $r->status, 'school' => $school ? $school->name : null,
                'school_lat' => $school ? (float)$school->lat : null,
                'school_lon' => $school ? (float)$school->lon : null,
                'vehicle_class' => $r->vehicle_class,
                'polyline' => $r->polyline,
                'driver' => $drv ? ['name' => $drv->name, 'plate' => $drv->plate, 'car_model' => $drv->car_model] : null,
                'stops' => array_map(fn($p) => ['seq' => $p['seq'], 'lat' => $p['lat'], 'lon' => $p['lon'], 'children' => $p['children_total'], 'done' => self::pointDone($p)], $grp),
                'bus' => $last ? ['lat' => (float)$last->lat, 'lon' => (float)$last->lon, 'speed' => $last->speed !== null ? (float)$last->speed : null, 'bearing' => $last->bearing !== null ? (float)$last->bearing : null] : null,
                'next' => $next, 'eta_min' => $eta, 'picked' => $picked, 'total' => count($grp),
            ];
        }
        return $this->ok($out);
    }

    private static function pointDone(array $p): bool
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

    private static function hv(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $R = 6371000.0;
        $dLat = deg2rad($lat2 - $lat1);
        $dLon = deg2rad($lon2 - $lon1);
        $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLon / 2) ** 2;
        return $R * 2 * atan2(sqrt($a), sqrt(1 - $a));
    }

    /** GET /api/admin/routes/{id}/live — полный трекинг одного рейса для диспетчера. */
    public function actionRouteLive($id)
    {
        $r = RouteTrip::findOne((int)$id);
        if (!$r) {
            $this->abort(404, 'Рейс не найден');
        }
        $school = School::findOne($r->school_id);
        $last = Track::find()->where(['route_id' => $r->id])->orderBy('id DESC')->one();
        $grp = self::groupStops($r);
        $picked = 0;
        $next = null;
        foreach ($grp as $p) {
            if (self::pointDone($p)) {
                $picked++;
            } elseif ($next === null) {
                $next = ['lat' => $p['lat'], 'lon' => $p['lon'], 'label' => 'Точка ' . $p['seq']];
            }
        }
        if ($next === null && $school) {
            $next = ['lat' => (float)$school->lat, 'lon' => (float)$school->lon, 'label' => $school->name];
        }
        $drv = $r->driver_id ? Driver::findOne($r->driver_id) : null;
        $eta = null;
        if ($last && $next) {
            $km = self::hv((float)$last->lat, (float)$last->lon, $next['lat'], $next['lon']) / 1000.0;
            $kmh = ($last->speed && (float)$last->speed > 5) ? (float)$last->speed : 20;
            $eta = (int)max(1, ceil($km / $kmh * 60));
        }
        $events = (new \yii\db\Query())
            ->select(['type', 'text', 'created_at'])
            ->from('shuttle_event')
            ->where(['route_id' => (int)$r->id])
            ->orderBy(['id' => SORT_DESC])->limit(30)->all();

        return $this->ok([
            'id' => $r->id,
            'status' => $r->status,
            'school' => $school ? $school->name : null,
            'school_lat' => $school ? (float)$school->lat : null,
            'school_lon' => $school ? (float)$school->lon : null,
            'vehicle_class' => $r->vehicle_class,
            'polyline' => $r->polyline,
            'driver' => $drv ? $drv->card() : null,
            'stops' => array_map(fn($p) => [
                'seq' => $p['seq'], 'lat' => $p['lat'], 'lon' => $p['lon'],
                'children' => $p['children_total'], 'done' => self::pointDone($p),
            ], $grp),
            'bus' => $last ? [
                'lat' => (float)$last->lat, 'lon' => (float)$last->lon,
                'speed' => $last->speed !== null ? (float)$last->speed : null,
                'bearing' => $last->bearing !== null ? (float)$last->bearing : null,
                'at' => (int)$last->recorded_at,
            ] : null,
            'next' => $next,
            'eta_min' => $eta,
            'picked' => $picked,
            'total' => count($grp),
            'events' => $events,
        ]);
    }

    /** GET /api/admin/routes — список рейсов (обратная совместимость). */
    public function actionRoutes()
    {
        $routes = RouteTrip::find()->orderBy('id')->all();
        return $this->ok([
            'routes' => array_map(function (RouteTrip $r) {
                $school = School::findOne($r->school_id);
                $members = RouteMember::find()->where(['route_id' => $r->id])->orderBy('pickup_seq')->all();
                return [
                    'id' => $r->id,
                    'school' => $school ? $school->name : null,
                    'status' => $r->status,
                    'vehicle_class' => $r->vehicle_class,
                    'capacity' => (int)$r->capacity,
                    'children' => (int)$r->current_children,
                    'min_children' => (int)$r->min_children,
                    'distance_km' => (float)$r->distance_km,
                    'has_driver' => (bool)$r->driver_id,
                    'stops' => array_map(fn(RouteMember $m) => ['seq' => (int)$m->pickup_seq, 'status' => $m->status], $members),
                ];
            }, $routes),
        ]);
    }

    /** POST /api/admin/routes/{id}/start — операторский старт рейса (боевой режим). */
    public function actionStartRoute($id)
    {
        $route = RouteTrip::findOne((int)$id);
        if (!$route) {
            $this->abort(404, 'Рейс не найден');
        }
        if (!$route->driver_id) {
            $this->abort(422, 'Нельзя стартовать: водитель не назначен');
        }
        if ($route->status === RouteTrip::STATUS_RUNNING) {
            return $this->ok(['route' => ['id' => (int)$route->id, 'status' => $route->status], 'already' => true]);
        }
        if (in_array($route->status, [RouteTrip::STATUS_DONE, RouteTrip::STATUS_CANCELLED], true)) {
            $this->abort(422, 'Рейс уже завершён или отменён');
        }
        $route->status = RouteTrip::STATUS_RUNNING;
        $route->save(false);
        \app\services\NotifyService::routeStage($route, 'started', 'Рейс начался — водитель выехал по маршруту');
        return $this->ok(['route' => ['id' => (int)$route->id, 'status' => $route->status]]);
    }
}
