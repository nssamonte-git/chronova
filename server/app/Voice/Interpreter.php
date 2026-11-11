<?php

namespace App\Voice;

/** Turns a spoken or typed instruction into a proposal (see ProposalSchema). */
interface Interpreter
{
    /**
     * @param  array  $context  validated request context (InterpretRequestParser)
     * @return Interpretation the provider's raw proposal; it is validated afterwards
     *
     * @throws ProviderUnavailable when the provider can't be reached or answers with an error
     */
    public function interpret(string $transcript, array $context): Interpretation;
}
