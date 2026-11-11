<?php

namespace App\Voice;

/** Writes a short spoken summary of changes the user is about to confirm. */
interface Explainer
{
    /**
     * @param  array  $changes  validated change list (ChangeList)
     * @param  string|null  $language  ISO 639 code to answer in, or null for English
     *
     * @throws ProviderUnavailable when the provider fails or answers with something unusable
     */
    public function explain(array $changes, string $today, ?string $language): Explanation;
}
