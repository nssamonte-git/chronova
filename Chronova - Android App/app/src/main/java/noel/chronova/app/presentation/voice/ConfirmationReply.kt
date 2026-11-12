package noel.chronova.app.presentation.voice

/**
 * How a spoken answer to "Shall I apply it?" is read: a short yes or no, or
 * anything else — which is taken as a corrected request ("no, make it three hours").
 * English words only; matched on the phone, no extra request.
 */
enum class ConfirmationReply {
    Yes,
    No,
    Other,
    ;

    companion object {
        private val YES = listOf(
            "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "apply", "apply it", "do it", "go ahead",
            "confirm", "please do", "sounds good", "correct", "that's right", "that is right", "go for it",
        )
        private val NO = listOf(
            "no", "nope", "cancel", "stop", "never mind", "nevermind", "don't", "do not", "leave it",
            "forget it", "not now", "no thanks", "no thank you",
        )

        /** Longer answers than this are requests, not a plain yes or no. */
        private const val MAX_WORDS = 4

        fun of(text: String): ConfirmationReply {
            val clean = text.lowercase().replace(Regex("[^a-z' ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (clean.isEmpty()) return Other
            val words = clean.split(' ')
            if (words.size > MAX_WORDS) return Other
            val padded = " $clean "
            val yes = YES.any { " $it " in padded }
            val no = NO.any { " $it " in padded }
            return when {
                yes && !no -> Yes
                no && !yes -> No
                else -> Other
            }
        }
    }
}
