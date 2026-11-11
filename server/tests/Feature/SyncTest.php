<?php

/*
 * Conformance tests for docs/sync-protocol.md, ported from the reference suite
 * (tag serverless-backend-v1, backend/test/sync-core.test.ts).
 */

use App\Models\SyncEvent;
use App\Models\Task;
use App\Models\User;
use Illuminate\Support\Carbon;
use Laravel\Sanctum\Sanctum;

const NOW = 1_800_000_000_000;

beforeEach(function () {
    Carbon::setTestNow(Carbon::createFromTimestampMs(NOW));
    $this->alice = User::factory()->create();
    $this->bob = User::factory()->create();
});

function project(string $id, int $updatedAt, string $name = 'Launch'): array
{
    return ['entity' => 'project', 'id' => $id, 'deleted' => false, 'updatedAt' => $updatedAt,
        'data' => ['name' => $name, 'startDate' => '2026-03-02', 'endDate' => '2026-03-04']];
}

function task(string $id, int $updatedAt, string $title = 'Design'): array
{
    return ['entity' => 'task', 'id' => $id, 'deleted' => false, 'updatedAt' => $updatedAt,
        'data' => ['projectId' => 'p1', 'title' => $title, 'startMillis' => 1000, 'endMillis' => 2000, 'color' => 'BLUE', 'sortOrder' => 0]];
}

/** Syncs as $user and returns the decoded 200 response. */
function syncAs(User $user, array $body): array
{
    app('auth')->forgetGuards();
    Sanctum::actingAs($user, ['sync']);

    return test()->postJson('/v1/sync', $body)->assertOk()->json();
}

it('stores pushed changes and returns them with a cursor', function () {
    $body = syncAs($this->alice, ['cursor' => null, 'changes' => [project('p1', NOW - 10), task('t1', NOW - 5)]]);

    expect($body['results'])->toBe([
        ['entity' => 'project', 'id' => 'p1', 'status' => 'applied'],
        ['entity' => 'task', 'id' => 't1', 'status' => 'applied'],
    ])
        ->and(array_column($body['changes'], 'id'))->toBe(['p1', 't1'])
        ->and($body['changes'][1])->toBe(task('t1', NOW - 5))
        ->and($body['cursor'])->toBe('2')
        ->and($body['hasMore'])->toBeFalse();
});

it('pulls only what changed after the cursor', function () {
    $first = syncAs($this->alice, ['changes' => [project('p1', NOW - 10)]]);
    syncAs($this->alice, ['changes' => [task('t1', NOW - 5)]]); // another device
    $pull = syncAs($this->alice, ['cursor' => $first['cursor']]);
    expect(array_column($pull['changes'], 'id'))->toBe(['t1'])->and($pull['cursor'])->toBe('2');

    $again = syncAs($this->alice, ['cursor' => $pull['cursor']]);
    expect($again['changes'])->toBe([])->and($again['cursor'])->toBe('2');
});

it('last write wins by edit time, and ties keep the server copy', function () {
    syncAs($this->alice, ['changes' => [task('t1', NOW - 100, 'Server')]]);
    expect(syncAs($this->alice, ['changes' => [task('t1', NOW - 200, 'Older edit')]])['results'][0]['status'])->toBe('stale')
        ->and(syncAs($this->alice, ['changes' => [task('t1', NOW - 100, 'Same time')]])['results'][0]['status'])->toBe('stale')
        ->and(syncAs($this->alice, ['changes' => [task('t1', NOW - 50, 'Newer edit')]])['results'][0]['status'])->toBe('applied')
        ->and(Task::where('client_id', 't1')->value('title'))->toBe('Newer edit');
});

it('returns the server copy of a stale push even when it is behind the cursor', function () {
    syncAs($this->alice, ['changes' => [task('t1', NOW - 100, 'Server')]]);
    $pulled = syncAs($this->alice, ['cursor' => null]); // this device is now past seq 1
    $stale = syncAs($this->alice, ['cursor' => $pulled['cursor'], 'changes' => [task('t1', NOW - 500, 'Slow clock')]]);

    expect($stale['results'][0]['status'])->toBe('stale')
        ->and($stale['changes'])->toBe([task('t1', NOW - 100, 'Server')]);
});

it('clamps future edit times to the server clock', function () {
    syncAs($this->alice, ['changes' => [task('t1', NOW + 86_400_000, 'Clock ahead')]]);
    expect(Task::where('client_id', 't1')->value('client_updated_at'))->toBe(NOW);

    Carbon::setTestNow(Carbon::createFromTimestampMs(NOW + 1000));
    expect(syncAs($this->alice, ['changes' => [task('t1', NOW + 1000, 'Honest')]])['results'][0]['status'])->toBe('applied');
});

it('stores tombstones without data, and removes the deleted content', function () {
    syncAs($this->alice, ['changes' => [task('t1', NOW - 10)]]);
    $body = syncAs($this->alice, ['changes' => [['entity' => 'task', 'id' => 't1', 'deleted' => true, 'updatedAt' => NOW - 5]]]);

    expect($body['results'][0]['status'])->toBe('applied')
        ->and(end($body['changes']))->toBe(['entity' => 'task', 'id' => 't1', 'deleted' => true, 'updatedAt' => NOW - 5]);
    $row = Task::where('client_id', 't1')->sole();
    expect($row->deleted)->toBeTrue()->and($row->title)->toBeNull()->and($row->project_client_id)->toBeNull();
});

it('stores a tombstone for a record the server never saw', function () {
    syncAs($this->alice, ['changes' => [['entity' => 'project', 'id' => 'gone', 'deleted' => true, 'updatedAt' => NOW - 5]]]);
    $pull = syncAs($this->bob, ['cursor' => null]);
    expect($pull['changes'])->toBe([]); // and nothing leaks to another user
    expect(syncAs($this->alice, ['cursor' => null])['changes'][0]['deleted'])->toBeTrue();
});

it('keeps users apart, even with the same client ids', function () {
    syncAs($this->alice, ['changes' => [project('sample-project', NOW - 10, 'Alice')]]);
    expect(syncAs($this->bob, ['cursor' => null])['changes'])->toBe([]);

    syncAs($this->bob, ['changes' => [project('sample-project', NOW - 20, 'Bob')]]);
    expect(syncAs($this->bob, ['cursor' => null])['changes'][0]['data']['name'])->toBe('Bob')
        ->and(syncAs($this->alice, ['cursor' => null])['changes'][0]['data']['name'])->toBe('Alice');
});

it('pages large pulls', function () {
    $changes = array_map(fn ($i) => task("t$i", NOW - 1), range(0, 509));
    syncAs($this->alice, ['changes' => array_slice($changes, 0, 500)]);
    syncAs($this->alice, ['changes' => array_slice($changes, 500)]);

    $page1 = syncAs($this->alice, ['cursor' => null]);
    expect($page1['changes'])->toHaveCount(500)->and($page1['hasMore'])->toBeTrue();
    $page2 = syncAs($this->alice, ['cursor' => $page1['cursor']]);
    expect($page2['changes'])->toHaveCount(10)->and($page2['hasMore'])->toBeFalse();
});

it('pages across record types in sequence order', function () {
    syncAs($this->alice, ['changes' => [project('p1', NOW - 3), task('t1', NOW - 2)]]);
    syncAs($this->alice, ['changes' => [project('p1', NOW - 1, 'Renamed')]]);
    $pull = syncAs($this->alice, ['cursor' => null]);
    expect(array_map(fn ($c) => $c['entity'].':'.$c['id'], $pull['changes']))->toBe(['task:t1', 'project:p1'])
        ->and($pull['cursor'])->toBe('3');
});

it('rejects invalid requests with 400 and stores nothing', function () {
    Sanctum::actingAs($this->alice, ['sync']);
    $this->postJson('/v1/sync', ['changes' => [array_merge(task('t1', NOW), ['data' => ['title' => 'x']])]])
        ->assertStatus(400)->assertJsonPath('error', 'changes[0]: invalid projectId');
    expect(Task::count())->toBe(0)->and($this->alice->fresh()->sync_seq)->toBe(0);
});

it('rejects malformed input', function (array $body, string $error) {
    Sanctum::actingAs($this->alice, ['sync']);
    $this->postJson('/v1/sync', $body)->assertStatus(400)->assertJsonPath('error', $error);
})->with([
    'cursor' => [['cursor' => 'abc'], 'invalid cursor'],
    'changes type' => [['changes' => ['x' => 1]], 'changes must be an array'],
    'too many' => [['changes' => array_fill(0, 501, ['entity' => 'task'])], 'at most 500 changes per request'],
    'entity' => [['changes' => [['entity' => 'user', 'id' => 'a', 'deleted' => false, 'updatedAt' => 1]]], 'changes[0]: unknown entity'],
    'id' => [['changes' => [['entity' => 'task', 'id' => '../x', 'deleted' => false, 'updatedAt' => 1]]], 'changes[0]: invalid id'],
    'updatedAt' => [['changes' => [['entity' => 'task', 'id' => 'a', 'deleted' => false, 'updatedAt' => -1]]], 'changes[0]: invalid updatedAt'],
    'deleted' => [['changes' => [['entity' => 'task', 'id' => 'a', 'deleted' => 'yes', 'updatedAt' => 1]]], 'changes[0]: deleted must be a boolean'],
    'dates' => [['changes' => [['entity' => 'project', 'id' => 'p', 'deleted' => false, 'updatedAt' => 1,
        'data' => ['name' => 'A', 'startDate' => '2026-03-04', 'endDate' => '2026-03-02']]]], 'changes[0]: invalid endDate'],
    'times' => [['changes' => [['entity' => 'task', 'id' => 't', 'deleted' => false, 'updatedAt' => 1,
        'data' => ['projectId' => 'p', 'title' => 'T', 'startMillis' => 2000, 'endMillis' => 1000, 'color' => 'BLUE', 'sortOrder' => 0]]]], 'changes[0]: invalid times'],
    'blank name' => [['changes' => [['entity' => 'project', 'id' => 'p', 'deleted' => false, 'updatedAt' => 1,
        'data' => ['name' => '   ', 'startDate' => '2026-03-02', 'endDate' => '2026-03-02']]]], 'changes[0]: invalid name'],
    'self link' => [['changes' => [['entity' => 'dependency', 'id' => 'd', 'deleted' => false, 'updatedAt' => 1,
        'data' => ['projectId' => 'p', 'predecessorId' => 'a', 'successorId' => 'a']]]], 'changes[0]: a task cannot depend on itself'],
]);

it('accepts an empty pull', function () {
    expect(syncAs($this->alice, []))->toBe(['results' => [], 'changes' => [], 'cursor' => '0', 'hasMore' => false]);
});

it('drops unknown fields', function () {
    $sneaky = project('p1', NOW - 1);
    $sneaky['data']['owner'] = 'mallory';
    $sneaky['extra'] = true;
    $body = syncAs($this->alice, ['changes' => [$sneaky]]);
    expect($body['changes'][0])->toBe(project('p1', NOW - 1));
});

it('keeps text exactly as sent (no input trimming)', function () {
    $body = syncAs($this->alice, ['changes' => [task('t1', NOW - 1, '  Spaced  ')]]);
    expect($body['changes'][0]['data']['title'])->toBe('  Spaced  ');
});

it('records counts, not content, for the health dashboard', function () {
    syncAs($this->alice, ['changes' => [task('t1', NOW - 100)]]);
    syncAs($this->alice, ['changes' => [task('t1', NOW - 200)]]); // stale
    $events = SyncEvent::orderBy('id')->get();
    expect($events)->toHaveCount(2)
        ->and($events[1]->only(['status', 'pushed', 'stale']))->toBe(['status' => 200, 'pushed' => 1, 'stale' => 1])
        ->and($this->alice->fresh()->last_synced_at)->not->toBeNull();
});

it('requires a token with the sync ability from an enabled account', function () {
    $this->postJson('/v1/sync', [])->assertUnauthorized();
    Sanctum::actingAs($this->alice, ['other']);
    $this->postJson('/v1/sync', [])->assertForbidden();
    $this->alice->forceFill(['disabled_at' => now()])->save();
    app('auth')->forgetGuards();
    Sanctum::actingAs($this->alice->fresh(), ['sync']);
    $this->postJson('/v1/sync', [])->assertForbidden()->assertJsonPath('error', 'account_disabled');
});
