<?php

use App\Http\Controllers\Api\AccountController;
use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\SyncController;
use App\Http\Controllers\Api\VoiceController;
use Illuminate\Support\Facades\Route;

// Every route here is under /v1 (see bootstrap/app.php).

Route::prefix('auth')->middleware('throttle:auth')->group(function () {
    Route::post('register', [AuthController::class, 'register']);
    Route::post('verify', [AuthController::class, 'verify']);
    Route::post('resend', [AuthController::class, 'resend']);
    Route::post('login', [AuthController::class, 'login']);
    Route::post('forgot', [AuthController::class, 'forgot']);
    Route::post('reset', [AuthController::class, 'reset']);
});

Route::middleware(['auth:sanctum', 'abilities:sync', 'active'])->group(function () {
    Route::post('auth/logout', [AuthController::class, 'logout']);
    Route::get('me', [AuthController::class, 'me']);
    Route::delete('account', [AccountController::class, 'destroy']);
    Route::post('sync', SyncController::class)->middleware('throttle:sync');
    Route::post('voice/transcribe', [VoiceController::class, 'transcribe'])->middleware('throttle:voice');
    Route::post('voice/interpret', [VoiceController::class, 'interpret'])->middleware('throttle:voice');
    Route::post('voice/explain', [VoiceController::class, 'explain'])->middleware('throttle:voice');
});
