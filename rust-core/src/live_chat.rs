use serde_json::Value;
use std::collections::BTreeMap;

use crate::html_extract::{extract_yt_initial_data_json, extract_ytcfg};

#[derive(Clone, Debug)]
pub(crate) struct CustomEmoji {
    emoji_id: String,
    shortcut: String,
    image_url: String,
    is_custom: bool,
}

#[derive(Clone, Debug)]
pub(crate) struct Badge {
    badge_type: String,
    image_url: Option<String>,
    tooltip: Option<String>,
}

#[derive(Clone, Debug)]
pub(crate) enum ActionPanel {
    Input {
        send_params: String,
        author_name: String,
        author_photo_url: Option<String>,
        badges: Vec<Badge>,
        custom_emojis: Vec<CustomEmoji>,
    },
    SignInRequired {
        button_text: String,
        subtext: Option<String>,
    },
    Restricted {
        message: String,
        icon_type: Option<String>,
        learn_more_label: Option<String>,
        learn_more_url: Option<String>,
        is_subscribers_only: bool,
    },
}

#[derive(Clone, Debug)]
pub(crate) struct Message {
    id: String,
    author: String,
    text: String,
    offset: Option<i64>,
    emojis: BTreeMap<String, String>,
    author_photo_url: Option<String>,
    badges: Vec<Badge>,
    badge: Option<String>,
    purchase_amount: Option<String>,
    is_super_chat: bool,
    is_membership: bool,
    is_system_notice: bool,
    membership_subtext: Option<String>,
    membership_header: Option<String>,
    action_label: Option<String>,
    action_url: Option<String>,
}

#[derive(Clone, Debug)]
pub struct ParsedChatBatch {
    pub(crate) messages: Vec<Message>,
    pub continuation: Option<String>,
    pub seek_continuation: Option<String>,
    pub chosen_reload_token: Option<String>,
    pub timeout_ms: i64,
    pub last_offset: Option<i64>,
    pub(crate) action_panel: Option<ActionPanel>,
    pub(crate) pinned_message: Option<Message>,
    pub unpin_banner: bool,
    pub mode_notice: Option<String>,
}

pub struct HtmlBootstrapResult {
    pub found: bool,
    pub api_key: Option<String>,
    pub context_json: Option<String>,
    pub watch_continuation: Option<String>,
    pub batch: Option<ParsedChatBatch>,
}

fn find_json_object<'a>(val: &'a Value, key: &str) -> Option<&'a Value> {
    match val {
        Value::Object(map) => {
            if let Some(v) = map.get(key) {
                if v.is_object() {
                    return Some(v);
                }
            }
            for (_, v) in map {
                if let Some(found) = find_json_object(v, key) {
                    return Some(found);
                }
            }
            None
        }
        Value::Array(arr) => {
            for v in arr {
                if let Some(found) = find_json_object(v, key) {
                    return Some(found);
                }
            }
            None
        }
        _ => None,
    }
}

fn opt_str<'a>(val: &'a Value, key: &str) -> Option<&'a str> {
    val.get(key).and_then(Value::as_str)
}

fn non_blank(s: Option<&str>) -> Option<String> {
    s.map(str::trim)
        .filter(|t| !t.is_empty())
        .map(str::to_string)
}

fn normalize_url(raw: Option<&str>) -> Option<String> {
    let trimmed = raw.map(str::trim).filter(|s| !s.is_empty())?;
    if trimmed.starts_with("//") {
        Some(format!("https:{trimmed}"))
    } else if trimmed.starts_with('/') {
        Some(format!("https://www.youtube.com{trimmed}"))
    } else if trimmed.starts_with("https://") || trimmed.starts_with("http://") {
        Some(trimmed.to_string())
    } else {
        None
    }
}

fn parse_json_text(val: Option<&Value>, emojis: Option<&mut BTreeMap<String, String>>) -> String {
    let val = match val {
        Some(v) => v,
        None => return String::new(),
    };
    if let Some(simple) = opt_str(val, "simpleText").filter(|s| !s.trim().is_empty()) {
        return simple.to_string();
    }
    let runs = match val.get("runs").and_then(Value::as_array) {
        Some(r) => r,
        None => return String::new(),
    };

    let mut out = String::new();
    let mut emoji_map = emojis;
    for run in runs {
        if let Some(t) = opt_str(run, "text").filter(|s| !s.is_empty()) {
            out.push_str(t);
        } else if let Some(emoji) = run.get("emoji").filter(|v| v.is_object()) {
            let shortcut = emoji
                .get("shortcuts")
                .and_then(Value::as_array)
                .and_then(|a| a.first())
                .and_then(Value::as_str)
                .unwrap_or("");
            let url = emoji
                .get("image")
                .and_then(|im| im.get("thumbnails"))
                .and_then(Value::as_array)
                .and_then(|a| a.last())
                .and_then(|t| opt_str(t, "url"))
                .unwrap_or("");
            if let Some(map) = emoji_map.as_deref_mut() {
                if url.starts_with("https://") {
                    let token = format!("\u{FFF0}{}\u{FFF1}", map.len());
                    map.insert(token.clone(), url.to_string());
                    out.push_str(&token);
                    continue;
                }
            }
            out.push_str(shortcut);
        }
    }
    out
}

fn last_thumb_url(obj: Option<&Value>) -> Option<String> {
    obj?.get("thumbnails")?
        .as_array()?
        .last()
        .and_then(|t| opt_str(t, "url"))
        .map(str::to_string)
}

fn parse_chat_input_renderer(
    input: &Value,
    fallback_author: &str,
    fallback_photo: Option<&str>,
) -> Option<ActionPanel> {
    let send_ep = find_json_object(input, "sendLiveChatMessageEndpoint")?;
    let send_params = non_blank(opt_str(send_ep, "params"))?;

    let author_name = {
        let parsed = parse_json_text(input.get("authorName"), None);
        if parsed.trim().is_empty() {
            fallback_author.to_string()
        } else {
            parsed
        }
    };
    let author_photo_url = last_thumb_url(input.get("authorPhoto"))
        .map(|u| normalize_url(Some(&u)).unwrap_or(u))
        .or_else(|| fallback_photo.map(str::to_string));

    let mut badges = Vec::new();
    if let Some(raw_badges) = input.get("authorBadges").and_then(Value::as_array) {
        for b in raw_badges {
            let b_obj = match b
                .get("liveChatAuthorBadgeRenderer")
                .or_else(|| b.get("authorBadgeRenderer"))
            {
                Some(v) if v.is_object() => v,
                _ => continue,
            };
            let tooltip = non_blank(opt_str(b_obj, "tooltip"));
            let custom_url = last_thumb_url(
                b_obj
                    .get("customThumbnail")
                    .or_else(|| b_obj.get("image")),
            )
            .filter(|s| !s.trim().is_empty());
            let resolved = custom_url
                .as_deref()
                .map(|u| normalize_url(Some(u)).unwrap_or_else(|| u.to_string()));
            badges.push(Badge {
                badge_type: "MEMBER".to_string(),
                image_url: resolved,
                tooltip: Some(tooltip.unwrap_or_else(|| "Member".to_string())),
            });
        }
    }

    let mut custom_emojis = Vec::new();
    if let Some(picker) = find_json_object(input, "liveChatEmojiPickerRenderer") {
        if let Some(cats) = picker.get("categories").and_then(Value::as_array) {
            for c in cats {
                let cat = match c.get("liveChatEmojiPickerCategoryRenderer") {
                    Some(v) => v,
                    None => continue,
                };
                if let Some(emojis) = cat.get("emojis").and_then(Value::as_array) {
                    for em in emojis {
                        let eid = match non_blank(opt_str(em, "emojiId")) {
                            Some(v) => v,
                            None => continue,
                        };
                        let eurl = match last_thumb_url(em.get("image")).filter(|s| !s.trim().is_empty()) {
                            Some(v) => v,
                            None => continue,
                        };
                        let shortcut = em
                            .get("shortcuts")
                            .and_then(Value::as_array)
                            .and_then(|a| a.first())
                            .and_then(Value::as_str)
                            .filter(|s| !s.trim().is_empty())
                            .unwrap_or(":emoji:")
                            .to_string();
                        let is_custom = em
                            .get("isCustomEmoji")
                            .and_then(Value::as_bool)
                            .unwrap_or(true);
                        custom_emojis.push(CustomEmoji {
                            emoji_id: eid,
                            shortcut,
                            image_url: normalize_url(Some(&eurl)).unwrap_or(eurl),
                            is_custom,
                        });
                    }
                }
            }
        }
    }

    Some(ActionPanel::Input {
        send_params,
        author_name,
        author_photo_url,
        badges,
        custom_emojis,
    })
}

fn parse_restricted_participation(restricted: &Value) -> ActionPanel {
    let msg_obj = restricted.get("message");
    let raw_message = parse_json_text(msg_obj, None).trim().to_string();
    let icon_type = non_blank(restricted.get("icon").and_then(|i| opt_str(i, "iconType")));

    let explicit_btn = restricted
        .get("learnMore")
        .and_then(|lm| lm.get("buttonRenderer"))
        .or_else(|| {
            restricted
                .get("buttons")
                .and_then(Value::as_array)
                .and_then(|arr| {
                    arr.iter()
                        .filter_map(|item| item.get("buttonRenderer"))
                        .find(|btn| find_json_object(btn, "urlEndpoint").is_some())
                })
        });

    let mut learn_more_label = explicit_btn
        .map(|b| parse_json_text(b.get("text"), None).trim().to_string())
        .filter(|s| !s.is_empty());
    let mut learn_more_url = explicit_btn
        .and_then(|b| find_json_object(b, "urlEndpoint"))
        .and_then(|ep| normalize_url(opt_str(ep, "url")));

    if learn_more_url.is_none() {
        if let Some(runs) = msg_obj.and_then(|m| m.get("runs")).and_then(Value::as_array) {
            for run in runs {
                if let Some(url) = find_json_object(run, "urlEndpoint")
                    .and_then(|ep| normalize_url(opt_str(ep, "url")))
                {
                    let run_label = opt_str(run, "text").unwrap_or("").trim();
                    learn_more_url = Some(url);
                    if learn_more_label.is_none() && !run_label.is_empty() {
                        learn_more_label = Some(run_label.to_string());
                    }
                    break;
                }
            }
        }
    }

    let clean_message = if let Some(ref lbl) = learn_more_label {
        if raw_message.to_lowercase().ends_with(&lbl.to_lowercase()) {
            let cut = raw_message.len().saturating_sub(lbl.len());
            raw_message[..cut]
                .trim_end_matches(|c: char| c == ' ' || c == '•' || c == '\u{00A0}')
                .to_string()
        } else {
            raw_message.clone()
        }
    } else {
        raw_message.clone()
    };

    let lower = raw_message.to_lowercase();
    let is_subs_only = lower.contains("subscriber")
        || lower.contains("subscribe")
        || lower.contains("pelanggan")
        || learn_more_url
            .as_deref()
            .map(|u| u.to_lowercase().contains("subs_only"))
            .unwrap_or(false);

    ActionPanel::Restricted {
        message: if clean_message.is_empty() {
            "Chat is restricted".to_string()
        } else {
            clean_message
        },
        icon_type,
        learn_more_label,
        learn_more_url,
        is_subscribers_only: is_subs_only,
    }
}

fn parse_action_message_renderer(msg_obj: &Value) -> Option<ActionPanel> {
    let btn = msg_obj
        .get("button")
        .and_then(|b| b.get("buttonRenderer"))
        .or_else(|| find_json_object(msg_obj, "buttonRenderer"));
    let btn_text = btn
        .map(|b| parse_json_text(b.get("text"), None).trim().to_string())
        .unwrap_or_default();
    let btn_lower = btn_text.to_lowercase();
    let has_sign_in = btn
        .map(|b| find_json_object(b, "signInEndpoint").is_some())
        .unwrap_or(false)
        || find_json_object(msg_obj, "signInEndpoint").is_some()
        || btn_lower.contains("sign in")
        || btn_lower.contains("masuk");

    let subtext = msg_obj
        .get("subtext")
        .and_then(|s| s.get("messageSubtextRenderer"))
        .and_then(|msr| msr.get("text"))
        .map(|t| parse_json_text(Some(t), None))
        .filter(|s| !s.trim().is_empty())
        .or_else(|| {
            let t = parse_json_text(msg_obj.get("subtext"), None);
            if t.trim().is_empty() { None } else { Some(t) }
        })
        .or_else(|| {
            let t = parse_json_text(msg_obj.get("text"), None);
            if t.trim().is_empty() { None } else { Some(t) }
        });

    if has_sign_in {
        return Some(ActionPanel::SignInRequired {
            button_text: if btn_text.is_empty() {
                "Sign in to chat".to_string()
            } else {
                btn_text
            },
            subtext: Some(
                subtext.unwrap_or_else(|| "All messages you send will appear publicly".to_string()),
            ),
        });
    }

    let main_text = parse_json_text(msg_obj.get("text"), None).trim().to_string();
    let restricted_text = if !main_text.is_empty() {
        main_text
    } else {
        subtext.unwrap_or_default().trim().to_string()
    };
    if restricted_text.is_empty() {
        return None;
    }

    let url = btn
        .and_then(|b| find_json_object(b, "urlEndpoint"))
        .and_then(|ep| normalize_url(opt_str(ep, "url")))
        .or_else(|| {
            btn.and_then(|b| {
                if find_json_object(b, "channelCreationFormEndpoint").is_some()
                    || find_json_object(b, "createChannelEndpoint").is_some()
                {
                    Some("https://www.youtube.com/create_channel".to_string())
                } else {
                    None
                }
            })
        });

    let lower = restricted_text.to_lowercase();
    Some(ActionPanel::Restricted {
        message: restricted_text,
        icon_type: non_blank(msg_obj.get("icon").and_then(|i| opt_str(i, "iconType"))),
        learn_more_label: if !btn_text.is_empty() && url.is_some() {
            Some(btn_text)
        } else {
            None
        },
        learn_more_url: url,
        is_subscribers_only: lower.contains("subscriber") || lower.contains("pelanggan"),
    })
}

fn parse_action_panel(
    root: &Value,
    fallback_author: &str,
    fallback_photo: Option<&str>,
) -> Option<ActionPanel> {
    let raw_panel = root
        .get("continuationContents")
        .and_then(|c| c.get("liveChatContinuation"))
        .and_then(|l| l.get("actionPanel"))
        .or_else(|| {
            root.get("contents")
                .and_then(|c| c.get("liveChatRenderer"))
                .and_then(|l| l.get("actionPanel"))
        })
        .or_else(|| find_json_object(root, "actionPanel"))
        .or_else(|| root.get("liveChatActionPanelRenderer"));

    let panel_obj = raw_panel.map(|p| p.get("liveChatActionPanelRenderer").unwrap_or(p));
    if let Some(panel) = panel_obj {
        if let Some(inp) = panel.get("liveChatMessageInputRenderer") {
            if let Some(cfg) = parse_chat_input_renderer(inp, fallback_author, fallback_photo) {
                return Some(cfg);
            }
        }
        if let Some(restr) = panel.get("liveChatRestrictedParticipationRenderer") {
            return Some(parse_restricted_participation(restr));
        }
        if let Some(msg) = panel.get("messageRenderer") {
            if let Some(parsed) = parse_action_message_renderer(msg) {
                return Some(parsed);
            }
        }
    }

    if let Some(inp) = find_json_object(root, "liveChatMessageInputRenderer") {
        if let Some(cfg) = parse_chat_input_renderer(inp, fallback_author, fallback_photo) {
            return Some(cfg);
        }
    }
    if let Some(restr) = find_json_object(root, "liveChatRestrictedParticipationRenderer") {
        return Some(parse_restricted_participation(restr));
    }
    None
}

fn parse_notice_item(mode_obj: &Value, offset_ms: Option<i64>) -> Option<Message> {
    let parts = [
        parse_json_text(mode_obj.get("text"), None),
        parse_json_text(mode_obj.get("subtext"), None),
        parse_json_text(mode_obj.get("message"), None),
    ];
    let msg_text = parts
        .iter()
        .filter(|s| !s.trim().is_empty())
        .map(String::as_str)
        .collect::<Vec<_>>()
        .join(" ")
        .trim()
        .to_string();

    let lower = msg_text.to_lowercase();
    let eligible = lower.contains("mode")
        || lower.contains("subscriber")
        || lower.contains("member")
        || lower.contains("pelanggan")
        || lower.contains("anggota")
        || lower.contains("slow");
    if !eligible {
        return None;
    }

    let button = mode_obj
        .get("actionButton")
        .and_then(|ab| ab.get("buttonRenderer"))
        .or_else(|| find_json_object(mode_obj, "buttonRenderer"));
    let action_label = button
        .map(|b| parse_json_text(b.get("text"), None).trim().to_string())
        .filter(|s| !s.is_empty());
    let action_url = button
        .and_then(|b| find_json_object(b, "urlEndpoint"))
        .and_then(|ep| normalize_url(opt_str(ep, "url")));

    let clean_msg = if let Some(ref lbl) = action_label {
        if msg_text.to_lowercase().ends_with(&lbl.to_lowercase()) {
            let cut = msg_text.len().saturating_sub(lbl.len());
            msg_text[..cut].trim_end().to_string()
        } else {
            msg_text.clone()
        }
    } else {
        msg_text.clone()
    };

    let notice_id = non_blank(opt_str(mode_obj, "id")).unwrap_or_else(|| {
        let ts = opt_str(mode_obj, "timestampUsec").unwrap_or("");
        format!("{ts}:{msg_text}")
    });

    Some(Message {
        id: notice_id,
        author: "YouTube".to_string(),
        text: clean_msg,
        offset: offset_ms,
        emojis: BTreeMap::new(),
        author_photo_url: None,
        badges: Vec::new(),
        badge: None,
        purchase_amount: None,
        is_super_chat: false,
        is_membership: false,
        is_system_notice: true,
        membership_subtext: None,
        membership_header: None,
        action_label,
        action_url,
    })
}

fn parse_message_obj(
    message: &Value,
    time: Option<i64>,
    is_super_chat: bool,
    is_membership: bool,
) -> Option<Message> {
    let mut emojis = BTreeMap::new();
    let purchase_amount = parse_json_text(message.get("purchaseAmountText"), None);
    let header_primary = parse_json_text(message.get("headerPrimaryText"), Some(&mut emojis));
    let header_subtext = parse_json_text(message.get("headerSubtext"), Some(&mut emojis));
    let user_message = parse_json_text(message.get("message"), Some(&mut emojis));

    let membership_header = if is_membership {
        if !header_primary.trim().is_empty() {
            Some(header_primary.clone())
        } else if header_subtext.trim().is_empty() {
            Some("Anggota baru".to_string())
        } else {
            None
        }
    } else {
        None
    };

    let membership_sub = if is_membership {
        if !header_subtext.trim().is_empty() {
            Some(header_subtext.clone())
        } else if header_primary.trim().is_empty() {
            Some("Baru saja bergabung menjadi anggota!".to_string())
        } else {
            None
        }
    } else {
        None
    };

    let content = if is_membership {
        if !user_message.trim().is_empty() {
            user_message
        } else if let (Some(h), Some(s)) = (&membership_header, &membership_sub) {
            format!("{h} • {s}")
        } else if let Some(s) = &membership_sub {
            s.clone()
        } else if let Some(h) = &membership_header {
            h.clone()
        } else {
            "Bergabung menjadi anggota".to_string()
        }
    } else if !user_message.trim().is_empty() && !header_subtext.trim().is_empty() {
        format!("{header_subtext} • {user_message}")
    } else if !user_message.trim().is_empty() {
        user_message
    } else if !header_subtext.trim().is_empty() {
        header_subtext
    } else if !header_primary.trim().is_empty() {
        header_primary
    } else if !purchase_amount.trim().is_empty() {
        purchase_amount.clone()
    } else {
        String::new()
    };

    if content.trim().is_empty() {
        return None;
    }

    let photo_url = last_thumb_url(message.get("authorPhoto"));
    let mut badge_list = Vec::new();
    if let Some(badges) = message.get("authorBadges").and_then(Value::as_array) {
        for b in badges {
            let b_obj = match b
                .get("liveChatAuthorBadgeRenderer")
                .or_else(|| b.get("authorBadgeRenderer"))
            {
                Some(v) if v.is_object() => v,
                _ => continue,
            };
            let icon_type = b_obj
                .get("icon")
                .and_then(|i| opt_str(i, "iconType"))
                .unwrap_or("");
            let tooltip = non_blank(opt_str(b_obj, "tooltip")).or_else(|| {
                non_blank(
                    b_obj
                        .get("accessibility")
                        .and_then(|a| a.get("accessibilityData"))
                        .and_then(|ad| opt_str(ad, "label")),
                )
            });

            let custom_url = last_thumb_url(
                b_obj
                    .get("customThumbnail")
                    .or_else(|| b_obj.get("image")),
            )
            .filter(|s| !s.trim().is_empty())
            .or_else(|| non_blank(b_obj.get("customThumbnail").and_then(|c| opt_str(c, "url"))));

            let resolved_url = custom_url.map(|u| {
                if u.starts_with("//") {
                    format!("https:{u}")
                } else {
                    u
                }
            });

            if let Some(url) = resolved_url.filter(|u| !u.trim().is_empty()) {
                badge_list.push(Badge {
                    badge_type: "MEMBER".to_string(),
                    image_url: Some(url),
                    tooltip: Some(tooltip.unwrap_or_else(|| "Member".to_string())),
                });
            } else if icon_type == "MODERATOR" {
                badge_list.push(Badge {
                    badge_type: "MODERATOR".to_string(),
                    image_url: None,
                    tooltip: Some(tooltip.unwrap_or_else(|| "Moderator".to_string())),
                });
            } else if icon_type == "OWNER" {
                badge_list.push(Badge {
                    badge_type: "OWNER".to_string(),
                    image_url: None,
                    tooltip: Some(tooltip.unwrap_or_else(|| "Owner".to_string())),
                });
            } else if icon_type == "VERIFIED" {
                badge_list.push(Badge {
                    badge_type: "VERIFIED".to_string(),
                    image_url: None,
                    tooltip: Some(tooltip.unwrap_or_else(|| "Verified".to_string())),
                });
            } else if icon_type == "MEMBER" {
                badge_list.push(Badge {
                    badge_type: "MEMBER".to_string(),
                    image_url: None,
                    tooltip: Some(tooltip.unwrap_or_else(|| "Member".to_string())),
                });
            } else if tooltip
                .as_deref()
                .map(|t| t.to_lowercase().contains("member"))
                .unwrap_or(false)
            {
                badge_list.push(Badge {
                    badge_type: "MEMBER".to_string(),
                    image_url: None,
                    tooltip,
                });
            }
        }
    }

    if is_membership && !badge_list.iter().any(|b| b.badge_type == "MEMBER") {
        badge_list.push(Badge {
            badge_type: "MEMBER".to_string(),
            image_url: None,
            tooltip: Some("Member".to_string()),
        });
    }

    let primary_badge = if badge_list.iter().any(|b| b.badge_type == "OWNER") {
        Some("OWNER".to_string())
    } else if badge_list.iter().any(|b| b.badge_type == "MODERATOR") {
        Some("MODERATOR".to_string())
    } else if badge_list.iter().any(|b| b.badge_type == "MEMBER") {
        Some("MEMBER".to_string())
    } else {
        badge_list.first().map(|b| b.badge_type.clone())
    };

    let id = non_blank(opt_str(message, "id")).unwrap_or_else(|| {
        let ts = opt_str(message, "timestampUsec").unwrap_or("");
        format!("{ts}:{content}")
    });

    let author = {
        let parsed = parse_json_text(message.get("authorName"), None);
        if !parsed.trim().is_empty() {
            parsed
        } else if let Some(raw) = non_blank(opt_str(message, "authorName")) {
            raw
        } else if is_membership {
            "Member".to_string()
        } else {
            "Viewers".to_string()
        }
    };

    Some(Message {
        id,
        author,
        text: content,
        offset: time,
        emojis,
        author_photo_url: photo_url,
        badges: badge_list,
        badge: primary_badge,
        purchase_amount: if purchase_amount.trim().is_empty() {
            None
        } else {
            Some(purchase_amount)
        },
        is_super_chat,
        is_membership,
        is_system_notice: false,
        membership_subtext: membership_sub,
        membership_header,
        action_label: None,
        action_url: None,
    })
}

fn parse_action(
    action: &Value,
    time: Option<i64>,
    messages: &mut Vec<Message>,
    pinned_message: &mut Option<Message>,
    unpin_banner: &mut bool,
    last_offset: &mut Option<i64>,
) {
    if let Some(replay) = action.get("replayChatItemAction") {
        let current = opt_str(replay, "videoOffsetTimeMsec")
            .and_then(|s| s.parse::<i64>().ok())
            .map(|v| v.max(0));
        if let Some(c) = current {
            *last_offset = Some(c);
        }
        if let Some(nested) = replay.get("actions").and_then(Value::as_array) {
            for item in nested {
                parse_action(
                    item,
                    Some(current.unwrap_or(0)),
                    messages,
                    pinned_message,
                    unpin_banner,
                    last_offset,
                );
            }
        }
        return;
    }

    if action.get("removeBannerForLiveChatCommand").is_some() {
        *unpin_banner = true;
        *pinned_message = None;
        return;
    }

    if let Some(banner_cmd) = action.get("addBannerToLiveChatCommand") {
        let banner_contents = banner_cmd
            .get("bannerRenderer")
            .and_then(|b| b.get("liveChatBannerRenderer"))
            .and_then(|l| l.get("contents"));
        if let Some(bc) = banner_contents {
            let is_banner_mem = bc.get("liveChatMembershipItemRenderer").is_some();
            let banner_msg = bc
                .get("liveChatTextMessageRenderer")
                .or_else(|| bc.get("liveChatPaidMessageRenderer"))
                .or_else(|| bc.get("liveChatMembershipItemRenderer"));
            if let Some(bm) = banner_msg {
                if let Some(parsed) = parse_message_obj(bm, time, false, is_banner_mem) {
                    *pinned_message = Some(parsed);
                    *unpin_banner = false;
                }
            }
        }
        return;
    }

    let item = match action
        .get("addChatItemAction")
        .and_then(|a| a.get("item"))
    {
        Some(v) => v,
        None => return,
    };

    if let Some(mode_obj) = item
        .get("liveChatModeChangeMessageRenderer")
        .or_else(|| item.get("liveChatViewerEngagementMessageRenderer"))
    {
        if let Some(notice) = parse_notice_item(mode_obj, time) {
            messages.push(notice);
        }
        return;
    }

    if let Some(gift_purchase) = item.get("liveChatSponsorshipsGiftPurchaseAnnouncementRenderer") {
        let mut emojis = BTreeMap::new();
        let header_obj = gift_purchase
            .get("header")
            .and_then(|h| h.get("liveChatSponsorshipsHeaderRenderer"));
        let primary = {
            let p = parse_json_text(header_obj.and_then(|h| h.get("primaryText")), Some(&mut emojis));
            if p.trim().is_empty() {
                "Membagikan Langganan Hadiah".to_string()
            } else {
                p
            }
        };
        let user_msg = parse_json_text(gift_purchase.get("message"), Some(&mut emojis));
        let photo_url = last_thumb_url(gift_purchase.get("authorPhoto"));
        let author = {
            let a = parse_json_text(gift_purchase.get("authorName"), None);
            if !a.trim().is_empty() {
                a
            } else {
                non_blank(opt_str(gift_purchase, "authorName")).unwrap_or_else(|| "Sponsor".to_string())
            }
        };
        let id = non_blank(opt_str(gift_purchase, "id")).unwrap_or_else(|| {
            let ts = opt_str(gift_purchase, "timestampUsec").unwrap_or("");
            format!("{ts}:{author}:gift")
        });
        let text = if !user_msg.trim().is_empty() {
            user_msg.clone()
        } else {
            primary.clone()
        };
        messages.push(Message {
            id,
            author,
            text,
            offset: time,
            emojis,
            author_photo_url: photo_url,
            badges: vec![Badge {
                badge_type: "MEMBER".to_string(),
                image_url: None,
                tooltip: Some("Member".to_string()),
            }],
            badge: Some("MEMBER".to_string()),
            purchase_amount: None,
            is_super_chat: false,
            is_membership: true,
            is_system_notice: false,
            membership_subtext: if user_msg.trim().is_empty() {
                None
            } else {
                Some(user_msg)
            },
            membership_header: Some(primary),
            action_label: None,
            action_url: None,
        });
        return;
    }

    if let Some(gift_redeem) = item.get("liveChatSponsorshipsGiftRedemptionAnnouncementRenderer") {
        let mut emojis = BTreeMap::new();
        let user_msg = {
            let m = parse_json_text(gift_redeem.get("message"), Some(&mut emojis));
            if m.trim().is_empty() {
                "Menerima langganan hadiah".to_string()
            } else {
                m
            }
        };
        let photo_url = last_thumb_url(gift_redeem.get("authorPhoto"));
        let author = {
            let a = parse_json_text(gift_redeem.get("authorName"), None);
            if !a.trim().is_empty() {
                a
            } else {
                non_blank(opt_str(gift_redeem, "authorName")).unwrap_or_else(|| "Member".to_string())
            }
        };
        let id = non_blank(opt_str(gift_redeem, "id")).unwrap_or_else(|| {
            let ts = opt_str(gift_redeem, "timestampUsec").unwrap_or("");
            format!("{ts}:{author}:redeem")
        });
        messages.push(Message {
            id,
            author,
            text: user_msg.clone(),
            offset: time,
            emojis,
            author_photo_url: photo_url,
            badges: vec![Badge {
                badge_type: "MEMBER".to_string(),
                image_url: None,
                tooltip: Some("Member".to_string()),
            }],
            badge: Some("MEMBER".to_string()),
            purchase_amount: None,
            is_super_chat: false,
            is_membership: true,
            is_system_notice: false,
            membership_subtext: Some(user_msg),
            membership_header: Some("Menerima Keanggotaan Hadiah".to_string()),
            action_label: None,
            action_url: None,
        });
        return;
    }

    let is_super_chat = item.get("liveChatPaidMessageRenderer").is_some()
        || item.get("liveChatPaidStickerRenderer").is_some();
    let is_membership = item.get("liveChatMembershipItemRenderer").is_some();
    let msg_obj = item
        .get("liveChatTextMessageRenderer")
        .or_else(|| item.get("liveChatPaidMessageRenderer"))
        .or_else(|| item.get("liveChatMembershipItemRenderer"))
        .or_else(|| item.get("liveChatPaidStickerRenderer"));
    if let Some(m) = msg_obj {
        if let Some(parsed) = parse_message_obj(m, time, is_super_chat, is_membership) {
            messages.push(parsed);
        }
    }
}

fn extract_sort_filter_token(renderer: &Value, top_chat: bool) -> Option<String> {
    let sub_menu = find_json_object(renderer, "sortFilterSubMenuRenderer")?
        .get("subMenuItems")?
        .as_array()?;
    let target_idx = if top_chat {
        0
    } else if sub_menu.len() > 1 {
        1
    } else {
        0
    };
    sub_menu
        .get(target_idx)
        .and_then(|it| it.get("continuation"))
        .and_then(|c| c.get("reloadContinuationData"))
        .and_then(|r| non_blank(opt_str(r, "continuation")))
}

fn extract_continuation_token(chat_renderer: &Value) -> Option<String> {
    let conts = chat_renderer.get("continuations")?.as_array()?;
    for c in conts {
        for key in [
            "reloadContinuationData",
            "liveChatReplayContinuationData",
            "timedContinuationData",
            "invalidationContinuationData",
        ] {
            if let Some(tok) = c
                .get(key)
                .and_then(|o| non_blank(opt_str(o, "continuation")))
            {
                return Some(tok);
            }
        }
    }
    None
}

fn parse_renderer(
    root: &Value,
    renderer: &Value,
    fallback_author: &str,
    fallback_photo: Option<&str>,
    top_chat: bool,
) -> ParsedChatBatch {
    let action_panel = parse_action_panel(root, fallback_author, fallback_photo)
        .or_else(|| parse_action_panel(renderer, fallback_author, fallback_photo));

    let mut messages = Vec::new();
    let mut pinned_message = None;
    let mut unpin_banner = false;
    let mut last_offset = None;

    if let Some(actions) = renderer.get("actions").and_then(Value::as_array) {
        for act in actions {
            parse_action(
                act,
                None,
                &mut messages,
                &mut pinned_message,
                &mut unpin_banner,
                &mut last_offset,
            );
        }
    }

    let mut seek_continuation = None;
    let mut next_cont_obj: Option<&Value> = None;
    let mut is_invalidation = false;

    if let Some(continuations) = renderer.get("continuations").and_then(Value::as_array) {
        for c in continuations {
            if let Some(seek_tok) = c
                .get("playerSeekContinuationData")
                .and_then(|o| non_blank(opt_str(o, "continuation")))
            {
                seek_continuation = Some(seek_tok);
            }
            if next_cont_obj.is_none() {
                if let Some(inv) = c.get("invalidationContinuationData").filter(|v| v.is_object()) {
                    next_cont_obj = Some(inv);
                    is_invalidation = true;
                } else {
                    for key in [
                        "timedContinuationData",
                        "liveChatReplayContinuationData",
                        "reloadContinuationData",
                    ] {
                        if let Some(obj) = c.get(key).filter(|v| v.is_object()) {
                            next_cont_obj = Some(obj);
                            break;
                        }
                    }
                }
            }
        }
    }

    let continuation = next_cont_obj.and_then(|o| non_blank(opt_str(o, "continuation")));
    let timeout_ms = if is_invalidation {
        2000
    } else if let Some(obj) = next_cont_obj {
        obj.get("timeoutMs")
            .and_then(Value::as_i64)
            .unwrap_or(2500)
            .clamp(1000, 5000)
    } else {
        2500
    };

    let chosen_reload_token = extract_sort_filter_token(renderer, top_chat);

    ParsedChatBatch {
        messages,
        continuation,
        seek_continuation,
        chosen_reload_token,
        timeout_ms,
        last_offset,
        action_panel,
        pinned_message,
        unpin_banner,
        mode_notice: None,
    }
}

pub fn parse_live_chat_response(
    json_str: &str,
    fallback_author: &str,
    fallback_photo: Option<&str>,
    top_chat: bool,
) -> Result<ParsedChatBatch, String> {
    let root: Value =
        serde_json::from_str(json_str).map_err(|e| format!("Invalid chat JSON: {e}"))?;
    let renderer = root
        .get("continuationContents")
        .and_then(|c| c.get("liveChatContinuation"))
        .or_else(|| root.get("contents").and_then(|c| c.get("liveChatRenderer")))
        .ok_or_else(|| "Unrecognized chat response".to_string())?;
    Ok(parse_renderer(
        &root,
        renderer,
        fallback_author,
        fallback_photo,
        top_chat,
    ))
}

pub fn parse_html_bootstrap(
    html: &str,
    is_live: bool,
    top_chat: bool,
    fallback_author: &str,
    fallback_photo: Option<&str>,
) -> HtmlBootstrapResult {
    let data_json = match extract_yt_initial_data_json(html) {
        Some(s) => s,
        None => {
            return HtmlBootstrapResult {
                found: false,
                api_key: None,
                context_json: None,
                watch_continuation: None,
                batch: None,
            }
        }
    };
    let root: Value = match serde_json::from_str(&data_json) {
        Ok(v) => v,
        Err(_) => {
            return HtmlBootstrapResult {
                found: false,
                api_key: None,
                context_json: None,
                watch_continuation: None,
                batch: None,
            }
        }
    };

    let cfg = extract_ytcfg(html);
    let api_key = cfg.as_ref().map(|c| c.api_key.clone());
    let context_json = cfg.as_ref().map(|c| c.context_json.clone());

    if !is_live {
        let chat = find_json_object(&root, "liveChatRenderer");
        let watch_cont = chat.and_then(extract_continuation_token);
        return HtmlBootstrapResult {
            found: chat.is_some() && cfg.is_some() && watch_cont.is_some(),
            api_key,
            context_json,
            watch_continuation: watch_cont,
            batch: None,
        };
    }

    let renderer = root
        .get("contents")
        .and_then(|c| c.get("liveChatRenderer"))
        .or_else(|| {
            root.get("continuationContents")
                .and_then(|c| c.get("liveChatContinuation"))
        });

    match renderer {
        Some(r) => {
            let batch = parse_renderer(&root, r, fallback_author, fallback_photo, top_chat);
            HtmlBootstrapResult {
                found: true,
                api_key,
                context_json,
                watch_continuation: None,
                batch: Some(batch),
            }
        }
        None => HtmlBootstrapResult {
            found: false,
            api_key,
            context_json,
            watch_continuation: None,
            batch: None,
        },
    }
}

fn write_str(buf: &mut Vec<u8>, s: &str) {
    let bytes = s.as_bytes();
    buf.extend_from_slice(&(bytes.len() as u32).to_be_bytes());
    buf.extend_from_slice(bytes);
}

fn write_opt_str(buf: &mut Vec<u8>, s: Option<&str>) {
    match s {
        Some(v) => {
            buf.push(1);
            write_str(buf, v);
        }
        None => buf.push(0),
    }
}

fn write_badge(buf: &mut Vec<u8>, b: &Badge) {
    write_str(buf, &b.badge_type);
    write_opt_str(buf, b.image_url.as_deref());
    write_opt_str(buf, b.tooltip.as_deref());
}

fn write_message(buf: &mut Vec<u8>, m: &Message) {
    write_str(buf, &m.id);
    write_str(buf, &m.author);
    write_str(buf, &m.text);
    match m.offset {
        Some(o) => {
            buf.push(1);
            buf.extend_from_slice(&o.to_be_bytes());
        }
        None => buf.push(0),
    }
    buf.extend_from_slice(&(m.emojis.len() as u32).to_be_bytes());
    for (k, v) in &m.emojis {
        write_str(buf, k);
        write_str(buf, v);
    }
    write_opt_str(buf, m.author_photo_url.as_deref());
    buf.extend_from_slice(&(m.badges.len() as u32).to_be_bytes());
    for b in &m.badges {
        write_badge(buf, b);
    }
    write_opt_str(buf, m.badge.as_deref());
    write_opt_str(buf, m.purchase_amount.as_deref());
    let mut flags = 0u8;
    if m.is_super_chat {
        flags |= 1;
    }
    if m.is_membership {
        flags |= 2;
    }
    if m.is_system_notice {
        flags |= 4;
    }
    buf.push(flags);
    write_opt_str(buf, m.membership_subtext.as_deref());
    write_opt_str(buf, m.membership_header.as_deref());
    write_opt_str(buf, m.action_label.as_deref());
    write_opt_str(buf, m.action_url.as_deref());
}

fn write_batch(buf: &mut Vec<u8>, b: &ParsedChatBatch) {
    write_opt_str(buf, b.continuation.as_deref());
    write_opt_str(buf, b.seek_continuation.as_deref());
    write_opt_str(buf, b.chosen_reload_token.as_deref());
    buf.extend_from_slice(&b.timeout_ms.to_be_bytes());
    match b.last_offset {
        Some(o) => {
            buf.push(1);
            buf.extend_from_slice(&o.to_be_bytes());
        }
        None => buf.push(0),
    }
    buf.push(if b.unpin_banner { 1 } else { 0 });
    write_opt_str(buf, b.mode_notice.as_deref());

    match &b.action_panel {
        None => buf.push(0),
        Some(ActionPanel::Input {
            send_params,
            author_name,
            author_photo_url,
            badges,
            custom_emojis,
        }) => {
            buf.push(1);
            write_str(buf, send_params);
            write_str(buf, author_name);
            write_opt_str(buf, author_photo_url.as_deref());
            buf.extend_from_slice(&(badges.len() as u32).to_be_bytes());
            for bg in badges {
                write_badge(buf, bg);
            }
            buf.extend_from_slice(&(custom_emojis.len() as u32).to_be_bytes());
            for em in custom_emojis {
                write_str(buf, &em.emoji_id);
                write_str(buf, &em.shortcut);
                write_str(buf, &em.image_url);
                buf.push(if em.is_custom { 1 } else { 0 });
            }
        }
        Some(ActionPanel::SignInRequired {
            button_text,
            subtext,
        }) => {
            buf.push(2);
            write_str(buf, button_text);
            write_opt_str(buf, subtext.as_deref());
        }
        Some(ActionPanel::Restricted {
            message,
            icon_type,
            learn_more_label,
            learn_more_url,
            is_subscribers_only,
        }) => {
            buf.push(3);
            write_str(buf, message);
            write_opt_str(buf, icon_type.as_deref());
            write_opt_str(buf, learn_more_label.as_deref());
            write_opt_str(buf, learn_more_url.as_deref());
            buf.push(if *is_subscribers_only { 1 } else { 0 });
        }
    }

    match &b.pinned_message {
        Some(p) => {
            buf.push(1);
            write_message(buf, p);
        }
        None => buf.push(0),
    }

    buf.extend_from_slice(&(b.messages.len() as u32).to_be_bytes());
    for m in &b.messages {
        write_message(buf, m);
    }
}

pub fn encode_batch(b: &ParsedChatBatch) -> Vec<u8> {
    let mut buf = Vec::with_capacity(1024 + b.messages.len() * 128);
    write_batch(&mut buf, b);
    buf
}

pub fn encode_bootstrap(res: &HtmlBootstrapResult) -> Vec<u8> {
    let mut buf = Vec::with_capacity(2048);
    buf.push(if res.found { 1 } else { 0 });
    write_opt_str(&mut buf, res.api_key.as_deref());
    write_opt_str(&mut buf, res.context_json.as_deref());
    write_opt_str(&mut buf, res.watch_continuation.as_deref());
    match &res.batch {
        Some(b) => {
            buf.push(1);
            write_batch(&mut buf, b);
        }
        None => buf.push(0),
    }
    buf
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_live_html_bootstrap_with_messages_emojis_and_input_panel() {
        let html = r#"
        <html><script>
        ytcfg.set({"INNERTUBE_API_KEY":"KEY_1","INNERTUBE_CONTEXT":{"client":{"clientName":"WEB"}}});
        window["ytInitialData"] = {
          "contents": {
            "liveChatRenderer": {
              "actionPanel": {
                "liveChatMessageInputRenderer": {
                  "authorName": {"simpleText": "@User"},
                  "sendButton": {"buttonRenderer": {"serviceEndpoint": {"sendLiveChatMessageEndpoint": {"params": "SEND_P"}}}}
                }
              },
              "actions": [
                {
                  "addChatItemAction": {
                    "item": {
                      "liveChatTextMessageRenderer": {
                        "id": "msg-1",
                        "authorName": {"simpleText": "Alice"},
                        "message": {"runs": [
                          {"text": "Hi "},
                          {"emoji": {"shortcuts": [":wave:"], "image": {"thumbnails": [{"url": "https://yt3.ggpht.com/wave.png"}]}}}
                        ]},
                        "authorBadges": [
                          {"liveChatAuthorBadgeRenderer": {"icon": {"iconType": "MODERATOR"}, "tooltip": "Moderator"}}
                        ]
                      }
                    }
                  }
                }
              ],
              "continuations": [
                {"invalidationContinuationData": {"continuation": "CONT_LIVE"}}
              ]
            }
          }
        };
        </script></html>
        "#;

        let boot = parse_html_bootstrap(html, true, true, "@Fallback", None);
        assert!(boot.found);
        assert_eq!(boot.api_key.as_deref(), Some("KEY_1"));
        let batch = boot.batch.as_ref().unwrap();
        assert_eq!(batch.continuation.as_deref(), Some("CONT_LIVE"));
        assert_eq!(batch.timeout_ms, 2000);
        assert_eq!(batch.messages.len(), 1);
        assert_eq!(batch.messages[0].author, "Alice");
        assert_eq!(batch.messages[0].text, "Hi \u{FFF0}0\u{FFF1}");
        assert_eq!(
            batch.messages[0].emojis.get("\u{FFF0}0\u{FFF1}").map(String::as_str),
            Some("https://yt3.ggpht.com/wave.png")
        );
        assert_eq!(batch.messages[0].badge.as_deref(), Some("MODERATOR"));
        assert!(matches!(batch.action_panel, Some(ActionPanel::Input { .. })));

        let encoded = encode_bootstrap(&boot);
        assert!(!encoded.is_empty());
    }

    #[test]
    fn parses_replay_response_with_banner_and_seek_continuation() {
        let json = r#"{
          "continuationContents": {
            "liveChatContinuation": {
              "actions": [
                {
                  "replayChatItemAction": {
                    "videoOffsetTimeMsec": "45000",
                    "actions": [
                      {
                        "addBannerToLiveChatCommand": {
                          "bannerRenderer": {
                            "liveChatBannerRenderer": {
                              "contents": {
                                "liveChatTextMessageRenderer": {
                                  "id": "pin-1",
                                  "authorName": {"simpleText": "Host"},
                                  "message": {"simpleText": "Welcome!"}
                                }
                              }
                            }
                          }
                        }
                      },
                      {
                        "addChatItemAction": {
                          "item": {
                            "liveChatPaidMessageRenderer": {
                              "id": "sc-1",
                              "authorName": {"simpleText": "Bob"},
                              "purchaseAmountText": {"simpleText": "$5.00"},
                              "message": {"simpleText": "Nice stream"}
                            }
                          }
                        }
                      }
                    ]
                  }
                }
              ],
              "continuations": [
                {"playerSeekContinuationData": {"continuation": "SEEK_TOK"}},
                {"liveChatReplayContinuationData": {"continuation": "NEXT_REPLAY", "timeoutMs": 3200}}
              ]
            }
          }
        }"#;

        let batch = parse_live_chat_response(json, "", None, true).unwrap();
        assert_eq!(batch.last_offset, Some(45_000));
        assert_eq!(batch.seek_continuation.as_deref(), Some("SEEK_TOK"));
        assert_eq!(batch.continuation.as_deref(), Some("NEXT_REPLAY"));
        assert_eq!(batch.timeout_ms, 3200);
        assert_eq!(batch.pinned_message.as_ref().unwrap().text, "Welcome!");
        assert_eq!(batch.messages.len(), 1);
        assert!(batch.messages[0].is_super_chat);
        assert_eq!(batch.messages[0].purchase_amount.as_deref(), Some("$5.00"));
        assert_eq!(batch.messages[0].offset, Some(45_000));
    }
}
