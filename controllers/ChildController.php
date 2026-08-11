<?php
namespace app\controllers;

use app\models\Child;

class ChildController extends BaseApiController
{
    public function actionIndex()
    {
        $parent = $this->requireParent();
        $children = Child::find()->where(['parent_id' => $parent->id])->orderBy('id')->all();
        return $this->ok([
            'children' => array_map(fn(Child $c) => [
                'id' => $c->id, 'name' => $c->name, 'grade' => $c->grade,
            ], $children),
        ]);
    }

    public function actionCreate()
    {
        $parent = $this->requireParent();
        $name = trim((string)$this->param('name'));
        if ($name === '') {
            $this->abort(422, 'Укажите имя ребёнка');
        }
        $c = new Child();
        $c->parent_id = $parent->id;
        $c->name = $name;
        $c->grade = $this->param('grade');
        $c->created_at = time();
        $c->save(false);
        return $this->ok(['child' => ['id' => $c->id, 'name' => $c->name, 'grade' => $c->grade]]);
    }
}
