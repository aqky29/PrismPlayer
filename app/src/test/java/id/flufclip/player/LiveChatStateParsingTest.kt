package id.flufclip.player

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatStateParsingTest {

    @Test
    fun `logged out live chat actionPanel messageRenderer parses SignInRequired`() {
        val root = JSONObject(
            """
            {
              "continuationContents": {
                "liveChatContinuation": {
                  "actionPanel": {
                    "messageRenderer": {
                      "button": {
                        "buttonRenderer": {
                          "text": { "runs": [{ "text": "Sign in to chat" }] },
                          "navigationEndpoint": {
                            "signInEndpoint": {}
                          }
                        }
                      },
                      "subtext": {
                        "messageSubtextRenderer": {
                          "text": { "simpleText": "All messages you send will appear publicly" }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val panel = parseLiveChatActionPanel(root)
        assertTrue(panel is LiveChatActionPanel.SignInRequired)
        val signIn = panel as LiveChatActionPanel.SignInRequired
        assertEquals("Sign in to chat", signIn.buttonText)
        assertEquals("All messages you send will appear publicly", signIn.subtext)
    }

    @Test
    fun `logged in eligible liveChatMessageInputRenderer parses Input config`() {
        val root = JSONObject(
            """
            {
              "contents": {
                "liveChatRenderer": {
                  "actionPanel": {
                    "liveChatMessageInputRenderer": {
                      "authorName": { "simpleText": "@Tester" },
                      "authorPhoto": {
                        "thumbnails": [
                          { "url": "//yt3.ggpht.com/avatar.jpg" }
                        ]
                      },
                      "sendButton": {
                        "buttonRenderer": {
                          "serviceEndpoint": {
                            "sendLiveChatMessageEndpoint": {
                              "params": "SEND_PARAMS_123"
                            }
                          }
                        }
                      },
                      "emojiPicker": {
                        "liveChatEmojiPickerRenderer": {
                          "categories": [
                            {
                              "liveChatEmojiPickerCategoryRenderer": {
                                "emojis": [
                                  {
                                    "emojiId": "UC123/hype",
                                    "shortcuts": [":hype:"],
                                    "isCustomEmoji": true,
                                    "image": {
                                      "thumbnails": [{ "url": "//yt3.ggpht.com/hype.png" }]
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
                }
              }
            }
            """.trimIndent()
        )

        val panel = parseLiveChatActionPanel(root)
        assertTrue(panel is LiveChatActionPanel.Input)
        val config = (panel as LiveChatActionPanel.Input).config
        assertEquals("SEND_PARAMS_123", config.sendParams)
        assertEquals("@Tester", config.authorName)
        assertEquals("https://yt3.ggpht.com/avatar.jpg", config.authorPhotoUrl)
        assertEquals(1, config.customEmojis.size)
        assertEquals("https://yt3.ggpht.com/hype.png", config.customEmojis[0].imageUrl)
    }

    @Test
    fun `subscribers only liveChatRestrictedParticipationRenderer parses Restricted with learnMore`() {
        val root = JSONObject(
            """
            {
              "continuationContents": {
                "liveChatContinuation": {
                  "actionPanel": {
                    "liveChatRestrictedParticipationRenderer": {
                      "icon": { "iconType": "LOCK" },
                      "message": {
                        "runs": [
                          { "text": "Subscribers-only mode." }
                        ]
                      },
                      "learnMore": {
                        "buttonRenderer": {
                          "text": { "runs": [{ "text": "Learn more" }] },
                          "navigationEndpoint": {
                            "urlEndpoint": {
                              "url": "//support.google.com/youtube/?p=subs_only_chat_viewer"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val panel = parseLiveChatActionPanel(root)
        assertTrue(panel is LiveChatActionPanel.Restricted)
        val restricted = panel as LiveChatActionPanel.Restricted
        assertEquals("Subscribers-only mode.", restricted.message)
        assertEquals("LOCK", restricted.iconType)
        assertEquals("Learn more", restricted.learnMoreLabel)
        assertEquals("https://support.google.com/youtube/?p=subs_only_chat_viewer", restricted.learnMoreUrl)
        assertTrue(restricted.isSubscribersOnly)
    }

    @Test
    fun `inline link in restricted message runs avoids duplicating learnMore text`() {
        val root = JSONObject(
            """
            {
              "actionPanel": {
                "liveChatRestrictedParticipationRenderer": {
                  "icon": { "iconType": "LOCK" },
                  "message": {
                    "runs": [
                      { "text": "Mode khusus subscriber. " },
                      {
                        "text": "Pelajari lebih lanjut",
                        "navigationEndpoint": {
                          "urlEndpoint": {
                            "url": "https://support.google.com/youtube/?p=subs_only_chat_viewer"
                          }
                        }
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )

        val panel = parseLiveChatActionPanel(root) as? LiveChatActionPanel.Restricted
        assertNotNull(panel)
        assertEquals("Mode khusus subscriber.", panel!!.message)
        assertEquals("Pelajari lebih lanjut", panel.learnMoreLabel)
        assertEquals("https://support.google.com/youtube/?p=subs_only_chat_viewer", panel.learnMoreUrl)
        assertTrue(panel.isSubscribersOnly)
    }

    @Test
    fun `slow mode restriction sets TIMER icon and isSubscribersOnly false`() {
        val root = JSONObject(
            """
            {
              "actionPanel": {
                "liveChatRestrictedParticipationRenderer": {
                  "icon": { "iconType": "TIMER" },
                  "message": { "simpleText": "Slow mode is on. Wait 10 seconds to send a message." }
                }
              }
            }
            """.trimIndent()
        )

        val panel = parseLiveChatActionPanel(root) as? LiveChatActionPanel.Restricted
        assertNotNull(panel)
        assertEquals("TIMER", panel!!.iconType)
        assertFalse(panel.isSubscribersOnly)
    }

    @Test
    fun `continuation without actionPanel returns null so existing panel state persists`() {
        val continuation = JSONObject(
            """
            {
              "continuationContents": {
                "liveChatContinuation": {
                  "actions": [
                    {
                      "addChatItemAction": {
                        "item": {
                          "liveChatTextMessageRenderer": {
                            "id": "m1",
                            "message": { "runs": [{ "text": "Halo" }] }
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

        assertNull(parseLiveChatActionPanel(continuation))
    }

    @Test
    fun `empty chat messageRenderer outside actionPanel is not mistaken for SignInRequired`() {
        val root = JSONObject(
            """
            {
              "continuationContents": {
                "liveChatContinuation": {
                  "actions": [
                    {
                      "addChatItemAction": {
                        "item": {
                          "messageRenderer": {
                            "text": { "runs": [{ "text": "Say something..." }] }
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

        assertNull(parseLiveChatActionPanel(root))
    }

    @Test
    fun `liveChatViewerEngagementMessageRenderer parses clickable learnMore URL cleanly`() {
        val noticeObj = JSONObject(
            """
            {
              "id": "subs-notice-1",
              "icon": { "iconType": "YOUTUBE_ROUND" },
              "message": {
                "runs": [
                  { "text": "Subscribers-only mode. Messages that appear are from people who've subscribed to this channel for 1 minute or longer." }
                ]
              },
              "actionButton": {
                "buttonRenderer": {
                  "text": { "simpleText": "Learn more" },
                  "navigationEndpoint": {
                    "urlEndpoint": {
                      "url": "//support.google.com/youtube/?p=subs_only_chat_viewer&hl=en"
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val item = parseLiveChatNoticeItem(noticeObj, 12_000L)
        assertNotNull(item)
        assertTrue(item!!.isSystemNotice)
        assertEquals(
            "Subscribers-only mode. Messages that appear are from people who've subscribed to this channel for 1 minute or longer.",
            item.text
        )
        assertEquals("Learn more", item.actionLabel)
        assertEquals("https://support.google.com/youtube/?p=subs_only_chat_viewer&hl=en", item.actionUrl)
        assertEquals(12_000L, item.offset)
    }

    @Test
    fun `normalizeLiveChatUrl resolves protocol-relative and relative youtube urls`() {
        assertEquals("https://support.google.com/youtube/answer/2524549", normalizeLiveChatUrl("//support.google.com/youtube/answer/2524549"))
        assertEquals("https://www.youtube.com/channel/UC123", normalizeLiveChatUrl("/channel/UC123"))
        assertEquals("https://youtube.com", normalizeLiveChatUrl("https://youtube.com"))
        assertNull(normalizeLiveChatUrl("   "))
        assertNull(normalizeLiveChatUrl("javascript:alert(1)"))
    }

    @Test
    fun `restricted action messageRenderer prefers text over generic subtext and exposes create channel link`() {
        val root = JSONObject(
            """
            {
              "continuationContents": {
                "liveChatContinuation": {
                  "actionPanel": {
                    "liveChatActionPanelRenderer": {
                      "messageRenderer": {
                        "text": { "simpleText": "Create a channel to join the chat" },
                        "subtext": {
                          "messageSubtextRenderer": {
                            "text": { "simpleText": "All messages that you send will appear publicly" }
                          }
                        },
                        "button": {
                          "buttonRenderer": {
                            "text": { "simpleText": "Create channel" },
                            "navigationEndpoint": {
                              "channelCreationFormEndpoint": {}
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val panel = parseLiveChatActionPanel(root) as? LiveChatActionPanel.Restricted
        assertNotNull(panel)
        assertEquals("Create a channel to join the chat", panel!!.message)
        assertEquals("Create channel", panel.learnMoreLabel)
        assertEquals("https://www.youtube.com/create_channel", panel.learnMoreUrl)
    }
}
