<?php
// Router for PHP built-in server: serve existing static files, else hand to Yii.
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);

// Корень открывает родительское приложение.
if ($path === '/' || $path === '') {
    header('Content-Type: text/html; charset=utf-8');
    readfile(__DIR__ . '/index.html');
    return true;
}

$file = __DIR__ . $path;
if (is_file($file)) {
    return false; // let the built-in server serve the static asset
}
require __DIR__ . '/index.php';
