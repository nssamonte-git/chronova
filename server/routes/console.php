<?php

use App\Models\EmailCode;
use App\Models\VoiceDecision;
use Illuminate\Support\Facades\Schedule;

// Housekeeping (runs in the scheduler container).
Schedule::command('sanctum:prune-expired --hours=24')->daily();
Schedule::call(fn () => EmailCode::where('expires_at', '<', now()->subDay())->delete())->name('prune-email-codes')->daily();
// Voice decisions older than VOICE_DECISION_RETENTION_DAYS (90 by default).
Schedule::command('model:prune', ['--model' => [VoiceDecision::class]])->daily();
