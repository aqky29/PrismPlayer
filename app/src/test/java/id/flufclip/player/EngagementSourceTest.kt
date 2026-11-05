package id.flufclip.player

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngagementSourceTest {
    @Test fun readsLegacyLikeStatusAndSubscriptionFromServer() {
        val snapshot = EngagementSource.parse(JSONObject("""
            {"contents":{"twoColumnWatchNextResults":{"results":{"results":{"contents":[
              {"videoPrimaryInfoRenderer":{"videoActions":{"menuRenderer":{"topLevelButtons":[
                {"segmentedLikeDislikeButtonRenderer":{
                  "likeButton":{"toggleButtonRenderer":{"isToggled":true}},
                  "dislikeButton":{"toggleButtonRenderer":{"isToggled":false}}
                }}
              ]}}}},
              {"videoSecondaryInfoRenderer":{"owner":{"videoOwnerRenderer":{
                "subscribeButton":{"subscribeButtonRenderer":{"channelId":"UCabcdefghijklmnopqrstuv","subscribed":true}}
              }}}}
            ]}}}}}
        """.trimIndent()))
        assertEquals(1, snapshot.rating)
        assertEquals(true, snapshot.subscribed)
        assertEquals("UCabcdefghijklmnopqrstuv", snapshot.channelId)
    }

    @Test fun readsToggleButtonViewModels() {
        val snapshot = EngagementSource.parse(JSONObject("""
            {"contents":{"likeButtonViewModel":{"toggleButtonViewModel":{"isToggled":false}},
              "dislikeButtonViewModel":{"toggleButtonViewModel":{"isToggled":true}}}}
        """.trimIndent()))
        assertEquals(-1, snapshot.rating)
    }

    @Test fun unresolvedEntityStateIsUnknownRatherThanOff() {
        val snapshot = EngagementSource.parse(JSONObject("""
            {"contents":{"likeButtonViewModel":{"stateEntityKey":"like-key"}}}
        """.trimIndent()))
        assertNull(snapshot.rating)
        assertNull(snapshot.subscribed)
    }

    @Test fun entityKeyStateFromFrameworkUpdatesIsApplied() {
        val snapshot = EngagementSource.parse(JSONObject("""
            {"frameworkUpdates":{"entityBatchUpdate":{"mutations":[
              {"payload":{"toggleButtonMutation":{"entityKey":"like-key","toggled":true}}}
            ]}},
            "contents":{"likeButtonViewModel":{"stateEntityKey":"like-key"},
              "dislikeButtonViewModel":{"stateEntityKey":"dislike-key"}}}
        """.trimIndent()))
        assertEquals(1, snapshot.rating)
    }

    @Test fun entityKeyOnTheMutationNodeIsAlsoResolved() {
        val snapshot = EngagementSource.parse(JSONObject("""
            {"frameworkUpdates":{"entityBatchUpdate":{"mutations":[
              {"entityKey":"like-key","payload":{"toggleButtonMutation":{"toggled":true}}}
            ]}},
            "contents":{"likeButtonViewModel":{"stateEntityKey":"like-key"}}}
        """.trimIndent()))
        assertEquals(1, snapshot.rating)
    }

    @Test fun missingEngagementDataStaysUnknown() {
        val snapshot = EngagementSource.parse(JSONObject("""{"contents":{"videoPrimaryInfoRenderer":{}}}"""))
        assertNull(snapshot.rating)
        assertNull(snapshot.subscribed)
        assertNull(snapshot.channelId)
    }
}
