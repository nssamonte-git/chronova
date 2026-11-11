<?php

namespace App\Http\Controllers\Api;

use App\Accounts\DeleteUserAccount;
use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Http\Response;

class AccountController extends Controller
{
    /** DELETE /v1/account — the caller deletes their own account and every synced record. */
    public function destroy(Request $request, DeleteUserAccount $delete): Response
    {
        $delete($request->user());

        return response()->noContent();
    }
}
