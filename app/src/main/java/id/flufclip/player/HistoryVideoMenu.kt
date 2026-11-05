package id.flufclip.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal data class HistoryActions(
    val queue: (WatchedVideo, Boolean) -> Unit,
    val watchLater: (WatchedVideo) -> Unit,
    val save: (WatchedVideo) -> Unit,
    val share: (WatchedVideo) -> Unit,
    val remove: (WatchedVideo) -> Unit,
    val castScreen: () -> Unit,
)

@Composable
internal fun HistoryVideoMenu(video: WatchedVideo, actions: HistoryActions, compact: Boolean = false) {
    var open by remember(video.url) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(if (compact) 28.dp else 40.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                "Video options",
                modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                tint = androidx.compose.ui.graphics.Color(0xFFCCCCCC)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Cast screen") }, leadingIcon = { Icon(painterResource(R.drawable.yt_cast), null) }, onClick = { open = false; actions.castScreen() })
            DropdownMenuItem(text = { Text("Play next in queue") }, leadingIcon = { Icon(painterResource(R.drawable.yt_history_queue_next), null) }, onClick = { open = false; actions.queue(video, true) })
            DropdownMenuItem(text = { Text("Play last in queue") }, leadingIcon = { Icon(painterResource(R.drawable.yt_history_queue_last), null) }, onClick = { open = false; actions.queue(video, false) })
            DropdownMenuItem(text = { Text("Save to Watch later") }, leadingIcon = { Icon(painterResource(R.drawable.yt_w_history), null) }, onClick = { open = false; actions.watchLater(video) })
            DropdownMenuItem(text = { Text("Save to playlist") }, leadingIcon = { Icon(painterResource(R.drawable.yt_w_save), null) }, onClick = { open = false; actions.save(video) })
            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(painterResource(R.drawable.yt_w_share), null) }, onClick = { open = false; actions.share(video) })
            DropdownMenuItem(text = { Text("Remove from watch history") }, leadingIcon = { Icon(Icons.Default.Close, null) }, onClick = { open = false; actions.remove(video) })
        }
    }
}
