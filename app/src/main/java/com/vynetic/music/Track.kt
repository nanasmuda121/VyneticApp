package com.vynetic.music
data class Track(
    val title: String, val artist: String, val url: String,
    val thumbnail: String, val durationSeconds: Long
) {
    val durationLabel: String get() =
        if (durationSeconds < 0) "—" else "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
}
