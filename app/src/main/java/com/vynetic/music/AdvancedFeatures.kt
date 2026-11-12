package com.vynetic.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val FeatureBg = Color(0xFF090C12)

@Composable
fun AdvancedFeatures(
    activity: MainActivity,
    track: Track,
    playing: Boolean,
    position: Long,
    duration: Long,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlay: (Track, List<Track>) -> Unit
) {
    var page by remember { mutableStateOf("Now Playing") }
    var playlists by remember { mutableStateOf(loadPlaylists(activity)) }
    var newPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var quality by remember {
        mutableStateOf(activity.getSharedPreferences("vynetic_settings", 0).getString("quality", "Auto (terbaik tersedia)") ?: "Auto (terbaik tersedia)")
    }
    var eqOn by remember { mutableStateOf(PlaybackService.eqAvailable) }
    val bandValues = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    var recommendations by remember { mutableStateOf(emptyList<Track>()) }
    var recommendationLoading by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(loadFavoritesFromPrefs(activity).any { it.url == track.url }) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = FeatureBg) {
            Column(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D1420), FeatureBg, Color(0xFF070A0F))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Top header bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (page == "Now Playing") onClose() else page = "Now Playing" },
                        modifier = Modifier.clip(CircleShape).background(Color(0x22FFFFFF))
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        page,
                        Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color(0x1FFFFFFF),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Text(
                            "VYNETIC HD",
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                when (page) {
                    "Now Playing" -> {
                        Spacer(Modifier.height(10.dp))
                        // Big album art with subtle glass frame
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .vyneticGlass(RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = track.thumbnail,
                                contentDescription = track.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Title, artist, favorite button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    track.artist,
                                    color = VyneticMuted,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val favs = loadFavoritesFromPrefs(activity)
                                    val updated = if (favs.any { it.url == track.url }) {
                                        favs.filterNot { it.url == track.url }
                                    } else {
                                        favs + track
                                    }
                                    saveFavoritesToPrefs(activity, updated)
                                    isFavorite = updated.any { it.url == track.url }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorit",
                                    tint = if (isFavorite) VyneticPink else VyneticMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Scrubber Slider
                        Slider(
                            value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f,
                            onValueChange = { onSeek(it.toLong()) },
                            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                            enabled = duration > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = VyneticPrimary,
                                activeTrackColor = VyneticPrimary,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatFeatureTime(position), color = VyneticMuted, fontSize = 11.sp)
                            Text(formatFeatureTime(duration), color = VyneticMuted, fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Player controls with real Material icons
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0x18FFFFFF))
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = "Sebelumnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Button(
                                onClick = onToggle,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = VyneticPrimary),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(68.dp)
                            ) {
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Jeda" else "Putar",
                                    tint = Color(0xFF0A0D14),
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0x18FFFFFF))
                            ) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = "Berikutnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Navigation pills
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureNav("Lirik") { page = "Lirik" }
                            FeatureNav("Equalizer") { page = "Equalizer" }
                            FeatureNav("Playlist") { page = "Playlist" }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureNav("Artis") { page = "Rekomendasi" }
                            FeatureNav("Kualitas Audio") { page = "Kualitas Audio" }
                        }
                    }

                    "Lirik" -> {
                        Spacer(Modifier.height(14.dp))
                        Text(track.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(track.artist, color = VyneticMuted)
                        Spacer(Modifier.height(20.dp))
                        Box(
                            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                                .vyneticGlass(RoundedCornerShape(20.dp)).padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("♫", color = VyneticPrimary, fontSize = 52.sp)
                                Spacer(Modifier.height(12.dp))
                                Text("Lirik Belum Tersedia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Fitur integrasi lirik tersinkronisasi otomatis akan hadir di pembaruan berikutnya.",
                                    color = VyneticMuted,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    "Equalizer" -> {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Equalizer Suara", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("Tingkatkan bass, vokal, dan treble perangkat", color = VyneticMuted, fontSize = 12.sp)
                            }
                            Switch(
                                checked = eqOn,
                                onCheckedChange = {
                                    eqOn = it
                                    PlaybackService.setEqualizerEnabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = VyneticPrimary
                                )
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz").forEachIndexed { index, label ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Slider(
                                        value = bandValues[index],
                                        onValueChange = { value ->
                                            bandValues[index] = value
                                            val range = PlaybackService.eqBandRange
                                            val level = (value / 12f * (range[1] - range[0]) / 2f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                                            PlaybackService.setEqualizerBand(index, level)
                                        },
                                        valueRange = -12f..12f,
                                        enabled = eqOn,
                                        modifier = Modifier.height(200.dp).width(42.dp),
                                        colors = SliderDefaults.colors(thumbColor = VyneticPrimary, activeTrackColor = VyneticPrimary)
                                    )
                                    Text(label, color = if (eqOn) Color.White else VyneticMuted, fontSize = 9.sp)
                                }
                            }
                        }
                        Text(
                            "Geser level frekuensi untuk menyesuaikan respon suara.",
                            color = VyneticMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    "Playlist" -> {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { newPlaylist = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VyneticPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Buat Playlist Baru", color = Color(0xFF0A0D14), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (playlists.isEmpty()) {
                            Text("Belum ada playlist tersimpan.", color = VyneticMuted, fontSize = 13.sp)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(playlists.keys.toList()) { name ->
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .vyneticGlass(RoundedCornerShape(16.dp)).padding(14.dp)
                                ) {
                                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("${playlists[name]?.size ?: 0} lagu", color = VyneticMuted, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        TextButton(
                                            onClick = {
                                                val updated = playlists.toMutableMap()
                                                updated[name] = (updated[name].orEmpty().filterNot { it.url == track.url } + track)
                                                playlists = updated
                                                savePlaylists(activity, playlists)
                                            }
                                        ) {
                                            Text("+ Tambah Lagu Ini", color = VyneticPrimary)
                                        }
                                        TextButton(
                                            onClick = {
                                                playlists = playlists - name
                                                savePlaylists(activity, playlists)
                                            }
                                        ) {
                                            Text("Hapus", color = Color(0xFFFF6B6B))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Rekomendasi" -> {
                        Spacer(Modifier.height(12.dp))
                        Text("Lagu Lainnya dari ${track.artist}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(
                            onClick = {
                                recommendationLoading = true
                                scope.launch {
                                    recommendations = runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            InnerTubeRepository.searchMusic("${track.artist} lagu")
                                        }
                                    }.getOrDefault(emptyList()).filterNot { it.url == track.url }.take(15)
                                    recommendationLoading = false
                                }
                            },
                            enabled = !recommendationLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = VyneticPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            Text(
                                if (recommendationLoading) "Mencari musik…" else "Muat Musik Artis Ini",
                                color = Color(0xFF0A0D14),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                            items(recommendations, key = { it.url }) { item ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .vyneticGlass(RoundedCornerShape(14.dp))
                                        .clickable { onPlay(item, recommendations) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(item.thumbnail, null, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(item.artist, color = VyneticMuted, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, null, tint = VyneticPrimary)
                                }
                            }
                        }
                    }

                    "Kualitas Audio" -> {
                        Spacer(Modifier.height(16.dp))
                        Text("Format & Bitrate Streaming", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(
                            "Secara default VYNETIC menggunakan audio AAC 128 kbps (setara 256 kbps MP3) untuk kualitas optimal.",
                            color = VyneticMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )
                        listOf(
                            "Auto (terbaik tersedia)",
                            "Tinggi · 192 kbps",
                            "Sedang · 128 kbps",
                            "Rendah · 64 kbps"
                        ).forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .vyneticGlass(RoundedCornerShape(14.dp), active = quality == option)
                                    .clickable {
                                        quality = option
                                        activity.getSharedPreferences("vynetic_settings", 0).edit().putString("quality", option).apply()
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = quality == option,
                                    onClick = {
                                        quality = option
                                        activity.getSharedPreferences("vynetic_settings", 0).edit().putString("quality", option).apply()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = VyneticPrimary)
                                )
                                Text(option, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    if (newPlaylist) AlertDialog(
        onDismissRequest = { newPlaylist = false },
        title = { Text("Playlist Baru") },
        text = { TextField(value = playlistName, onValueChange = { playlistName = it }, placeholder = { Text("Nama playlist") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val name = playlistName.trim()
                if (name.isNotEmpty()) {
                    playlists = playlists + (name to listOf(track))
                    savePlaylists(activity, playlists)
                }
                playlistName = ""
                newPlaylist = false
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text("Batal") } }
    )
}

@Composable
private fun RowScope.FeatureNav(label: String, onClick: () -> Unit) {
    Surface(
        Modifier.weight(1f).clickable(onClick = onClick),
        color = Color(0x221E293B),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            label,
            Modifier.padding(vertical = 12.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatFeatureTime(ms: Long): String {
    val sec = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun loadFavoritesFromPrefs(activity: MainActivity): List<Track> {
    val raw = activity.getSharedPreferences("vynetic_library", 0).getString("favorites", "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(Track(obj.optString("title"), obj.optString("artist"), obj.optString("url"), obj.optString("thumbnail"), obj.optLong("duration")))
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveFavoritesToPrefs(activity: MainActivity, tracks: List<Track>) {
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
    activity.getSharedPreferences("vynetic_library", 0).edit().putString("favorites", array.toString()).apply()
}

private fun loadPlaylists(activity: MainActivity): Map<String, List<Track>> {
    val raw = activity.getSharedPreferences("vynetic_library", 0).getString("playlists", "{}") ?: "{}"
    return runCatching {
        val obj = JSONObject(raw)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val arr = obj.optJSONArray(name) ?: JSONArray()
                val tracks = buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        add(Track(item.optString("title"), item.optString("artist"), item.optString("url"), item.optString("thumbnail"), item.optLong("duration")))
                    }
                }
                put(name, tracks)
            }
        }
    }.getOrDefault(emptyMap())
}

private fun savePlaylists(activity: MainActivity, playlists: Map<String, List<Track>>) {
    val root = JSONObject()
    playlists.forEach { (name, tracks) ->
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(JSONObject().apply {
                put("title", t.title)
                put("artist", t.artist)
                put("url", t.url)
                put("thumbnail", t.thumbnail)
                put("duration", t.durationSeconds)
            })
        }
        root.put(name, arr)
    }
    activity.getSharedPreferences("vynetic_library", 0).edit().putString("playlists", root.toString()).apply()
}
