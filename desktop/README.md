# MonkeySee Desktop Client

The desktop client receives the RTSP stream from your Android phone and creates a virtual webcam device that can be used by any application.

## Architecture

The client is built with Rust and consists of three crates:

- **monkeysee-client**: CLI application that ties everything together
- **monkeysee-rtsp**: RTSP/RTP client library for receiving video streams
- **monkeysee-virt**: Platform-specific virtual camera implementations

## Prerequisites

### Linux

Install v4l2loopback kernel module:

```bash
# Ubuntu/Debian
sudo apt install v4l2loopback-dkms

# Arch Linux
sudo pacman -S v4l2loopback-dkms

# Fedora
sudo dnf install v4l2loopback
```

Load the module:

```bash
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
```

This creates a virtual camera at `/dev/video10`.

To load it automatically on boot:

```bash
echo "v4l2loopback" | sudo tee /etc/modules-load.d/v4l2loopback.conf
echo "options v4l2loopback devices=1 video_nr=10 card_label=\"MonkeySee\" exclusive_caps=1" | sudo tee /etc/modprobe.d/v4l2loopback.conf
```

### Windows

Windows support requires a virtual camera driver. The DirectShow implementation is currently a stub and needs to be completed.

Options:
1. Use OBS VirtualCam (install OBS Studio)
2. Implement DirectShow filter (requires C++ development)

### macOS

macOS support requires a CoreMediaIO DAL plugin. The implementation is currently a stub.

Options:
1. Use OBS VirtualCam (install OBS Studio)
2. Implement CoreMediaIO plugin (requires Objective-C/Swift development)

## Building

Install Rust (if not already installed):

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

Build the project:

```bash
cd desktop
cargo build --release
```

The compiled binary will be at `target/release/monkeysee-client`.

## Usage

### Basic Usage

```bash
./target/release/monkeysee-client --url rtsp://192.168.1.100:8554/camera
```

Replace `192.168.1.100` with your Android phone's IP address (shown in the app).

### Options

```
Options:
  -u, --url <URL>          RTSP URL of the camera stream
  -d, --device <DEVICE>    Virtual camera device path [default: /dev/video0]
      --width <WIDTH>      Video width [default: 1280]
      --height <HEIGHT>    Video height [default: 720]
      --fps <FPS>          Frames per second [default: 30]
  -v, --verbose            Verbose logging
  -h, --help              Print help
```

### Linux Example

```bash
# Stream to /dev/video10 with debug output
./target/release/monkeysee-client \
  --url rtsp://192.168.1.100:8554/camera \
  --device /dev/video10 \
  --verbose
```

## Testing the Virtual Camera

### Linux

Use v4l2-ctl to verify the device:

```bash
v4l2-ctl --list-devices
```

Test with ffplay:

```bash
ffplay /dev/video10
```

Use in applications:
- Chrome/Firefox: Select "MonkeySee" in video settings
- Zoom/Teams/Discord: Select the virtual camera device
- OBS: Add "Video Capture Device" source

## Troubleshooting

### Linux

**Problem**: "Device not found"
```bash
# Check if v4l2loopback is loaded
lsmod | grep v4l2loopback

# Load it manually
sudo modprobe v4l2loopback
```

**Problem**: "Permission denied"
```bash
# Add user to video group
sudo usermod -a -G video $USER

# Or set permissions (not recommended)
sudo chmod 666 /dev/video10
```

**Problem**: Build fails with "libclang not found"
```bash
# Ubuntu/Debian
sudo apt install libclang-dev

# Arch Linux
sudo pacman -S clang

# Fedora
sudo dnf install clang-devel
```

## Current Limitations

The current implementation is a functional prototype with the following limitations:

1. **Video Decoding**: The H.264 decoder is not yet implemented. You'll need to add a decoder using:
   - ffmpeg-next (Rust bindings for FFmpeg)
   - GStreamer bindings
   - Custom H.264 decoder

2. **Frame Format Conversion**: Conversion from H.264 to YUV420 is not implemented

3. **Platform Support**: Windows and macOS implementations are stubs

4. **Error Recovery**: Limited error handling and reconnection logic

## Next Steps

To complete the implementation:

1. Add H.264 decoder (recommend using `ffmpeg-next`)
2. Implement frame format conversion
3. Add audio support (AAC decoding and virtual audio device)
4. Implement Windows DirectShow filter
5. Implement macOS CoreMediaIO plugin
6. Add GUI interface (using egui or iced)

## Contributing

Contributions are welcome! Areas that need work:

- [ ] H.264 decoder integration
- [ ] Windows virtual camera implementation
- [ ] macOS virtual camera implementation
- [ ] Audio streaming support
- [ ] GUI application
- [ ] Better error handling and logging
- [ ] Unit tests and integration tests

## License

MIT License
