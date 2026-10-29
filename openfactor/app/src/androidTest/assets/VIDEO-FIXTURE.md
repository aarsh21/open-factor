# Synthetic playback fixture

Three three-second silent H.264 baseline 96×160 color clips are authored test data, not exercise demonstrations or extracted app assets. They are packaged only into the instrumentation test APK, not a public/release app APK.

Produced with ffmpeg n9.0.2 using `-f lavfi -i color=c=<color>:s=96x160:r=12:d=3 -an -c:v libx264 -profile:v baseline -pix_fmt yuv420p -movflags +faststart`, with colors `0x202020`, `0x3982ff` and `0x91d8ad`. The source preview SVG is a single authored color rectangle. No third-party content is present.
