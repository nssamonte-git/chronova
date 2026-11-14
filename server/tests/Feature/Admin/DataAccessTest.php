<?php

use App\Filament\Resources\Users\Pages\UserData;
use App\Filament\Resources\Users\Pages\ViewUser;
use App\Models\AuditLog;
use App\Models\DataAccessGrant;
use App\Models\Project;
use App\Models\Staff;
use App\Models\Task;
use App\Models\User;
use Filament\Facades\Filament;
use Livewire\Livewire;

beforeEach(function () {
    Filament::setCurrentPanel('admin');
    $this->ana = User::factory()->create(['email' => 'ana@example.com']);
    Project::factory()->for($this->ana)->create(['client_id' => 'p1', 'name' => 'Website launch']);
    Task::factory()->for($this->ana)->create(['project_client_id' => 'p1', 'title' => 'Design review',
        'start_millis' => 1_772_442_000_000, 'end_millis' => 1_772_449_200_000]);
    Task::factory()->for($this->ana)->create(['project_client_id' => 'p1', 'title' => 'Deleted thing', 'deleted' => true]);
    $this->support = staffMember();
    $this->actingAs($this->support, 'staff');
});

function grantFor(Staff $staff, User $user, int $minutes = 30): DataAccessGrant
{
    return DataAccessGrant::create(['staff_id' => $staff->id, 'user_id' => $user->id, 'reason' => 'Ticket #42: tasks missing', 'expires_at' => now()->addMinutes($minutes)]);
}

it('refuses the data page without a grant', function () {
    $this->get("/admin/users/{$this->ana->id}/data")->assertForbidden();
    expect(AuditLog::count())->toBe(0);
});

it('needs a real reason to grant access', function () {
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])
        ->callAction('requestDataAccess', ['reason' => 'because'])
        ->assertHasFormErrors(['reason']);
    expect(DataAccessGrant::count())->toBe(0);
});

it('grants 30 minutes of access with an audited reason and opens the data page', function () {
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])
        ->callAction('requestDataAccess', ['reason' => 'Ticket #42: tasks missing after sync'])
        ->assertHasNoFormErrors()
        ->assertRedirect("/admin/users/{$this->ana->id}/data");

    $grant = DataAccessGrant::sole();
    expect($grant->staff_id)->toBe($this->support->id)
        ->and((int) round(now()->diffInMinutes($grant->expires_at)))->toBe(30);
    expect(AuditLog::sole()->only(['action', 'subject_user_id', 'reason']))
        ->toBe(['action' => 'user.data_access_granted', 'subject_user_id' => $this->ana->id, 'reason' => 'Ticket #42: tasks missing after sync']);
});

it('shows live projects and tasks read-only, and audits every view', function () {
    grantFor($this->support, $this->ana);

    $this->get("/admin/users/{$this->ana->id}/data")->assertOk()
        ->assertSee('Website launch')
        ->assertSee('Design review — Mon 2 Mar 17:00 → Mon 2 Mar 19:00 (PH time)')
        ->assertDontSee('Deleted thing');
    $this->get("/admin/users/{$this->ana->id}/data")->assertOk();

    expect(AuditLog::where('action', 'user.data_viewed')->count())->toBe(2);
});

it('does not share a grant with other staff', function () {
    grantFor(staffMember(), $this->ana);
    $this->get("/admin/users/{$this->ana->id}/data")->assertForbidden();
});

it('stops working when the grant expires', function () {
    grantFor($this->support, $this->ana);
    $this->travel(31)->minutes();
    $this->get("/admin/users/{$this->ana->id}/data")->assertForbidden();
});

it('can be ended early', function () {
    grantFor($this->support, $this->ana);
    Livewire::test(UserData::class, ['record' => $this->ana->getRouteKey()])
        ->callAction('endAccess')
        ->assertRedirect("/admin/users/{$this->ana->id}");

    expect(DataAccessGrant::active()->count())->toBe(0)
        ->and(AuditLog::where('action', 'user.data_access_ended')->count())->toBe(1);
    $this->get("/admin/users/{$this->ana->id}/data")->assertForbidden();
});

it('offers "View data" only while a grant is active', function () {
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])
        ->assertActionHidden('viewData')
        ->assertActionVisible('requestDataAccess');
    grantFor($this->support, $this->ana);
    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])
        ->assertActionVisible('viewData')
        ->assertActionHidden('requestDataAccess');
});
