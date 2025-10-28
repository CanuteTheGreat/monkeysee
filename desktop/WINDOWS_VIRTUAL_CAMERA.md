# Windows Virtual Camera Implementation Guide

## Overview

Implementing a virtual webcam on Windows requires creating a **DirectShow filter** that acts as a video capture device. This is a complex undertaking that involves COM programming and Windows kernel-mode drivers.

## Current Status

The MonkeySee Windows virtual camera is currently a **stub implementation**. The `monkeysee-virt/src/windows.rs` file contains placeholder code that needs to be replaced with a full DirectShow filter.

## Implementation Options

### Option 1: Use Existing Virtual Camera Software

**Easiest approach** - Users can install existing virtual camera software:

1. **OBS Virtual Camera** (Free, Open Source)
   - Download OBS Studio: https://obsproject.com/
   - Install and enable Virtual Camera feature
   - Run MonkeySee client to output to a file/pipe
   - Configure OBS to read from that source

2. **ManyCam** (Freemium)
   - Commercial solution with virtual webcam built-in
   - https://manycam.com/

3. **Camera2** (Commercial)
   - Professional virtual camera for Windows
   - https://www.camera2.app/

### Option 2: DirectShow Virtual Camera Filter

**Full integration** - Implement a native DirectShow filter for MonkeySee.

#### Requirements

- **Windows SDK** (10.0+)
- **Visual Studio** (2019 or later)
- **DirectShow Base Classes** (strmbase.lib)
- Knowledge of COM programming
- Understanding of video capture device interfaces

#### Architecture

```
MonkeySee Client (Rust)
        ↓
   Shared Memory / Named Pipe
        ↓
DirectShow Filter (C++)
        ↓
   Windows Video Capture API
        ↓
   Applications (Zoom, Teams, etc.)
```

#### Implementation Steps

1. **Create DirectShow Filter DLL**
   ```cpp
   class MonkeySeeFilter : public CSource {
   public:
       static CUnknown * WINAPI CreateInstance(LPUNKNOWN lpunk, HRESULT *phr);

   private:
       MonkeySeeFilter(LPUNKNOWN lpunk, HRESULT *phr);
   };

   class MonkeySeeStream : public CSourceStream {
   public:
       HRESULT FillBuffer(IMediaSample *pms);
       HRESULT DecideBufferSize(IMemAllocator *pAlloc, ALLOCATOR_PROPERTIES *pProperties);
       HRESULT GetMediaType(int iPosition, CMediaType *pmt);
   };
   ```

2. **Implement IPC Mechanism**
   - Use **shared memory** for frame data (fastest)
   - Or use **named pipes** for simplicity
   - Frame format: Raw YUV420 or RGB24

3. **Register the Filter**
   ```cpp
   // Register as video capture device
   STDAPI DllRegisterServer() {
       HRESULT hr = AMovieDllRegisterServer2(TRUE);
       if (SUCCEEDED(hr)) {
           IFilterMapper2 *pFM2 = NULL;
           hr = CoCreateInstance(CLSID_FilterMapper2, NULL, CLSCTX_INPROC_SERVER,
                                 IID_IFilterMapper2, (void **)&pFM2);
           // Register in CLSID_VideoInputDeviceCategory
       }
       return hr;
   }
   ```

4. **Update Rust Side**
   ```rust
   // monkeysee-virt/src/windows.rs
   use windows::Win32::System::Memory::*;

   pub struct WindowsVirtualCamera {
       shared_memory: HANDLE,
       buffer: *mut u8,
       width: u32,
       height: u32,
   }

   impl WindowsVirtualCamera {
       pub fn init(&mut self, format: VideoFormat) -> Result<()> {
           // Create shared memory
           let size = (format.width * format.height * 3 / 2) as usize;
           unsafe {
               self.shared_memory = CreateFileMappingW(
                   INVALID_HANDLE_VALUE,
                   None,
                   PAGE_READWRITE,
                   0,
                   size as u32,
                   w!("MonkeySeeSharedMemory"),
               )?;

               self.buffer = MapViewOfFile(
                   self.shared_memory,
                   FILE_MAP_ALL_ACCESS,
                   0,
                   0,
                   size,
               ) as *mut u8;
           }
           Ok(())
       }

       pub fn write_frame(&mut self, data: &[u8]) -> Result<()> {
           unsafe {
               std::ptr::copy_nonoverlapping(
                   data.as_ptr(),
                   self.buffer,
                   data.len(),
               );
           }
           Ok(())
       }
   }
   ```

#### References

- **DirectShow Documentation**: https://docs.microsoft.com/en-us/windows/win32/directshow/directshow
- **Virtual Camera Sample**: https://github.com/rdp/screen-capture-recorder-to-video-windows-free
- **BaseClasses**: Included in Windows SDK
- **OBS Virtual Camera Source**: https://github.com/obsproject/obs-studio/tree/master/plugins/win-dshow

### Option 3: Use OBS Virtual Camera Library

**Moderate effort** - Integrate with OBS's virtual camera implementation.

OBS Studio has an open-source virtual camera plugin that can be adapted:

1. Extract OBS virtual camera plugin code
2. Create C bindings for Rust
3. Integrate with MonkeySee client

**Advantages**:
- Battle-tested code
- Wide compatibility
- Already handles DirectShow complexity

**Repository**: https://github.com/obsproject/obs-studio/tree/master/plugins/win-dshow

## Recommended Approach

For most users: **Option 1** (use existing software)
For developers: **Option 3** (adapt OBS virtual camera)
For full control: **Option 2** (implement from scratch)

## Testing

Once implemented, test with:

```bash
# Check if device is registered
ffmpeg -list_devices true -f dshow -i dummy

# Should show:
# "MonkeySee Virtual Camera" (video)

# Test with ffplay
ffplay -f dshow -i video="MonkeySee Virtual Camera"
```

## Current Stub Limitations

The current `windows.rs` implementation:
- ❌ Does not create a real virtual camera
- ❌ Cannot be detected by Windows applications
- ❌ Requires external virtual camera software

To use MonkeySee on Windows now:
1. Install OBS Studio
2. Use OBS Virtual Camera
3. Feed MonkeySee output to OBS via NDI or file

## Contributing

If you implement a Windows virtual camera for MonkeySee:

1. Create the DirectShow filter DLL
2. Update `monkeysee-virt/src/windows.rs`
3. Add installation instructions
4. Submit a pull request!

Target architecture:
- Support Windows 10 and 11
- 64-bit (x86_64)
- Automatic registration during install
- Uninstaller that cleans up registry

## License Note

Any DirectShow filter implementation must be compatible with MonkeySee's MIT license.
