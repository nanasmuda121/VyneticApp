package com.vynetic.music

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.media3.common.MimeTypes
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

// Authentic Liquid Glass - Grey & Black (Abu-Abu & Hitam) Palette
val VyneticBg = Color(0xFF0B0D11)
val VyneticSurface = Color(0xFF13161C)
val VyneticCard = Color(0xFF181C24)
val VyneticPrimary = Color(0xFFF1F5F9) // Polished Titanium White
val VyneticSecondary = Color(0xFF94A3B8) // Chrome Grey
val VyneticAccent = Color(0xFF64748B) // Slate Smoke
val VyneticText = Color(0xFFFFFFFF)
val VyneticMuted = Color(0xFFA1AAB7)
val VyneticPink = Color(0xFFFB7185)

// Aliases for compatibility
val VyneticBlue = VyneticPrimary
val VyneticMint = VyneticSecondary

class MainActivity : ComponentActivity() {
    var controller: MediaController? = null
        private set
    private var controllerFuture: ListenableFuture<MediaController>? = null

    var currentTrack by mutableStateOf<Track?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var status by mutableStateOf("Jelajahi musik favoritmu")
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var lastCrashReport by mutableStateOf<String?>(null)
        private set

    private var queue: List<Track> = emptyList()
    private var queueIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read previous crash if any
        runCatching {
            val savedCrash = getSharedPreferences("vynetic_prefs", 0).getString("last_crash", null)
            if (!savedCrash.isNullOrBlank()) {
                lastCrashReport = savedCrash
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        connectController()

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                InnerTubeRepository.initialize()
                StreamResolver.ensureInitialized()
            }
        }

        // Real-time position & duration polling
        lifecycleScope.launch {
            while (true) {
                runCatching {
                    controller?.let { player ->
                        if (player.playbackState != Player.STATE_IDLE) {
                            positionMs = player.currentPosition.coerceAtLeast(0L)
                            durationMs = player.duration.takeIf { it > 0L } ?: 0L
                        }
                    }
                }
                delay(400)
            }
        }

        setContent { VyneticRoot(this) }
    }

    private fun connectController() {
        if (controller != null) return
        try {
            val serviceIntent = Intent(this, PlaybackService::class.java)
            startService(serviceIntent)
        } catch (_: Throwable) {}

        try {
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
                            runCatching {
                                when (playbackState) {
                                    Player.STATE_BUFFERING -> {
                                        buffering = true
                                    }
                                    Player.STATE_READY -> {
                                        buffering = false
                                        durationMs = controller?.duration?.coerceAtLeast(0L) ?: 0L
                                        status = "Sedang diputar: ${currentTrack?.title ?: ""}"
                                    }
                                    Player.STATE_ENDED -> {
                                        buffering = false
                                        next()
                                    }
                                    Player.STATE_IDLE -> {
                                        buffering = false
                                    }
                                }
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            buffering = false
                            playing = false
                            status = "Gagal memutar audio: ${error.message ?: "sumber tidak merespons"}"
                        }
                    })
                }.onFailure {
                    status = "Player belum siap: ${it.message ?: "menghubungkan"}"
                }
            }, { command -> runOnUiThread(command) })
        } catch (t: Throwable) {
            status = "Koneksi player gagal: ${t.message}"
        }
    }

    fun dismissCrashNotice() {
        lastCrashReport = null
        runCatching {
            getSharedPreferences("vynetic_prefs", 0).edit().remove("last_crash").apply()
        }
    }

    fun play(track: Track, newQueue: List<Track> = listOf(track)) {
        currentTrack = track
        status = "Menyiapkan audio: ${track.title}…"
        buffering = true
        queue = newQueue.ifEmpty { listOf(track) }
        queueIndex = queue.indexOfFirst { it.url == track.url }.coerceAtLeast(0)
        playResolved(track)
    }

    private fun playResolved(track: Track) {
        lifecycleScope.launch {
            try {
                buffering = true
                currentTrack = track
                status = "Memuat audio stream…"
                val quality = getSharedPreferences("vynetic_settings", 0)
                    .getString("quality", "Auto (terbaik tersedia)") ?: "Auto (terbaik tersedia)"
                val resolved = withContext(Dispatchers.IO) {
                    StreamResolver.resolve(track.url, quality)
                }

                val mimeType = if (resolved.mimeType.contains("webm")) {
                    MimeTypes.AUDIO_WEBM
                } else {
                    MimeTypes.AUDIO_MP4
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
                val item = MediaItem.Builder()
                    .setUri(resolved.url)
                    .setMimeType(mimeType)
                    .setMediaMetadata(metadata)
                    .build()

                runCatching {
                    val serviceIntent = Intent(this@MainActivity, PlaybackService::class.java)
                    startService(serviceIntent)
                }

                var player = controller
                if (player == null) {
                    connectController()
                    delay(350)
                    player = controller
                }

                if (player != null) {
                    player.setMediaItem(item)
                    player.prepare()
                    player.play()
                    status = "Sedang diputar: ${track.title}"
                } else {
                    status = "Sedang menyambungkan pemutar, silakan tap lagu sekali lagi."
                }
            } catch (error: Throwable) {
                buffering = false
                Log.e("MainActivity", "Gagal memutar lagu", error)
                status = when {
                    error.message?.contains("age", true) == true -> "Konten ini memiliki batasan usia/wilayah."
                    else -> "Gagal memutar: ${error.message ?: "sumber tidak merespons"}"
                }
            }
        }
    }

    fun toggle() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                if (it.playbackState == Player.STATE_IDLE || it.mediaItemCount == 0) {
                    currentTrack?.let { track -> playResolved(track) }
                } else {
                    it.play()
                }
            }
        }
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
        delay(1100)
        showSplash = false
    }
    if (showSplash) VyneticSplash() else VyneticApp(activity)
}

@Composable
private fun VyneticSplash() {
    val infinite = rememberInfiniteTransition(label = "splash")
    val glow by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val bars = listOf(16, 32, 48, 28, 56, 36, 22, 44, 30, 18)

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF222733), Color(0xFF11141A), Color(0xFF07080A)),
                radius = 700f
            )
        ), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(100.dp).clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF374151), Color(0xFF1F242D), Color(0xFF0F1115))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = glow * 0.75f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("V", color = Color.White, fontSize = 62.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(20.dp))
            Text("VYNETIC", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
            Text("YOUR SOUND. YOUR SPACE.", color = VyneticSecondary, fontSize = 10.sp, letterSpacing = 2.5.sp, modifier = Modifier.padding(top = 6.dp))

            Row(
                Modifier.padding(top = 26.dp).height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bars.forEachIndexed { index, height ->
                    val animated by infinite.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400 + index * 50, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "wave$index"
                    )
                    Box(
                        Modifier.width(4.dp).height((height * animated).dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFE2E8F0), Color(0xFF475569))
                                )
                            )
                    )
                }
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
    var selectedGenre by remember { mutableStateOf("🔥 Trending") }
    var showFullPlayer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val genres = listOf("🔥 Trending", "🇮🇩 Pop Indo", "🎧 Akustik", "⚡ Viral", "☕ Lofi", "🎸 Rock")

    // Auto-load trending tracks on startup
    LaunchedEffect(Unit) {
        if (results.isEmpty()) {
            loading = true
            activity.status = "Memuat musik populer…"
            runCatching {
                withContext(Dispatchers.IO) {
                    InnerTubeRepository.searchMusic("Trending Musik Indonesia")
                }
            }.onSuccess {
                results = it
                activity.status = "${it.size} lagu populer siap diputar"
            }.onFailure {
                activity.status = "Gunakan kolom pencarian untuk mencari musik."
            }
            loading = false
        }
    }

    val shownTracks = if (tab == 2) favorites else results

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = VyneticBg,
            surface = VyneticSurface,
            primary = VyneticPrimary,
            secondary = VyneticSecondary
        )
    ) {
        Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161921), Color(0xFF0F1116), Color(0xFF08090C))
                )
            )
        ) {
            // Header bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF475569), Color(0xFF1E242E), Color(0xFF0F1115))
                            )
                        )
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("VYNETIC", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 3.sp)
                    Text("YOUR SOUND. YOUR SPACE.", fontSize = 9.sp, color = VyneticSecondary, letterSpacing = 1.5.sp)
                }
                Surface(
                    color = Color(0x22FFFFFF),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Text(
                        "v1.04 · LIQUID",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFFE2E8F0),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                // Crash alert banner if previous crash occurred
                activity.lastCrashReport?.let { crashText ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF22171A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FB7185))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("⚠️ Terdeteksi Crash Sebelumnya", color = Color(0xFFFF7185), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                crashText.lines().take(3).joinToString("\n"),
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                            Button(
                                onClick = { activity.dismissCrashNotice() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D24)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Tutup Peringatan", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                // Title header
                Text(
                    when (tab) { 2 -> "Koleksi Favorit"; 1 -> "Cari Musik"; else -> "Jelajahi Musik" },
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                )

                // Search Bar
                if (tab != 2) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .vyneticGlass(RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Color(0xFFCBD5E1))
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Cari lagu, artis, atau judul…", color = Color(0xFF64748B), fontSize = 13.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
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
                                        activity.status = if (it.isEmpty()) "Tidak ada hasil ditemukan."
                                        else "${it.size} lagu ditemukan."
                                    }.onFailure {
                                        activity.status = "Pencarian gagal: ${it.message ?: "periksa koneksi"}"
                                    }
                                    loading = false
                                }
                            },
                            enabled = !loading && query.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A313C)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DFFFFFF)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("Cari", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // Interactive genre chips in liquid glass
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        genres.forEach { genre ->
                            val isSelected = selectedGenre == genre
                            Surface(
                                modifier = Modifier.clip(CircleShape).clickable {
                                    selectedGenre = genre
                                    scope.launch {
                                        loading = true
                                        val searchTerm = when (genre) {
                                            "🔥 Trending" -> "Trending Musik Indonesia"
                                            "🇮🇩 Pop Indo" -> "Lagu Pop Indonesia Terbaru"
                                            "🎧 Akustik" -> "Akustik Indonesia Santai"
                                            "⚡ Viral" -> "Lagu Viral TikTok Indonesia"
                                            "☕ Lofi" -> "Indonesian Lofi Chill Beats"
                                            "🎸 Rock" -> "Lagu Band Rock Pop Indonesia"
                                            else -> genre
                                        }
                                        activity.status = "Memuat $genre…"
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                InnerTubeRepository.searchMusic(searchTerm)
                                            }
                                        }.onSuccess {
                                            results = it
                                            activity.status = "${it.size} lagu $genre dimuat"
                                        }
                                        loading = false
                                    }
                                },
                                color = if (isSelected) Color(0xFF333C4A) else Color(0x221B1E26),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0x99FFFFFF) else Color(0x1FFFFFFF)
                                )
                            ) {
                                Text(
                                    genre,
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Status message
                Text(
                    activity.status,
                    color = VyneticMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (loading && shownTracks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (shownTracks.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (tab == 2) "♡" else "♫", color = Color(0xFF94A3B8), fontSize = 52.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (tab == 2) "Belum ada lagu favorit tersimpan" else "Pilih genre atau ketik pencarian musik",
                            color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (tab == 2) "Tekan ikon hati pada lagu untuk menyimpannya di sini."
                            else "Pilih salah satu chip di atas untuk memuat lagu secara instan.",
                            color = VyneticMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shownTracks, key = { it.url }) { track ->
                            val isCurrentTrack = activity.currentTrack?.url == track.url
                            TrackRow(
                                track = track,
                                isPlaying = isCurrentTrack && activity.playing,
                                isCurrent = isCurrentTrack,
                                favorite = favorites.any { it.url == track.url },
                                onFavorite = {
                                    favorites = if (favorites.any { it.url == track.url }) {
                                        favorites.filterNot { it.url == track.url }
                                    } else favorites + track
                                    saveFavorites(activity, favorites)
                                },
                                onPlay = {
                                    runCatching { activity.play(track, shownTracks) }
                                        .onFailure { activity.status = "Gagal memutar: ${it.message}" }
                                }
                            )
                        }
                    }
                }
            }

            // Floating MiniPlayer in dark liquid glass
            activity.currentTrack?.let { track ->
                MiniPlayer(
                    track = track,
                    playing = activity.playing,
                    buffering = activity.buffering,
                    position = activity.positionMs,
                    duration = activity.durationMs,
                    onPrevious = { activity.previous() },
                    onToggle = { activity.toggle() },
                    onNext = { activity.next() },
                    onSeek = { activity.seekTo(it) },
                    onOpen = { showFullPlayer = true }
                )
            }

            // Full Player Screen
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

            // Bottom Navigation Bar in liquid smoke
            NavigationBar(containerColor = Color(0xFA0E1015), tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Jelajahi") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    label = { Text("Cari") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Favorite, contentDescription = "Favorites") },
                    label = { Text("Favorit") },
                    colors = navColors()
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .vyneticGlass(RoundedCornerShape(16.dp), active = isCurrent)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with rounded frame & specular border
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E222A))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) Color(0xFFFFFFFF) else Color(0xFFE2E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                track.artist,
                color = VyneticMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                track.durationLabel,
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Favorite button
        IconButton(onClick = onFavorite) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorit",
                tint = if (favorite) VyneticPink else Color(0xFF64748B),
                modifier = Modifier.size(22.dp)
            )
        }

        // Active equalizer animation or Play icon
        if (isPlaying) {
            LiveEqualizerBars()
        } else {
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onPlay),
                color = if (isCurrent) Color(0xFF333C4A) else Color(0x22FFFFFF),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Putar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveEqualizerBars() {
    val infinite = rememberInfiniteTransition(label = "eq_bars")
    val h1 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = Modifier.size(36.dp).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height((22 * h1).dp).clip(CircleShape).background(Color(0xFFE2E8F0)))
        Box(Modifier.width(3.dp).height((22 * h2).dp).clip(CircleShape).background(Color(0xFFE2E8F0)))
        Box(Modifier.width(3.dp).height((22 * h3).dp).clip(CircleShape).background(Color(0xFFE2E8F0)))
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    playing: Boolean,
    buffering: Boolean,
    position: Long,
    duration: Long,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).vyneticGlass(RoundedCornerShape(18.dp), active = true)
    ) {
        // Thin liquid specular progress line on top of mini player
        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color(0xFFCBD5E1),
            trackColor = Color(0x22FFFFFF)
        )

        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E222A))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    track.artist,
                    color = VyneticMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFFFFFF))
            ) {
                if (buffering) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF0F1115))
                } else {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Jeda" else "Putar",
                        tint = Color(0xFF0F1115),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White,
    selectedTextColor = Color.White,
    indicatorColor = Color(0x22FFFFFF),
    unselectedIconColor = Color(0xFF64748B),
    unselectedTextColor = Color(0xFF64748B)
)

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
