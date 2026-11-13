<?php

namespace App\Push;

/** Records what would be sent; the default for development and tests. Tokens in [unregistered] answer so. */
final class FakePushSender implements PushSender
{
    /** @var list<array{token: string, data: array<string, string>, urgent: bool, collapseKey: ?string}> */
    public array $sent = [];

    /** @var list<string> */
    public array $unregistered = [];

    public function send(string $token, array $data, bool $urgent, ?string $collapseKey = null): PushResult
    {
        if (in_array($token, $this->unregistered, true)) {
            return PushResult::Unregistered;
        }
        $this->sent[] = ['token' => $token, 'data' => $data, 'urgent' => $urgent, 'collapseKey' => $collapseKey];

        return PushResult::Sent;
    }
}
