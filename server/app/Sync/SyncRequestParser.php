<?php

namespace App\Sync;

/**
 * Validates a sync request and copies only known fields (docs/sync-protocol.md,
 * "Field rules"). Returns ['cursor' => int, 'changes' => list] or ['error' => string].
 * A port of the reference parser, so error messages match the original backend.
 */
class SyncRequestParser
{
    public const MAX_CHANGES = 500;

    public const ENTITIES = ['project', 'task', 'dependency'];

    private const MAX_TEXT = 200;

    public const MAX_NOTES = 2000;

    private const ID = '/^[A-Za-z0-9_-]{1,64}$/';

    private const DATE = '/^\d{4}-\d{2}-\d{2}$/';

    public function parse(mixed $body): array
    {
        if (! is_array($body) || array_is_list($body) && $body !== []) {
            return ['error' => 'body must be an object'];
        }
        $cursor = 0;
        if (isset($body['cursor'])) {
            if (! is_string($body['cursor']) || ! preg_match('/^\d{1,15}$/', $body['cursor'])) {
                return ['error' => 'invalid cursor'];
            }
            $cursor = (int) $body['cursor'];
        }
        $raw = $body['changes'] ?? [];
        if (! is_array($raw) || ! array_is_list($raw)) {
            return ['error' => 'changes must be an array'];
        }
        if (count($raw) > self::MAX_CHANGES) {
            return ['error' => 'at most '.self::MAX_CHANGES.' changes per request'];
        }
        $changes = [];
        foreach ($raw as $index => $item) {
            $change = $this->change($item);
            if (is_string($change)) {
                return ['error' => "changes[$index]: $change"];
            }
            $changes[] = $change;
        }

        return ['cursor' => $cursor, 'changes' => $changes];
    }

    private function change(mixed $item): array|string
    {
        if (! is_array($item) || (array_is_list($item) && $item !== [])) {
            return 'must be an object';
        }
        $entity = $item['entity'] ?? null;
        if (! is_string($entity) || ! in_array($entity, self::ENTITIES, true)) {
            return 'unknown entity';
        }
        $id = $item['id'] ?? null;
        if (! is_string($id) || ! preg_match(self::ID, $id)) {
            return 'invalid id';
        }
        if (! is_bool($item['deleted'] ?? null)) {
            return 'deleted must be a boolean';
        }
        if (! $this->isMillis($item['updatedAt'] ?? null)) {
            return 'invalid updatedAt';
        }
        $change = ['entity' => $entity, 'id' => $id, 'deleted' => $item['deleted'], 'updatedAt' => $item['updatedAt']];
        if ($item['deleted']) {
            return $change; // tombstones carry no data
        }
        $data = $this->data($entity, $item['data'] ?? null);

        return is_string($data) ? $data : $change + ['data' => $data];
    }

    private function data(string $entity, mixed $data): array|string
    {
        if (! is_array($data) || (array_is_list($data) && $data !== [])) {
            return 'data is required unless deleted';
        }

        return match ($entity) {
            'project' => $this->project($data),
            'task' => $this->task($data),
            'dependency' => $this->dependency($data),
        };
    }

    private function project(array $data): array|string
    {
        ['name' => $name, 'startDate' => $start, 'endDate' => $end] = $data + ['name' => null, 'startDate' => null, 'endDate' => null];
        if (! $this->isText($name)) {
            return 'invalid name';
        }
        if (! is_string($start) || ! preg_match(self::DATE, $start)) {
            return 'invalid startDate';
        }
        if (! is_string($end) || ! preg_match(self::DATE, $end) || $end < $start) {
            return 'invalid endDate';
        }

        return ['name' => $name, 'startDate' => $start, 'endDate' => $end];
    }

    private function task(array $data): array|string
    {
        $data += ['projectId' => null, 'title' => null, 'startMillis' => null, 'endMillis' => null, 'color' => null, 'sortOrder' => null];
        if (! is_string($data['projectId']) || ! preg_match(self::ID, $data['projectId'])) {
            return 'invalid projectId';
        }
        if (! $this->isText($data['title'])) {
            return 'invalid title';
        }
        // Added later, so optional: an app that predates them leaves them out
        // (progress and notes then keep the stored value; see SyncService).
        if (array_key_exists('milestone', $data) && ! is_bool($data['milestone'])) {
            return 'invalid milestone';
        }
        $milestone = $data['milestone'] ?? false;
        $start = $data['startMillis'];
        $end = $data['endMillis'];
        if (! $this->isMillis($start) || ! $this->isMillis($end) || ($milestone ? $end !== $start : $end <= $start)) {
            return 'invalid times';
        }
        if (array_key_exists('progress', $data)) {
            $progress = $data['progress'];
            if (! is_int($progress) || $progress < 0 || $progress > 100 || ($milestone && $progress !== 0 && $progress !== 100)) {
                return 'invalid progress';
            }
        } elseif ($milestone) {
            return 'invalid progress'; // a milestone's progress (0 or 100) must be stated
        }
        if (array_key_exists('notes', $data) && (! is_string($data['notes']) || mb_strlen($data['notes']) > self::MAX_NOTES)) {
            return 'invalid notes';
        }
        if (! is_string($data['color']) || ! preg_match('/^[A-Z]{1,20}$/', $data['color'])) {
            return 'invalid color';
        }
        if (! is_int($data['sortOrder']) || $data['sortOrder'] < 0) {
            return 'invalid sortOrder';
        }

        return [
            'projectId' => $data['projectId'], 'title' => $data['title'], 'startMillis' => $data['startMillis'],
            'endMillis' => $data['endMillis'], 'color' => $data['color'], 'sortOrder' => $data['sortOrder'],
            'milestone' => $milestone,
        ] + array_intersect_key($data, ['progress' => true, 'notes' => true]);
    }

    private function dependency(array $data): array|string
    {
        $data += ['projectId' => null, 'predecessorId' => null, 'successorId' => null];
        foreach (['projectId', 'predecessorId', 'successorId'] as $key) {
            if (! is_string($data[$key]) || ! preg_match(self::ID, $data[$key])) {
                return 'invalid ids';
            }
        }
        if ($data['predecessorId'] === $data['successorId']) {
            return 'a task cannot depend on itself';
        }

        return ['projectId' => $data['projectId'], 'predecessorId' => $data['predecessorId'], 'successorId' => $data['successorId']];
    }

    private function isText(mixed $value): bool
    {
        return is_string($value) && trim($value) !== '' && mb_strlen($value) <= self::MAX_TEXT;
    }

    /** Integer ≥ 0 that JSON numbers (doubles) can represent exactly. */
    private function isMillis(mixed $value): bool
    {
        return is_int($value) && $value >= 0 && $value <= 9_007_199_254_740_991;
    }
}
