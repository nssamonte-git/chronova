<?php

use App\Models\Staff;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/*
 * Feature tests run against the real Postgres test database (chronova_test),
 * not SQLite: the sync protocol relies on Postgres upserts and row locks.
 */
pest()->extend(TestCase::class)
    ->use(RefreshDatabase::class)
    ->in('Feature');

/** A staff member who has already enrolled an authenticator app. */
function staffMember(string $role = Staff::ROLE_SUPPORT, array $attributes = []): Staff
{
    $staff = Staff::factory()->create(['role' => $role] + $attributes);
    $staff->saveAppAuthenticationSecret('JBSWY3DPEHPK3PXP');

    return $staff->fresh();
}

/** A valid /v1/voice/interpret context (docs/voice-api.md), shared by the voice tests. */
function voiceContext(array $overrides = []): array
{
    return array_merge([
        'projectId' => 'p1',
        'projectName' => 'Launch',
        'startDate' => '2026-11-12',
        'endDate' => '2026-11-18',
        'timeZone' => 'America/Los_Angeles',
        'today' => '2026-11-12',
        'now' => '2026-11-12T10:15',
        'revision' => 'r17',
        'tasks' => [
            ['id' => 't1', 'title' => 'Design', 'start' => '2026-11-12T09:00', 'end' => '2026-11-12T11:00', 'predecessorIds' => []],
            ['id' => 't2', 'title' => 'Build', 'start' => '2026-11-12T11:00', 'end' => '2026-11-13T17:00', 'predecessorIds' => ['t1']],
            ['id' => 't3', 'title' => 'Review', 'start' => '2026-11-16T09:00', 'end' => '2026-11-16T10:00', 'predecessorIds' => ['t2']],
        ],
    ], $overrides);
}
