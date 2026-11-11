<?php

namespace App\Http\Controllers\Api;

use App\Auth\EmailCodes;
use App\Http\Controllers\Controller;
use App\Http\Requests\Auth\CredentialsRequest;
use App\Http\Requests\Auth\EmailRequest;
use App\Http\Requests\Auth\RegisterRequest;
use App\Http\Requests\Auth\VerifyRequest;
use App\Mail\AlreadyRegisteredMail;
use App\Mail\EmailCodeMail;
use App\Models\EmailCode;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Mail;

/**
 * Account and token endpoints for the app. Responses never reveal whether an email
 * has an account: sign-up and resend always answer the same way.
 */
class AuthController extends Controller
{
    public const TOKEN_DAYS = 60;

    /** bcrypt (cost 12) of a random string nobody knows; checked for unknown emails. */
    private const TIMING_HASH = '$2y$12$IK5CWdVliV96w2ZAuEaw8u.UgI189NjHNmxzFGpjJbAM5L/ifXBGu';

    public function __construct(private readonly EmailCodes $codes) {}

    /** POST /v1/auth/register — always 202; a code (or a "you already have an account" note) is emailed. */
    public function register(RegisterRequest $request): JsonResponse
    {
        $email = mb_strtolower(trim($request->string('email')));
        $user = $this->findByEmail($email);

        if ($user === null) {
            $user = User::create(['email' => $email, 'password' => $request->string('password')]);
            $this->sendCode($user, EmailCode::PURPOSE_VERIFY);
        } elseif ($user->email_verified_at === null) {
            // Never confirmed: whoever proves they own the inbox sets the password.
            $user->update(['password' => $request->string('password')]);
            $this->sendCode($user, EmailCode::PURPOSE_VERIFY);
        } elseif (! $user->isDisabled()) {
            Mail::to($user->email)->queue(new AlreadyRegisteredMail);
        }

        return $this->verificationRequired();
    }

    /** POST /v1/auth/verify — confirms the email and signs in. */
    public function verify(VerifyRequest $request): JsonResponse
    {
        $user = $this->findByEmail($request->string('email'));
        if ($user === null || $user->email_verified_at !== null
            || ! $this->codes->consume($user, EmailCode::PURPOSE_VERIFY, $request->string('code'))) {
            return response()->json(['error' => 'invalid_code'], 422);
        }
        $user->forceFill(['email_verified_at' => now()])->save();

        return $this->signedIn($user, $request->string('device_name', 'android'));
    }

    /** POST /v1/auth/resend — always 202. */
    public function resend(EmailRequest $request): JsonResponse
    {
        $user = $this->findByEmail($request->string('email'));
        if ($user !== null && $user->email_verified_at === null) {
            $this->sendCode($user, EmailCode::PURPOSE_VERIFY);
        }

        return $this->verificationRequired();
    }

    /** POST /v1/auth/login */
    public function login(CredentialsRequest $request): JsonResponse
    {
        $user = $this->findByEmail($request->string('email'));
        // Check a password even for unknown emails, so timing doesn't reveal which exist.
        $hash = $user?->password ?? self::TIMING_HASH;
        if (! Hash::check($request->string('password'), $hash) || $user === null) {
            return response()->json(['error' => 'invalid_credentials'], 401);
        }
        if ($user->isDisabled()) {
            return response()->json(['error' => 'account_disabled'], 403);
        }
        if ($user->email_verified_at === null) {
            $this->sendCode($user, EmailCode::PURPOSE_VERIFY);

            return response()->json(['error' => 'verification_required'], 403);
        }

        return $this->signedIn($user, $request->string('device_name', 'android'));
    }

    /** POST /v1/auth/logout — revokes the token used for this request. */
    public function logout(Request $request): Response
    {
        $request->user()->currentAccessToken()->delete();

        return response()->noContent();
    }

    /** GET /v1/me */
    public function me(Request $request): JsonResponse
    {
        return response()->json(['user' => $this->userJson($request->user())]);
    }

    private function signedIn(User $user, string $deviceName): JsonResponse
    {
        $token = $user->createToken(mb_substr($deviceName, 0, 100), ['sync'], now()->addDays(self::TOKEN_DAYS));

        return response()->json(['token' => $token->plainTextToken, 'user' => $this->userJson($user)]);
    }

    private function sendCode(User $user, string $purpose): void
    {
        Mail::to($user->email)->queue(new EmailCodeMail($purpose, $this->codes->issue($user, $purpose)));
    }

    private function verificationRequired(): JsonResponse
    {
        return response()->json(['status' => 'verification_required'], 202);
    }

    private function findByEmail(string $email): ?User
    {
        return User::whereRaw('lower(email) = ?', [mb_strtolower(trim($email))])->first();
    }

    private function userJson(User $user): array
    {
        return ['id' => $user->id, 'email' => $user->email];
    }
}
