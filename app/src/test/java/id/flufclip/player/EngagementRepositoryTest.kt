package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngagementRepositoryTest {
    private var session = 1
    private val repository = EngagementRepository { session }

    @Test fun unknownInitialStateSendsExplicitGoalInsteadOfAssumingOff() {
        val request = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        assertEquals(1, request.target)
        assertEquals("like", ratingAction(request.target))
        assertTrue(repository.rating("dQw4w9WgXcQ").pending)
        repository.completeRating(request, MutationResult.Confirmed)
        assertEquals(1, repository.rating("dQw4w9WgXcQ").value)
        // Status awal tidak diketahui, jadi jumlah suka tidak dikarang berubah.
        assertEquals(0, repository.rating("dQw4w9WgXcQ").likeDelta)
        val unlike = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        repository.completeRating(unlike, MutationResult.Confirmed)
        assertEquals(0, repository.rating("dQw4w9WgXcQ").likeDelta)
    }

    @Test fun knownStateProducesPreciseLikeCountDelta() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        assertEquals(0, repository.rating("dQw4w9WgXcQ").value)
        val like = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        repository.completeRating(like, MutationResult.Confirmed)
        assertEquals(1, repository.rating("dQw4w9WgXcQ").likeDelta)

        val unlike = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        assertEquals(0, unlike.target)
        repository.completeRating(unlike, MutationResult.Confirmed)
        // Kembali ke hitungan server: suka lalu batal suka tidak boleh menyisakan -1.
        assertEquals(0, repository.rating("dQw4w9WgXcQ").likeDelta)
    }

    @Test fun alreadyLikedVideoIsNotCountedTwice() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 1))
        val unlike = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        assertEquals(0, unlike.target)
        repository.completeRating(unlike, MutationResult.Confirmed)
        assertEquals(-1, repository.rating("dQw4w9WgXcQ").likeDelta)
    }

    @Test fun tapsDuringPendingUpdateTheDisplayedIntentWithoutStartingASecondRequest() {
        assertTrue(repository.toggleRating("dQw4w9WgXcQ", 1) != null)
        assertNull(repository.toggleRating("dQw4w9WgXcQ", -1))
        assertEquals(-1, repository.rating("dQw4w9WgXcQ").value)
        assertTrue(repository.rating("dQw4w9WgXcQ").pending)
    }

    @Test fun rapidTapsConvergeToTheLastIntentAfterEarlierRequestCompletes() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        val first = repository.toggleRating("dQw4w9WgXcQ", 1)!! // like
        assertNull(repository.toggleRating("dQw4w9WgXcQ", 1)) // tapped again while pending
        assertEquals(0, repository.rating("dQw4w9WgXcQ").value)

        repository.completeRating(first, MutationResult.Confirmed)
        assertEquals(1, repository.rating("dQw4w9WgXcQ").confirmed)
        val followUp = repository.followUpRating("dQw4w9WgXcQ")!!
        assertEquals(0, followUp.target)
        repository.completeRating(followUp, MutationResult.Confirmed)
        assertEquals(0, repository.rating("dQw4w9WgXcQ").value)
        assertEquals(0, repository.rating("dQw4w9WgXcQ").confirmed)
        assertNull(repository.followUpRating("dQw4w9WgXcQ"))
    }

    @Test fun lateResultFromOldSessionNeverWritesNewSession() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        val request = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        session = 2
        assertNull(repository.completeRating(request, MutationResult.Confirmed))
        assertNull(repository.rating("dQw4w9WgXcQ").value)
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 1), session = 1)
        assertNull(repository.rating("dQw4w9WgXcQ").value)
    }

    @Test fun rejectionRestoresPreviousValueAndUncertainIsNotShownAsConfirmed() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        val reject = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        repository.completeRating(reject, MutationResult.Rejected("nope"))
        assertEquals(0, repository.rating("dQw4w9WgXcQ").value)
        assertFalse(repository.rating("dQw4w9WgXcQ").uncertain)

        val uncertain = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        repository.completeRating(uncertain, MutationResult.Uncertain("timeout"))
        assertEquals(0, repository.rating("dQw4w9WgXcQ").value)
        assertTrue(repository.rating("dQw4w9WgXcQ").uncertain)
    }

    @Test fun subscriptionStateIsSharedAndSerializedPerChannel() {
        val request = repository.setSubscription("UCabcdefghijklmnopqrstuv", true)!!
        repository.completeSubscription(request, MutationResult.Confirmed)
        assertEquals(true, repository.subscription("UCabcdefghijklmnopqrstuv").subscribed)
        val unsubscribe = repository.setSubscription("UCabcdefghijklmnopqrstuv", false)!!
        repository.completeSubscription(unsubscribe, MutationResult.Confirmed)
        assertEquals(false, repository.subscription("UCabcdefghijklmnopqrstuv").subscribed)
    }

    @Test fun rapidSubscriptionTapsKeepTheLastIntentAndSendItAfterwards() {
        val channel = "UCabcdefghijklmnopqrstuv"
        val first = repository.setSubscription(channel, true)!!
        assertNull(repository.setSubscription(channel, false))
        repository.completeSubscription(first, MutationResult.Confirmed)
        assertEquals(false, repository.subscription(channel).subscribed)
        assertEquals(true, repository.subscription(channel).confirmed)
        val followUp = repository.followUpSubscription(channel)!!
        assertEquals(false, followUp.target)
        repository.completeSubscription(followUp, MutationResult.Confirmed)
        assertEquals(false, repository.subscription(channel).subscribed)
        assertEquals(false, repository.subscription(channel).confirmed)
    }

    @Test fun serverSeedDoesNotOverwriteAnAlreadyKnownValue() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 1))
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        assertEquals(1, repository.rating("dQw4w9WgXcQ").value)

        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(subscribed = true, channelId = "UCabcdefghijklmnopqrstuv"))
        assertTrue(repository.subscription("UCabcdefghijklmnopqrstuv").subscribed == true)
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(subscribed = false, channelId = "UCabcdefghijklmnopqrstuv"))
        assertTrue(repository.subscription("UCabcdefghijklmnopqrstuv").subscribed == true)
    }

    @Test fun authoritativeRefreshReplacesKnownValueAndClearsUncertainty() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        val request = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        repository.completeRating(request, MutationResult.Uncertain("connection lost"))
        assertEquals(0, repository.rating("dQw4w9WgXcQ").value)
        assertTrue(repository.rating("dQw4w9WgXcQ").uncertain)

        val generation = repository.generation("dQw4w9WgXcQ")
        repository.refresh("dQw4w9WgXcQ", EngagementSnapshot(rating = 1), generation = generation)
        assertEquals(1, repository.rating("dQw4w9WgXcQ").value)
        assertFalse(repository.rating("dQw4w9WgXcQ").uncertain)
    }

    @Test fun refreshStartedBeforeANewerTapCannotOverwriteIt() {
        val stale = repository.generation("dQw4w9WgXcQ")
        repository.toggleRating("dQw4w9WgXcQ", 1)
        repository.refresh("dQw4w9WgXcQ", EngagementSnapshot(rating = 0), generation = stale)
        assertEquals(1, repository.rating("dQw4w9WgXcQ").value)
    }

    @Test fun abandonedOperationDoesNotLeavePendingLocked() {
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(rating = 0))
        val request = repository.toggleRating("dQw4w9WgXcQ", 1)!!
        repository.abandonRating(request)
        val state = repository.rating("dQw4w9WgXcQ")
        assertFalse(state.pending)
        assertTrue(state.uncertain)
        assertEquals(0, state.value)
        assertEquals(1, repository.toggleRating("dQw4w9WgXcQ", 1)!!.target)
    }

    @Test fun playlistsAndWatchLaterAreSeparateFromAnyPlaylistSummary() {
        repository.setPlaylists("dQw4w9WgXcQ", setOf("PLexample"))
        assertEquals(true, repository.saved("dQw4w9WgXcQ").saved)
        assertEquals(false, repository.saved("dQw4w9WgXcQ").watchLater)
        assertEquals(setOf("PLexample"), repository.saved("dQw4w9WgXcQ").playlistIds)

        repository.setWatchLater("dQw4w9WgXcQ")
        assertEquals(true, repository.saved("dQw4w9WgXcQ").watchLater)
        assertEquals(setOf("PLexample", "WL"), repository.saved("dQw4w9WgXcQ").playlistIds)
        assertEquals(true, repository.saved("dQw4w9WgXcQ").saved)
    }

    @Test fun savedStateFromDialogOverridesUnknownSeed() {
        assertNull(repository.saved("dQw4w9WgXcQ").saved)
        repository.seed("dQw4w9WgXcQ", EngagementSnapshot(saved = false))
        assertEquals(false, repository.saved("dQw4w9WgXcQ").saved)
        repository.setSaved("dQw4w9WgXcQ", true)
        assertEquals(true, repository.saved("dQw4w9WgXcQ").saved)
    }
}
