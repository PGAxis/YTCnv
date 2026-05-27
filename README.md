# YouTube Converter

A simple, open-source YouTube downloader for Android. Download YouTube videos as MP3 or MP4 directly to your device, with metadata and thumbnail embedding.

<div align=center>

<p align=center>
  <a href="https://apt.izzysoft.de/packages/com.pg_axis.ytcnv"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="80" alt="Get it at IzzyOnDroid"></a>
</p>

<p align=center>
  <a href="https://apt.izzysoft.de/packages/com.pg_axis.ytcnv"><img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.pg_axis.ytcnv&label=IzzyOnDroid"/></a>
  <a href="https://apt.izzysoft.de/packages/com.pg_axis.ytcnv"><img src="https://img.shields.io/badge/dynamic/json?url=https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json&query=$.['com.pg_axis.ytcnv']&label=IzzyOnDroid%20yearly%20downloads"/></a>
</p>

<p align=center>
  <a href="https://shields.rbtlog.dev/com.pg_axis.ytcnv"><img src="https://shields.rbtlog.dev/simple/com.pg_axis.ytcnv"/></a>
</p>

</div>

## Features

- Download YouTube videos as **MP3** or **MP4**
- Embedded metadata (title, artist) and album art for MP3s
- **Quick download** mode — download without manually picking quality
- **Manual quality selection** — pick audio bitrate or video resolution
- YouTube **search** with thumbnails, duration, and channel name
- **Download history** — tap any past download to re-download it
- Search history
- Custom download destination via folder picker
- Share YouTube links directly from the YouTube app
- Update notifications via GitHub Releases
- Dark theme

## Building

### Requirements

- Android Studio
- Android SDK 30+
- JDK 17+

### FFmpeg AAR

This project uses a custom-built FFmpeg AAR for compatibility with devices using 16KB memory page sizes (e.g. newer Samsung devices). The prebuilt AAR is included in `app/libs/`, but if you need to build it yourself:

1. Clone the fork: [ffmpeg-kit-16KB-for-YTCnv](https://github.com/PGAxis/ffmpeg-kit-16KB-for-YTCnv)
2. Follow the build instructions in that repository
3. Copy the resulting `ffmpeg-kit.aar` into `app/libs/`

The prebuilt AAR included in this repository was built from that fork with no modifications.

### Steps

1. Clone this repository
2. Open in Android Studio
3. Sync Gradle
4. Build → Generate Signed APK (or run directly on a device)

## Dependencies

- [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) — YouTube stream extraction
- [FFmpegKit](https://github.com/arthenica/ffmpeg-kit) (custom build) — audio/video processing
- [Smart exception](https://github.com/tanersener/smart-exception)
- [Coil](https://github.com/coil-kt/coil) — image loading
- [Gson](https://github.com/google/gson) — JSON serialization
- [AXS](https://github.com/PGAxis/AxsFileFormat) - Settings handling
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI

## License

GNU GPL v3 License — see [LICENSE](LICENSE) for details.

## Contact

- Issues: [Issue page](https://github.com/PGAxis/YTCnv/issues)
- Email: pgaxis.dev@gmail.com
