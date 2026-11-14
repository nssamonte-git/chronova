<?php

namespace App\Models;

use Database\Factories\StaffFactory;
use Filament\Auth\MultiFactor\App\Contracts\HasAppAuthentication;
use Filament\Auth\MultiFactor\App\Contracts\HasAppAuthenticationRecovery;
use Filament\Models\Contracts\FilamentUser;
use Filament\Panel;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use SensitiveParameter;

/**
 * Someone who uses the admin panel (separate guard from app users).
 * Support staff handle users; admins can also delete users and manage staff.
 * Staff must set up an authenticator app unless an admin grants an exemption.
 */
#[Fillable(['name', 'email', 'password', 'role'])]
#[Hidden(['password', 'remember_token', 'app_authentication_secret', 'app_authentication_recovery_codes'])]
class Staff extends Authenticatable implements FilamentUser, HasAppAuthentication, HasAppAuthenticationRecovery
{
    /** @use HasFactory<StaffFactory> */
    use HasFactory;

    public const ROLE_ADMIN = 'admin';

    public const ROLE_SUPPORT = 'support';

    public const ROLES = [self::ROLE_ADMIN => 'Admin', self::ROLE_SUPPORT => 'Support'];

    protected $table = 'staff';

    protected function casts(): array
    {
        return [
            'disabled_at' => 'datetime',
            'password' => 'hashed',
            'mfa_required' => 'boolean',
            'app_authentication_secret' => 'encrypted',
            'app_authentication_recovery_codes' => 'encrypted:array',
        ];
    }

    public function setEmailAttribute(string $value): void
    {
        $this->attributes['email'] = mb_strtolower(trim($value));
    }

    public function canAccessPanel(Panel $panel): bool
    {
        return $panel->getId() === 'admin' && ! $this->isDisabled();
    }

    public function isAdmin(): bool
    {
        return $this->role === self::ROLE_ADMIN;
    }

    public function isDisabled(): bool
    {
        return $this->disabled_at !== null;
    }

    public function hasMfa(): bool
    {
        return filled($this->app_authentication_secret);
    }

    public function requiresMfa(): bool
    {
        return $this->mfa_required ?? true;
    }

    public function getAppAuthenticationSecret(): ?string
    {
        return $this->requiresMfa() ? $this->app_authentication_secret : null;
    }

    public function saveAppAuthenticationSecret(#[SensitiveParameter] ?string $secret): void
    {
        $this->forceFill(['app_authentication_secret' => $secret])->save();
    }

    public function getAppAuthenticationHolderName(): string
    {
        return $this->email;
    }

    public function getAppAuthenticationRecoveryCodes(): ?array
    {
        return $this->app_authentication_recovery_codes;
    }

    public function saveAppAuthenticationRecoveryCodes(#[SensitiveParameter] ?array $codes): void
    {
        $this->forceFill(['app_authentication_recovery_codes' => $codes])->save();
    }

    public function auditLogs(): HasMany
    {
        return $this->hasMany(AuditLog::class);
    }

    public function dataAccessGrants(): HasMany
    {
        return $this->hasMany(DataAccessGrant::class);
    }
}
