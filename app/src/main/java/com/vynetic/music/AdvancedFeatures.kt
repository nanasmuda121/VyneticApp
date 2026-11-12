package com.vynetic.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
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
import org.json.JSONArray
import org.json.JSONObject

private val FeatureBg = Color(0xFF030406)
private val FeatureCard = Color(0xCC101923)

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
    var quality by remember { mutableStateOf(activity.getSharedPreferences("vynetic_settings", 0).getString("quality", "Auto (terbaik tersedia)") ?: "Auto (terbaik tersedia)") }
    var eqOn by remember { mutableStateOf(true) }
    val bandValues = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    var recommendations by remember { mutableStateOf(emptyList<Track>()) }
    var recommendationLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = FeatureBg) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF08111C), FeatureBg, Color(0xFF050A11)))).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (page == "Now Playing") onClose() else page = "Now Playing" }) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
                    }
                    Text(page, Modifier.weight(1f), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("VYNETIC", color = VyneticBlue, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }
                when (page) {
                    "Now Playing" -> {
                        Spacer(Modifier.height(12.dp))
                        AsyncImage(track.thumbnail, track.title, Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).vyneticGlass(RoundedCornerShape(20.dp)))
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = VyneticMuted, fontSize = 14.sp)
                            }
                            Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF75A8), modifier = Modifier.size(26.dp))
                        }
                        Slider(value = if (duration > 0) position.coerceIn(0, duration).toFloat() else 0f,
                            onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = duration > 0,
                            colors = SliderDefaults.colors(thumbColor = VyneticBlue, activeTrackColor = VyneticBlue))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatFeatureTime(position), color = VyneticMuted, fontSize = 11.sp)
                            Text(formatFeatureTime(duration), color = VyneticMuted, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onPrevious) { Text("|◀", color = Color.White, fontSize = 20.sp) }
                            Button(onClick = onToggle, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VyneticBlue), modifier = Modifier.size(66.dp)) {
                                Text(if (playing) "Ⅱ" else "▶", fontSize = 23.sp, color = Color.White)
                            }
                            TextButton(onClick = onNext) { Text("▶|", color = Color.White, fontSize = 20.sp) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureNav("Lirik") { page = "Lirik" }
                            FeatureNav("Equalizer") { page = "Equalizer" }
                            FeatureNav("Playlist") { page = "Playlist" }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureNav("Album") { page = "Album" }
                            FeatureNav("Rekomendasi") { page = "Rekomendasi" }
                            FeatureNav("Kualitas") { page = "Kualitas Audio" }
                        }
                    }
                    "Lirik" -> {
                        Spacer(Modifier.height(18.dp))
                        Text(track.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(track.artist, color = VyneticMuted)
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).vyneticGlass(RoundedCornerShape(20.dp)).padding(20.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("♫", color = VyneticBlue, fontSize = 48.sp)
                                Text("Lirik belum tersedia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Vynetic belum terhubung ke penyedia database lirik. Fitur ini siap menjadi tempat tampilan lirik tersinkron.", color = VyneticMuted, fontSize = 13.sp)
                            }
                        }
                    }
                    "Equalizer" -> {
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Equalizer", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(eqOn, onCheckedChange = { eqOn = it; PlaybackService.setEqualizerEnabled(it) })
                        }
                        Text("Atur karakter suara perangkat", color = VyneticMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(28.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                            listOf("60", "230", "910", "3.6k", "14k").forEachIndexed { index, label ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Slider(value = bandValues[index], onValueChange = { value ->
                                        bandValues[index] = value
                                        val range = PlaybackService.eqBandRange
                                        val level = (value / 12f * (range[1] - range[0]) / 2f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                                        PlaybackService.setEqualizerBand(index, level)
                                    }, valueRange = -12f..12f, enabled = eqOn, modifier = Modifier.height(220.dp).width(48.dp),
                                        colors = SliderDefaults.colors(thumbColor = VyneticBlue, activeTrackColor = VyneticBlue))
                                    Text(label, color = VyneticMuted, fontSize = 10.sp)
                                    Text("Hz", color = VyneticMuted, fontSize = 9.sp)
                                }
                            }
                        }
                        Text("Geser untuk mengatur frekuensi rendah hingga tinggi. Efek equalizer bergantung pada dukungan perangkat.", color = VyneticMuted, fontSize = 12.sp)
                    }
                    "Playlist" -> {
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { newPlaylist = true }, colors = ButtonDefaults.buttonColors(containerColor = VyneticBlue), modifier = Modifier.fillMaxWidth()) { Text("+ Buat playlist permanen") }
                        Spacer(Modifier.height(12.dp))
                        if (playlists.isEmpty()) Text("Belum ada playlist. Buat playlist untuk menyimpan lagu.", color = VyneticMuted)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(playlists.keys.toList()) { name ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).vyneticGlass(RoundedCornerShape(20.dp)).padding(13.dp)) {
                                    Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${playlists[name]?.size ?: 0} lagu", color = VyneticMuted, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            val updated = playlists.toMutableMap(); updated[name] = (updated[name].orEmpty().filterNot { it.url == track.url } + track)
                                            playlists = updated; savePlaylists(activity, playlists)
                                        }) { Text("+ Tambahkan lagu ini") }
                                        TextButton(onClick = { playlists = playlists - name; savePlaylists(activity, playlists) }) { Text("Hapus", color = Color(0xFFFF8A9D)) }
                                    }
                                    playlists[name].orEmpty().forEach { saved ->
                                        Text("• ${saved.title} — ${saved.artist}", color = VyneticMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    "Album" -> {
                        Spacer(Modifier.height(22.dp))
                        AsyncImage(track.thumbnail, null, Modifier.fillMaxWidth().weight(.8f).clip(RoundedCornerShape(22.dp)).vyneticGlass(RoundedCornerShape(20.dp)))
                        Spacer(Modifier.height(16.dp))
                        Text("Album / rilisan terkait", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Text("${track.title} • ${track.artist}", color = VyneticMuted)
                        Text("Informasi album resmi belum disediakan oleh hasil pencarian saat ini. Cover dan metadata lagu ditampilkan dari sumber musik.", color = VyneticMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    "Rekomendasi" -> {
                        Spacer(Modifier.height(12.dp))
                        Text("Musik dari artis yang sama", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Button(onClick = {
                            recommendationLoading = true
                            scope.launch {
                                recommendations = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { InnerTubeRepository.searchMusic("${track.artist} songs") } }
                                    .getOrDefault(emptyList()).filterNot { it.url == track.url }.take(12)
                                recommendationLoading = false
                            }
                        }, enabled = !recommendationLoading, colors = ButtonDefaults.buttonColors(containerColor = VyneticBlue), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(if (recommendationLoading) "Mencari…" else "Cari rekomendasi")
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            items(recommendations, key = { it.url }) { item ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).vyneticGlass(RoundedCornerShape(20.dp)).clickable { onPlay(item, recommendations) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(item.thumbnail, null, Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)))
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        Text(item.artist, color = VyneticMuted, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, null, tint = VyneticBlue)
                                }
                            }
                        }
                    }
                    "Kualitas Audio" -> {
                        Spacer(Modifier.height(20.dp))
                        Text("Kualitas streaming", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Pilih batas bitrate audio yang akan diminta dari sumber.", color = VyneticMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
                        listOf("Auto (terbaik tersedia)", "Rendah · 64 kbps", "Sedang · 128 kbps", "Tinggi · 192 kbps").forEach { option ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).vyneticGlass(RoundedCornerShape(20.dp)).clickable {
                                quality = option
                                activity.getSharedPreferences("vynetic_settings", 0).edit().putString("quality", option).apply()
                            }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = quality == option, onClick = {
                                    quality = option
                                    activity.getSharedPreferences("vynetic_settings", 0).edit().putString("quality", option).apply()
                                })
                                Text(option, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Text("Catatan: bitrate yang tersedia bergantung pada sumber lagu. Perubahan berlaku saat lagu berikutnya diputar.", color = VyneticMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }

    if (newPlaylist) AlertDialog(
        onDismissRequest = { newPlaylist = false },
        title = { Text("Playlist baru") },
        text = { TextField(value = playlistName, onValueChange = { playlistName = it }, placeholder = { Text("Nama playlist") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val name = playlistName.trim()
            if (name.isNotEmpty()) {
                playlists = playlists + (name to listOf(track)); savePlaylists(activity, playlists)
            }
            playlistName = ""; newPlaylist = false
        }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text("Batal") } }
    )
}

@Composable
private fun RowScope.FeatureNav(label: String, onClick: () -> Unit) {
    Surface(Modifier.weight(1f).clickable(onClick = onClick), color = Color(0xCC101B27), shape = RoundedCornerShape(12.dp)) {
        Text(label, Modifier.padding(vertical = 13.dp), color = VyneticBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private fun formatFeatureTime(ms: Long): String {
    val sec = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun loadPlaylists(activity: MainActivity): Map<String, List<Track>> {
    val raw = activity.getSharedPreferences("vynetic_library", 0).getString("playlists", "{}") ?: "{}"
    return runCatching {
        val obj = JSONObject(raw)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next(); val arr = obj.optJSONArray(name) ?: JSONArray()
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
        tracks.forEach { t -> arr.put(JSONObject().apply { put("title", t.title); put("artist", t.artist); put("url", t.url); put("thumbnail", t.thumbnail); put("duration", t.durationSeconds) }) }
        root.put(name, arr)
    }
    activity.getSharedPreferences("vynetic_library", 0).edit().putString("playlists", root.toString()).apply()
}
