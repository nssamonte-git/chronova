<?php

namespace App\Push;

use Illuminate\Contracts\Cache\Repository as Cache;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory as Http;
use Illuminate\Support\Facades\Log;

/**
 * Firebase Cloud Messaging's HTTP v1 API. The service account signs a short JWT (RS256, with
 * PHP's openssl), exchanged for an access token that is cached for most of its hour. Only
 * outcomes are logged, never tokens or message content.
 */
final class FcmPushSender implements PushSender
{
    private const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

    private const CACHE_KEY = 'push.fcm.access_token';

    public function __construct(private readonly Http $http, private readonly Cache $cache, private readonly array $config) {}

    public function send(string $token, array $data, bool $urgent, ?string $collapseKey = null): PushResult
    {
        $account = $this->account();
        if ($account === null) {
            Log::warning('push: FCM credentials missing or unreadable');

            return PushResult::Failed;
        }
        $access = $this->accessToken($account);
        if ($access === null) {
            return PushResult::Failed;
        }

        $android = ['priority' => $urgent ? 'HIGH' : 'NORMAL', 'ttl' => '86400s'];
        if ($collapseKey !== null) {
            $android['collapse_key'] = $collapseKey;
        }
        try {
            $response = $this->http->withToken($access)
                ->acceptJson()
                ->timeout($this->config['timeout'] ?? 10)
                ->post(rtrim($this->config['base_url'], '/').'/projects/'.$account['project_id'].'/messages:send', [
                    'message' => ['token' => $token, 'data' => array_map('strval', $data), 'android' => $android],
                ]);
        } catch (ConnectionException) {
            Log::warning('push: FCM unreachable');

            return PushResult::Failed;
        }
        if ($response->successful()) {
            return PushResult::Sent;
        }
        $status = $response->json('error.status');
        $code = collect($response->json('error.details', []))->pluck('errorCode')->filter()->first();
        if ($response->status() === 404 || $status === 'NOT_FOUND' || $code === 'UNREGISTERED') {
            return PushResult::Unregistered;
        }
        if ($response->status() === 401) {
            $this->cache->forget(self::CACHE_KEY); // an expired access token: the next send gets a new one
        }
        Log::warning('push: FCM answered with HTTP '.$response->status());

        return PushResult::Failed;
    }

    /** @return array{project_id: string, client_email: string, private_key: string, token_uri: string}|null */
    private function account(): ?array
    {
        $json = $this->config['credentials_json'] ?? null;
        $path = $this->config['credentials'] ?? null;
        if (blank($json) && filled($path) && is_readable($path)) {
            $json = file_get_contents($path);
        }
        $account = is_string($json) ? json_decode($json, true) : null;
        if (! is_array($account)) {
            return null;
        }
        foreach (['project_id', 'client_email', 'private_key'] as $key) {
            if (blank($account[$key] ?? null)) {
                return null;
            }
        }
        $account['token_uri'] ??= 'https://oauth2.googleapis.com/token';

        return $account;
    }

    private function accessToken(array $account): ?string
    {
        $cached = $this->cache->get(self::CACHE_KEY);
        if (is_string($cached)) {
            return $cached;
        }
        $now = time();
        $assertion = $this->jwt([
            'iss' => $account['client_email'],
            'scope' => self::SCOPE,
            'aud' => $account['token_uri'],
            'iat' => $now,
            'exp' => $now + 3600,
        ], $account['private_key']);
        if ($assertion === null) {
            Log::warning('push: the FCM private key could not sign');

            return null;
        }
        try {
            $response = $this->http->asForm()->acceptJson()->timeout($this->config['timeout'] ?? 10)->post($account['token_uri'], [
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => $assertion,
            ]);
        } catch (ConnectionException) {
            Log::warning('push: Google OAuth unreachable');

            return null;
        }
        $token = $response->json('access_token');
        if (! $response->successful() || ! is_string($token)) {
            Log::warning('push: Google OAuth answered with HTTP '.$response->status());

            return null;
        }
        // Kept a little under its lifetime.
        $this->cache->put(self::CACHE_KEY, $token, max(60, (int) $response->json('expires_in', 3600) - 300));

        return $token;
    }

    private function jwt(array $claims, string $privateKey): ?string
    {
        $encode = fn (array $part) => rtrim(strtr(base64_encode(json_encode($part, JSON_UNESCAPED_SLASHES)), '+/', '-_'), '=');
        $input = $encode(['alg' => 'RS256', 'typ' => 'JWT']).'.'.$encode($claims);
        $key = openssl_pkey_get_private($privateKey);
        if ($key === false || ! openssl_sign($input, $signature, $key, OPENSSL_ALGO_SHA256)) {
            return null;
        }

        return $input.'.'.rtrim(strtr(base64_encode($signature), '+/', '-_'), '=');
    }
}
