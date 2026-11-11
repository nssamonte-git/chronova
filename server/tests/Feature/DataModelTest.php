<?php

use App\Models\AuditLog;
use App\Models\DataAccessGrant;
use App\Models\Dependency;
use App\Models\Project;
use App\Models\Staff;
use App\Models\SyncEvent;
use App\Models\Task;
use App\Models\User;
use Illuminate\Database\QueryException;

it('stores emails lower-cased and rejects case-only duplicates', function () {
    $user = User::factory()->create(['email' => '  Ana@Example.COM ']);
    expect($user->fresh()->email)->toBe('ana@example.com');

    // Bypass the model mutator to prove the database itself enforces it.
    expect(fn () => DB::table('users')->insert([
        'id' => (string) Str::uuid(), 'email' => 'ANA@example.com', 'password' => 'x',
        'created_at' => now(), 'updated_at' => now(),
    ]))->toThrow(QueryException::class);
});

it('uses uuids for users and never exposes the password', function () {
    $user = User::factory()->create();
    expect(Str::isUuid($user->id))->toBeTrue()
        ->and($user->toArray())->not->toHaveKey('password');
});

it('allows the same client id for different users but not twice for one user', function () {
    [$ana, $ben] = User::factory()->count(2)->create();
    Project::factory()->for($ana)->create(['client_id' => 'sample-project']);
    Project::factory()->for($ben)->create(['client_id' => 'sample-project']);
    expect(Project::where('client_id', 'sample-project')->count())->toBe(2);

    expect(fn () => Project::factory()->for($ana)->create(['client_id' => 'sample-project']))
        ->toThrow(QueryException::class);
});

it('finds a project\'s tasks by client id within the same user only', function () {
    [$ana, $ben] = User::factory()->count(2)->create();
    $project = Project::factory()->for($ana)->create(['client_id' => 'p1']);
    Task::factory()->for($ana)->count(2)->create(['project_client_id' => 'p1']);
    Task::factory()->for($ben)->create(['project_client_id' => 'p1']); // same client id, other user

    expect($project->tasks()->count())->toBe(2);
});

it('deletes a user\'s synced records with the user but keeps anonymous sync metrics', function () {
    $user = User::factory()->create();
    Project::factory()->for($user)->create();
    Task::factory()->for($user)->create();
    Dependency::factory()->for($user)->create();
    $event = new SyncEvent(['status' => 200, 'duration_ms' => 12]);
    $event->user_id = $user->id;
    $event->save();

    $user->delete();

    expect(Project::count() + Task::count() + Dependency::count())->toBe(0)
        ->and($event->fresh()->user_id)->toBeNull();
});

it('keeps audit logs immutable and independent of the user they describe', function () {
    $staff = Staff::factory()->admin()->create();
    $user = User::factory()->create();
    $log = AuditLog::create(['staff_id' => $staff->id, 'action' => 'user.disable', 'subject_user_id' => $user->id, 'ip' => '127.0.0.1']);

    expect(fn () => $log->update(['action' => 'tampered']))->toThrow(LogicException::class)
        ->and(fn () => $log->delete())->toThrow(LogicException::class);

    $user->delete();
    expect($log->fresh()->subject_user_id)->toBe($user->id);
});

it('treats only unexpired data access grants as active', function () {
    $staff = Staff::factory()->create();
    $user = User::factory()->create();
    DataAccessGrant::create(['staff_id' => $staff->id, 'user_id' => $user->id, 'reason' => 'Sync issue #12', 'expires_at' => now()->addMinutes(30)]);
    DataAccessGrant::create(['staff_id' => $staff->id, 'user_id' => $user->id, 'reason' => 'Old', 'expires_at' => now()->subMinute()]);

    expect(DataAccessGrant::active()->count())->toBe(1);
});

it('separates staff roles', function () {
    expect(Staff::factory()->admin()->create()->isAdmin())->toBeTrue()
        ->and(Staff::factory()->create()->isAdmin())->toBeFalse();
});
