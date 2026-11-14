<?php

use App\Filament\Auth\StaffLogin;
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

    it('lets staff with an exemption sign in with their password', function (bool $enrolled) {
        $staff = $enrolled ? staffMember() : Staff::factory()->create();
        $staff->forceFill(['mfa_required' => false])->save();

        Livewire::test(StaffLogin::class)
            ->fillForm(['email' => $staff->email, 'password' => 'Staff-password-1'])
            ->call('authenticate')
            ->assertRedirect('/admin');

        $this->assertAuthenticatedAs($staff, 'staff');
        $this->get('/admin')->assertOk();
        $this->get('/admin/multi-factor-authentication/set-up')->assertRedirect('/admin');
    })->with([false, true]);

    it('requires the authenticator step on login for enrolled staff without an exemption', function () {
        $staff = staffMember();

        $login = Livewire::test(StaffLogin::class)
            ->fillForm(['email' => $staff->email, 'password' => 'Staff-password-1'])
            ->call('authenticate')
            ->assertNoRedirect();

        $this->assertGuest('staff');

        $code = Filament::getMultiFactorAuthenticationProviders()['app']->getCurrentCode($staff);
        $login->set('data.multiFactor.app.code', $code)
            ->call('authenticate')
            ->assertRedirect('/admin');

        $this->assertAuthenticatedAs($staff, 'staff');
    });

    it('still denies password sign-in for disabled staff and invalid passwords', function () {
        $staff = Staff::factory()->create(['mfa_required' => false, 'disabled_at' => now()]);

        Livewire::test(StaffLogin::class)
            ->fillForm(['email' => $staff->email, 'password' => 'Staff-password-1'])
            ->call('authenticate')
            ->assertHasFormErrors(['email']);

        $staff->forceFill(['disabled_at' => null])->save();

        Livewire::test(StaffLogin::class)
            ->fillForm(['email' => $staff->email, 'password' => 'Wrong-password-1'])
            ->call('authenticate')
            ->assertHasFormErrors(['email']);

        $this->assertGuest('staff');
    });

    it('ends a password-only session when the account requires 2FA again', function () {
        $staff = staffMember(attributes: ['mfa_required' => false]);
        Livewire::test(StaffLogin::class)
            ->fillForm(['email' => $staff->email, 'password' => 'Staff-password-1'])
            ->call('authenticate')
            ->assertRedirect('/admin');

        $staff->forceFill(['mfa_required' => true])->save();

        $this->actingAs($staff, 'staff')->get('/admin')->assertRedirect('/admin/login');
        $this->assertGuest('staff');
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
            ->and($sam->requiresMfa())->toBeTrue()
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
        Livewire::test(ListStaff::class)
            ->assertActionHidden(TestAction::make('disable')->table($admin))
            ->assertActionHidden(TestAction::make('disableMfa')->table($admin))
            ->mountAction(TestAction::make('disableMfa')->table($admin))
            ->call('callMountedAction');
        expect($admin->fresh()->requiresMfa())->toBeTrue()->and(AuditLog::count())->toBe(0);
    });

    it('lets admins turn off and restore 2FA for one staff member and audits both changes', function () {
        $admin = staffMember(Staff::ROLE_ADMIN);
        $sam = staffMember();
        $secret = $sam->getAppAuthenticationSecret();
        $sam->saveAppAuthenticationRecoveryCodes(['test-recovery-code']);
        $oldToken = $sam->getRememberToken();
        $this->actingAs($admin, 'staff');

        Livewire::test(ListStaff::class)
            ->assertActionVisible(TestAction::make('disableMfa')->table($sam))
            ->callAction(TestAction::make('disableMfa')->table($sam));
        $sam->refresh();
        expect($sam->requiresMfa())->toBeFalse()
            ->and($sam->getRememberToken())->not->toBe($oldToken)
            ->and($sam->getAppAuthenticationSecret())->toBeNull()
            ->and($admin->fresh()->requiresMfa())->toBeTrue();

        Livewire::test(ListStaff::class)
            ->assertActionHidden(TestAction::make('resetMfa')->table($sam))
            ->assertActionVisible(TestAction::make('requireMfa')->table($sam))
            ->callAction(TestAction::make('requireMfa')->table($sam));
        $sam->refresh();
        expect($sam->requiresMfa())->toBeTrue()
            ->and($sam->getAppAuthenticationSecret())->toBe($secret)
            ->and($sam->getAppAuthenticationRecoveryCodes())->toBe(['test-recovery-code']);

        $events = AuditLog::orderBy('id')->get();
        expect($events->pluck('action')->all())->toBe(['staff.mfa_requirement_changed', 'staff.mfa_requirement_changed'])
            ->and($events->pluck('staff_id')->all())->toBe([$admin->id, $admin->id])
            ->and($events->pluck('details')->all())->toBe([
                ['staff_id' => $sam->id, 'mfa_required' => false],
                ['staff_id' => $sam->id, 'mfa_required' => true],
            ]);
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
