#!/bin/bash
#
# MonkeySee v0.2.0 - Dependency Installation Script
# Run this to install all required system dependencies
#

set -e  # Exit on error

echo "=================================="
echo "MonkeySee Dependency Setup"
echo "=================================="
echo ""

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
else
    echo "Cannot detect OS. Please install dependencies manually."
    exit 1
fi

echo "Detected OS: $OS"
echo ""

# Install dependencies based on OS
case "$OS" in
    ubuntu|debian|linuxmint|pop)
        echo "Installing dependencies for Ubuntu/Debian..."
        sudo apt update

        echo "→ Installing build essentials..."
        sudo apt install -y build-essential pkg-config libclang-dev

        echo "→ Installing FFmpeg development libraries..."
        sudo apt install -y libavcodec-dev libavformat-dev libavutil-dev \
                            libavdevice-dev libswscale-dev libavfilter-dev

        echo "→ Installing ALSA development libraries..."
        sudo apt install -y libasound2-dev

        echo "→ Installing v4l2loopback..."
        sudo apt install -y v4l2loopback-dkms

        echo "→ Installing FFmpeg (for recording)..."
        sudo apt install -y ffmpeg
        ;;

    fedora|rhel|centos)
        echo "Installing dependencies for Fedora/RHEL..."

        echo "→ Installing build essentials..."
        sudo dnf install -y clang-devel pkg-config

        echo "→ Installing FFmpeg development libraries..."
        sudo dnf install -y ffmpeg-devel

        echo "→ Installing ALSA development libraries..."
        sudo dnf install -y alsa-lib-devel

        echo "→ Installing v4l2loopback..."
        sudo dnf install -y v4l2loopback

        echo "→ Installing FFmpeg (for recording)..."
        sudo dnf install -y ffmpeg
        ;;

    arch|manjaro)
        echo "Installing dependencies for Arch Linux..."

        echo "→ Installing build essentials..."
        sudo pacman -S --noconfirm base-devel clang pkg-config

        echo "→ Installing FFmpeg..."
        sudo pacman -S --noconfirm ffmpeg

        echo "→ Installing ALSA..."
        sudo pacman -S --noconfirm alsa-lib

        echo "→ Installing v4l2loopback..."
        sudo pacman -S --noconfirm v4l2loopback-dkms
        ;;

    *)
        echo "Unsupported OS: $OS"
        echo ""
        echo "Please install the following packages manually:"
        echo "  - pkg-config"
        echo "  - libclang (clang development headers)"
        echo "  - FFmpeg development libraries (libavcodec, libavformat, libavutil, etc.)"
        echo "  - ALSA development libraries"
        echo "  - v4l2loopback kernel module"
        echo "  - ffmpeg binary"
        exit 1
        ;;
esac

echo ""
echo "=================================="
echo "Setting up v4l2loopback..."
echo "=================================="
echo ""

# Load v4l2loopback module
if ! lsmod | grep -q v4l2loopback; then
    echo "→ Loading v4l2loopback kernel module..."
    sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
    echo "  Created virtual camera at /dev/video10"
else
    echo "  v4l2loopback already loaded"
fi

# Make it persistent across reboots
echo "→ Making v4l2loopback persistent..."
if [ ! -f /etc/modules-load.d/v4l2loopback.conf ]; then
    echo "v4l2loopback" | sudo tee /etc/modules-load.d/v4l2loopback.conf > /dev/null
    echo "  Created /etc/modules-load.d/v4l2loopback.conf"
fi

if [ ! -f /etc/modprobe.d/v4l2loopback.conf ]; then
    echo "options v4l2loopback devices=1 video_nr=10 card_label=\"MonkeySee\" exclusive_caps=1" | \
        sudo tee /etc/modprobe.d/v4l2loopback.conf > /dev/null
    echo "  Created /etc/modprobe.d/v4l2loopback.conf"
fi

echo ""
echo "=================================="
echo "Verifying installation..."
echo "=================================="
echo ""

# Check Rust
if command -v rustc &> /dev/null; then
    RUST_VERSION=$(rustc --version)
    echo "✓ Rust: $RUST_VERSION"
else
    echo "✗ Rust not found!"
    echo "  Install from: https://rustup.rs/"
fi

# Check pkg-config
if command -v pkg-config &> /dev/null; then
    echo "✓ pkg-config: $(pkg-config --version)"
else
    echo "✗ pkg-config not found"
fi

# Check libclang
if pkg-config --exists libclang 2>/dev/null || [ -f /usr/lib/libclang.so ] || [ -f /usr/lib64/libclang.so ]; then
    echo "✓ libclang: installed"
else
    echo "✗ libclang not found"
fi

# Check FFmpeg libraries
if pkg-config --exists libavcodec 2>/dev/null; then
    FFMPEG_VERSION=$(pkg-config --modversion libavcodec)
    echo "✓ FFmpeg libraries: $FFMPEG_VERSION"
else
    echo "✗ FFmpeg development libraries not found"
fi

# Check FFmpeg binary
if command -v ffmpeg &> /dev/null; then
    FFMPEG_BIN_VERSION=$(ffmpeg -version | head -n 1 | cut -d' ' -f3)
    echo "✓ FFmpeg binary: $FFMPEG_BIN_VERSION"
else
    echo "✗ FFmpeg binary not found"
fi

# Check ALSA
if pkg-config --exists alsa 2>/dev/null; then
    ALSA_VERSION=$(pkg-config --modversion alsa)
    echo "✓ ALSA: $ALSA_VERSION"
else
    echo "✗ ALSA development libraries not found"
fi

# Check v4l2loopback
if lsmod | grep -q v4l2loopback; then
    echo "✓ v4l2loopback: loaded"
    if [ -e /dev/video10 ]; then
        echo "  Virtual camera: /dev/video10 ✓"
    else
        echo "  Virtual camera: /dev/video10 ✗"
    fi
else
    echo "✗ v4l2loopback not loaded"
fi

echo ""
echo "=================================="
echo "Setup Complete!"
echo "=================================="
echo ""
echo "Next steps:"
echo "  1. Build the project:"
echo "     cd desktop/monkeysee-client && cargo build --release"
echo "  2. Run the client:"
echo "     ./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10"
echo "  3. See TESTING_V0.2.md for comprehensive testing guide"
echo ""
echo "Virtual camera created at: /dev/video10"
echo "Use this device in any webcam application!"
echo ""
