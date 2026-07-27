# AzazelsExplorer

A file manager and media downloader for Android, themed after Azazel from Helltaker.

## Features

### File Browser
- Navigate the full device filesystem with directory stack back-navigation
- Sort by name, date, size, or type (directories always sort first)
- Filter by category: Images, Videos, Documents, Audio, APKs, Archives
- Real-time text search
- Long-press context menu: Rename, Duplicate, Move, Zip, Delete, Share
- **Filtered/Dashboard view from quick access**: All quick access categories (filter-based like Photos/Videos AND folder-based like Documents/Organized Photos) show category name as title, "Viewing from Dashboard" subtitle, and back arrow to return

### Dashboard
- Storage donut chart with per-category breakdown (Photos, Videos, Audio, Docs, Other)
- Quick access grid: tap any category to browse matching files with back arrow and subtitle
- Recent files list (10 most recently modified)
- Tablet-optimized dual-column layout

### Media Downloader
- Download media from TikTok, X/Twitter, Instagram, Facebook, Vimeo, Reddit, and direct URLs
- Email/password authentication via Supabase
- Job tracking with status indicators, retry, and open-in-folder
- 15-second rate limit cooldown between downloads

### Organize Files
- Description text explains what the feature does before you start
- Scans Downloads, Pictures, DCIM, Movies, and Music directories (up to 4 levels deep)
- Loading spinner shown while scanning
- Filter by category with chip toggles (Images, Documents, Videos, Audio)
- Batch select/deselect with per-item checkboxes
- Moves files into structured folders:
  - Images → `Pictures/Photos/{App}/{EXT}`
  - Documents → `Documents/{EXT}`
  - Videos → `Movies/Videos/{App}/{EXT}`
  - Audio → `Music/{EXT}`
- Source app detection (WhatsApp, Instagram, Telegram, TikTok, Discord, etc.)
- Progress bar with cancellation support
- Results summary after organizing

### File Detail & Properties
- Preview thumbnails for images and videos
- Action buttons: Rename, Delete, Move, Copy, Open With, Share, Properties
- Full metadata view: name, path, size, date, read/write permissions
- Copy path to clipboard

### Image Viewer
- Full-screen zoomable viewer with pinch-to-zoom and double-tap zoom (PhotoView)

### ZIP Viewer
- Browse contents of `.zip` files with file count, folder count, and total size
- **Extract All**: Extract every file to `Downloads/Extracted/{zipname}/`
- **Extract Single File**: Tap any file to extract or share it individually
- Open extracted files/folders directly from the completion dialog
- Extracted files are automatically scanned into MediaStore (appear in Recent Files)

## Themes

| Theme | Light Mode | Dark Mode |
|---|---|---|
| **Azazel Gold** (default) | Gold accent, warm ivory background | Crimson red, deep plum background |
| **Dark Mode** | Toggle in Settings | Near-AMOLED deep purple |

## Requirements

- Android 7.0 (API 24) or higher
- `MANAGE_EXTERNAL_STORAGE` permission for full file access on Android 11+

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Network access for downloads and authentication |
| `READ_EXTERNAL_STORAGE` | File access on Android 10 and below |
| `MANAGE_EXTERNAL_STORAGE` | Full file access on Android 11+ |

## Tech Stack

- **Language:** Kotlin
- **UI:** Material3, Jetpack Navigation, RecyclerView, ViewBinding-ready layouts
- **Networking:** Retrofit 2.9 + Gson
- **Image Loading:** Glide 4.16
- **Image Viewer:** PhotoView 2.3
- **Auth Backend:** Supabase
- **Download Backend:** Custom Render-hosted service

## Intents

| Intent Type | Usage | Location |
|---|---|---|
| **Explicit** | Launch `AboutActivity` from Settings | `SettingsFragment.kt` → `Intent(requireContext(), AboutActivity::class.java)` |
| **Implicit** | Open files with external apps | `FileDetailFragment`, `BrowserFragment`, `ZipContentsFragment` — `Intent.ACTION_VIEW` |
| **Implicit** | Share files via chooser | `FileDetailFragment`, `BrowserFragment`, `ZipContentsFragment` — `Intent.ACTION_SEND` |

## Building

```bash
# Compile debug Kotlin sources
.\gradlew.bat compileDebugKotlin

# Build debug APK
.\gradlew.bat assembleDebug
```

## Project Structure

```
app/src/main/java/com/azazel/explorer/
├── MainActivity.kt              # Single-activity host
├── AboutActivity.kt             # About screen (explicit intent target)
├── data/                        # Repositories, session, theme prefs
├── model/                       # Enums (SortOrder, FileFilter)
├── network/                     # Retrofit API services, auth clients
├── ui/
│   ├── browser/                 # File browser + adapter
│   ├── dashboard/               # Home dashboard + quick access grid
│   ├── detail/                  # File detail screen
│   ├── downloads/               # Media downloader + job list
│   ├── organize/                # File organizer (preview, manager, adapter)
│   ├── preview/                 # Full-screen image viewer
│   ├── properties/              # File metadata screen
│   ├── settings/                # App settings
│   ├── views/                   # Custom views (StorageDonutView)
│   └── zip/                     # ZIP contents viewer
└── util/                        # FormatUtils
```

## Screens

| Screen | Description |
|---|---|
| Dashboard | Storage overview, quick access categories, recent files |
| Browser | Full filesystem navigation with sort/filter/search, dashboard view with category title, subtitle, and back arrow |
| Downloader | Auth-gated media download with job tracking |
| File Detail | File preview with action buttons |
| Properties | Full file metadata |
| Image Viewer | Full-screen zoomable image |
| ZIP Viewer | Browse, extract, and share archive contents |
| Organize | Preview and batch-move files into structured folders |
| Settings | Dark mode toggle, app info, link to About screen |
| About | App icon, version, description, features list, tech stack (launched via explicit intent) |

## License

Personal project - not licensed for distribution.
