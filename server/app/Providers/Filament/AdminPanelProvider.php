<?php

namespace App\Providers\Filament;

use App\Filament\Auth\StaffLogin;
use App\Http\Middleware\EnsureStaffMultiFactorAuthentication;
use Filament\Auth\MultiFactor\App\AppAuthentication;
use Filament\Http\Middleware\Authenticate;
use Filament\Http\Middleware\AuthenticateSession;
use Filament\Http\Middleware\DisableBladeIconComponents;
use Filament\Http\Middleware\DispatchServingFilamentEvent;
use Filament\Pages\Dashboard;
use Filament\Panel;
use Filament\PanelProvider;
use Filament\Support\Colors\Color;
use Filament\View\PanelsRenderHook;
use Illuminate\Cookie\Middleware\AddQueuedCookiesToResponse;
use Illuminate\Cookie\Middleware\EncryptCookies;
use Illuminate\Foundation\Http\Middleware\PreventRequestForgery;
use Illuminate\Routing\Middleware\SubstituteBindings;
use Illuminate\Session\Middleware\StartSession;
use Illuminate\View\Middleware\ShareErrorsFromSession;

class AdminPanelProvider extends PanelProvider
{
    /** Slate-like grays on the logo's navy hue; 950 is the logo's background (#021037). */
    public const NAVY_GRAYS = [
        50 => 'oklch(0.984 0.003 248)',
        100 => 'oklch(0.968 0.007 250)',
        200 => 'oklch(0.93 0.013 256)',
        300 => 'oklch(0.86 0.022 258)',
        400 => 'oklch(0.72 0.035 260)',
        500 => 'oklch(0.57 0.045 262)',
        600 => 'oklch(0.46 0.052 263)',
        700 => 'oklch(0.38 0.06 263)',
        800 => 'oklch(0.3 0.07 263)',
        900 => 'oklch(0.235 0.078 263)',
        950 => 'oklch(0.192 0.08 263)',
    ];

    public function panel(Panel $panel): Panel
    {
        return $panel
            ->default()
            ->id('admin')
            ->path('admin')
            ->brandName('Chronova Admin')
            // The Chronova logo and the logo's look (tools/brand/generate.py, resources/css/admin-theme.css).
            ->brandLogo(fn () => view('filament.admin.brand'))
            ->brandLogoHeight('2.25rem')
            ->favicon(asset('images/brand/favicon-32.png'))
            ->renderHook(PanelsRenderHook::HEAD_END, fn (): string => '<link rel="apple-touch-icon" href="'.e(asset('images/brand/apple-touch-icon.png')).'">'
                .'<meta name="theme-color" content="#021037">')
            ->darkMode(true, isForced: true)
            // Staff only: a separate guard and table from app users.
            ->authGuard('staff')
            ->login(StaffLogin::class)
            ->multiFactorAuthentication([
                AppAuthentication::make()->recoverable()->brandName('Chronova Admin'),
            ], isRequired: true)
            ->multiFactorAuthenticationRequiredMiddlewareName(EnsureStaffMultiFactorAuthentication::class)
            ->colors([
                // From the logo: cyan stroke, sky/blue middle, teal glow; grays tinted to its navy.
                'primary' => Color::generatePalette('#00f9fb'),
                'info' => Color::generatePalette('#02a8fd'),
                'success' => Color::generatePalette('#02fde1'),
                'gray' => self::NAVY_GRAYS,
            ])
            ->discoverResources(in: app_path('Filament/Resources'), for: 'App\Filament\Resources')
            ->discoverPages(in: app_path('Filament/Pages'), for: 'App\Filament\Pages')
            ->pages([
                Dashboard::class,
            ])
            ->discoverWidgets(in: app_path('Filament/Widgets'), for: 'App\Filament\Widgets')
            ->middleware([
                EncryptCookies::class,
                AddQueuedCookiesToResponse::class,
                StartSession::class,
                AuthenticateSession::class,
                ShareErrorsFromSession::class,
                PreventRequestForgery::class,
                SubstituteBindings::class,
                DisableBladeIconComponents::class,
                DispatchServingFilamentEvent::class,
            ])
            ->authMiddleware([
                Authenticate::class,
                EnsureStaffMultiFactorAuthentication::class,
            ], isPersistent: true);
    }
}
