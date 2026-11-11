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
