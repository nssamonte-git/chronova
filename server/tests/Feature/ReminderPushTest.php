<?php

use App\Jobs\SendDueReminders;
use App\Models\Device;
use App\Models\Project;
use App\Models\Task;
use App\Models\User;
use App\Push\PushSender;
use Illuminate\Console\Scheduling\Schedule;
use Illuminate\Support\Facades\Cache;

const REMINDER_MINUTE = 60_000;
const REMINDER_NOW = 1_800_000_000_000;

beforeEach(function () {
    $this->user = User::factory()->create();
    $this->project = Project::factory()->for($this->user)->create(['client_id' => 'p1', 'name' => 'Jnn']);
    $session = $this->user->createToken('android', ['sync'])->accessToken;
    $this->device = Device::create([
        'user_id' => $this->user->getKey(), 'personal_access_token_id' => $session->getKey(), 'token' => 'fcm-1',
        'platform' => 'android', 'time_zone' => 'Asia/Manila', 'reminder_lead' => '15_min', 'last_seen_at' => now(),
    ]);
    $this->sender = app(PushSender::class);
    // The last run was a minute ago.
    Cache::forever('push.reminders.checked_up_to', REMINDER_NOW - REMINDER_MINUTE);
});

function reminderTask(array $attributes): Task
{
    return Task::factory()->for(test()->user)->create($attributes + ['project_client_id' => 'p1']);
}

function runReminders(int $now = REMINDER_NOW): int
{
    return app(SendDueReminders::class)($now);
}

it('pushes the reminders that came due since the last run, worded by the phone', function () {
    // 15 minutes before: Build's reminder time is 30 s ago; Paint's is in 10 minutes.
    reminderTask(['client_id' => 'build', 'title' => 'Build', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE - 30_000]);
    reminderTask(['client_id' => 'paint', 'title' => 'Paint', 'start_millis' => REMINDER_NOW + 25 * REMINDER_MINUTE]);

    expect(runReminders())->toBe(1)
        ->and($this->sender->sent)->toHaveCount(1)
        ->and($this->sender->sent[0])->toMatchArray(['token' => 'fcm-1', 'urgent' => true, 'collapseKey' => null])
        ->and($this->sender->sent[0]['data'])->toBe([
            'type' => 'reminder', 'taskId' => 'build', 'projectId' => 'p1', 'projectName' => 'Jnn', 'title' => 'Build',
            'milestone' => 'false', 'startMillis' => (string) (REMINDER_NOW + 15 * REMINDER_MINUTE - 30_000), 'atMillis' => (string) (REMINDER_NOW - 30_000),
        ]);

    // Ten minutes later Paint's comes, once.
    expect(runReminders(REMINDER_NOW + 10 * REMINDER_MINUTE))->toBe(1)
        ->and(array_column(array_column($this->sender->sent, 'data'), 'taskId'))->toBe(['build', 'paint'])
        ->and(runReminders(REMINDER_NOW + 11 * REMINDER_MINUTE))->toBe(0);
});

it('never sends a reminder twice, even when a run looks back again', function () {
    reminderTask(['client_id' => 'build', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE]);
    runReminders();
    Cache::forever('push.reminders.checked_up_to', REMINDER_NOW - 5 * REMINDER_MINUTE);
    expect(runReminders())->toBe(0)->and($this->sender->sent)->toHaveCount(1);
});

it('skips done, deleted and unscheduled tasks, deleted projects, other users and devices with reminders off', function () {
    reminderTask(['client_id' => 'done', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE, 'progress' => 100]);
    reminderTask(['client_id' => 'gone', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE, 'deleted' => true]);
    reminderTask(['client_id' => 'tombstone', 'start_millis' => null]);
    Project::factory()->for($this->user)->create(['client_id' => 'p2', 'deleted' => true]);
    reminderTask(['client_id' => 'in-a-deleted-project', 'project_client_id' => 'p2', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE]);
    $other = User::factory()->create();
    Project::factory()->for($other)->create(['client_id' => 'p1']);
    Task::factory()->for($other)->create(['client_id' => 'someone-elses', 'project_client_id' => 'p1', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE]);

    expect(runReminders())->toBe(0);

    reminderTask(['client_id' => 'build', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE]);
    $this->device->update(['reminder_lead' => 'off']);
    expect(runReminders())->toBe(0)->and($this->sender->sent)->toBe([]);
});

it('uses each device\'s own lead, and a milestone is reminded too', function () {
    reminderTask(['client_id' => 'launch', 'title' => 'Launch', 'milestone' => true, 'start_millis' => REMINDER_NOW + 60 * REMINDER_MINUTE, 'end_millis' => REMINDER_NOW + 60 * REMINDER_MINUTE]);
    $this->device->update(['reminder_lead' => '1_hour']);
    expect(runReminders())->toBe(1)->and($this->sender->sent[0]['data']['milestone'])->toBe('true');
});

it('starts with a short look back and never sends a backlog over a day old', function () {
    Cache::forget('push.reminders.checked_up_to');
    reminderTask(['client_id' => 'old', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE - 10 * REMINDER_MINUTE]); // due 10 minutes ago
    reminderTask(['client_id' => 'fresh', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE - REMINDER_MINUTE]); // due a minute ago
    expect(runReminders())->toBe(1)->and($this->sender->sent[0]['data']['taskId'])->toBe('fresh');

    // After two days off, only the last day counts.
    Cache::forever('push.reminders.checked_up_to', REMINDER_NOW - 2 * 86_400_000);
    reminderTask(['client_id' => 'yesterday', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE - 23 * 60 * REMINDER_MINUTE]);
    reminderTask(['client_id' => 'two-days-ago', 'start_millis' => REMINDER_NOW + 15 * REMINDER_MINUTE - 47 * 60 * REMINDER_MINUTE]);
    expect(runReminders())->toBe(2) // "old" (10 minutes ago) and "yesterday"
        ->and(array_column(array_column($this->sender->sent, 'data'), 'taskId'))->not->toContain('two-days-ago');
});

it('is scheduled every minute', function () {
    $events = collect(app(Schedule::class)->events())->map->getSummaryForDisplay();
    expect($events)->toContain('send-due-reminders')->toContain('prune-sent-reminders');
});
