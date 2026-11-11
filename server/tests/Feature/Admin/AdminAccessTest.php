<?php

use App\Filament\Resources\Staff\Pages\CreateStaff;
use App\Filament\Resources\Staff\Pages\EditStaff;
use App\Filament\Resources\Staff\Pages\ListStaff;
use App\Models\AuditLog;
use App\Models\Staff;
use App\Models\User;
use Filament\Actions\Testing\TestAction;
use Filament\Facades\Filament;
use Illuminate\Support\Facades\Hash;
use Livewire\Livewire;

/** A staff member who has already enrolled an authenticator app. */
function staffMember(string $role = Staff::ROLE_SUPPORT, array $attributes = []): Staff
{
    $staff = Staff::factory()->create(['role' => $role] + $attributes);
    $staff->saveAppAuthenticationSecret('JBSWY3DPEHPK3PXP');

    return $staff->fresh();
}

beforeEach(fn () => Filament::setCurrentPanel('admin'));

describe('panel access', function () {
    it('sends guests to the staff login', function () {
        $this->get('/admin')->assertRedirect('/admin/login');
    });

    it('never lets an app user in, even when signed in on the web guard', function () {
        $this->actingAs(User::factory()->create(), 'web')->get('/admin')->assertRedirect('/admin/login');
    });

    it('requires staff to enrol an authenticator app before anything else', function () {
        $staff = Staff::factory()->admin()->create();
        $this->actingAs($staff, 'staff')->get('/admin')->assertRedirect('/admin/multi-factor-authentication/set-up');
        $this->actingAs($staff, 'staff')->get('/admin/staff')->assertRedirect('/admin/multi-factor-authentication/set-up');
    });

    it('lets enrolled staff in', function () {
        $this->actingAs(staffMember(), 'staff')->get('/admin')->assertOk();
    });

    it('shuts out disabled staff', function () {
        $this->actingAs(staffMember(attributes: ['disabled_at' => now()]), 'staff')->get('/admin')->assertForbidden();
    });

    it('keeps MFA secrets encrypted at rest', function () {
        $staff = staffMember();
        $raw = DB::table('staff')->where('id', $staff->id)->value('app_authentication_secret');
        expect($raw)->not->toBe('JBSWY3DPEHPK3PXP')->and($staff->getAppAuthenticationSecret())->toBe('JBSWY3DPEHPK3PXP');
    });
});

describe('roles', function () {
    it('limits staff management and the audit log to admins', function () {
        $support = staffMember();
        $this->actingAs($support, 'staff')->get('/admin/staff')->assertForbidden();
        $this->actingAs($support, 'staff')->get('/admin/audit-logs')->assertForbidden();

        $admin = staffMember(Staff::ROLE_ADMIN);
        $this->actingAs($admin, 'staff')->get('/admin/staff')->assertOk();
        $this->actingAs($admin, 'staff')->get('/admin/audit-logs')->assertOk();
    });

    it('has no way to create, edit or delete audit entries', function () {
        $this->actingAs(staffMember(Staff::ROLE_ADMIN), 'staff')->get('/admin/audit-logs/create')->assertNotFound();
        expect(Route::has('filament.admin.resources.audit-logs.edit'))->toBeFalse();
    });
});

describe('managing staff', function () {
    it('creates a staff member with a hashed password and audits it', function () {
        $admin = staffMember(Staff::ROLE_ADMIN);
        $this->actingAs($admin, 'staff');

        Livewire::test(CreateStaff::class)
            ->fillForm(['name' => 'Sam', 'email' => 'Sam@Example.com', 'role' => Staff::ROLE_SUPPORT, 'password' => 'Long-password-123'])
            ->call('create')
            ->assertHasNoFormErrors();

        $sam = Staff::where('email', 'sam@example.com')->sole();
        expect(Hash::check('Long-password-123', $sam->password))->toBeTrue()
            ->and($sam->hasMfa())->toBeFalse()
            ->and(AuditLog::sole()->only(['staff_id', 'action']))->toBe(['staff_id' => $admin->id, 'action' => 'staff.created']);
    });

    it('rejects weak staff passwords', function () {
        $this->actingAs(staffMember(Staff::ROLE_ADMIN), 'staff');
        Livewire::test(CreateStaff::class)
            ->fillForm(['name' => 'Sam', 'email' => 'sam@example.com', 'role' => Staff::ROLE_SUPPORT, 'password' => 'short'])
            ->call('create')
            ->assertHasFormErrors(['password']);
        expect(Staff::where('email', 'sam@example.com')->exists())->toBeFalse();
    });

    it('disables, enables and resets MFA for others, with an audit entry each', function () {
        $this->actingAs(staffMember(Staff::ROLE_ADMIN), 'staff');
        $sam = staffMember();

        Livewire::test(ListStaff::class)->callAction(TestAction::make('disable')->table($sam));
        expect($sam->fresh()->isDisabled())->toBeTrue();
        Livewire::test(ListStaff::class)->callAction(TestAction::make('enable')->table($sam));
        expect($sam->fresh()->isDisabled())->toBeFalse();
        Livewire::test(ListStaff::class)->callAction(TestAction::make('resetMfa')->table($sam));
        expect($sam->fresh()->hasMfa())->toBeFalse();

        expect(AuditLog::orderBy('id')->pluck('action')->all())->toBe(['staff.disabled', 'staff.enabled', 'staff.mfa_reset']);
    });

    it('does not let admins edit or disable themselves', function () {
        $admin = staffMember(Staff::ROLE_ADMIN);
        $this->actingAs($admin, 'staff')->get("/admin/staff/{$admin->id}/edit")->assertForbidden();
        Livewire::test(ListStaff::class)->assertActionHidden(TestAction::make('disable')->table($admin));
    });

    it('audits which fields changed on edit', function () {
        $this->actingAs(staffMember(Staff::ROLE_ADMIN), 'staff');
        $sam = staffMember();
        Livewire::test(EditStaff::class, ['record' => $sam->getRouteKey()])
            ->fillForm(['role' => Staff::ROLE_ADMIN])
            ->call('save')
            ->assertHasNoFormErrors();
        $log = AuditLog::sole();
        expect($log->action)->toBe('staff.updated')->and($log->details['fields'])->toBe(['role'])->and($sam->fresh()->isAdmin())->toBeTrue();
    });
});

describe('create-staff command', function () {
    it('creates the first admin from the shell', function () {
        $this->artisan('chronova:create-staff', ['email' => 'Root@Example.com', '--name' => 'Root', '--role' => 'admin'])
            ->expectsQuestion('Password (12+ characters, upper- and lower-case letters and a number)', 'Long-password-123')
            ->expectsQuestion('Repeat the password', 'Long-password-123')
            ->assertSuccessful();
        expect(Staff::where('email', 'root@example.com')->sole()->isAdmin())->toBeTrue()
            ->and(AuditLog::sole()->details['via'])->toBe('console');
    });

    it('refuses weak passwords and mismatches', function () {
        $this->artisan('chronova:create-staff', ['email' => 'a@example.com', '--name' => 'A'])
            ->expectsQuestion('Password (12+ characters, upper- and lower-case letters and a number)', 'weak')
            ->assertFailed();
        $this->artisan('chronova:create-staff', ['email' => 'a@example.com', '--name' => 'A'])
            ->expectsQuestion('Password (12+ characters, upper- and lower-case letters and a number)', 'Long-password-123')
            ->expectsQuestion('Repeat the password', 'Long-password-124')
            ->assertFailed();
        expect(Staff::count())->toBe(0);
    });
});
