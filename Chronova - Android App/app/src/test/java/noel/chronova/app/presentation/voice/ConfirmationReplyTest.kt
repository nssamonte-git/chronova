package noel.chronova.app.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationReplyTest {
    @Test
    fun shortYesAndNo() {
        listOf("Yes", "yeah.", "Sure!", "OK", "Okay, do it", "Apply it", "go ahead", "Yes please", "That's right").forEach {
            assertEquals(it, ConfirmationReply.Yes, ConfirmationReply.of(it))
        }
        listOf("No", "Nope.", "Cancel", "never mind", "Don't", "No thanks", "Stop", "Forget it").forEach {
            assertEquals(it, ConfirmationReply.No, ConfirmationReply.of(it))
        }
    }

    @Test
    fun aLoneWordThatSoundsLikeNoIsNo() {
        // On the phone, "No." came back from the transcriber as "Now".
        listOf("Now", "now.", "Know", "Nah").forEach { assertEquals(it, ConfirmationReply.No, ConfirmationReply.of(it)) }
        assertEquals(ConfirmationReply.Other, ConfirmationReply.of("now move it to Friday"))
    }

    @Test
    fun anythingLongerOrMixedIsARequest() {
        listOf(
            "No, make it three hours",
            "yes but move it to Friday instead",
            "push design by a day",
            "",
            "yes no",
        ).forEach { assertEquals(it, ConfirmationReply.Other, ConfirmationReply.of(it)) }
    }
}
