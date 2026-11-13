<?php

namespace App\Sync;

use App\Models\Dependency;
use App\Models\Project;
use App\Models\SyncRecord;
use App\Models\Task;
use App\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * The sync protocol (docs/sync-protocol.md) on Postgres:
 * - one transaction per request, with the user's row locked FOR UPDATE, so a user's
 *   syncs run one at a time and sequence numbers never interleave (rule 5);
 * - each push is an upsert that only replaces an older copy (last write wins, ties
 *   keep the server copy — rule 1), with future edit times clamped (rule 2);
 * - stale pushes add the winning copy to the response (rule 3);
 * - pulls return records with seq > cursor, oldest first, 500 per page (rule 4).
 */
class SyncService
{
    public const PAGE_SIZE = 500;

    /** @var array<string, class-string<SyncRecord>> */
    private const MODELS = ['project' => Project::class, 'task' => Task::class, 'dependency' => Dependency::class];

    /**
     * Columns for fields added after the first release: a push that leaves the field out
     * (an older app) keeps the stored value instead of clearing it; a tombstone still clears it.
     */
    private const KEEP_WHEN_ABSENT = ['task' => ['progress', 'notes']];

    /**
     * @param  array{cursor: int, changes: list<array>}  $request  as returned by SyncRequestParser
     * @return array{response: array, stale: int}
     */
    public function sync(User $user, array $request, int $nowMillis): array
    {
        return DB::transaction(function () use ($user, $request, $nowMillis) {
            $seq = (int) User::query()->whereKey($user->getKey())->lockForUpdate()->value('sync_seq');
            $results = [];
            $winners = [];
            foreach ($request['changes'] as $change) {
                $change['updatedAt'] = min($change['updatedAt'], $nowMillis);
                $applied = $this->upsertIfNewer($user, $change, $seq + 1);
                if ($applied) {
                    $seq++;
                } else {
                    $current = $this->current($user, $change['entity'], $change['id']);
                    if ($current !== null) {
                        $winners[] = $current;
                    }
                }
                $results[] = ['entity' => $change['entity'], 'id' => $change['id'], 'status' => $applied ? 'applied' : 'stale'];
            }
            User::query()->whereKey($user->getKey())->update(['sync_seq' => $seq, 'last_synced_at' => now()]);

            [$page, $hasMore] = $this->changesSince($user, $request['cursor']);
            $cursor = $page === [] ? $request['cursor'] : end($page)['seq'];
            $inPage = array_flip(array_map(fn ($c) => $c['entity'].'#'.$c['id'], $page));
            $extra = array_filter($winners, fn ($c) => ! isset($inPage[$c['entity'].'#'.$c['id']]));

            return [
                'response' => [
                    'results' => $results,
                    'changes' => array_map($this->withoutSeq(...), [...$page, ...array_values($extra)]),
                    'cursor' => (string) $cursor,
                    'hasMore' => $hasMore,
                ],
                'stale' => count(array_filter($results, fn ($r) => $r['status'] === 'stale')),
            ];
        });
    }

    /** Inserts or replaces the record only if the stored copy is older. Returns whether it was written. */
    private function upsertIfNewer(User $user, array $change, int $seq): bool
    {
        $model = self::MODELS[$change['entity']];
        $table = (new $model)->getTable();
        // Tombstones clear the entity fields: deleted content isn't kept on the server.
        $fields = $change['deleted'] ? array_fill_keys(array_keys($this->columns($change['entity'], [])), null)
            : $this->columns($change['entity'], $change['data']);
        $now = Carbon::now();
        $row = [
            'user_id' => $user->getKey(),
            'client_id' => $change['id'],
            'deleted' => $change['deleted'],
            'client_updated_at' => $change['updatedAt'],
            'seq' => $seq,
            ...$fields,
            'created_at' => $now,
            'updated_at' => $now,
        ];
        $columns = array_keys($row);
        $updates = array_diff($columns, ['user_id', 'client_id', 'created_at']);
        $keep = self::KEEP_WHEN_ABSENT[$change['entity']] ?? [];
        $set = fn ($c) => in_array($c, $keep, true)
            ? "$c = case when excluded.deleted then null else coalesce(excluded.$c, $table.$c) end"
            : "$c = excluded.$c";
        $sql = sprintf(
            'insert into %1$s (%2$s) values (%3$s) on conflict (user_id, client_id) do update set %4$s '.
            'where %1$s.client_updated_at < excluded.client_updated_at returning id',
            $table,
            implode(', ', $columns),
            implode(', ', array_fill(0, count($columns), '?')),
            implode(', ', array_map($set, $updates)),
        );

        return DB::select($sql, array_values($row)) !== [];
    }

    /** @return array<string, mixed> database columns for an entity's wire fields */
    private function columns(string $entity, array $data): array
    {
        return match ($entity) {
            'project' => [
                'name' => $data['name'] ?? null,
                'start_date' => $data['startDate'] ?? null,
                'end_date' => $data['endDate'] ?? null,
            ],
            'task' => [
                'project_client_id' => $data['projectId'] ?? null,
                'title' => $data['title'] ?? null,
                'start_millis' => $data['startMillis'] ?? null,
                'end_millis' => $data['endMillis'] ?? null,
                'color' => $data['color'] ?? null,
                'sort_order' => $data['sortOrder'] ?? null,
                'progress' => $data['progress'] ?? null,
                'notes' => $data['notes'] ?? null,
                'milestone' => $data['milestone'] ?? null,
            ],
            'dependency' => [
                'project_client_id' => $data['projectId'] ?? null,
                'predecessor_client_id' => $data['predecessorId'] ?? null,
                'successor_client_id' => $data['successorId'] ?? null,
            ],
        };
    }

    private function current(User $user, string $entity, string $clientId): ?array
    {
        $record = self::MODELS[$entity]::query()->where('user_id', $user->getKey())->where('client_id', $clientId)->first();

        return $record === null ? null : $this->toChange($record);
    }

    /** @return array{0: list<array>, 1: bool} the next page after the cursor and whether more follow */
    private function changesSince(User $user, int $cursor): array
    {
        $all = [];
        foreach (self::MODELS as $model) {
            // Each table contributes at most one page + 1, which is enough to find the overall page.
            $rows = $model::query()->where('user_id', $user->getKey())->where('seq', '>', $cursor)
                ->orderBy('seq')->limit(self::PAGE_SIZE + 1)->get();
            foreach ($rows as $row) {
                $all[] = $this->toChange($row);
            }
        }
        usort($all, fn ($a, $b) => $a['seq'] <=> $b['seq']);

        return [array_slice($all, 0, self::PAGE_SIZE), count($all) > self::PAGE_SIZE];
    }

    private function toChange(SyncRecord $record): array
    {
        $change = [
            'entity' => $record::entity(),
            'id' => $record->client_id,
            'deleted' => $record->deleted,
            'updatedAt' => $record->client_updated_at,
            'seq' => $record->seq,
        ];
        if ($record->deleted) {
            return $change;
        }

        return $change + ['data' => match (true) {
            $record instanceof Project => [
                'name' => $record->name,
                'startDate' => $record->start_date?->format('Y-m-d'),
                'endDate' => $record->end_date?->format('Y-m-d'),
            ],
            $record instanceof Task => [
                'projectId' => $record->project_client_id,
                'title' => $record->title,
                'startMillis' => $record->start_millis,
                'endMillis' => $record->end_millis,
                'color' => $record->color,
                'sortOrder' => $record->sort_order,
                'progress' => $record->progress ?? 0,
                'notes' => $record->notes ?? '',
                'milestone' => $record->milestone ?? false,
            ],
            $record instanceof Dependency => [
                'projectId' => $record->project_client_id,
                'predecessorId' => $record->predecessor_client_id,
                'successorId' => $record->successor_client_id,
            ],
        }];
    }

    private function withoutSeq(array $change): array
    {
        unset($change['seq']);

        return $change;
    }
}
