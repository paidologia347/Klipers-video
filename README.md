# Klipers Video (Native Android Kotlin)

Aplikasi Android native untuk workflow **download sumber video + manual cut + AutoCut berbasis recipe waktu**, disetarakan secara fungsional dari alur utama referensi `Intisari-AutoCut` (tanpa menyalin aset/lisensi/secret proprietary).

## Ringkasan Fitur

- **Download sumber video** melalui URL direct file (menggunakan `DownloadManager`).
- **Pilih video lokal** dari storage via Storage Access Framework.
- **Manual Cut**: potong satu rentang waktu (start-end).
- **AutoCut Recipe**: potong banyak segmen dari teks recipe (satu segmen per baris).
- Engine pemotongan memakai **FFmpegKit** (native ffmpeg di Android).

Contoh format recipe:

```text
00:00:10-00:00:20
00:00:35,00:00:48
1) 00:01:00 > 00:01:15
```

## Prasyarat

- **JDK 17**
- **Android SDK** terpasang (via Android Studio)
- **Android Studio** versi modern (Hedgehog / Iguana / Koala atau lebih baru)
- Koneksi internet saat build pertama untuk download dependensi Gradle/Android

## Struktur Proyek

- `settings.gradle.kts`
- `build.gradle.kts` (root)
- `gradle.properties`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- `app/` (module Android)

## Build di Android Studio

1. Buka folder repository ini di Android Studio.
2. Tunggu Gradle Sync selesai.
3. Pilih build variant `debug`.
4. Jalankan **Build > Build APK(s)**.

## Build via CLI

Dari root repository:

```bash
./gradlew assembleDebug
```

## Lokasi Output APK Debug

APK debug ada di path standar Gradle module app:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Catatan Keamanan

- Tidak ada secret/kredensial hardcoded di source code.
- Aplikasi hanya menggunakan input user (video/recipe/URL) untuk proses lokal.
