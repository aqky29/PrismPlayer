package id.flufclip.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Composable
internal fun ChannelMembershipOverviewSection(
    page: ChannelPage,
    loadedItems: List<StreamInfoItem>,
    isWideScreen: Boolean,
    onOpenVideo: (StreamInfoItem) -> Unit
) {
    val context = LocalContext.current
    val ui = page.membershipUi
    val channelMembershipUrl = remember(page.info.url) {
        val base = page.info.url.trimEnd('/')
        if (base.isBlank()) "https://www.youtube.com" else "$base/membership"
    }
    val openOnYouTube: () -> Unit = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channelMembershipUrl))) }
    }

    val perks = remember(ui?.perks, loadedItems.size) {
        if (!ui?.perks.isNullOrEmpty()) ui!!.perks
        else {
            val countLabel = if (loadedItems.size >= 10) "${loadedItems.size}+" else loadedItems.size.coerceAtLeast(1).toString()
            listOf(
                ChannelPerkItem("$countLabel Members-only videos", "videos"),
                ChannelPerkItem("Live replays & exclusives", "posts"),
                ChannelPerkItem("Loyalty badges & emojis", "badges")
            )
        }
    }

    val highlighted = remember(ui?.highlighted, loadedItems) {
        when {
            !ui?.highlighted.isNullOrEmpty() -> ui!!.highlighted
            loadedItems.isEmpty() -> emptyList()
            else -> loadedItems
                .sortedByDescending { item -> maxOf(item.viewCount, 0L) + item.duration }
                .take(4)
        }
    }

    val recent = remember(ui?.recentVideos, loadedItems, highlighted) {
        when {
            !ui?.recentVideos.isNullOrEmpty() -> ui!!.recentVideos
            loadedItems.isEmpty() -> emptyList()
            else -> {
                val highlightUrls = highlighted.mapTo(HashSet()) { it.url }
                val distinctRecent = loadedItems.filter { it.url !in highlightUrls }.take(8)
                if (distinctRecent.isNotEmpty()) distinctRecent else loadedItems.take(8)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MembershipStatusHeaderRow(
            page = page,
            ui = ui,
            loadedCount = loadedItems.size,
            onAction = openOnYouTube
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Your perks",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(perks) { perk ->
                    MembershipPerkOutlineCard(perk = perk, onClick = openOnYouTube)
                }
            }
        }

        if (!ui?.ourMembersAvatars.isNullOrEmpty() && ui?.isJoinedMember != true) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ui?.ourMembersTitle ?: "Our members",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = ui?.ourMembersSubtitle ?: "Thank you, channel members!",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui?.ourMembersAvatars.orEmpty()) { avatarUrl ->
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Channel member",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Surface)
                        )
                    }
                }
            }
        }

        if (highlighted.isNotEmpty()) {
            MembershipCarouselShelf(
                title = "Highlighted content for members",
                items = highlighted,
                cardWidth = if (isWideScreen) 304.dp else 272.dp,
                onOpenVideo = onOpenVideo
            )
        }

        if (recent.isNotEmpty()) {
            MembershipCarouselShelf(
                title = "Recent members-only videos",
                items = recent,
                cardWidth = if (isWideScreen) 240.dp else 212.dp,
                onOpenVideo = onOpenVideo
            )
        }

        if (loadedItems.isNotEmpty()) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
            Text(
                text = "All members-only videos",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
            )
        }
    }
}

@Composable
private fun MembershipStatusHeaderRow(
    page: ChannelPage,
    ui: ChannelMembershipUiData?,
    loadedCount: Int,
    onAction: () -> Unit
) {
    val isJoined = ui?.isJoinedMember == true
    val title = ui?.memberTierTitle?.takeIf { it.isNotBlank() } ?: "${page.info.name} Membership"
    val statusText = ui?.memberStatusLabel?.takeIf { it.isNotBlank() } ?: if (isJoined) "Member" else "Membership perks"
    val subText = ui?.memberTenureLabel?.takeIf { it.isNotBlank() }
        ?: if (loadedCount > 0) "$loadedCount+ members-only videos & replays" else "Become a member on YouTube"
    val pillLabel = if (isJoined) "Upgrade" else "Join"
    val avatarUrl = ui?.memberAvatarUrl?.takeIf { it.isNotBlank() } ?: page.info.avatars.lastOrNull()?.url

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(if (isJoined) Color(0xFF1E7E34) else Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = page.info.name.firstOrNull()?.uppercase() ?: "M",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .offset(x = 2.dp, y = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E8E3E))
                    .border(2.dp, Background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusText,
                color = if (isJoined) CallToActionBlue else MembershipGreen,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            Text(
                text = subText,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Surface)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pillLabel,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        IconButton(onClick = onAction, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Membership options on YouTube",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun MembershipPerkOutlineCard(
    perk: ChannelPerkItem,
    onClick: () -> Unit
) {
    val icon = when (perk.kind) {
        "posts" -> Icons.AutoMirrored.Outlined.Article
        "badges" -> Icons.Default.Star
        else -> Icons.Outlined.VideoLibrary
    }
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 176.dp, max = 256.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (perk.kind == "badges") MembershipGreen else TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = perk.title,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MembershipCarouselShelf(
    title: String,
    items: List<StreamInfoItem>,
    cardWidth: androidx.compose.ui.unit.Dp,
    onOpenVideo: (StreamInfoItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.url }) { stream ->
                MembershipShelfVideoCard(
                    item = stream,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onOpenVideo(stream) }
                )
            }
        }
    }
}
