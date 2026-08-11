<?php
namespace app\commands;

use app\models\Child;
use app\models\Driver;
use app\models\ParentUser;
use app\models\RouteMember;
use app\models\RouteTrip;
use app\models\School;
use app\models\Subscription;
use app\models\Track;
use app\models\Attendance;
use app\services\PricingService;
use app\services\RoutingService;
use app\services\MatchingService;
use Yii;
use yii\console\Controller;

class SeedController extends Controller
{
    /** Базовый сид: школы (кураторский список) + водители. */
    public function actionIndex()
    {
        // Кураторский список Б-точек: гос/частные школы + учебные центры (только партнёры).
        $schools = [
            ['Школа №64', 41.3111, 69.2797, 'Ташкент, Мирабадский р-н', 'state', 'school64', '08:00', '13:00'],
            ['Частная школа «Kelajak»', 41.3260, 69.2280, 'Ташкент, Юнусабадский р-н', 'private', 'kelajak', '08:30', '14:00'],
            ['Учебный центр «PDP Academy»', 41.2990, 69.2400, 'Ташкент, Чиланзарский р-н', 'center', 'pdp', '09:00', '12:00'],
            ['Yuksalish Uchtepa', 41.290612, 69.182960, 'Ташкент, Учтепинский р-н', 'private', 'yuksalishuchtepa', '08:00', '13:00'],
        ];
        foreach ($schools as [$name, $lat, $lon, $addr, $type, $slug, $st, $et]) {
            $s = School::findOne(['name' => $name]);
            if (!$s) { $s = new School(); $s->name = $name; }
            $s->lat = $lat; $s->lon = $lon; $s->address = $addr; $s->type = $type; $s->partner = true;
            $s->tg_slug = $slug; $s->start_time = $st; $s->end_time = $et;
            $s->save(false);
            $this->stdout("School #{$s->id}: {$name} [{$type}]\n");
        }
        $school = School::findOne(['name' => 'Школа №64']);

        foreach ([
            ['+998901112233', 'Алишер (van10)', 'van10', 10, '01 A 123 BC'],
            ['+998907778899', 'Бахтиёр (van20)', 'van20', 20, '01 B 777 CD'],
        ] as [$phone, $name, $vc, $cap, $plate]) {
            $d = Driver::findOne(['phone' => $phone]);
            if (!$d) {
                $d = new Driver();
                $d->phone = $phone;
                $d->created_at = time();
            }
            $d->name = $name;
            $d->vehicle_class = $vc;
            $d->capacity = $cap;
            $d->plate = $plate;
            $d->online = false;
            $d->save(false);
            $this->stdout("Driver #{$d->id}: {$name}\n");
        }
        return 0;
    }

    /** Демо-коридор: 2 родителя с ожидающими совместными заявками (для добора до порога). */
    public function actionDemo()
    {
        $school = School::findOne(['name' => 'Школа №64']);
        if (!$school) {
            $this->actionIndex();
            $school = School::findOne(['name' => 'Школа №64']);
        }
        $routing = new RoutingService();
        $pricing = new PricingService();

        $seed = [
            ['+998901000001', 'Родитель Ойбек', 'Ойбек', 41.2870, 69.2040, 'Чиланзар, кв. 12'],
            ['+998901000002', 'Родитель Нилуфар', 'Малика', 41.2895, 69.2075, 'Чиланзар, кв. 14'],
        ];
        foreach ($seed as [$phone, $pname, $cname, $lat, $lon, $addr]) {
            $parent = ParentUser::findOne(['phone' => $phone]);
            if (!$parent) {
                $parent = new ParentUser();
                $parent->phone = $phone;
                $parent->name = $pname;
                $parent->created_at = time();
                $parent->save(false);
            }
            $child = Child::findOne(['parent_id' => $parent->id, 'name' => $cname]);
            if (!$child) {
                $child = new Child();
                $child->parent_id = $parent->id;
                $child->name = $cname;
                $child->grade = '3-класс';
                $child->created_at = time();
                $child->save(false);
            }
            $exists = Subscription::findOne(['parent_id' => $parent->id, 'child_id' => $child->id]);
            if ($exists) {
                $this->stdout("Sub exists for {$pname}\n");
                continue;
            }
            $dist = $routing->distanceKm($lat, $lon, $school->lat, $school->lon);
            $calc = $pricing->calcMonthly($dist, 31, 'van10', Subscription::MODE_SHARED, 3);
            $sub = new Subscription();
            $sub->parent_id = $parent->id;
            $sub->child_id = $child->id;
            $sub->children_count = 1;
            $sub->home_lat = $lat;
            $sub->home_lon = $lon;
            $sub->home_address = $addr;
            $sub->school_id = $school->id;
            $sub->days_mask = 31;
            $sub->vehicle_options = 'van10,van20';
            $sub->mode = Subscription::MODE_SHARED;
            $sub->distance_km = $dist;
            $sub->monthly_price = $calc['monthly'];
            $sub->status = Subscription::STATUS_FORMING;
            $sub->created_at = time();
            $sub->save(false);
            $this->stdout("Forming sub #{$sub->id}: {$pname} / {$cname} ({$dist} км)\n");
        }
        $this->stdout("Демо-коридор готов. Порог рейса = " . Yii::$app->params['matching']['min_children_default'] . " (нужен ещё 1 ребёнок).\n");
        return 0;
    }

    /** Полный сброс данных shuttle (для повторного демо). */
    public function actionReset()
    {
        foreach ([Track::class, Attendance::class, RouteMember::class, RouteTrip::class,
                     Subscription::class, Child::class, ParentUser::class, Driver::class, School::class] as $m) {
            $m::deleteAll();
        }
        // сброс sequence не обязателен для демо
        $this->stdout("Данные shuttle очищены.\n");
        return 0;
    }

    /** God тест-аккаунт для жюри. Активация с 1 ребёнка: DEMO_MIN_CHILDREN=1 php yii seed/god */
    public function actionGod()
    {
        $this->actionIndex();
        $school = School::findOne(['name' => 'Школа №64']);

        $d = Driver::findOne(['phone' => '+998900000000']);
        if (!$d) { $d = new Driver(); $d->phone = '+998900000000'; $d->created_at = time(); }
        $d->name = 'Жасур Каримов'; $d->vehicle_class = 'van10'; $d->capacity = 10; $d->plate = '01 A 001 AA';
        $d->car_model = 'Chevrolet Damas'; $d->car_color = 'Белый'; $d->online = true;
        $d->verified = true; $d->status = 'active'; $d->profession = 'Профессиональный водитель';
        $d->experience_years = 8; $d->rating = 4.9; $d->rides_count = 340; $d->id_verified = true;
        $d->photo_url = 'https://randomuser.me/api/portraits/men/32.jpg'; $d->save(false);
        $this->stdout("God driver #{$d->id} (+998900000000)\n");

        $have = (new \yii\db\Query())->from('shuttle_review')->where(['driver_id' => $d->id])->count();
        if ($have == 0) {
            $reviews = [[5, 'Всегда вовремя, ребёнок доволен'], [5, 'Аккуратный и вежливый'],
                [5, 'Спокойно отпускаю ребёнка'], [4, 'Хороший водитель, рекомендую'],
                [5, 'Безопасно и пунктуально'], [5, 'Очень отзывчивый']];
            foreach ($reviews as [$st, $tx]) {
                Yii::$app->db->createCommand()->insert('shuttle_review', [
                    'driver_id' => $d->id, 'stars' => $st, 'text' => $tx, 'created_at' => time(),
                ])->execute();
            }
            $this->stdout("Отзывов добавлено: " . count($reviews) . "\n");
        }

        $p = ParentUser::findOne(['phone' => '+998900000001']);
        if (!$p) { $p = new ParentUser(); $p->phone = '+998900000001'; $p->name = 'Тест Родитель'; $p->created_at = time(); $p->save(false); }
        $c = Child::findOne(['parent_id' => $p->id, 'name' => 'Амина']);
        if (!$c) { $c = new Child(); $c->parent_id = $p->id; $c->name = 'Амина'; $c->grade = '2-класс'; $c->created_at = time(); $c->save(false); }
        if (!Subscription::findOne(['parent_id' => $p->id, 'child_id' => $c->id])) {
            $routing = new RoutingService(); $pricing = new PricingService();
            $lat = 41.2905; $lon = 69.2050;
            $dist = $routing->distanceKm($lat, $lon, $school->lat, $school->lon);
            $calc = $pricing->calcMonthly($dist, 31, 'van10', Subscription::MODE_SHARED, 3);
            $s = new Subscription();
            $s->parent_id = $p->id; $s->child_id = $c->id; $s->children_count = 1;
            $s->home_lat = $lat; $s->home_lon = $lon; $s->home_address = 'Чиланзар, демо';
            $s->school_id = $school->id; $s->days_mask = 31; $s->vehicle_options = 'van10,van20';
            $s->mode = Subscription::MODE_SHARED; $s->distance_km = $dist; $s->monthly_price = $calc['monthly'];
            $s->status = Subscription::STATUS_FORMING; $s->created_at = time(); $s->save(false);
            $this->stdout("God forming sub #{$s->id}\n");
        }
        (new MatchingService())->runForSchool($school->id);
        $min = (int)(getenv('DEMO_MIN_CHILDREN') ?: 3);
        $this->stdout("God готов. Водитель +998900000000, родитель +998900000001. min_children={$min}.\n");
        if ($min > 1) $this->stdout("Для рейса с 1 ребёнка: DEMO_MIN_CHILDREN=1 php yii seed/god\n");
        return 0;
    }
}
