<?php

use App\Filament\Resources\Users\Pages\ListUsers;
use App\Filament\Resources\Users\Pages\ViewUser;
use App\Models\AuditLog;
use App\Models\Project;
use App\Models\Staff;
use App\Models\SyncEvent;
use App\Models\Task;
use App\Models\User;
use Filament\Actions\Testing\TestAction;
use Filament\Facades\Filament;
use Livewire\Livewire;

beforeEach(function () {
    Filament::setCurrentPanel('admin');
    $this->ana = User::factory()->create(['email' => 'ana@example.com']);
    Project::factory()->for($this->ana)->count(2)->create();
    Project::factory()->for($this->ana)->create(['deleted' => true]);
    Task::factory()->for($this->ana)->create(['title' => 'Secret roadmap item']);
    $this->ana->createToken('android', ['sync']);
});

it('lists and searches accounts with counts for support staff', function () {
    $this->actingAs(staffMember(), 'staff');
    User::factory()->create(['email' => 'ben@example.com']);

    Livewire::test(ListUsers::class)
        ->assertCanSeeTableRecords([$this->ana])
        ->searchTable('ana@')
        ->assertCanSeeTableRecords([$this->ana])
        ->assertCountTableRecords(1)
        ->assertTableColumnStateSet('live_projects_count', 2, $this->ana) // the tombstone isn't counted
        ->assertTableColumnStateSet('devices_count', 1, $this->ana);
});

it('shows account metadata but never project or task content', function () {
    $event = new SyncEvent(['status' => 200, 'pushed' => 3, 'pulled' => 5, 'stale' => 1, 'duration_ms' => 12]);
    $event->user_id = $this->ana->id;
    $event->save();
    $this->actingAs(staffMember(), 'staff')
        ->get("/admin/users/{$this->ana->id}")
        ->assertOk()
        ->assertSee('ana@example.com')
        ->assertSee('pushed 3, pulled 5, stale 1')
        ->assertDontSee('Secret roadmap item');
});

it('lets support disable, enable and sign a user out everywhere, all audited', function () {
    $support = staffMember();
    $this->actingAs($support, 'staff');

    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])->callAction('disable');
    expect($this->ana->fresh()->isDisabled())->toBeTrue();
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])->callAction('enable');
    expect($this->ana->fresh()->isDisabled())->toBeFalse();
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])->callAction('signOutEverywhere');
    expect($this->ana->tokens()->count())->toBe(0);

    expect(AuditLog::orderBy('id')->get(['staff_id', 'action', 'subject_user_id'])->toArray())->toBe([
        ['staff_id' => $support->id, 'action' => 'user.disabled', 'subject_user_id' => $this->ana->id],
        ['staff_id' => $support->id, 'action' => 'user.enabled', 'subject_user_id' => $this->ana->id],
        ['staff_id' => $support->id, 'action' => 'user.signed_out_everywhere', 'subject_user_id' => $this->ana->id],
    ]);
});

it('does not let support delete accounts', function () {
    $this->actingAs(staffMember(), 'staff');
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])->assertActionHidden('delete');
    Livewire::test(ListUsers::class)->assertActionHidden(TestAction::make('delete')->table($this->ana));
});

it('lets admins delete only after typing the email and giving a reason', function () {
    $admin = staffMember(Staff::ROLE_ADMIN);
    $this->actingAs($admin, 'staff');

    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])
        ->callAction('delete', ['confirmation' => 'wrong@example.com', 'reason' => 'GDPR request #7'])
        ->assertHasFormErrors(['confirmation']);
    expect(User::find($this->ana->id))->not->toBeNull();

    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])
        ->callAction('delete', ['confirmation' => 'ana@example.com', 'reason' => 'GDPR request #7'])
        ->assertHasNoFormErrors()
        ->assertRedirect('/admin/users');

    expect(User::find($this->ana->id))->toBeNull()
        ->and(Project::count() + Task::count())->toBe(0)
        ->and($this->ana->tokens()->count())->toBe(0);
    $log = AuditLog::sole();
    expect($log->only(['action', 'subject_user_id', 'reason']))->toBe(['action' => 'user.deleted', 'subject_user_id' => $this->ana->id, 'reason' => 'GDPR request #7'])
        ->and(json_encode($log->details))->not->toContain('ana@example.com');
});

it('has no create or edit pages for accounts', function () {
    $this->actingAs(staffMember(Staff::ROLE_ADMIN), 'staff')->get('/admin/users/create')->assertNotFound();
    expect(Route::has('filament.admin.resources.users.edit'))->toBeFalse();
});
