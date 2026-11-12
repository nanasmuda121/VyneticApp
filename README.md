# VYNETIC — Android Music Player V.1.00 (Beta)

Aplikasi Android native berbasis Kotlin, Jetpack Compose, dan AndroidX Media3 dengan tema Black & Soft Blue Liquid Glass.

## Mesin data: YouTube Music InnerTube
- Pencarian katalog menggunakan endpoint InnerTube YouTube Music (`WEB_REMIX`).
- Permintaan pemutar menggunakan endpoint InnerTube YouTube (`ANDROID`).
- Tidak memerlukan YouTube Data API key. InnerTube merupakan API internal tidak resmi dan dapat berubah sewaktu-waktu.
- Hasil pencarian dipetakan menjadi judul, artis, thumbnail, durasi, dan video ID.
- URL audio langsung yang dikembalikan oleh endpoint player diteruskan ke Media3 ExoPlayer.

## Fitur aplikasi
- Pencarian lagu dan artis.
- Pemutaran streaming online melalui Media3 ExoPlayer.
- Pemutaran latar belakang melalui MediaSessionService.
- Mini-player, antrean, dan kontrol notifikasi Android.
- Favorit dan playlist lokal permanen melalui SharedPreferences.
- Panel lirik (UI; penyedia lirik belum disambungkan).
- Equalizer 5-band jika didukung perangkat Android.
- Pilihan kualitas Auto, 64, 128, dan 192 kbps berdasarkan format langsung yang tersedia.
- Rekomendasi berbasis pencarian artis.
- Tema Black & Soft Blue Liquid Glass dengan panel kaca berlapis, highlight biru lembut, dan sudut membulat.
- Splash screen pembuka dengan logo VYNETIC, animasi glow, dan visual waveform audio.
- Label versi aplikasi: V.1.00 (Beta).

## Batasan playback yang penting
Versi ini menerima **direct audio URL** dari respons InnerTube. Jika YouTube mengembalikan format yang hanya memiliki `signatureCipher`, SABR/UMP segmented transport, atau memerlukan token/perangkat tertentu, aplikasi akan menampilkan pesan bahwa format tersebut belum didukung. Karena InnerTube merupakan API internal, hasil dan ketersediaan stream dapat berubah berdasarkan waktu, wilayah, konten, dan kebijakan layanan. Tidak ada jaminan semua lagu bisa diputar.

Proyek ini belum dikompilasi atau diuji pada perangkat Android dalam lingkungan pembuatan ini. Karena itu, ZIP adalah source project, bukan APK siap instal, dan keberhasilan build/playback belum dapat diklaim.

## Build APK
Persyaratan: Android Studio yang mendukung Android Gradle Plugin 8.11.1, Android SDK Platform 36, JDK 17, dan koneksi internet untuk Gradle dependency sync.

1. Extract ZIP.
2. Buka folder `VyneticAndroid` melalui Android Studio.
3. Tunggu Gradle Sync.
4. Pilih **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. APK debug biasanya berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Identitas proyek
- Nama developer: **Dev**.

## Lisensi dan atribusi
- AndroidX Media3 — Apache-2.0.
- OkHttp — Apache-2.0.
- Coil — Apache-2.0.

VYNETIC tidak berafiliasi dengan YouTube atau Google. Gunakan layanan dan konten sesuai ketentuan yang berlaku serta hormati hak cipta.


## Visual theme update
The interface palette is now soft silver/gray with glossy black liquid-glass panels and monochrome metallic accents. Version label remains V.1.00 (Beta).

## Build via GitHub Actions
Push proyek ini ke GitHub (branch `main`/`master`) atau jalankan manual dari tab **Actions → Build APK → Run workflow**.
APK debug tersedia di bagian **Artifacts** (`Vynetic-debug-apk`) setelah build selesai.
