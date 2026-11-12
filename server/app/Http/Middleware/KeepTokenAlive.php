<?php

namespace App\Http\Middleware;

use App\Http\Controllers\Api\AuthController;
use Carbon\CarbonInterface;
use Closure;
use Illuminate\Http\Request;
use Laravel\Sanctum\PersonalAccessToken;
use Symfony\Component\HttpFoundation\Response;

/**
 * Keeps a device signed in while it is used: each request moves its token's expiry to
 * AuthController::TOKEN_DAYS from now, writing at most once a day. A device left unused
 * for that long has to sign in again; signing out still revokes the token at once.
 */
class KeepTokenAlive
{
    public function handle(Request $request, Closure $next): Response
    {
        $token = $request->user()?->currentAccessToken();
        $renewBefore = now()->addDays(AuthController::TOKEN_DAYS - 1);
        // Only a stored token with an expiry (not a test stand-in or a token without one).
        if ($token instanceof PersonalAccessToken && $token->exists && $token->expires_at instanceof CarbonInterface && $token->expires_at->lt($renewBefore)) {
            $token->forceFill(['expires_at' => now()->addDays(AuthController::TOKEN_DAYS)])->save();
        }

        return $next($request);
    }
}
