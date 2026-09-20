#!/usr/bin/env bash
# Boots the app on the CI emulator with a few generated test tones on the
# "phone", plays one, and proves it from the view hierarchy. The screenshot it
# takes is the one the README shows.
#
# A file rather than inline workflow script: the emulator action feeds inline
# scripts to `sh -c` one line at a time, which breaks every multi-line `if`.
set -x

PKG=no.mwmai.music.debug
ACTIVITY=no.mwmai.music.MainActivity
OUT=smoke
mkdir -p "$OUT"

# Tagged mp3 tones. The names say what they are: nobody's music is in this repo.
N=1
for HZ in 196 220 262 294 330 392 440 523; do
    ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=${HZ}:duration=$((150 + N * 17))" \
        -metadata title="Sine ${HZ} Hz" -metadata artist="Test Tones" \
        -metadata album="Emulator Smoke" -metadata track="$N" \
        -codec:a libmp3lame -b:a 64k "$OUT/tone_${HZ}.mp3" || { echo "::error::ffmpeg failed"; exit 1; }
    N=$((N + 1))
done
adb shell mkdir -p /sdcard/Music
for F in "$OUT"/tone_*.mp3; do
    adb push "$F" /sdcard/Music/ >/dev/null
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "file:///sdcard/Music/$(basename "$F")" >/dev/null 2>&1 || true
done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true
rm -f "$OUT"/tone_*.mp3
sleep 8

chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true
adb shell pm grant "$PKG" android.permission.READ_MEDIA_AUDIO 2>/dev/null || true

adb logcat -c || true
# No -W: it waits for a launch to settle, which never happens if the app dies.
adb shell am start -n "$PKG/$ACTIVITY" || echo "am start returned $?"
sleep 20

dump() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$OUT/$1" >/dev/null 2>&1 || true
}
crashed() {
    adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
    if grep -qE "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"; then
        echo "::error::App crashed"
        grep -B 2 -A 45 -m 1 -E "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"
        return 0
    fi
    return 1
}

dump ui_launch.xml
adb exec-out screencap -p > "$OUT/launch.png" 2>/dev/null || true
crashed && exit 1

MISSING=0
for TEXT in "MWM MUSIC" "LIBRARY" "PLAYLISTS" "FAVORITES" "QUEUE" "Sine 220 Hz" "Test Tones"; do
    if ! grep -qF "$TEXT" "$OUT/ui_launch.xml"; then
        echo "::error::The library screen is missing the text: $TEXT"
        MISSING=1
    fi
done
if [ "$MISSING" -ne 0 ]; then
    head -c 6000 "$OUT/ui_launch.xml"
    exit 1
fi

# Tap the "Sine 262 Hz" row, heart the playing track, then check the deck.
tap() {
    python3 tools/ui_center.py "$OUT/$1" "$2" > "$OUT/tap.txt" || { echo "::error::nothing to tap for: $2"; exit 1; }
    # shellcheck disable=SC2046
    adb shell input tap $(cat "$OUT/tap.txt")
}
tap ui_launch.xml "text=Sine 262 Hz"
sleep 6
dump ui_playing.xml
tap ui_playing.xml "desc=Add to favorites"
sleep 9
dump ui_final.xml
adb exec-out screencap -p > "$OUT/playing.png" 2>/dev/null || true
crashed && exit 1

# "Pause" is only on screen while the player reports isPlaying, and the
# position readout must have left 0:00, so this is sound actually running.
if ! grep -qF 'content-desc="Pause"' "$OUT/ui_final.xml"; then
    echo "::error::Tapped a track but the deck never switched to Pause"
    head -c 6000 "$OUT/ui_final.xml"
    exit 1
fi
if grep -qF 'text="0:00"' "$OUT/ui_final.xml"; then
    echo "::error::The deck shows Pause but the clock is stuck at 0:00"
    exit 1
fi
if ! grep -qF 'content-desc="Remove from favorites"' "$OUT/ui_final.xml"; then
    echo "::error::The heart did not register as a favorite"
    exit 1
fi
echo "Library listed the tones, one is playing, the clock is moving, a favorite was saved."
