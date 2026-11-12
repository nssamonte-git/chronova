<?php

namespace App\Voice;

/** Writes a short spoken summary of changes the user is asked about, or has just applied. */
interface Explainer
{
    /**
     * @param  array  $changes  validated change list (ChangeList)
     * @param  string|null  $language  ISO 639 code to answer in, or null for English
     * @param  string  $stage  ChangeList::PROPOSAL (what will change, ending with a question) or ChangeList::APPLIED (what has changed)
     * @param  array  $warnings  validated clashes the proposal would cause (ChangeList::WARNING_FIELDS); named before the question
     *
     * @throws ProviderUnavailable when the provider fails or answers with something unusable
     */
    public function explain(array $changes, string $today, ?string $language, string $stage = ChangeList::PROPOSAL, array $warnings = []): Explanation;
}
