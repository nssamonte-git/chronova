<?php

namespace App\Mail;

use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;

/**
 * Sent when someone tries to register an email that already has an account. The API
 * answers exactly as for a new sign-up, so it doesn't reveal which emails exist.
 */
class AlreadyRegisteredMail extends Mailable implements ShouldQueue
{
    use Queueable;

    public function envelope(): Envelope
    {
        return new Envelope(subject: 'You already have a Chronova account');
    }

    public function content(): Content
    {
        return new Content(text: 'mail.already-registered');
    }
}
