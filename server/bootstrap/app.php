<?php

use App\Http\Middleware\EnsureUserIsActive;
use App\Http\Middleware\UseCloudFrontViewerAddress;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Laravel\Sanctum\Http\Middleware\CheckAbilities;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        apiPrefix: 'v1',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Behind the load balancer: trust X-Forwarded-* only from the proxies named in
        // TRUSTED_PROXIES (comma-separated, e.g. the VPC range). Unset = trust none.
        if ($proxies = getenv('TRUSTED_PROXIES')) {
            $middleware->trustProxies(at: array_map('trim', explode(',', $proxies)));
        }
        // API clients are never redirected to a login page: unauthenticated /v1 calls get a 401.
        $middleware->redirectGuestsTo(fn (Request $request) => $request->is('v1/*') ? null : '/');
        $middleware->prepend(UseCloudFrontViewerAddress::class);
        $middleware->alias([
            'abilities' => CheckAbilities::class,
            'active' => EnsureUserIsActive::class,
        ]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('v1/*') || $request->expectsJson(),
        );
    })->create();
