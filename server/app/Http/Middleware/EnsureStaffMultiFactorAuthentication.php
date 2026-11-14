<?php

namespace App\Http\Middleware;

use App\Filament\Auth\StaffLogin;
use Closure;
use Filament\Auth\MultiFactor\Http\Middleware\EnsureMultiFactorAuthenticationIsEnabled;
use Filament\Facades\Filament;
use Illuminate\Http\Request;

class EnsureStaffMultiFactorAuthentication extends EnsureMultiFactorAuthenticationIsEnabled
{
    public function handle(Request $request, Closure $next): mixed
    {
        if ($request->routeIs('filament.admin.auth.logout')) {
            return $next($request);
        }

        $staff = Filament::auth()->user();
        $isSetup = $request->routeIs(Filament::getCurrentPanel()->getSetUpRequiredMultiFactorAuthenticationRouteName());

        if (! $staff->requiresMfa()) {
            $request->session()->put(StaffLogin::MFA_EXEMPT_SESSION, true);

            return $isSetup ? redirect()->intended(Filament::getUrl()) : $next($request);
        }

        if ($request->session()->get(StaffLogin::MFA_EXEMPT_SESSION)) {
            Filament::auth()->logoutCurrentDevice();
            $request->session()->invalidate();
            $request->session()->regenerateToken();

            return redirect()->guest(Filament::getLoginUrl());
        }

        return $isSetup ? $next($request) : parent::handle($request, $next);
    }
}
