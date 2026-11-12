package com.vynetic.music

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

val VyneticBg = Color(0xFFE9EAED)
val VyneticCard = Color(0xDDE3E5E9)
val VyneticBlue = Color(0xFF17191D)
val VyneticMint = Color(0xFF3C424B)
val VyneticMuted = Color(0xFF69717C)

class MainActivity : ComponentActivity() {
    var controller: MediaController? = null
        private set
    private var controllerFuture: ListenableFuture<MediaController>? = null

    var currentTrack by mutableStateOf<Track?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var status by mutableStateOf("Cari lagu, artis, atau judul musik.")
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    private var queue: List<Track> = emptyList()
    private var queueIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            durationMs = controller?.duration?.coerceAtLeast(0L) ?: 0L
                        }
                        if (playbackState == Player.STATE_ENDED) next()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        status = "Error pemutaran: ${error.message ?: "gagal memuat audio"}"
                        playing = false
                    }
                })
            }.onFailure { status = "Player belum siap: ${it.message ?: "error"}" }
        }, { command -> runOnUiThread(command) })

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { InnerTubeRepository.initialize() }
        }
        lifecycleScope.launch {
            while (true) {
                controller?.let { player ->
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L
                }
                delay(500)
            }
        }

        setContent { VyneticRoot(this) }
    }

    fun play(track: Track, newQueue: List<Track> = listOf(track)) {
        queue = newQueue.ifEmpty { listOf(track) }
        queueIndex = queue.indexOfFirst { it.url == track.url }.coerceAtLeast(0)
        playResolved(track)
    }

    private fun playResolved(track: Track) {
        lifecycleScope.launch {
            try {
                status = "Menyiapkan audio…"
                val quality = getSharedPreferences("vynetic_settings", 0)
                    .getString("quality", "Auto (terbaik tersedia)") ?: "Auto (terbaik tersedia)"
                val streamUrl = withContext(Dispatchers.IO) {
                    InnerTubeRepository.resolveAudioUrl(track.url, quality)
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnail.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build()
                val item = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(metadata)
                    .build()

                val player = controller
                if (player == null) {
                    status = "Player belum tersambung. Sedang menghubungkan..."
                    return@launch
                }
                player.setMediaItem(item)
                player.prepare()
                player.play()
                currentTrack = track
                status = "Sedang diputar"
            } catch (error: Throwable) {
                status = when {
                    error.message?.contains("age", true) == true -> "Konten ini memiliki batasan usia/wilayah."
                    else -> "Tidak dapat memutar lagu: ${error.message ?: "sumber sedang bermasalah"}"
                }
            }
        }
    }

    fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        if (queue.isEmpty()) return
        queueIndex = (queueIndex + 1) % queue.size
        playResolved(queue[queueIndex])
    }

    fun previous() {
        if (queue.isEmpty()) return
        if ((controller?.currentPosition ?: 0L) > 5000L) {
            controller?.seekTo(0L)
            return
        }
        queueIndex = if (queueIndex <= 0) queue.lastIndex else queueIndex - 1
        playResolved(queue[queueIndex])
    }

    fun seekTo(value: Long) {
        controller?.seekTo(value.coerceAtLeast(0L))
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun VyneticRoot(activity: MainActivity) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2300)
        showSplash = false
    }
    if (showSplash) VyneticSplash() else VyneticApp(activity)
}

@Composable
private fun VyneticSplash() {
    val infinite = rememberInfiniteTransition(label = "splash")
    val glow by infinite.animateFloat(
        initialValue = 0.28f, targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val bars = listOf(15, 27, 39, 23, 48, 31, 19, 38, 25, 14)
    MaterialTheme(colorScheme = lightColorScheme(
        background = VyneticBg, surface = VyneticCard, primary = VyneticBlue, secondary = VyneticMint
    )) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F1F3), Color(0xFFE4E6E9)),
                    radius = 850f
                )
            ), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(104.dp).clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF777D87), Color(0xFF252930), Color(0xFF08090B))))
                        .border(1.dp, Color.White.copy(alpha = glow * .55f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", color = Color(0xFFF4F5F7), fontSize = 66.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(24.dp))
                Text("VYNETIC", color = Color(0xFF202329), fontSize = 29.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 7.sp)
                Text("YOUR SOUND. YOUR SPACE.", color = VyneticBlue.copy(alpha = .9f),
                    fontSize = 9.sp, letterSpacing = 2.2.sp, modifier = Modifier.padding(top = 8.dp))
                Row(
                    Modifier.padding(top = 30.dp).height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bars.forEachIndexed { index, height ->
                        val animated by infinite.animateFloat(
                            initialValue = .35f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(450 + index * 65, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ), label = "wave$index"
                        )
                        Box(Modifier.width(4.dp).height((height * animated).dp)
                            .clip(CircleShape).background(Brush.verticalGradient(
                                listOf(Color(0xFF858B95), Color(0xFF202329))
                            )))
                    }
                }
                Text("V.1.00  •  BETA", color = VyneticMuted, fontSize = 10.sp,
                    letterSpacing = 2.sp, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun VyneticApp(activity: MainActivity) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Track>()) }
    var favorites by remember { mutableStateOf(loadFavorites(activity)) }
    var loading by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var showFullPlayer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shownTracks = if (tab == 2) favorites else results

    MaterialTheme(colorScheme = lightColorScheme(
        background = VyneticBg,
        surface = VyneticCard,
        primary = VyneticBlue,
        secondary = VyneticMint
    )) {
        Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFF1F2F4), Color(0xFFE5E7EA), Color(0xFFD9DCE1)))
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B919B), Color(0xFF353A43), Color(0xFF090A0D)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", color = VyneticBg, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("VYNETIC", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("YOUR SOUND. YOUR SPACE.", fontSize = 9.sp, color = VyneticMuted, letterSpacing = 1.5.sp)
                }
                Surface(color = VyneticCard, shape = CircleShape) {
                    Text("V.1.00  •  BETA", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = VyneticMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                Text(
                    when (tab) { 2 -> "Your favorites"; 1 -> "Search music"; else -> "Discover your sound" },
                    fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202329)
                )
                Text("Temukan musik yang cocok dengan suasana hatimu.",
                    color = VyneticMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))

                if (tab != 2) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .vyneticGlass(RoundedCornerShape(18.dp)).padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = VyneticBlue)
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Cari lagu atau artis…", color = VyneticMuted) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = VyneticMint
                            )
                        )
                        Button(
                            onClick = {
                                if (query.isNotBlank()) scope.launch {
                                    loading = true
                                    activity.status = "Mencari musik…"
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            InnerTubeRepository.searchMusic(query.trim())
                                        }
                                    }.onSuccess {
                                        results = it
                                        activity.status = if (it.isEmpty()) "Tidak ada hasil. Coba kata kunci lain."
                                        else "${it.size} lagu ditemukan."
                                    }.onFailure {
                                        activity.status = "Pencarian gagal: ${it.message ?: "periksa koneksi"}"
                                    }
                                    loading = false
                                }
                            },
                            enabled = !loading && query.isNotBlank(),
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Cari", fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        Modifier.padding(top = 13.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Music", "YouTube Music", "Online").forEach {
                            Surface(color = VyneticCard, shape = CircleShape) {
                                Text(it, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (it == "Music") VyneticMint else VyneticMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Text(activity.status, color = VyneticMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp))

                if (shownTracks.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 55.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (tab == 2) "♡" else "♫", color = VyneticBlue, fontSize = 56.sp)
                        Text(if (tab == 2) "Belum ada favorit" else "Your next favorite song is waiting",
                            color = Color(0xFF202329), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (tab == 2) "Tekan ikon hati pada lagu untuk menyimpannya."
                            else "Masukkan judul lagu atau nama artis di kolom pencarian.",
                            color = VyneticMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(shownTracks, key = { it.url }) { track ->
                            TrackRow(
                                track = track,
                                favorite = favorites.any { it.url == track.url },
                                onFavorite = {
                                    favorites = if (favorites.any { it.url == track.url }) {
                                        favorites.filterNot { it.url == track.url }
                                    } else favorites + track
                                    saveFavorites(activity, favorites)
                                },
                                onPlay = { activity.play(track, shownTracks) }
                            )
                        }
                    }
                }
            }

            activity.currentTrack?.let { track ->
                MiniPlayer(
                    track = track,
                    playing = activity.playing,
                    position = activity.positionMs,
                    duration = activity.durationMs,
                    onPrevious = { activity.previous() },
                    onToggle = { activity.toggle() },
                    onNext = { activity.next() },
                    onSeek = { activity.seekTo(it) },
                    onOpen = { showFullPlayer = true }
                )
            }

            if (showFullPlayer) activity.currentTrack?.let { track ->
                AdvancedFeatures(
                    activity = activity,
                    track = track,
                    playing = activity.playing,
                    position = activity.positionMs,
                    duration = activity.durationMs,
                    onClose = { showFullPlayer = false },
                    onToggle = { activity.toggle() },
                    onNext = { activity.next() },
                    onPrevious = { activity.previous() },
                    onSeek = { activity.seekTo(it) },
                    onPlay = { selected, list -> activity.play(selected, list) }
                )
            }

            NavigationBar(containerColor = Color(0xFFE2E4E8), tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Home") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Search, null) }, label = { Text("Search") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Favorite, null) }, label = { Text("Favorites") },
                    colors = navColors()
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .vyneticGlass(RoundedCornerShape(17.dp)).clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF252930))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(track.artist, color = VyneticMuted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
            Text(track.durationLabel, color = VyneticMint, fontSize = 10.sp,
                modifier = Modifier.padding(top = 5.dp))
        }
        IconButton(onClick = onFavorite) {
            Icon(Icons.Rounded.Favorite, null,
                tint = if (favorite) Color(0xFFFF719A) else VyneticMuted)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = VyneticMint)
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    playing: Boolean,
    position: Long,
    duration: Long,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(17.dp)).vyneticGlass(RoundedCornerShape(17.dp)).padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(track.thumbnail, null,
                Modifier.size(43.dp).clip(RoundedCornerShape(10.dp)).background(VyneticCard))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(track.artist, color = VyneticMuted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White)
            }
            IconButton(onClick = onToggle) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = VyneticMint)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, null, tint = Color.White)
            }
        }
        Slider(
            value = if (duration > 0L) position.coerceIn(0L, duration).toFloat() else 0f,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
            enabled = duration > 0L,
            modifier = Modifier.height(22.dp),
            colors = SliderDefaults.colors(
                thumbColor = VyneticMint,
                activeTrackColor = VyneticBlue,
                inactiveTrackColor = VyneticMuted.copy(alpha = .25f)
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = VyneticMuted, fontSize = 9.sp)
            Text(formatTime(duration), color = VyneticMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = VyneticMint,
    selectedTextColor = VyneticMint,
    indicatorColor = Color(0xFFCACDD3),
    unselectedIconColor = VyneticMuted,
    unselectedTextColor = VyneticMuted
)

private fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun loadFavorites(activity: MainActivity): List<Track> {
    val raw = activity.getSharedPreferences("vynetic_library", 0).getString("favorites", "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(Track(
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    url = obj.optString("url"),
                    thumbnail = obj.optString("thumbnail"),
                    durationSeconds = obj.optLong("duration")
                ))
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveFavorites(activity: MainActivity, tracks: List<Track>) {
    val array = JSONArray()
    tracks.forEach { track ->
        array.put(JSONObject().apply {
            put("title", track.title)
            put("artist", track.artist)
            put("url", track.url)
            put("thumbnail", track.thumbnail)
            put("duration", track.durationSeconds)
        })
    }
    activity.getSharedPreferences("vynetic_library", 0)
        .edit().putString("favorites", array.toString()).apply()
}
