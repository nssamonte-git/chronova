<?php

namespace App\Jobs;

use App\Models\Device;
use App\Push\Pusher;
use Illuminate\Contracts\Cache\Repository as Cache;
use Illuminate\Support\Facades\DB;

/**
 * Pushes task reminders (docs/push.md), run every minute by the scheduler. For each device with
 * reminders on: the user's synced tasks that are not done, in live projects, whose reminder time
 * (start minus the device's lead) came since the last run. Never more than a day late, never
 * twice to a device (sent_reminders). The phone words it, and skips it if its own copy of the
 * task says otherwise (an unsynced change), which its own alarm covers.
 */
final class SendDueReminders
{
    private const LEAD_MILLIS = ['at_start' => 0, '15_min' => 900_000, '1_hour' => 3_600_000, '1_day' => 86_400_000];

    private const MAX_LATE_MILLIS = 86_400_000;

    /** How far back the very first run looks (a fresh server never sends a backlog). */
    private const FIRST_LOOK_MILLIS = 120_000;

    private const CHECKED_KEY = 'push.reminders.checked_up_to';

    public function __construct(private readonly Pusher $pusher, private readonly Cache $cache) {}

    /** @return int how many reminders were sent */
    public function __invoke(?int $nowMillis = null): int
    {
        $now = $nowMillis ?? (int) floor(microtime(true) * 1000);
        $since = max((int) $this->cache->get(self::CHECKED_KEY, $now - self::FIRST_LOOK_MILLIS), $now - self::MAX_LATE_MILLIS);
        $this->cache->forever(self::CHECKED_KEY, $now);

        $sent = 0;
        $devices = Device::query()->where('reminder_lead', '!=', 'off')->get();
        foreach ($devices as $device) {
            $lead = self::LEAD_MILLIS[$device->reminder_lead] ?? null;
            if ($lead === null) {
                continue;
            }
            foreach ($this->dueTasks($device->user_id, $lead, $since, $now) as $task) {
                $at = $task->start_millis - $lead;
                $new = DB::table('sent_reminders')->insertOrIgnore([
                    'device_id' => $device->id, 'task_client_id' => $task->client_id, 'at_millis' => $at, 'created_at' => now(),
                ]);
                if ($new === 0) {
                    continue;
                }
                $sent += $this->pusher->send([$device], [
                    'type' => 'reminder',
                    'taskId' => $task->client_id,
                    'projectId' => $task->project_client_id,
                    'projectName' => (string) $task->project_name,
                    'title' => (string) $task->title,
                    'milestone' => $task->milestone ? 'true' : 'false',
                    'startMillis' => (string) $task->start_millis,
                    'atMillis' => (string) $at,
                ], urgent: true);
            }
        }

        return $sent;
    }

    private function dueTasks(string $userId, int $lead, int $since, int $now): iterable
    {
        return DB::table('tasks')
            ->join('projects', function ($join) {
                $join->on('projects.user_id', '=', 'tasks.user_id')->on('projects.client_id', '=', 'tasks.project_client_id');
            })
            ->where('tasks.user_id', $userId)
            ->where('tasks.deleted', false)
            ->where('projects.deleted', false)
            ->whereNotNull('tasks.start_millis')
            ->where(fn ($q) => $q->whereNull('tasks.progress')->orWhere('tasks.progress', '<', 100))
            ->where('tasks.start_millis', '>', $since + $lead)
            ->where('tasks.start_millis', '<=', $now + $lead)
            ->get(['tasks.client_id', 'tasks.project_client_id', 'tasks.title', 'tasks.start_millis', 'tasks.milestone', 'projects.name as project_name']);
    }
}
