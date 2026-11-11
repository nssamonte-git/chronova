<?php

namespace App\Voice;

use RuntimeException;

/**
 * A voice provider failed. The message never contains the provider's response
 * body or the user's words, so it is safe to log.
 */
class ProviderUnavailable extends RuntimeException {}
