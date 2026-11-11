<?php

use App\Http\Middleware\UseCloudFrontViewerAddress;
use Illuminate\Support\Facades\Route;

beforeEach(fn () => Route::get('/_ip', fn () => response()->json(['ip' => request()->ip(), 'url' => url('/x')])));

it('parses IPv4 and IPv6 viewer addresses and rejects junk', function () {
    expect(UseCloudFrontViewerAddress::ipFrom('198.51.100.10:46532'))->toBe('198.51.100.10')
        ->and(UseCloudFrontViewerAddress::ipFrom('2001:db8::1:46532'))->toBe('2001:db8::1')
        ->and(UseCloudFrontViewerAddress::ipFrom('not-an-ip:1'))->toBeNull()
        ->and(UseCloudFrontViewerAddress::ipFrom(''))->toBeNull();
});

it('ignores the header unless running behind CloudFront', function () {
    $this->getJson('/_ip', ['CloudFront-Viewer-Address' => '198.51.100.10:1'])->assertJsonPath('ip', '127.0.0.1');
});

it('uses the CloudFront viewer address, not X-Forwarded-For, behind CloudFront', function () {
    config(['app.behind_cloudfront' => true]);
    $this->getJson('/_ip', ['CloudFront-Viewer-Address' => '198.51.100.10:46532', 'X-Forwarded-For' => '6.6.6.6'])
        ->assertJsonPath('ip', '198.51.100.10');
});

it('rate-limits sign-in by the real client address behind CloudFront', function () {
    config(['app.behind_cloudfront' => true]);
    foreach (range(1, 30) as $_) {
        $this->postJson('/v1/auth/login', ['email' => 'x'.random_int(1, 1e9).'@example.com', 'password' => 'x'],
            ['CloudFront-Viewer-Address' => '198.51.100.10:1', 'X-Forwarded-For' => '10.0.0.'.random_int(1, 254)]);
    }
    // A spoofed X-Forwarded-For doesn't reset the per-IP limit.
    $this->postJson('/v1/auth/login', ['email' => 'last@example.com', 'password' => 'x'],
        ['CloudFront-Viewer-Address' => '198.51.100.10:1', 'X-Forwarded-For' => '10.9.9.9'])->assertTooManyRequests();
});
