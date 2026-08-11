<?php
namespace app\controllers;

use app\models\Child;
use app\models\Driver;
use app\models\RouteDriver;
use app\models\RouteMember;
use app\models\RouteTrip;
use app\models\School;
use app\models\Subscription;
use app\models\Track;
use app\services\BalanceService;
use app\services\NotifyService;
use app\services\RouteMonitorService;
use app\services\OtpService;
use Yii;

class DriverController extends BaseApiController
{
    /** POST /api/driver/otp — запросить код водителю (SMS/Telegram). */
    public function actionOtp()
    {
        $phone = preg_replace('/[^\d+]/', '', (string)$this->param('phone'));
        if (!$phone) {
            $this->abort(422, 'Укажите телефон');
        }
        $r = OtpService::request($phone, 'driver');
        $out = ['phone' => $phone, 'channel' => $r['channel'], 'message' => 'Код отправлен'];
        if ($r['dev_code'] !== null) {
            $out['dev_code'] = $r['dev_code'];
            $out['message'] = 'Код (демо): ' . $r['dev_code'];
        }
        return $this->ok($out);
    }

    /** POST /api/driver/login — вход/регистрация водителя по OTP. Новый водитель = pending (верификация диспетчером). */
    public function actionLogin()
    {
        $phone = preg_replace('/[^\d+]/', '', (string)$this->param('phone'));
        $code = (string)$this->param('code');
        if (!OtpService::verify($phone, $code, 'driver')) {
            $this->abort(401, 'Неверный или просроченный код');
        }
        $driver = Driver::findOne(['phone' => $phone]);
        $isNew = false;
        if (!$driver) {
            $driver = new Driver();
            $driver->phone = $phone;
            $driver->name = $this->param('name') ?: 'Водитель';
            $driver->vehicle_class = $this->param('vehicle_class') ?: 'car7';
            $driver->capacity = (int)($this->param('capacity') ?: 7);
            $driver->plate = $this->param('plate') ?: '';
            $driver->car_model = $this->param('car_model');
            $driver->car_color = $this->param('car_color');
            $driver->online = false;
            $driver->verified = false;
            $driver->status = 'pending';
            $driver->created_at = time();
            $isNew = true;
        }
        $driver->auth_key = Yii::$app->security->generateRandomString(40);
        $driver->save(false);
        return $this->ok([
            'token' => $driver->auth_key,
            'driver' => $this->driverData($driver),
            'is_new' => $isNew,
            'verified' => (bool)$driver->verified,
        ]);
    }

    /** POST /api/driver/online — выйти на линию. */
    public function actionOnline()
    {
        $driver = $this->requireDriver();
        $goOnline = (bool)($this->param('online') ?? true);
        if ($goOnline && !$driver->verified) {
            $this->abort(403, 'Аккаунт на проверке у диспетчера — выход на линию будет доступен после верификации');
        }
        $driver->online = $goOnline;
        if ($this->param('lat')) {
            $driver->cur_lat = (float)$this->param('lat');
            $driver->cur_lon = (float)$this->param('lon');
        }
        $driver->save(false);
        return $this->ok(['driver' => $this->driverData($driver)]);
    }

    /** GET /api/driver/routes — доступные рейсы + назначенные мне. */
    public function actionRoutes()
    {
        $driver = $this->requireDriver();

        // Доступные: активные рейсы без водителя, вместимость которых водитель тянет.
        $available = RouteTrip::find()
            ->where(['status' => RouteTrip::STATUS_ACTIVE, 'driver_id' => null])
            ->andWhere(['<=', 'current_children', $driver->capacity])
            ->orderBy('id')->all();

        // Мои: назначенные мне (active/running).
        $mine = RouteTrip::find()
            ->where(['driver_id' => $driver->id])
            ->andWhere(['in', 'status', [RouteTrip::STATUS_ACTIVE, RouteTrip::STATUS_RUNNING]])
            ->orderBy('id')->all();

        return $this->ok([
            'available' => array_map(fn($r) => $this->routeBrief($r), $available),
            'mine' => array_map(fn($r) => $this->routeBrief($r), $mine),
        ]);
    }

    /** POST /api/driver/routes/{id}/signup — заранее записаться на маршрут (интерес). */
    public function actionSignup($id)
    {
        $driver = $this->requireDriver();
        $route = RouteTrip::findOne((int)$id);
        if (!$route) {
            $this->abort(404, 'Рейс не найден');
        }
        $rd = RouteDriver::findOne(['route_id' => $route->id, 'driver_id' => $driver->id]);
        if (!$rd) {
            $rd = new RouteDriver();
            $rd->route_id = $route->id;
            $rd->driver_id = $driver->id;
            $rd->status = RouteDriver::STATUS_INTERESTED;
            $rd->created_at = time();
            $rd->save(false);
        }
        return $this->ok([
            'signed_up' => true,
            'route_id' => $route->id,
            'start_date' => $route->start_date,
            'interested' => (int)RouteDriver::find()->where(['route_id' => $route->id, 'status' => RouteDriver::STATUS_INTERESTED])->count(),
        ]);
    }

    /** POST /api/driver/routes/{id}/accept — принять рейс. */
    public function actionAccept($id)
    {
        $driver = $this->requireDriver();
        $route = RouteTrip::findOne((int)$id);
        if (!$route || $route->status !== RouteTrip::STATUS_ACTIVE) {
            $this->abort(409, 'Рейс недоступен');
        }
        if ($route->driver_id && $route->driver_id != $driver->id) {
            $this->abort(409, 'Рейс уже принят другим водителем');
        }
        if ($route->current_children > $driver->capacity) {
            $this->abort(409, 'Не хватает вместимости');
        }
        $route->driver_id = $driver->id;
        $route->save(false);

        // Отметить запись водителя как назначенную, остальных — отклонить.
        $rd = RouteDriver::findOne(['route_id' => $route->id, 'driver_id' => $driver->id]);
        if (!$rd) {
            $rd = new RouteDriver();
            $rd->route_id = $route->id;
            $rd->driver_id = $driver->id;
            $rd->created_at = time();
        }
        $rd->status = RouteDriver::STATUS_ASSIGNED;
        $rd->save(false);
        RouteDriver::updateAll(['status' => RouteDriver::STATUS_DECLINED],
            ['and', ['route_id' => $route->id], ['not', ['driver_id' => $driver->id]]]);

        // Демо: рейс стартует сразу при приёме (флаг params['demo']['autostart_on_accept']).
        // Боевой режим (флаг=false): старт задаёт оператор через /start.
        if (!empty(Yii::$app->params['demo']['autostart_on_accept'])) {
            $route->status = RouteTrip::STATUS_RUNNING;
            $route->save(false);
        }

        NotifyService::routeStage($route, 'accepted',
            'Ваш рейс принят. Водитель: ' . ($driver->name ?? '') . ' · ' . ($driver->car_model ?? '') . ' ' . ($driver->plate ?? ''));

        return $this->ok(['route' => $this->routeFull($route)]);
    }

    /** GET /api/driver/routes/{id} — полный рейс с точками. */
    public function actionRouteView($id)
    {
        $this->requireDriver();
        $route = RouteTrip::findOne((int)$id);
        if (!$route) {
            $this->abort(404, 'Рейс не найден');
        }
        return $this->ok(['route' => $this->routeFull($route)]);
    }

    /** POST /api/driver/routes/{id}/start — начать рейс. */
    public function actionStart($id)
    {
        $driver = $this->requireDriver();
        $route = $this->ownRoute($id, $driver);
        $route->status = RouteTrip::STATUS_RUNNING;
        $route->save(false);
        NotifyService::routeStage($route, 'started', 'Рейс начался — водитель выехал по маршруту');
        return $this->ok(['route' => $this->routeFull($route)]);
    }

    /** POST /api/driver/routes/{id}/pickup — отметить посадку ребёнка. */
    /** Гейтинг «Прибыл к точке»: отметка разрешена только в радиусе (по умолчанию 100 м). */
    private function enforceArrivalRadius(RouteTrip $route, Driver $driver, $stopLat, $stopLon): void
    {
        if ($driver->phone === '+998900000000') return;              // god/тест-водитель — без ограничений
        if ((getenv('ARRIVAL_GATING') ?: '1') !== '1') return;       // гейтинг можно отключить env-ом
        if ($stopLat === null || $stopLon === null) return;          // нет координат точки — не блокируем
        $dlat = $this->param('lat'); $dlon = $this->param('lon');    // позиция: из запроса или последний трек
        if ($dlat === null || $dlon === null) {
            $last = Track::find()->where(['route_id' => $route->id])->orderBy('id DESC')->one();
            if (!$last) return;
            $dlat = $last->lat; $dlon = $last->lon;
        }
        $radius = (int)(getenv('ARRIVAL_RADIUS_M') ?: 100);
        $dist = $this->haversineM((float)$dlat, (float)$dlon, (float)$stopLat, (float)$stopLon);
        if ($dist > $radius) {
            $this->abort(422, 'Вы не на точке: до неё ' . (int)round($dist) . ' м (нужно ≤ ' . $radius . ' м)');
        }
    }

    private function haversineM(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $R = 6371000.0;
        $dLat = deg2rad($lat2 - $lat1); $dLon = deg2rad($lon2 - $lon1);
        $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLon / 2) ** 2;
        return $R * 2 * atan2(sqrt($a), sqrt(1 - $a));
    }

    public function actionPickup($id)
    {
        $driver = $this->requireDriver();
        $route = $this->ownRoute($id, $driver);
        $childId = (int)$this->param('child_id');
        $seq = $this->param('seq');
        if ($childId) {
            $member = RouteMember::findOne(['route_id' => $route->id, 'child_id' => $childId]);
            if (!$member) {
                $this->abort(404, 'Участник не найден');
            }
            $this->enforceArrivalRadius($route, $driver, $member->pickup_lat, $member->pickup_lon);
            $member->status = 'picked';
            $member->save(false);
            NotifyService::routeStage($route, 'picked', 'Водитель забрал ребёнка (точка ' . (int)$member->pickup_seq . ')');
            return $this->ok(['picked' => ['child_id' => $member->child_id, 'seq' => (int)$member->pickup_seq]]);
        }
        if ($seq !== null) {
            // Отметить всю точку посадки (все дети на этой точке).
            $anyM = RouteMember::findOne(['route_id' => $route->id, 'pickup_seq' => (int)$seq]);
            $this->enforceArrivalRadius($route, $driver, $anyM->pickup_lat ?? null, $anyM->pickup_lon ?? null);
            $n = RouteMember::updateAll(['status' => 'picked'], ['route_id' => $route->id, 'pickup_seq' => (int)$seq]);
            NotifyService::routeStage($route, 'picked', 'Водитель забрал детей на точке ' . (int)$seq);
            return $this->ok(['picked_point' => (int)$seq, 'children' => $n]);
        }
        $this->abort(422, 'Укажите child_id или seq');
    }

    /** POST /api/driver/routes/{id}/complete — завершить рейс. */
    public function actionComplete($id)
    {
        $driver = $this->requireDriver();
        $route = $this->ownRoute($id, $driver);
        $alreadyDone = $route->status === RouteTrip::STATUS_DONE;
        $route->status = RouteTrip::STATUS_DONE;
        $route->save(false);
        RouteMember::updateAll(['status' => 'dropped'], ['route_id' => $route->id]);
        // Авто-списание за рейс (один раз): с клиентов доля за поездку, водителю — начисление.
        $billing = $alreadyDone ? ['ok' => true, 'skipped' => 'already_completed'] : BalanceService::chargeRouteComplete($route);
        NotifyService::routeStage($route, 'completed', 'Рейс завершён — ребёнок доставлен');
        return $this->ok(['route' => $this->routeBrief($route), 'billing' => $billing]);
    }

    /** POST /api/driver/routes/{id}/track — прислать координату. */
    public function actionTrack($id)
    {
        $driver = $this->requireDriver();
        $route = $this->ownRoute($id, $driver);
        $lat = (float)$this->param('lat');
        $lon = (float)$this->param('lon');
        $t = new Track();
        $t->route_id = $route->id;
        $t->lat = $lat;
        $t->lon = $lon;
        $t->recorded_at = time();
        $t->speed = $this->param('speed') !== null ? (float)$this->param('speed') : $t->speed;
        $t->accuracy = $this->param('accuracy') !== null ? (float)$this->param('accuracy') : null;
        $t->bearing = $this->param('bearing') !== null ? (float)$this->param('bearing') : null;
        $t->save(false);
        $driver->cur_lat = $lat;
        $driver->cur_lon = $lon;
        $driver->save(false);
        $warn = RouteMonitorService::onTrack($route, $driver, $this->param('speed'));
        NotifyService::pushLive($route, $lat, $lon);   // подвинуть живую геолокацию в Telegram
        return $this->ok(['saved' => true] + ($warn ? ['warn' => $warn] : []));
    }

    // ---- helpers ----

    private function ownRoute($id, Driver $driver): RouteTrip
    {
        $route = RouteTrip::findOne((int)$id);
        if (!$route) {
            $this->abort(404, 'Рейс не найден');
        }
        if ($route->driver_id != $driver->id) {
            $this->abort(403, 'Это не ваш рейс');
        }
        return $route;
    }

    private function driverData(Driver $d): array
    {
        return [
            'id' => $d->id, 'name' => $d->name, 'phone' => $d->phone,
            'vehicle_class' => $d->vehicle_class, 'capacity' => (int)$d->capacity,
            'plate' => $d->plate, 'online' => (bool)$d->online,
            'car_model' => $d->car_model, 'car_color' => $d->car_color,
            'verified' => (bool)$d->verified, 'status' => $d->status,
            'balance' => (int)$d->balance,
        ];
    }

    private function routeBrief(RouteTrip $r): array
    {
        $school = School::findOne($r->school_id);
        return [
            'id' => $r->id,
            'status' => $r->status,
            'school' => $school ? $school->name : null,
            'vehicle_class' => $r->vehicle_class,
            'children' => (int)$r->current_children,
            'capacity' => (int)$r->capacity,
            'distance_km' => (float)$r->distance_km,
            'pickup_mode' => $r->pickup_mode,
            'start_date' => $r->start_date,
            'interested' => (int)RouteDriver::find()->where(['route_id' => $r->id, 'status' => RouteDriver::STATUS_INTERESTED])->count(),
        ];
    }

    private function routeFull(RouteTrip $r): array
    {
        $school = School::findOne($r->school_id);
        return [
            'id' => $r->id,
            'status' => $r->status,
            'vehicle_class' => $r->vehicle_class,
            'capacity' => (int)$r->capacity,
            'children' => (int)$r->current_children,
            'distance_km' => (float)$r->distance_km,
            'pickup_mode' => $r->pickup_mode,
            'start_date' => $r->start_date,
            'polyline' => $r->polyline,
            'driver' => $r->driver_id ? (Driver::findOne($r->driver_id)?->card()) : null,
            'school' => $school ? ['name' => $school->name, 'lat' => (float)$school->lat, 'lon' => (float)$school->lon] : null,
            'stops' => self::groupStops($r),
        ];
    }

    /** Остановки, сгруппированные по точке посадки (дети — количеством). */
    public static function groupStops(RouteTrip $r): array
    {
        $members = RouteMember::find()->where(['route_id' => $r->id])->orderBy('pickup_seq')->all();
        $points = [];
        foreach ($members as $m) {
            $seq = (int)$m->pickup_seq;
            if (!isset($points[$seq])) {
                $points[$seq] = [
                    'seq' => $seq,
                    'lat' => (float)$m->pickup_lat,
                    'lon' => (float)$m->pickup_lon,
                    'type' => $m->pickup_type,
                    'children_total' => 0,
                    'pickups' => [],
                ];
            }
            $sub = Subscription::findOne($m->subscription_id);
            $points[$seq]['children_total'] += (int)$m->children_count;
            $points[$seq]['pickups'][] = [
                'children_count' => (int)$m->children_count,
                'address' => $sub ? $sub->home_address : null,
                'walk_m' => (int)$m->walk_m,
                'status' => $m->status,
            ];
        }
        return array_values($points);
    }
}
