<?php

use App\Models\EmailCode;
use Illuminate\Support\Facades\Schedule;

// Housekeeping (runs in the scheduler container).
Schedule::command('sanctum:prune-expired --hours=24')->daily();
Schedule::call(fn () => EmailCode::where('expires_at', '<', now()->subDay())->delete())->name('prune-email-codes')->daily();
