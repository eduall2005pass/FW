# 🪟 Floating Windows — Android App

Windows 11-style floating window system for Android.
Multiple draggable, resizable, minimizable overlay windows — all managed by a background service.

---

## 📁 Project Structure

```
FloatingWindowApp/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/floatingwindow/app/
│   │   ├── model/
│   │   │   └── WindowState.kt          ← Data model (position, size, status, z-index)
│   │   ├── manager/
│   │   │   └── FloatingWindowManager.kt ← Core WM: create/focus/minimize/z-order logic
│   │   ├── service/
│   │   │   └── FloatingWindowService.kt ← Foreground service, owns all overlay views
│   │   └── ui/
│   │       ├── FloatingWindowView.kt    ← Draggable/resizable window widget
│   │       ├── TaskbarView.kt           ← Bottom taskbar overlay
│   │       ├── WindowContentFactory.kt  ← Notepad, Calculator, Terminal, etc.
│   │       └── MainActivity.kt          ← Launcher UI
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml        ← App launcher screen
│       │   └── floating_window.xml      ← Window template
│       ├── drawable/                    ← All shape/vector drawables
│       └── values/                      ← themes, colors, strings
```

---

## ✅ Features

| Feature | Details |
|---|---|
| Multiple windows | Open unlimited simultaneous windows |
| Drag | Drag by title bar — clamped to screen edges |
| Resize | Bottom-right corner handle |
| Minimize | Hides window, chip stays in taskbar |
| Restore | Tap taskbar chip → window reappears with animation |
| Maximize | Toggle full-screen or double-tap title bar |
| Close | ✕ button with fade animation |
| Z-order | Focus brings window to front; tracked exactly |
| Auto-focus | Next window auto-focused on minimize/close |
| Switch | Cycle through all visible windows |
| Taskbar | Live chip per window; colored by state |
| 5 app types | Notepad · Calculator · Terminal · Files · Settings |
| Animations | Fade-scale in/out on open/minimize/close |

---

## 🚀 Setup in Android Studio

### 1. Open project
```
File → Open → select FloatingWindowApp/
```

### 2. Sync Gradle
Android Studio will auto-sync. If not: `File → Sync Project with Gradle Files`

### 3. Add `sdk.dir` to `local.properties`
```
sdk.dir=/Users/YOUR_NAME/Library/Android/sdk       # macOS
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk  # Windows
```

### 4. Run on device (API 26+)
- Physical device recommended (overlay windows don't work well on emulator)
- Enable **USB Debugging** on device

### 5. Grant overlay permission
On first launch → tap **"Grant Permission"** → enable "Display over other apps" for this app

---

## 🔧 How it works

```
MainActivity
    ↓ startForegroundService + bindService
FloatingWindowService   ←→   FloatingWindowManager
    ↓ addView to WindowManager          ↑ event callbacks
FloatingWindowView  (one per window)
    ↓ user drag/resize/button tap
FloatingWindowManager.moveWindow() / resizeWindow() / focusWindow() ...
    ↓ listener callback
FloatingWindowService updates view layout params
    ↓ windowManager.updateViewLayout()
System renders updated position/size
```

---

## 📱 Requirements

- Android 8.0+ (API 26)
- `SYSTEM_ALERT_WINDOW` permission (granted manually by user)
- `FOREGROUND_SERVICE` permission (auto-granted)

---

## 🎨 Extending

### Add a new window type
1. Add entry to `WindowContentType` enum in `WindowState.kt`
2. Add a `buildYourType()` function in `WindowContentFactory.kt`
3. Add a button in `activity_main.xml` and wire it in `MainActivity.kt`

### Add inter-window communication
Use `FloatingWindowService.getInstance()` from any view to call `getWindowManager()`.
