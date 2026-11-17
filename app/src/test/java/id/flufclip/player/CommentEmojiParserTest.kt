package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentEmojiParserTest {
    @Test
    fun parseFromNext_extractsModernCommentEntityCustomEmojis() {
        val json = """
            {
              "frameworkUpdates": {
                "entityBatchUpdate": {
                  "mutations": [
                    {
                      "entityKey": "entity-fallback",
                      "payload": {
                        "commentEntityPayload": {
                          "properties": {
                            "commentId": "Ugw123",
                            "content": {
                              "content": "Mantap :_wave: 🔥 :_pog:",
                              "attachmentRuns": [
                                {
                                  "startIndex": 7,
                                  "length": 7,
                                  "element": {
                                    "type": {
                                      "imageType": {
                                        "image": {
                                          "sources": [
                                            {"url": "//yt3.ggpht.com/wave-24.png", "width": 24, "height": 24},
                                            {"url": "//yt3.ggpht.com/wave-48.png", "width": 48, "height": 48}
                                          ]
                                        }
                                      }
                                    }
                                  }
                                },
                                {
                                  "startIndex": 15,
                                  "length": 2,
                                  "element": {
                                    "type": {
                                      "imageType": {
                                        "image": {
                                          "sources": [
                                            {"url": "https://fonts.gstatic.com/s/e/notoemoji/latest/1f525/64.png", "width": 64}
                                          ]
                                        }
                                      }
                                    }
                                  }
                                },
                                {
                                  "startIndex": 18,
                                  "length": 6,
                                  "element": {
                                    "type": {
                                      "imageType": {
                                        "image": {
                                          "sources": [
                                            {"url": "https://yt3.ggpht.com/pog-48.png", "width": 48}
                                          ]
                                        }
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = CommentEmojiParser.parseFromNext(json)
        assertEquals(1, parsed.size)
        val emojis = parsed["Ugw123"]!!
        assertEquals(2, emojis.size)
        assertEquals("https://yt3.ggpht.com/wave-48.png", emojis[":_wave:"])
        assertEquals("https://yt3.ggpht.com/pog-48.png", emojis[":_pog:"])
    }

    @Test
    fun parseFromNext_extractsLegacyCommentRendererCustomEmojis() {
        val json = """
            {
              "onResponseReceivedEndpoints": [
                {
                  "reloadContinuationItemsCommand": {
                    "continuationItems": [
                      {
                        "commentThreadRenderer": {
                          "comment": {
                            "commentRenderer": {
                              "commentId": "UgxLegacy1",
                              "contentText": {
                                "runs": [
                                  {"text": "Halo "},
                                  {
                                    "text": ":_heart:",
                                    "emoji": {
                                      "emojiId": "UC123/heart",
                                      "shortcuts": [":_heart:"],
                                      "isCustomEmoji": true,
                                      "image": {
                                        "thumbnails": [
                                          {"url": "https://yt3.ggpht.com/heart-24.png", "width": 24},
                                          {"url": "https://yt3.ggpht.com/heart-48.png", "width": 48}
                                        ]
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = CommentEmojiParser.parseFromNext(json)
        assertEquals("https://yt3.ggpht.com/heart-48.png", parsed["UgxLegacy1"]?.get(":_heart:"))
    }

    @Test
    fun parseFromNext_ignoresMalformedJsonAndInvalidBounds() {
        assertTrue(CommentEmojiParser.parseFromNext("not-json").isEmpty())

        val outOfBoundsJson = """
            {
              "frameworkUpdates": {
                "entityBatchUpdate": {
                  "mutations": [
                    {
                      "payload": {
                        "commentEntityPayload": {
                          "properties": {
                            "commentId": "UgwBad",
                            "content": {
                              "content": "short",
                              "attachmentRuns": [
                                {
                                  "startIndex": 2,
                                  "length": 20,
                                  "element": {"type": {"imageType": {"image": {"sources": [{"url": "https://yt3.ggpht.com/x.png"}]}}}}
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        assertTrue(CommentEmojiParser.parseFromNext(outOfBoundsJson).isEmpty())
    }

    @Test
    fun findMatches_skipsProtectedLinkRangesAndMatchesAdjacentEmojis() {
        val text = "https://example.com/:_a: :_a::_ab:"
        val emojis = mapOf(
            ":_a:" to "https://yt3.ggpht.com/a.png",
            ":_ab:" to "https://yt3.ggpht.com/ab.png",
        )
        val matches = CommentEmojiParser.findMatches(
            text = text,
            emojis = emojis,
            protectedRanges = listOf(0 until 24),
        )
        assertEquals(2, matches.size)
        assertEquals(":_a:", matches[0].token)
        assertEquals(25..28, matches[0].range)
        assertEquals(":_ab:", matches[1].token)
        assertEquals(29..33, matches[1].range)
    }

    @Test
    fun parseMetadataFromNext_extractsCommentAuthorAvatarUrl() {
        val json = """
            {
              "frameworkUpdates": {
                "entityBatchUpdate": {
                  "mutations": [
                    {
                      "payload": {
                        "commentEntityPayload": {
                          "properties": {
                            "commentId": "UgwAvatar1",
                            "content": {"content": "kirain live malam salah liat jadwal"}
                          },
                          "author": {
                            "displayName": "@itshujan",
                            "avatarThumbnailUrl": "//yt3.ggpht.com/avatar-user=s88"
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val meta = CommentEmojiParser.parseMetadataFromNext(json)
        assertEquals("https://yt3.ggpht.com/avatar-user=s88", meta.avatars["UgwAvatar1"])
    }
}
