<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\DB;

class AccountController extends Controller
{
    /**
     * DELETE /v1/account — deletes the caller's account and every synced record
     * (cascade), and revokes all tokens. Sync metrics stay, without the user.
     */
    public function destroy(Request $request): Response
    {
        $user = $request->user();
        DB::transaction(function () use ($user) {
            $user->tokens()->delete();
            $user->delete();
        });

        return response()->noContent();
    }
}
