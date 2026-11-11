<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * In AWS the app is only reachable through CloudFront (the load balancer is private),
 * so the client address comes from CloudFront's own `CloudFront-Viewer-Address`
 * header ("ip:port") instead of X-Forwarded-For, which a client could fake. Rate
 * limits and the audit log then see the real client IP. Off unless BEHIND_CLOUDFRONT.
 */
class UseCloudFrontViewerAddress
{
    public function handle(Request $request, Closure $next): Response
    {
        if (config('app.behind_cloudfront')) {
            $ip = self::ipFrom((string) $request->headers->get('CloudFront-Viewer-Address'));
            if ($ip !== null) {
                $request->server->set('REMOTE_ADDR', $ip);
            }
        }

        return $next($request);
    }

    /** "198.51.100.10:46532" or "2001:db8::1:46532" → the address, or null if malformed. */
    public static function ipFrom(string $header): ?string
    {
        $cut = strrpos($header, ':');
        $ip = $cut === false ? '' : substr($header, 0, $cut);

        return filter_var($ip, FILTER_VALIDATE_IP) !== false ? $ip : null;
    }
}
