<?php

namespace App\Mail;

use App\Auth\EmailCodes;
use App\Models\EmailCode;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/** A verification or password-reset code. */
class EmailCodeMail extends Mailable implements ShouldQueue
{
    use Queueable;

    public function __construct(public readonly string $purpose, public readonly string $code) {}

    public function envelope(): Envelope
    {
        return new Envelope(subject: $this->purpose === EmailCode::PURPOSE_RESET
            ? 'Your Chronova password reset code'
            : 'Your Chronova confirmation code');
    }

    public function content(): Content
    {
        return new Content(text: 'mail.email-code', with: ['minutes' => EmailCodes::TTL_MINUTES]);
    }
}
