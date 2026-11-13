<?php

use App\Jobs\SendDueReminders;
use App\Models\EmailCode;
use App\Models\VoiceDecision;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schedule;

// Housekeeping (runs in the scheduler container).
Schedule::command('sanctum:prune-expired --hours=24')->daily();
Schedule::call(fn () => EmailCode::where('expires_at', '<', now()->subDay())->delete())->name('prune-email-codes')->daily();
// Voice decisions older than VOICE_DECISION_RETENTION_DAYS (90 by default).
Schedule::command('model:prune', ['--model' => [VoiceDecision::class]])->daily();
// Task reminders by push (docs/push.md): every minute, one run at a time.
Schedule::call(fn () => app(SendDueReminders::class)())->name('send-due-reminders')->everyMinute()->withoutOverlapping();
Schedule::call(fn () => DB::table('sent_reminders')->where('created_at', '<', now()->subDays(2))->delete())->name('prune-sent-reminders')->daily();
