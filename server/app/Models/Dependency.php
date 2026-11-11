<?php

namespace App\Models;

use Database\Factories\DependencyFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;

/** Finish-to-start link: the successor task should start after the predecessor ends. */
class Dependency extends SyncRecord
{
    /** @use HasFactory<DependencyFactory> */
    use HasFactory;

    public static function entity(): string
    {
        return 'dependency';
    }
}
