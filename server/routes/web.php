<?php

use Illuminate\Support\Facades\Route;

// No public website: the app talks to /v1, staff use /admin.
Route::get('/', fn () => response()->json(['service' => 'chronova']));
