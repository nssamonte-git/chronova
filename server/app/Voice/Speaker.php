<?php

namespace App\Voice;

/** Turns a sentence or two into speech. */
interface Speaker
{
    /** @throws ProviderUnavailable when the provider fails or answers without audio */
    public function speak(string $text, ?string $language): Speech;
}
