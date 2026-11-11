<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/** Refuses requests from accounts that staff have disabled, even with a valid token. */
class EnsureUserIsActive
{
    public function handle(Request $request, Closure $next): Response
    {
        if ($request->user()?->isDisabled()) {
            return response()->json(['error' => 'account_disabled'], 403);
        }

        return $next($request);
    }
}
