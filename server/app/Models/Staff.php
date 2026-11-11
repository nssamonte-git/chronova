<?php

namespace App\Models;

use Database\Factories\StaffFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;

/**
 * Someone who uses the admin panel (separate guard from app users).
 * Support staff handle users; admins can also delete users and manage staff.
 */
#[Fillable(['name', 'email', 'password', 'role'])]
#[Hidden(['password', 'remember_token'])]
class Staff extends Authenticatable
{
    /** @use HasFactory<StaffFactory> */
    use HasFactory;

    public const ROLE_ADMIN = 'admin';

    public const ROLE_SUPPORT = 'support';

    protected $table = 'staff';

    protected function casts(): array
    {
        return ['disabled_at' => 'datetime', 'password' => 'hashed'];
    }

    public function setEmailAttribute(string $value): void
    {
        $this->attributes['email'] = mb_strtolower(trim($value));
    }

    public function isAdmin(): bool
    {
        return $this->role === self::ROLE_ADMIN;
    }

    public function isDisabled(): bool
    {
        return $this->disabled_at !== null;
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
