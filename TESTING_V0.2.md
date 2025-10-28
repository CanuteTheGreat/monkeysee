# MonkeySee v0.2.0 - Testing Guide

## Prerequisites

Before testing, ensure all system dependencies are installed:

### Ubuntu/Debian
```bash
# Install build essentials
sudo apt update
sudo apt install build-essential pkg-config libclang-dev

# Install FFmpeg development libraries
sudo apt install libavcodec-dev libavformat-dev libavutil-dev \
                 libavdevice-dev libswscale-dev libavfilter-dev

# Install ALSA development libraries (for audio)
sudo apt install libasound2-dev

# Install v4l2loopback (for virtual camera)
sudo apt install v4l2loopback-dkms

# Load v4l2loopback module
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
```

### Fedora/RHEL
```bash
sudo dnf install clang-devel pkg-config
sudo dnf install ffmpeg-devel alsa-lib-devel
sudo dnf install v4l2loopback
```

## Building

### 1. Build Desktop Client (CLI)
```bash
cd /home/canutethegreat/monkeysee/desktop/monkeysee-client
cargo build --release
```

### 2. Build Desktop GUI
```bash
cd /home/canutethegreat/monkeysee/desktop/monkeysee-gui
cargo build --release
```

### 3. Build Android App
```bash
cd /home/canutethegreat/monkeysee/android
./gradlew assembleDebug
```

## Running Tests

### Unit Tests
```bash
# Test adaptive bitrate module
cd /home/canutethegreat/monkeysee/desktop/monkeysee-client
cargo test adaptive_bitrate

# Test recorder module
cargo test recorder

# Run all tests
cargo test --workspace
```

## Feature Testing Checklist

### ✅ Feature 1: Enhanced GUI with Quality Presets

**Test Steps:**
1. Start the GUI:
   ```bash
   cd /home/canutethegreat/monkeysee/desktop/monkeysee-gui
   cargo run --release
   ```

2. Verify UI elements:
   - [ ] Quality preset dropdown shows: Low, Medium, High, Ultra, Custom
   - [ ] Resolution and FPS fields update when preset changes
   - [ ] "Enable Audio Playback" checkbox is present
   - [ ] "Auto-Reconnect on Failure" checkbox is present
   - [ ] "Advanced Settings" panel can be collapsed/expanded
   - [ ] Window size is 700x900

3. Test quality preset switching:
   - [ ] Select "Low" → Should show 640x480 @ 15fps
   - [ ] Select "Medium" → Should show 1280x720 @ 24fps
   - [ ] Select "High" → Should show 1280x720 @ 30fps
   - [ ] Select "Ultra" → Should show 1920x1080 @ 30fps
   - [ ] Manually change resolution → Preset switches to "Custom"

4. Test streaming with GUI:
   - [ ] Enter RTSP URL from Android app
   - [ ] Click "Start Streaming"
   - [ ] Verify connection status updates
   - [ ] Check stats dashboard updates in real-time
   - [ ] Verify FPS graph renders and updates

5. Test stats dashboard:
   - [ ] Video stats: frames, data, FPS, bitrate displayed
   - [ ] Audio stats: frames, data, bitrate displayed
   - [ ] Network stats: latency, jitter, packet loss displayed
   - [ ] FPS history graph shows last 60 seconds
   - [ ] Graph updates smoothly

---

### ✅ Feature 2: Adaptive Bitrate Control

**Test Steps:**
1. Start streaming with adaptive bitrate:
   ```bash
   cd /home/canutethegreat/monkeysee/desktop/monkeysee-client
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --adaptive-bitrate \
     --verbose
   ```

2. Verify startup:
   - [ ] Log shows "Adaptive bitrate enabled - quality will adjust based on network conditions"

3. Simulate good network conditions:
   - [ ] Stream for 30+ seconds with good WiFi
   - [ ] Check stats report shows low packet loss (<1%)
   - [ ] Verify latency is stable

4. Simulate network degradation (optional - requires `tc` tool):
   ```bash
   # Add 200ms latency
   sudo tc qdisc add dev wlan0 root netem delay 200ms

   # Add 5% packet loss
   sudo tc qdisc add dev wlan0 root netem loss 5%

   # Clean up
   sudo tc qdisc del dev wlan0 root
   ```
   - [ ] Watch logs for quality downgrade warnings
   - [ ] Verify downgrade reasoning is logged

5. Test cooldown mechanism:
   - [ ] Quality adjustments should not occur more than once per 10 seconds

6. Review algorithm behavior:
   - [ ] Downgrades occur when: packet loss >2%, latency >150ms, or jitter >30ms
   - [ ] Upgrades occur when: packet loss <1%, latency <105ms, and jitter <21ms
   - [ ] Quality levels: 0=Low, 1=Medium, 2=High, 3=Ultra

**Expected Logs:**
```
INFO Adaptive bitrate enabled - quality will adjust based on network conditions
DEBUG Network metrics: loss=0.12%, latency=45.2ms, jitter=8.3ms
WARN Network degradation detected: packet loss 3.2%, latency 180ms. Downgrading quality from High to Medium
INFO Network conditions excellent. Upgrading quality from Medium to High
WARN Quality adjustment recommended but not applied (requires server support)
```

---

### ✅ Feature 3: Stream Recording to MP4/MKV

**Test Steps:**
1. Record to MP4:
   ```bash
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --record test_recording.mp4 \
     --verbose
   ```

2. Verify startup:
   - [ ] Log shows "Recording enabled - saving to: test_recording.mp4"
   - [ ] Log shows FFmpeg command
   - [ ] Log shows "Recording started successfully"

3. Stream for 30+ seconds:
   - [ ] Verify frames are being written (check logs)
   - [ ] Monitor file size growth:
     ```bash
     watch -n 1 "ls -lh test_recording.mp4"
     ```

4. Stop streaming (Ctrl+C):
   - [ ] Log shows "Stopping recording... (N frames written)"
   - [ ] Log shows "Recording saved successfully to: test_recording.mp4"

5. Verify recording:
   ```bash
   # Check file info
   ffprobe test_recording.mp4

   # Play recording
   ffplay test_recording.mp4
   ```
   - [ ] File is valid MP4
   - [ ] Video codec is H.264
   - [ ] Resolution matches stream settings
   - [ ] Video plays smoothly

6. Test with audio:
   ```bash
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --audio \
     --record test_with_audio.mp4 \
     --verbose
   ```
   - [ ] Recording includes audio track
   - [ ] Audio codec is AAC
   - [ ] Audio/video sync is good

7. Test different formats:
   ```bash
   # MKV
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --record test.mkv

   # AVI
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --record test.avi
   ```
   - [ ] MKV recording works
   - [ ] AVI recording works
   - [ ] Each format is detected correctly from extension

8. Test error handling:
   ```bash
   # Invalid path
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --record /invalid/path/test.mp4
   ```
   - [ ] Error logged gracefully
   - [ ] Streaming continues without recording

---

### ✅ Feature 4: Network Statistics Tracking

**Test Steps:**
1. Start streaming with verbose logging:
   ```bash
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --verbose
   ```

2. Monitor periodic stats (every 10 seconds):
   - [ ] Stats report includes video frames, FPS, bitrate
   - [ ] Stats report includes audio frames, FPS, bitrate
   - [ ] Stats report includes packet loss percentage
   - [ ] Stats format: `Stats: 45.2s | Video: 1356 frames (30.0 fps, 1950.3 kbps) | Audio: 2174 frames (48.0 fps, 126.2 kbps) | Loss: 0.12%`

3. Verify packet loss detection:
   - [ ] Sequence numbers are tracked correctly
   - [ ] Sequence number wrapping (at u32::MAX) is handled
   - [ ] Packet loss percentage is accurate

4. Test with perfect network:
   - [ ] Good WiFi connection
   - [ ] Packet loss should be <0.5%
   - [ ] Latency should be <100ms

5. Test with degraded network:
   - [ ] Move phone far from router
   - [ ] Packet loss increases (>1%)
   - [ ] Stats accurately reflect degradation

---

## Integration Testing

### Combined Features Test
Test all features together:

```bash
./target/release/monkeysee-client \
  --url rtsp://PHONE_IP:8554/camera \
  --device /dev/video10 \
  --audio \
  --adaptive-bitrate \
  --record full_test_$(date +%Y%m%d_%H%M%S).mp4 \
  --verbose
```

**Verify:**
- [ ] All features initialize correctly
- [ ] Streaming works with all features enabled
- [ ] Stats show all metrics
- [ ] Adaptive bitrate monitors and logs adjustments
- [ ] Recording saves successfully
- [ ] Audio plays during recording
- [ ] Virtual camera works in apps (Zoom, Chrome, etc.)

---

## Performance Testing

### Resource Usage
Monitor system resources during streaming:

```bash
# CPU usage
top -p $(pgrep monkeysee-client)

# Memory usage
ps aux | grep monkeysee-client
```

**Expected:**
- [ ] CPU: 20-35% on desktop
- [ ] Memory: 50-100MB
- [ ] No memory leaks during long streaming sessions (30+ min)

### Network Usage
```bash
# Monitor bandwidth
iftop -i wlan0
```

**Expected:**
- [ ] Low quality: ~500 kbps
- [ ] Medium quality: ~1.5 Mbps
- [ ] High quality: ~2.5 Mbps
- [ ] Ultra quality: ~4 Mbps

### Latency
- [ ] End-to-end video latency: 50-150ms
- [ ] End-to-end audio latency: 25-80ms
- [ ] Acceptable for video calls and content creation

---

## Regression Testing

Verify old features still work:

1. **Basic Streaming:**
   ```bash
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10
   ```
   - [ ] Streaming works without optional features

2. **Auto-Reconnection:**
   - [ ] Disconnect WiFi on phone
   - [ ] Client attempts reconnection with exponential backoff
   - [ ] Streaming resumes when WiFi reconnects

3. **Audio Playback:**
   ```bash
   ./target/release/monkeysee-client \
     --url rtsp://PHONE_IP:8554/camera \
     --device /dev/video10 \
     --audio
   ```
   - [ ] Audio plays through speakers
   - [ ] Audio/video sync is good

4. **Virtual Camera:**
   - [ ] Test in Chrome: chrome://settings/content/camera
   - [ ] Test in Zoom: Settings → Video
   - [ ] Test with ffplay: `ffplay /dev/video10`
   - [ ] Camera appears as "MonkeySee"

---

## Known Limitations (from v0.2 implementation)

1. **Adaptive Bitrate**: Currently logs recommendations but doesn't apply them
   - Requires RTSP SET_PARAMETER support on server side
   - Will be implemented in future version

2. **GUI Recording**: Not yet integrated
   - Recording currently CLI-only
   - Will be added to GUI in v0.3

3. **Audio Recording**: May have sync issues
   - FFmpeg handles audio separately via pipe
   - Complex timing scenarios may need adjustment

4. **Windows/macOS Virtual Camera**: Not implemented
   - Implementation guides provided
   - Requires platform-specific development

---

## Troubleshooting

### Recording fails with "FFmpeg not found"
**Solution:** Install FFmpeg
```bash
sudo apt install ffmpeg  # Ubuntu/Debian
sudo dnf install ffmpeg  # Fedora
brew install ffmpeg      # macOS
```

### Virtual camera not found
**Solution:** Load v4l2loopback
```bash
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
```

### High packet loss
**Possible causes:**
- Weak WiFi signal → Move closer to router
- Network congestion → Switch to 5GHz band
- Phone CPU overload → Lower quality preset

### Audio playback issues
**Solution:** Check ALSA configuration
```bash
# List audio devices
aplay -L

# Test audio output
speaker-test -t wav -c 2
```

---

## Success Criteria

All tests pass if:
- [ ] Code compiles without errors
- [ ] All unit tests pass (4 tests in adaptive_bitrate.rs)
- [ ] All 4 new features work as documented
- [ ] No regressions in existing features
- [ ] Performance is acceptable
- [ ] Resource usage is within expected ranges
- [ ] Documentation is accurate

---

**Version:** 0.2.0
**Date:** 2025-10-27
**Test Environment:** Linux (Ubuntu/Debian recommended)
**Total Features to Test:** 13 (4 new + 9 existing)
