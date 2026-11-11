@if ($purpose === 'reset')
Use this code to reset your Chronova password:
@else
Use this code to confirm your email for Chronova:
@endif

{{ $code }}

It expires in {{ $minutes }} minutes. If you didn't ask for it, you can ignore this email.
