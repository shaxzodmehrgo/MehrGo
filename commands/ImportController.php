<?php
namespace app\commands;

use app\models\ParentUser;
use app\models\School;
use app\models\Subscription;
use app\services\MatchingService;
use app\services\PricingService;
use app\services\RoutingService;
use yii\console\Controller;

/**
 * Импорт реальных заявок из Telegram-бота (@mehrgoshuttlebot) для школы Yuksalish Uchtepa.
 * Направление: both — туда-обратно; am — только утром (дом→школа); pm — только вечером (школа→дом).
 */
class ImportController extends Controller
{
    public function actionYuksalish()
    {
        $school = School::findOne(['tg_slug' => 'yuksalishuchtepa']);
        if (!$school) {
            $this->stdout("Сначала seed/index (нет школы Yuksalish Uchtepa)\n");
            return 1;
        }
        $routing = new RoutingService();
        $pricing = new PricingService();

        // [phone, name, lat, lon, children, grade, direction]
        $apps = [
            ['998933013394', 'Ma\'rifat', 41.279230, 69.374731, 1, '1-4', 'both'],
            ['998932999713', 'Muyassar', 41.223421, 69.185901, 1, '5-7', 'both'],
            ['998999751515', 'Bekzod', 41.312564, 69.235112, 2, null, 'both'],
            ['998939076526', 'Ота-она', 41.313668, 69.306386, 1, '1-4', 'both'],
            ['998773942221', 'Dilshoda', 41.219465, 69.210234, 2, '1-4', 'both'],
            ['998900243825', 'Bibisora', 41.257274, 69.334843, 1, '1-4', 'both'],
            ['998946522663', 'Roziya Avazova', 41.318568, 69.198651, 1, '8-11', 'both'],
            ['998909561101', 'Sardor', 41.224416, 69.204738, 1, '1-4', 'pm'],
            ['998973681830', 'Dilshod Muxammedovich', 41.275554, 69.265328, 1, '1-4', 'both'],
            ['998909403439', 'Saydaliyeva', 41.268400, 69.292085, 2, '5-7', 'both'],
            ['998909127510', 'Kabul', 41.336341, 69.254313, 2, '1-4', 'both'],
            ['998971370703', 'Salamov S.S', 41.308135, 69.237444, 2, '1-4', 'both'],
            ['998995202300', 'Farmon', 41.367974, 69.391130, 2, '8-11', 'pm'],
            ['998977339761', 'Ота-она', 41.231380, 69.365700, 1, '1-4', 'am'],
            ['998933927767', 'Dildora', 41.318922, 69.224991, 1, '5-7', 'pm'],
            ['998900971976', 'Barnogul', 41.366081, 69.270107, 1, '5-7', 'both'],
            ['998881867323', 'Oyisha', 41.346002, 69.387479, 1, '1-4', 'both'],
            ['998935663836', 'G U', 41.303378, 69.248335, 2, '1-4', 'both'],
        ];

        $imported = 0;
        foreach ($apps as [$phone, $name, $lat, $lon, $children, $grade, $dir]) {
            $parent = ParentUser::findOne(['phone' => $phone]);
            if (!$parent) {
                $parent = new ParentUser();
                $parent->phone = $phone;
                $parent->name = $name;
                $parent->created_at = time();
                $parent->save(false);
            }
            if (Subscription::findOne(['parent_id' => $parent->id, 'school_id' => $school->id])) {
                continue;
            }
            $dist = $routing->distanceKm($lat, $lon, $school->lat, $school->lon);
            $calc = $pricing->calcMonthly($dist, 31, 'van10', Subscription::MODE_SHARED, 3, null, null, $dir);

            $sub = new Subscription();
            $sub->parent_id = $parent->id;
            $sub->children_count = (int)$children;
            $sub->home_lat = $lat;
            $sub->home_lon = $lon;
            $sub->home_address = null;
            $sub->school_id = $school->id;
            $sub->days_mask = 31;
            $sub->vehicle_options = 'car7,van9,van10,van20';
            $sub->mode = Subscription::MODE_SHARED;
            $sub->direction = $dir;
            $sub->grade_band = $grade;
            $sub->opt_elementary = ($grade === '1-4');
            $sub->distance_km = $dist;
            $sub->monthly_price = $calc['monthly'] * (int)$children;
            $sub->status = Subscription::STATUS_FORMING;
            $sub->created_at = time();
            $sub->save(false);
            $imported++;
        }
        $this->stdout(">>> Импортировано заявок: {$imported} (школа {$school->name})\n");

        $res = (new MatchingService())->runForSchool($school->id);
        $this->stdout(">>> Собрано рейсов: " . count($res['routes']) . " из " . $res['clusters'] . " кластеров\n");
        foreach ($res['routes'] as $r) {
            $this->stdout(sprintf("    рейс #%d: %s, детей %d/%d, %s, %s км, старт %s\n",
                $r['route_id'], $r['vehicle_class'], $r['children'], $r['capacity'],
                $r['status'], $r['distance_km'], $r['start_date']));
        }
        return 0;
    }
}
