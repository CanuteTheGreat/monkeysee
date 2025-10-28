# MonkeySee Testing Guide

Comprehensive testing documentation for MonkeySee.

## Test Coverage

### Rust Unit Tests ✅

**Location**: `desktop/monkeysee-rtsp/src/stream.rs`

Tests for RTP packet parsing:
- `test_parse_rtp_packet_basic` - Basic RTP packet parsing
- `test_parse_rtp_packet_too_short` - Error handling for malformed packets
- `test_parse_rtp_packet_wrong_version` - RTP version validation
- `test_parse_rtp_packet_with_csrc` - CSRC handling
- `test_sequence_number_extraction` - Sequence number parsing

**Run tests**:
```bash
cd desktop
cargo test --lib
```

### Android Unit Tests ✅

**Location**: `android/app/src/test/kotlin/com/monkeysee/app/rtsp/RtpPacketizerTest.kt`

Tests for RTP packetization:
- `single NAL unit packet for small frame` - Single packet mode
- `fragmented packets for large frame` - FU-A fragmentation
- `timestamp conversion to 90kHz clock` - Timestamp handling
- `sequence number increments` - Sequence tracking
- `start code variations handled correctly` - Start code removal
- `packet size does not exceed MTU` - MTU compliance

**Run tests**:
```bash
cd android
./gradlew test
```

## Integration Tests (TODO)

### End-to-End Streaming Test
```kotlin
// Test full pipeline from camera to virtual device
@Test
fun testFullStreamingPipeline() {
    // 1. Start RTSP server
    // 2. Connect client
    // 3. Stream test frames
    // 4. Verify frames received correctly
}
```

### Network Error Handling
```rust
#[tokio::test]
async fn test_connection_recovery() {
    // 1. Establish connection
    // 2. Simulate network interruption
    // 3. Verify auto-reconnect
}
```

### Format Negotiation
```rust
#[test]
fn test_sdp_parsing() {
    // 1. Generate SDP
    // 2. Parse and validate
    // 3. Ensure codec compatibility
}
```

## Manual Testing Checklist

### Android App
- [ ] Camera permission request
- [ ] Camera preview displays correctly
- [ ] IP address shown correctly
- [ ] Start streaming button works
- [ ] RTSP server accepts connections
- [ ] Video frames encoded successfully
- [ ] App doesn't crash when backgrounded
- [ ] Battery usage is reasonable

### Desktop Client
- [ ] Connects to RTSP server
- [ ] Receives and decodes video
- [ ] Writes to v4l2loopback device
- [ ] Virtual camera visible in apps
- [ ] Video quality is acceptable
- [ ] Latency is acceptable (<200ms)
- [ ] CPU usage is reasonable (<30%)
- [ ] No memory leaks

### End-to-End
- [ ] Works on same WiFi network
- [ ] Works with VLC player
- [ ] Works with ffplay
- [ ] Works in Chrome/Firefox webcam
- [ ] Works in Zoom
- [ ] Works in Discord
- [ ] Works in OBS Studio
- [ ] Handles phone rotation
- [ ] Recovers from network issues

## Performance Testing

### Latency Measurement
```bash
# Measure end-to-end latency
# 1. Display timestamp on phone screen
# 2. Capture desktop screen showing virtual camera
# 3. Compare timestamps
# Target: < 200ms
```

### Bandwidth Testing
```bash
# Monitor network usage
sudo iftop -i wlan0

# Expected: ~2 Mbps (2000 Kbps) at default settings
```

### CPU Profiling
```bash
# Android
adb shell top | grep monkeysee

# Desktop
top | grep monkeysee-client

# Target: < 30% CPU on both sides
```

## Test Data

### Valid RTP Packet (Hex)
```
80 60 00 01 00 00 00 0A 12 34 56 78 DE AD BE EF
^^ ^^ ^^^^^ ^^^^^^^^^^^ ^^^^^^^^^^^ ^^^^^^^^^^^
|  |  |     |           |           payload
|  |  |     |           SSRC
|  |  |     timestamp
|  |  sequence
|  PT + M
V,P,X,CC
```

### Valid H.264 NAL Unit
```
00 00 00 01 65 88 84 00 ...
^^^^^^^^^^^ ^^ ^^^^^^^^^^
start code  |  payload
            NAL header (IDR)
```

### SDP Example
```sdp
v=0
o=- 0 0 IN IP4 127.0.0.1
s=MonkeySee Camera
c=IN IP4 0.0.0.0
t=0 0
m=video 0 RTP/AVP 96
a=rtpmap:96 H264/90000
a=fmtp:96 packetization-mode=1
a=control:track0
```

## Continuous Integration (TODO)

### GitHub Actions Workflow
```yaml
name: Tests

on: [push, pull_request]

jobs:
  android-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Android tests
        run: cd android && ./gradlew test

  rust-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Install dependencies
        run: sudo apt install libavcodec-dev pkg-config
      - name: Run Rust tests
        run: cd desktop && cargo test
```

## Test Metrics

Target coverage:
- **Unit tests**: > 80% code coverage
- **Integration tests**: Critical paths covered
- **Manual testing**: All features validated before release

## Known Issues

1. FFmpeg tests require system libraries installed
2. Android tests need emulator or device for camera tests
3. Integration tests require network setup

## Contributing Tests

When adding new features, please include:
1. Unit tests for core logic
2. Integration tests for feature interaction
3. Update this document with test instructions

See [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) for more details.
