<?php

namespace App\Voice;

final readonly class Speech
{
    public function __construct(public string $audio, public string $type) {}
}
