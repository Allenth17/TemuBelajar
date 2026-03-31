package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiralen.temubelajar.core.ui.TBColors
import compose.icons.TablerIcons
import compose.icons.tablericons.Video
import compose.icons.tablericons.VideoOff
import platform.WebRTC.RTCVideoTrack
import platform.WebRTC.RTCMTLVideoView
import platform.WebRTC.RTCVideoRenderMode

@Composable
actual fun LocalVideoView(renderer: Any?, isMuted: Boolean, modifier: Modifier) {
    val track = renderer as? RTCVideoTrack

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (track != null && !isMuted) {
            key(track) {
                UIKitView(
                    factory = {
                        RTCMTLVideoView(frame = platform.CoreGraphics.CGRectZero).apply {
                            videoContentMode = RTCVideoRenderMode.RTCVideoRenderModeFit
                            track.addRenderer(this)
                        }
                    },
                    onRelease = { view ->
                        track.removeRenderer(view)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Icon(TablerIcons.VideoOff, contentDescription = null, tint = TBColors.TextSecondary)
        }
    }
}

@Composable
actual fun RemoteVideoView(renderer: Any?, modifier: Modifier) {
    val track = renderer as? RTCVideoTrack

    Box(modifier = modifier.background(Color(0xFF050510)), contentAlignment = Alignment.Center) {
        if (track != null) {
            key(track) {
                UIKitView(
                    factory = {
                        RTCMTLVideoView(frame = platform.CoreGraphics.CGRectZero).apply {
                            videoContentMode = RTCVideoRenderMode.RTCVideoRenderModeFit
                            track.addRenderer(this)
                        }
                    },
                    onRelease = { view ->
                        track.removeRenderer(view)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(TablerIcons.Video, contentDescription = null, tint = TBColors.TextMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Menunggu video...", color = TBColors.TextMuted, fontSize = 13.sp)
            }
        }
    }
}
