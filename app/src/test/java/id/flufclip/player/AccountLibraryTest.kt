package id.flufclip.player

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountLibraryTest {
    @Test fun readsServerCheckboxStateAndBothEditCommands() {
        val json = JSONObject("""
            {"contents":{"addToPlaylistRenderer":{"playlists":[
              {"playlistAddToOptionRenderer":{
                "playlistId":"PLexample", "title":{"simpleText":"Saved"},
                "containsSelectedVideos":"ALL",
                "addToPlaylistServiceEndpoint":{"playlistEditEndpoint":{"playlistId":"PLexample","actions":[{"action":"ACTION_ADD_VIDEO","addedVideoId":"abcdefghijk"}]}},
                "removeFromPlaylistServiceEndpoint":{"playlistEditEndpoint":{"playlistId":"PLexample","actions":[{"action":"ACTION_REMOVE_VIDEO_BY_VIDEO_ID","removedVideoId":"abcdefghijk"}]}}
              }}
            ]}}}
        """.trimIndent())
        val option = AccountLibrary.parseChoices(json).single()
        assertEquals("PLexample", option.id)
        assertEquals("Saved", option.title)
        assertTrue(option.selected)
        assertNotNull(option.add?.optJSONObject("playlistEditEndpoint"))
        assertNotNull(option.remove?.optJSONObject("playlistEditEndpoint"))
    }

    @Test fun parsesLibraryPlaylistLockupsWithoutConfusingVideos() {
        val json = JSONObject("""
            {"contents":[
              {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","contentId":"LL",
                "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i.ytimg.com/vi/a/hqdefault.jpg"}]}}},
                "metadata":{"lockupMetadataViewModel":{"title":{"content":"Liked videos"}}}}},
              {"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_VIDEO","contentId":"abcdefghijk"}}
            ]}
        """.trimIndent())
        val playlist = AccountLibrary.parsePlaylists(json).single()
        assertEquals("LL", playlist.id)
        assertEquals("Liked videos", playlist.title)
        assertTrue(playlist.thumbnail.startsWith("https://i.ytimg.com/"))
    }

    @Test(expected = IllegalStateException::class)
    fun doesNotTreatUnrecognizedPickerAsAnEmptyPlaylist() {
        AccountLibrary.parseChoices(JSONObject("""{"error":{"message":"unavailable"}}"""))
    }

    @Test fun parsesVideoLockupFromLikedVideos() {
        val json = JSONObject("""
            {"contents":{"sectionListRenderer":{"contents":[
              {"itemSectionRenderer":{"contents":[{"lockupViewModel":{
                "contentType":"LOCKUP_CONTENT_TYPE_VIDEO", "contentId":"abcdefghijk",
                "metadata":{"lockupMetadataViewModel":{"title":{"content":"A video"}}}
              }}]}}
            ]}}}
        """.trimIndent())
        val video = AccountLibrary.parseVideos(json).videos.single()
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", video.url)
        assertEquals("A video", video.title)
    }

    @Test fun parsesPodcastPlaylistHeaderAndEnrichesHistoryEntries() {
        val json = JSONObject("""
            {
              "header":{"pageHeaderRenderer":{"pageTitle":"ASMR","content":{"pageHeaderViewModel":{
                "title":{"dynamicTextViewModel":{"text":{"content":"ASMR"}}},
                "metadata":{"contentMetadataViewModel":{"metadataRows":[
                  {"metadataParts":[{"avatarStack":{"avatarStackViewModel":{"text":{"content":"by UNIS"}}}}]},
                  {"metadataParts":[{"text":{"content":"Podcast"}},{"text":{"content":"12 episodes"}}]}
                ]}}
              }}}},
              "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                {"itemSectionRenderer":{"contents":[{"playlistVideoListRenderer":{"contents":[
                  {"lockupViewModel":{
                    "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
                    "contentId":"BJpHCbsQmOg",
                    "metadata":{"lockupMetadataViewModel":{"title":{"content":"[UNIS ASMR] EP.12"},
                      "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"UNIS"}}]}]}}}}
                  }},
                  {"lockupViewModel":{
                    "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
                    "contentId":"K-S9AE0BVkE",
                    "metadata":{"lockupMetadataViewModel":{"title":{"content":"[UNIS ASMR] EP.01"},
                      "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"UNIS"}}]}]}}}}
                  }}
                ]}}]}}
              ]}}}}]}}
            }
        """.trimIndent())
        val page = AccountLibrary.parseVideos(json, playlistId = "PL7_cg21n5P1s83sN3To8VknroEDQamKNh")
        assertNotNull(page.header)
        assertEquals("ASMR", page.header?.title)
        assertEquals("UNIS", page.header?.owner)
        assertEquals("12 episodes", page.header?.count)
        assertEquals(2, page.videos.size)
        assertEquals("PL7_cg21n5P1s83sN3To8VknroEDQamKNh", page.videos[0].playlistId)
        assertEquals("ASMR", page.videos[0].playlistTitle)

        WatchHistory.rememberPlaylistVideos(
            null,
            PlaylistRef("PL7_cg21n5P1s83sN3To8VknroEDQamKNh", "ASMR", "12 videos", "UNIS"),
            page.videos.map { it.url }
        )
        val enriched = WatchHistory.enrichVideo(
            WatchedVideo("https://www.youtube.com/watch?v=BJpHCbsQmOg", "[UNIS ASMR] EP.12", "UNIS", "", 0L)
        )
        assertEquals("PL7_cg21n5P1s83sN3To8VknroEDQamKNh", enriched.playlistId)
        assertEquals("ASMR", enriched.playlistTitle)
        assertEquals("12 videos", enriched.playlistVideoCount)

        val preview = accountHistoryPreview(
            listOf(
                AccountHistoryEntry(enriched, "Today"),
                AccountHistoryEntry(
                    WatchHistory.enrichVideo(
                        WatchedVideo("https://www.youtube.com/watch?v=K-S9AE0BVkE", "[UNIS ASMR] EP.01", "UNIS", "", 0L)
                    ),
                    "Today"
                )
            )
        )
        assertEquals(1, preview.size)
        assertEquals("ASMR", preview.single().playlistTitle)
    }

    @Test fun readsChoicePrivacyAndThumbnailFromAddToPlaylistOption() {
        val json = JSONObject("""
            {"contents":{"addToPlaylistRenderer":{"playlists":[
              {"playlistAddToOptionRenderer":{
                "playlistId":"WL",
                "title":{"simpleText":"Watch later"},
                "containsSelectedVideos":"NONE",
                "privacyIcon":{"iconType":"PRIVACY_PRIVATE"},
                "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"}]}
              }}
            ]}}}
        """.trimIndent())
        val option = AccountLibrary.parseChoices(json).single()
        assertEquals("WL", option.id)
        assertEquals("Private", option.privacy)
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", option.thumbnail)
    }
}
