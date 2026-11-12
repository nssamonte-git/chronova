<?php

namespace App\Voice;

/** Turns a spoken or typed instruction into a proposal (see ProposalSchema). */
interface Interpreter
{
    /**
     * @param  array  $context  validated request context (InterpretRequestParser)
     * @param  array  $history  this user's recent decisions, oldest first (VoiceDecision::history)
     * @return Interpretation the provider's raw proposal; it is validated afterwards
     *
     * @throws ProviderUnavailable when the provider can't be reached or answers with an error
     */
    public function interpret(string $transcript, array $context, array $history = []): Interpretation;

    /**
     * A clash-free alternative to $planned, the operations proposed for $transcript,
     * which would cause $clashes (ChangeList::WARNING_FIELDS). "unsupported" when
     * there is no sensible one.
     *
     * @throws ProviderUnavailable when the provider can't be reached or answers with an error
     */
    public function resolve(string $transcript, array $context, array $planned, array $clashes, array $history = []): Interpretation;
}
