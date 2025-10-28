# macOS Virtual Camera Implementation Guide

## Overview

Implementing a virtual webcam on macOS requires creating a **CoreMediaIO DAL (Device Abstraction Layer) plugin**. This is a specialized system extension that presents itself as a camera device to applications.

## Current Status

The MonkeySee macOS virtual camera is currently a **stub implementation**. The `monkeysee-virt/src/macos.rs` file contains placeholder code that needs to be replaced with a full CoreMediaIO plugin.

## Implementation Options

### Option 1: Use Existing Virtual Camera Software

**Easiest approach** - Users can install existing virtual camera software:

1. **OBS Virtual Camera** (Free, Open Source)
   - Download OBS Studio: https://obsproject.com/
   - Install and enable Virtual Camera plugin
   - Available on macOS 10.13+

2. **CamTwist** (Free)
   - Virtual webcam for macOS
   - http://camtwiststudio.com/

3. **SnapCamera** (Free, but discontinued)
   - May still work on older macOS versions

### Option 2: CoreMediaIO DAL Plugin

**Full integration** - Implement a native CoreMediaIO plugin for MonkeySee.

#### Requirements

- **Xcode** (12.0 or later)
- **macOS SDK** (10.13+)
- **Swift** or **Objective-C++**
- Code signing certificate (for distribution)
- Understanding of CoreMedia framework

#### Architecture

```
MonkeySee Client (Rust)
        ↓
   XPC Service / Shared Memory
        ↓
CoreMediaIO DAL Plugin (Swift/ObjC)
        ↓
   CoreMediaIO Framework
        ↓
   Applications (Zoom, Safari, etc.)
```

#### Implementation Steps

1. **Create DAL Plugin Bundle**

Project structure:
```
MonkeySeeCamera.plugin/
├── Contents/
│   ├── Info.plist
│   ├── MacOS/
│   │   └── MonkeySeeCamera
│   └── Resources/
```

Info.plist:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" ...>
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>com.monkeysee.camera.dal</string>
    <key>CFBundleName</key>
    <string>MonkeySee Camera</string>
    <key>CMIOHardwarePlugIn</key>
    <dict>
        <key>CMIODeviceClassName</key>
        <string>MonkeySeeDevice</string>
        <key>CMIODeviceInterfaceVendor</key>
        <string>MonkeySee</string>
    </dict>
</dict>
</plist>
```

2. **Implement Plugin Entry Point**

```swift
import CoreMediaIO
import IOKit

@_cdecl("CMIOGetHardwarePlugIn")
func CMIOGetHardwarePlugIn(
    allocator: CFAllocator?,
    pluginRef: UnsafeMutablePointer<UnsafeMutableRawPointer?>
) -> OSStatus {
    let plugin = MonkeySeePlugin()
    pluginRef.pointee = Unmanaged.passRetained(plugin).toOpaque()
    return kCMIOHardwareNoError
}

class MonkeySeePlugin: NSObject, CMIOHardwarePlugInInterface {
    private var device: MonkeySeeDevice?

    func initialize(with host: CMIOHardwarePlugInHostInterface) -> OSStatus {
        device = MonkeySeeDevice()
        return device?.publish() ?? kCMIOHardwareUnspecifiedError
    }

    func teardown() -> OSStatus {
        device?.unpublish()
        return kCMIOHardwareNoError
    }
}
```

3. **Implement Virtual Device**

```swift
class MonkeySeeDevice: NSObject {
    private var deviceID: CMIODeviceID = 0
    private var streamID: CMIOStreamID = 0
    private var sharedMemory: UnsafeMutableRawPointer?

    func publish() -> OSStatus {
        // Create device
        var deviceID: CMIODeviceID = 0
        var streamID: CMIOStreamID = 0

        let deviceName = "MonkeySee Camera" as CFString
        let deviceUID = "com.monkeysee.camera.device" as CFString

        // Register device with CoreMediaIO
        var description = CMIODeviceDescription(
            name: deviceName,
            uid: deviceUID,
            modelUID: deviceUID,
            isHidden: false
        )

        let status = CMIOObjectCreate(
            kCMIOObjectSystemObject,
            &description,
            &deviceID
        )

        guard status == kCMIOHardwareNoError else {
            return status
        }

        self.deviceID = deviceID

        // Create video stream
        return createStream()
    }

    func createStream() -> OSStatus {
        var streamID: CMIOStreamID = 0

        let streamFormat = CMFormatDescription.create(
            mediaType: .video,
            mediaSubType: kCVPixelFormatType_420YpCbCr8Planar,
            width: 1280,
            height: 720,
            extensions: nil
        )

        let status = CMIOStreamCreate(
            deviceID,
            streamFormat,
            &streamID
        )

        self.streamID = streamID
        return status
    }

    func provideFrame() {
        // Read from shared memory and provide to stream
        guard let buffer = readFromSharedMemory() else {
            return
        }

        CMIOStreamDeckQueueFrame(streamID, buffer)
    }

    private func readFromSharedMemory() -> CMSampleBuffer? {
        // TODO: Implement shared memory reading
        // Use mach ports or XPC service to communicate with Rust client
        return nil
    }
}
```

4. **Implement IPC from Rust**

```rust
// monkeysee-virt/src/macos.rs
use core_foundation::base::TCFType;
use core_foundation::string::CFString;
use mach::port::*;
use mach::mach_port::*;

pub struct MacOSVirtualCamera {
    mach_port: mach_port_t,
    shared_memory: *mut u8,
    width: u32,
    height: u32,
}

impl MacOSVirtualCamera {
    pub fn init(&mut self, format: VideoFormat) -> Result<()> {
        unsafe {
            // Create Mach port for IPC
            let mut port: mach_port_t = 0;
            let kr = mach_port_allocate(
                mach_task_self(),
                MACH_PORT_RIGHT_RECEIVE,
                &mut port,
            );

            if kr != KERN_SUCCESS {
                return Err(Error::Init("Failed to create Mach port".into()));
            }

            self.mach_port = port;

            // Setup shared memory region
            let size = (format.width * format.height * 3 / 2) as usize;
            // Use mach_vm_allocate for shared memory
        }

        Ok(())
    }

    pub fn write_frame(&mut self, data: &[u8]) -> Result<()> {
        unsafe {
            // Write to shared memory
            std::ptr::copy_nonoverlapping(
                data.as_ptr(),
                self.shared_memory,
                data.len(),
            );

            // Send notification via Mach port
            // mach_msg to notify plugin of new frame
        }

        Ok(())
    }
}
```

5. **Install Plugin**

```bash
# Copy plugin to system location
sudo cp -R MonkeySeeCamera.plugin /Library/CoreMediaIO/Plug-Ins/DAL/

# Restart CoreMediaIO
sudo killall VDCAssistant
sudo killall AppleCameraAssistant
```

#### References

- **CoreMediaIO Documentation**: https://developer.apple.com/documentation/coremediaio
- **Sample DAL Plugin**: https://github.com/johnboiles/obs-mac-virtualcam
- **OBS Virtual Camera (macOS)**: https://github.com/obsproject/obs-studio/tree/master/plugins/mac-virtualcam
- **SimpleDALPlugin Example**: https://github.com/lvsti/SimpleDALPlugin

### Option 3: Adapt OBS Virtual Camera

**Moderate effort** - Use OBS's macOS virtual camera implementation.

OBS Studio has an excellent open-source virtual camera for macOS:

1. Extract OBS mac-virtualcam plugin code
2. Adapt to work standalone
3. Create C bindings for Rust integration

**Repository**: https://github.com/obsproject/obs-studio/tree/master/plugins/mac-virtualcam

This implementation uses:
- CoreMediaIO DAL plugin
- Mach ports for IPC
- IOSurface for zero-copy frame sharing

## Recommended Approach

For most users: **Option 1** (use OBS Virtual Camera)
For developers: **Option 3** (adapt OBS implementation)
For full control: **Option 2** (implement from scratch)

## System Requirements

- macOS 10.13 (High Sierra) or later
- CoreMediaIO framework
- Camera permissions in System Preferences

## Testing

Once implemented, test with:

```bash
# List video devices
ffmpeg -f avfoundation -list_devices true -i ""

# Should show:
# [AVFoundation indev @ 0x...] MonkeySee Camera

# Test with ffplay
ffplay -f avfoundation -i "MonkeySee Camera"

# Or test in Safari
# Visit https://webcamtests.com
```

## Security Considerations

### Code Signing

CoreMediaIO plugins on macOS require code signing:

```bash
# Sign the plugin
codesign --force --sign "Developer ID Application: Your Name" \
         /Library/CoreMediaIO/Plug-Ins/DAL/MonkeySeeCamera.plugin

# Verify signature
codesign -v /Library/CoreMediaIO/Plug-Ins/DAL/MonkeySeeCamera.plugin
```

### Notarization

For macOS 10.15+, plugins must be notarized:

```bash
# Create a zip
ditto -c -k --sequesterRsrc --keepParent MonkeySeeCamera.plugin MonkeySeeCamera.zip

# Upload for notarization
xcrun altool --notarize-app \
             --primary-bundle-id "com.monkeysee.camera.dal" \
             --username "your@email.com" \
             --password "@keychain:AC_PASSWORD" \
             --file MonkeySeeCamera.zip

# Staple the notarization
xcrun stapler staple MonkeySeeCamera.plugin
```

## Current Stub Limitations

The current `macos.rs` implementation:
- ❌ Does not create a real CoreMediaIO device
- ❌ Cannot be detected by macOS applications
- ❌ Requires external virtual camera software

To use MonkeySee on macOS now:
1. Install OBS Studio
2. Enable OBS Virtual Camera
3. Use MonkeySee with OBS as intermediary

## Troubleshooting

### Plugin Not Detected

```bash
# Check if plugin is in correct location
ls -la /Library/CoreMediaIO/Plug-Ins/DAL/

# Check plugin permissions
ls -l /Library/CoreMediaIO/Plug-Ins/DAL/MonkeySeeCamera.plugin

# View system logs
log show --predicate 'subsystem == "com.apple.coremedia"' --last 1m
```

### Camera Permission Issues

Grant camera access in:
System Preferences → Security & Privacy → Camera

## Contributing

If you implement a macOS virtual camera for MonkeySee:

1. Create the CoreMediaIO DAL plugin
2. Update `monkeysee-virt/src/macos.rs`
3. Provide build and install scripts
4. Add code signing instructions
5. Submit a pull request!

Target compatibility:
- macOS 10.13+ (High Sierra and later)
- Both Intel and Apple Silicon (universal binary)
- Automatic registration during install
- Clean uninstallation

## License Note

Any CoreMediaIO implementation must be compatible with MonkeySee's MIT license.
