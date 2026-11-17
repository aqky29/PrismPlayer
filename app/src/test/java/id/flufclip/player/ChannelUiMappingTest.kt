package id.flufclip.player

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs

class ChannelUiMappingTest {
    @Test
    fun membershipTabPlacedAtFarRightWithoutDuplicates() {
        val raw = listOf(
            ChannelTabs.VIDEOS,
            VideoRepository.TAB_MEMBERSHIP,
            ChannelTabs.SHORTS,
            ChannelTabs.LIVESTREAMS,
            ChannelTabs.PLAYLISTS,
            ChannelTabs.VIDEOS
        )
        val ordered = resolveChannelTabOrder(raw, hasMembership = true)
        assertEquals(
            listOf(
                ChannelTabs.VIDEOS,
                ChannelTabs.SHORTS,
                ChannelTabs.LIVESTREAMS,
                ChannelTabs.PLAYLISTS,
                VideoRepository.TAB_MEMBERSHIP
            ),
            ordered
        )
    }

    @Test
    fun membershipTabOmittedWhenChannelHasNoMembership() {
        val raw = listOf(ChannelTabs.VIDEOS, ChannelTabs.SHORTS, VideoRepository.TAB_MEMBERSHIP)
        val ordered = resolveChannelTabOrder(raw, hasMembership = false)
        assertEquals(listOf(ChannelTabs.VIDEOS, ChannelTabs.SHORTS), ordered)
        assertFalse(VideoRepository.TAB_MEMBERSHIP in ordered)
    }

    @Test
    fun browseStateDefaultsStartCleanly() {
        val state = BrowseState()
        assertEquals(ChannelSortOrder.LATEST, state.channelSort)
        assertEquals(ChannelAccessFilter.ALL, state.channelAccessFilter)
        assertEquals(PlaylistSortOrder.RECENT, state.playlistSort)
        assertTrue(state.channelTab == ChannelTabs.VIDEOS)
    }

    @Test
    fun membershipSurfaceDetectsJoinedPerksAndAvatars() {
        assertFalse(ChannelMembershipUiData().hasMembershipSurface)
        assertTrue(ChannelMembershipUiData(isJoinedMember = true).hasMembershipSurface)
        assertTrue(ChannelMembershipUiData(headerButtonLabel = "See perks").hasMembershipSurface)
        assertTrue(ChannelMembershipUiData(ourMembersAvatars = listOf("https://yt3.example/a.jpg")).hasMembershipSurface)
    }

    @Test
    fun channelTabsIncludeHomeAndPostsInYouTubeOrder() {
        val raw = listOf(
            ChannelTabs.PLAYLISTS,
            VideoRepository.TAB_POSTS,
            ChannelTabs.VIDEOS,
            VideoRepository.TAB_HOME,
            ChannelTabs.SHORTS,
            ChannelTabs.LIVESTREAMS
        )
        val ordered = resolveChannelTabOrder(raw, hasMembership = true)
        assertEquals(
            listOf(
                VideoRepository.TAB_HOME,
                ChannelTabs.VIDEOS,
                ChannelTabs.SHORTS,
                ChannelTabs.LIVESTREAMS,
                ChannelTabs.PLAYLISTS,
                VideoRepository.TAB_POSTS,
                VideoRepository.TAB_MEMBERSHIP
            ),
            ordered
        )
    }

    @Test
    fun activeChannelBrowseIgnoresSearchStateWhenNoPlaylistOpen() {
        val channelInfo = ChannelInfo(
            ServiceList.YouTube.serviceId,
            "UC1234567890",
            "https://www.youtube.com/channel/UC1234567890",
            "https://www.youtube.com/channel/UC1234567890",
            "Demo Channel"
        )
        val channelPage = ChannelPage(info = channelInfo, videos = emptyList())
        assertTrue(isActiveChannelBrowse(channelPage, playlist = null))
        assertFalse(isActiveChannelBrowse(channel = null, playlist = null))
    }

    @Test
    fun formatChannelMetaLinesSeparatesHandleAndStats() {
        val (line1, line2) = formatChannelMetaLines(
            handle = "@windahbasudara",
            subscriberCountText = "20.8M subscribers",
            videoCountText = "6.3K videos"
        )
        assertEquals("@windahbasudara", line1)
        assertEquals("20.8M subscribers · 6.3K videos", line2)

        val (onlyHandle, onlySubs) = formatChannelMetaLines(
            handle = "@demo",
            subscriberCountText = "100 subscribers",
            videoCountText = null
        )
        assertEquals("@demo", onlyHandle)
        assertEquals("100 subscribers", onlySubs)
    }

    @Test
    fun parsesChannelHeaderMetadataLinksHomeShelvesAndTabs() {
        val json = JSONObject(
            """
            {
              "header": {
                "pageHeaderRenderer": {
                  "content": {
                    "pageHeaderViewModel": {
                      "metadata": {
                        "contentMetadataViewModel": {
                          "metadataRows": [
                            {"metadataParts": [{"text": {"content": "@windahbasudara"}}]},
                            {"metadataParts": [{"text": {"content": "20.8M subscribers"}}, {"text": {"content": "6.3K videos"}}]}
                          ]
                        }
                      },
                      "description": {
                        "descriptionPreviewViewModel": {
                          "description": {"content": "Mau di Gaji dari Jadi Clipper?"}
                        }
                      },
                      "attribution": {
                        "attributionViewModel": {
                          "text": {"content": "Instagram and 2 more links"}
                        }
                      }
                    }
                  }
                }
              },
              "engagementPanels": [
                {
                  "channelExternalLinkViewModel": {
                    "title": {"content": "Instagram"},
                    "link": {
                      "content": "instagram.com/windahbasudara",
                      "commandRuns": [{"onTap": {"innertubeCommand": {"urlEndpoint": {"url": "https://www.youtube.com/redirect?q=https%3A%2F%2Finstagram.com%2Fwindahbasudara"}}}}]
                    }
                  }
                }
              ],
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "title": "Home",
                        "selected": true,
                        "endpoint": {"browseEndpoint": {"params": "Eghob21l"}},
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "itemSectionRenderer": {
                                  "contents": [
                                    {
                                      "shelfRenderer": {
                                        "title": {"simpleText": "For You"},
                                        "content": {
                                          "horizontalListRenderer": {
                                            "items": [
                                              {
                                                "gridVideoRenderer": {
                                                  "videoId": "abcdefghijk",
                                                  "title": {"simpleText": "Stream Highlights"},
                                                  "shortViewCountText": {"simpleText": "5.8M views"},
                                                  "publishedTimeText": {"simpleText": "4 weeks ago"}
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    },
                    {
                      "tabRenderer": {
                        "title": "Posts",
                        "selected": false,
                        "endpoint": {"browseEndpoint": {"params": "Eglwb3N0cw"}}
                      }
                    },
                    {
                      "expandableTabRenderer": {
                        "endpoint": {"browseEndpoint": {"params": "EgZzZWFyY2jyBgQKAggB"}}
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val ui = HomeFeed.parseChannelUiData(json)
        assertEquals("@windahbasudara", ui.handle)
        assertEquals("20.8M subscribers", ui.subscriberCountText)
        assertEquals("6.3K videos", ui.videoCountText)
        assertEquals("Mau di Gaji dari Jadi Clipper?", ui.description)
        assertEquals("Instagram and 2 more links", ui.linksSummary)
        assertEquals("https://instagram.com/windahbasudara", ui.links.single().url)
        assertTrue(ui.hasHomeTab)
        assertTrue(ui.hasPostsTab)
        assertEquals("Eglwb3N0cw", ui.postsTabParams)
        assertEquals("EgZzZWFyY2jyBgQKAggB", ui.searchTabParams)
        assertEquals(1, ui.homeShelves.size)
        assertEquals("For You", ui.homeShelves[0].title)
        assertEquals("Stream Highlights", ui.homeShelves[0].videos.single().name)
    }

    @Test
    fun parsesChannelPostsMultiImageCarouselAndContinuation() {
        val json = JSONObject(
            """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "title": "Posts",
                        "selected": true,
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "itemSectionRenderer": {
                                  "contents": [
                                    {
                                      "backstagePostThreadRenderer": {
                                        "post": {
                                          "backstagePostRenderer": {
                                            "postId": "UgkxPost123",
                                            "authorText": {"runs": [{"text": "Windah Basudara"}]},
                                            "publishedTimeText": {"runs": [{"text": "1 month ago"}]},
                                            "contentText": {"runs": [{"text": "Jadwal stream malam ini jam 7!"}]},
                                            "voteCount": {"simpleText": "37K"},
                                            "actionButtons": {
                                              "commentActionButtonsRenderer": {
                                                "replyButton": {
                                                  "buttonRenderer": {"text": {"simpleText": "1.3K"}}
                                                }
                                              }
                                            },
                                            "backstageAttachment": {
                                              "postMultiImageRenderer": {
                                                "images": [
                                                  {"backstageImageRenderer": {"image": {"thumbnails": [{"url": "https://yt3.example/img1.jpg", "width": 800, "height": 600}]}}},
                                                  {"backstageImageRenderer": {"image": {"thumbnails": [{"url": "https://yt3.example/img2.jpg", "width": 800, "height": 600}]}}}
                                                ]
                                              }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    {
                                      "continuationItemRenderer": {
                                        "continuationEndpoint": {
                                          "continuationCommand": {"token": "postNextToken123"}
                                        }
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val page = HomeFeed.parseChannelPostsPage(json)
        val post = page.posts.single()
        assertEquals("UgkxPost123", post.postId)
        assertEquals("Windah Basudara", post.author)
        assertEquals("1 month ago", post.publishedText)
        assertEquals("Jadwal stream malam ini jam 7!", post.text)
        assertEquals(2, post.images.size)
        assertEquals("https://yt3.example/img1.jpg", post.images[0])
        assertEquals("37K", post.voteCountText)
        assertEquals("1.3K", post.replyCountText)
        assertEquals("postNextToken123", page.nextToken)
    }

    @Test
    fun openChannelPageDefaultsToHomeWhenShelvesExistAndClearsSearch() {
        val state = BrowseState()
        state.channelSearchActive = true
        state.channelSearchQuery = "minecraft"
        state.channelSearchSubmitted = "minecraft"

        val channelInfo = ChannelInfo(
            ServiceList.YouTube.serviceId,
            "UC1234567890",
            "https://www.youtube.com/channel/UC1234567890",
            "https://www.youtube.com/channel/UC1234567890",
            "Demo Channel"
        )
        val shelf = ChannelHomeShelf(title = "For You", targetTab = ChannelTabs.VIDEOS)
        val pageWithShelves = ChannelPage(
            info = channelInfo,
            videos = emptyList(),
            uiData = ChannelUiData(hasHomeTab = true, homeShelves = listOf(shelf))
        )

        state.openChannelPage(pageWithShelves)
        assertEquals(VideoRepository.TAB_HOME, state.channelTab)
        assertEquals("", state.channelSearchQuery)
        assertEquals("", state.channelSearchSubmitted)
        assertFalse(state.channelSearchActive)
        assertEquals(1, state.channelHomeShelves.size)

        val pageWithoutShelves = ChannelPage(info = channelInfo, videos = emptyList())
        state.openChannelPage(pageWithoutShelves)
        assertEquals(ChannelTabs.VIDEOS, state.channelTab)
    }
}
