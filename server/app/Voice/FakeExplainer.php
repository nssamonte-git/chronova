<?php

namespace App\Voice;

/** For development and tests: the plain summary, no network. */
final class FakeExplainer implements Explainer
{
    public function explain(array $changes, string $today, ?string $language, string $stage = ChangeList::PROPOSAL, array $warnings = []): Explanation
    {
        return new Explanation(ChangeList::summary($changes, $stage, $warnings));
    }
}
