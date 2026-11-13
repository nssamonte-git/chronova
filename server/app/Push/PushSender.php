<?php

namespace App\Push;

/** Sends one data message to one device (docs/push.md); never throws. */
interface PushSender
{
    /**
     * @param  array<string, string>  $data  the message (FCM data values are strings)
     * @param  bool  $urgent  delivered at once even when the phone is idle (reminders, account alerts)
     * @param  string|null  $collapseKey  a newer message with the same key replaces an undelivered one
     */
    public function send(string $token, array $data, bool $urgent, ?string $collapseKey = null): PushResult;
}
