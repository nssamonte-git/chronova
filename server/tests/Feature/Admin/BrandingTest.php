<?php

use App\Providers\Filament\AdminPanelProvider;
use Filament\Facades\Filament;

it('is dark only, in the logo colours', function () {
    $panel = Filament::getPanel('admin');

    expect($panel->hasDarkMode())->toBeTrue()
        ->and($panel->hasDarkModeForced())->toBeTrue()
        ->and($panel->getColors()['gray'])->toBe(AdminPanelProvider::NAVY_GRAYS)
        ->and($panel->getColors()['primary'][500])->toContain('195.8'); // the logo's cyan hue
});

it('shows the logo, favicons and theme on the sign-in page', function () {
    $html = $this->get('/admin/login')->assertOk()->getContent();

    expect($html)
        ->toContain('images/brand/chronova-logo.png')
        ->toContain('images/brand/favicon-32.png')
        ->toContain('images/brand/apple-touch-icon.png')
        ->toContain('css/app/chronova-admin.css')
        ->toContain('class="chronova-brand"')
        ->toMatch('/<html[^>]*class="[^"]*\bdark\b/')
        ->not->toContain('fi-theme-switcher');
});

it('serves the brand files', function (string $path) {
    expect(public_path($path))->toBeFile();
})->with(['images/brand/chronova-logo.png', 'images/brand/favicon-32.png', 'images/brand/apple-touch-icon.png', 'favicon.ico']);

it('has published the current theme stylesheet', function () {
    // `php artisan filament:assets` copies resources/css/admin-theme.css; a stale copy would ship old styles.
    expect(file_get_contents(public_path('css/app/chronova-admin.css')))
        ->toBe(file_get_contents(resource_path('css/admin-theme.css')));
});
