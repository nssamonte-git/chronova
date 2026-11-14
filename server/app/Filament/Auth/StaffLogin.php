<?php

namespace App\Filament\Auth;

use App\Models\Staff;
use Filament\Auth\Http\Responses\Contracts\LoginResponse;
use Filament\Auth\Pages\Login;
use Illuminate\Contracts\Auth\Authenticatable;

class StaffLogin extends Login
{
    public const MFA_EXEMPT_SESSION = 'staff.mfa_exempt';

    protected ?bool $mfaRequiredAtPasswordCheck = null;

    public function authenticate(): ?LoginResponse
    {
        $this->mfaRequiredAtPasswordCheck = null;
        $response = parent::authenticate();

        if ($response !== null) {
            session()->put(self::MFA_EXEMPT_SESSION, $this->mfaRequiredAtPasswordCheck === false);
        }

        return $response;
    }

    protected function isUserAllowedToAccessPanel(Authenticatable $user): bool
    {
        if (! $user instanceof Staff || ! parent::isUserAllowedToAccessPanel($user)) {
            return false;
        }

        // Filament checks again after deciding whether to challenge. Reject a policy change mid-login.
        $this->mfaRequiredAtPasswordCheck ??= $user->requiresMfa();

        return $this->mfaRequiredAtPasswordCheck === $user->requiresMfa();
    }
}
