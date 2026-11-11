<?php

namespace App\Providers;

use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        //
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        // Sign-up, sign-in and code endpoints: per email+IP, and per IP overall.
        RateLimiter::for('auth', fn (Request $request) => [
            Limit::perMinute(5)->by('auth-email:'.mb_strtolower((string) $request->input('email')).'|'.$request->ip()),
            Limit::perMinute(30)->by('auth-ip:'.$request->ip()),
        ]);

        // A device syncs on demand, but a full pull of a big account takes a few pages.
        RateLimiter::for('sync', fn (Request $request) => Limit::perMinute(60)->by('sync:'.$request->user()?->getKey()));
    }
}
