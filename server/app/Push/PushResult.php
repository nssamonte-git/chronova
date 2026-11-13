<?php

namespace App\Push;

enum PushResult
{
    case Sent;

    /** The token is no longer valid (the app was uninstalled or its data cleared): forget the device. */
    case Unregistered;

    /** Anything else (network, quota, credentials); the device is kept. */
    case Failed;
}
