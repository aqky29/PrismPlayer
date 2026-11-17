package id.flufclip.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/** Channel subscribe pill with >=44dp touch target, notification sheet, and unsubscribe confirmation. */
@Composable internal fun SubscribeButton(
    subscribed: Boolean,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
    enabled: Boolean = true,
    uncertain: Boolean = false,
    channelName: String? = null,
    notificationPreference: NotificationPreference = NotificationPreference.PERSONALIZED,
    onNotificationPreference: (NotificationPreference) -> Unit = {},
    fullWidth: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    val wasSubscribed = remember { mutableStateOf(subscribed) }
    val pillScale = remember { Animatable(1f) }
    val bellRotation = remember { Animatable(0f) }

    LaunchedEffect(subscribed) {
        if (subscribed && !wasSubscribed.value) {
            pillScale.animateTo(1.05f, tween(durationMillis = 120, easing = FastOutSlowInEasing))
            pillScale.animateTo(1f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
            for (angle in listOf(-18f, 16f, -10f, 6f, 0f)) {
                bellRotation.animateTo(angle, tween(durationMillis = 85))
            }
        }
        wasSubscribed.value = subscribed
    }

    if (showNotificationSheet) {
        NotificationOptionsSheet(
            channelName = channelName,
            currentPreference = notificationPreference,
            onSelectPreference = { pref -> onNotificationPreference(pref) },
            onUnsubscribe = { showConfirmDialog = true },
            onDismiss = { showNotificationSheet = false }
        )
    }

    if (showConfirmDialog) {
        val targetName = channelName?.takeIf { it.isNotBlank() } ?: "this channel"
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Unsubscribe from $targetName?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onUnsubscribe()
                    }
                ) {
                    Text(
                        "Unsubscribe",
                        color = ErrorText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = Surface,
            textContentColor = TextPrimary,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(16.dp)
        )
    }

    val bellIcon = when (notificationPreference) {
        NotificationPreference.ALL -> Icons.Filled.NotificationsActive
        NotificationPreference.PERSONALIZED -> Icons.Outlined.Notifications
        NotificationPreference.NONE -> Icons.Outlined.NotificationsOff
    }

    val touchShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(touchShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (subscribed) "Subscribed, notification options" else "Subscribe"
            }
            .clickable(enabled = enabled) {
                if (subscribed) {
                    showNotificationSheet = true
                } else {
                    onSubscribe()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .height(36.dp)
                .graphicsLayer { scaleX = pillScale.value; scaleY = pillScale.value }
                .clip(touchShape)
                .background(if (subscribed) Surface else TextPrimary)
                .padding(horizontal = if (subscribed) 12.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (subscribed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = bellIcon,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = bellRotation.value }
                    )
                    Text(
                        text = "Subscribed",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    text = "Subscribe",
                    color = Background,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationOptionsSheet(
    channelName: String? = null,
    currentPreference: NotificationPreference,
    onSelectPreference: (NotificationPreference) -> Unit,
    onUnsubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            val title = channelName?.takeIf { it.isNotBlank() }?.let { "Notifications for $it" } ?: "Notifications"
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            NotificationOptionRow(
                icon = Icons.Filled.NotificationsActive,
                label = "All",
                selected = currentPreference == NotificationPreference.ALL,
                onClick = {
                    onSelectPreference(NotificationPreference.ALL)
                    onDismiss()
                }
            )

            NotificationOptionRow(
                icon = Icons.Outlined.Notifications,
                label = "Personalized",
                selected = currentPreference == NotificationPreference.PERSONALIZED,
                onClick = {
                    onSelectPreference(NotificationPreference.PERSONALIZED)
                    onDismiss()
                }
            )

            NotificationOptionRow(
                icon = Icons.Outlined.NotificationsOff,
                label = "None",
                selected = currentPreference == NotificationPreference.NONE,
                onClick = {
                    onSelectPreference(NotificationPreference.NONE)
                    onDismiss()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable {
                        onDismiss()
                        onUnsubscribe()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonRemove,
                    contentDescription = null,
                    tint = ErrorText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Unsubscribe",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ErrorText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun NotificationOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

