<?php

namespace App\Voice;

use Illuminate\Http\UploadedFile;

/** Turns a short recorded instruction into text. */
interface Transcriber
{
    /**
     * @param  string|null  $language  ISO 639-1/639-3 hint, or null to detect
     * @param  list<string>  $keyterms  words likely to be said (task titles), cleaned by KeyTerms
     *
     * @throws ProviderUnavailable when the provider can't be reached or answers with an error
     */
    public function transcribe(UploadedFile $audio, ?string $language, array $keyterms = []): Transcription;
}
