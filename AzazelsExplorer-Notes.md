# AzazelsExplorer - Developer Notes

Internal development notes, architecture decisions, and implementation details.

---

## Architecture

- **2 Activities**: `MainActivity` (single-activity host for Navigation Component), `AboutActivity` (standalone, launched via explicit intent)
- **9 Fragments** across 4 nav graphs
- **3 bottom tabs**: Dashboard, Browse, Downloader
- **4 nav graphs**: root (`nav_graph.xml`), dashboard, browser, downloader
- Bottom nav auto-hides on detail screens (file detail, properties, image viewer, zip viewer, settings)
- All navigation uses slide transitions (fade for nested browser actions)

## Package Structure

```
com.azazel.explorer/
├── MainActivity.kt
├── AboutActivity.kt              # Standalone Activity (explicit intent from Settings)
├── data/
│   ├── FileRepository.kt          # File listing (filesystem + MediaStore)
│   ├── SessionManager.kt          # Supabase auth token persistence
│   └── ThemePreferences.kt        # Dark mode pref + AppCompatDelegate
├── model/
│   └── FileEnums.kt               # SortOrder, FileFilter enums
├── network/
│   ├── ApiService.kt              # Backend: jobs CRUD
│   ├── AuthApiService.kt          # Supabase: signup, signin, refresh, logout
│   ├── RetrofitClient.kt          # Backend Retrofit instance + token interceptor
│   ├── AuthRetrofitClient.kt      # Supabase Retrofit instance
│   └── models/
│       └── DownloadJob.kt         # Request/response data classes
├── ui/
│   ├── browser/                   # BrowserFragment, FileAdapter
│   ├── dashboard/                 # DashboardFragment, QuickAccessAdapter
│   ├── detail/                    # FileDetailFragment
│   ├── downloads/                 # DownloadsFragment, JobAdapter
│   ├── organize/                  # OrganizePreviewFragment, OrganizeManager, OrganizeFileAdapter
│   ├── preview/                   # ImageViewerFragment (PhotoView + Glide)
│   ├── properties/                # FilePropertiesFragment
│   ├── settings/                  # SettingsFragment
│   ├── views/                     # StorageDonutView (custom Canvas chart)
│   └── zip/                       # ZipContentsFragment
└── util/
    └── FormatUtils.kt             # formatFileSize()
```

## Key Implementation Details

### FileAdapter (Browser)
- Uses `MutableList` with `submitList()` method and `currentItems` getter
- Calls `scheduleLayoutAnimation()` on list update via `onAttachedToRecyclerView`/`onDetachedFromRecyclerView`
- Layout animation: `layout_animation_fade.xml` (8% staggered fade-in)

### BrowserFragment Filtered/Dashboard View
- All Dashboard quick access categories (Photos, Videos, Audio, Documents, APKS, Archives + folder-based Documents, Organized Photos) now navigate with `fromDashboard = true`
- `fromDashboard` flag on nav graph arguments (both `nav_graph_dashboard.xml` action and `browserFragment` definition)
- Filtered view shows category name as toolbar title; folder-based view shows directory name
- Subtitle "Viewing from Dashboard" displayed below toolbar via `tv_filter_subtitle`
- Back arrow always shown (navigates up to Dashboard via `findNavController().navigateUp()`)
- Back button callback checks `args.fromDashboard` alongside `args.isFilteredView`
- Sort handlers use `loadFiles()` for folder-based categories (directory listing, not MediaStore)
- Sort handlers use `filterAndDisplay()` for filter-based categories (MediaStore query)
- "Show in Folder" context menu available in both filtered and dashboard views
- File detail `showLocationAction` enabled for both filtered and dashboard views
- `hasLoadedOnce` flag prevents `onResume` from double-firing `loadFiles()` on first creation

### DownloadsFragment
- Adapter created once via `isDownloadUiInitialized` flag to prevent recreation
- `isAdded` guards on all coroutine launches (`handleSessionExpired`, `loadHistory`, `startDownload`, `pollJob`, `waitForFileReady`, `startCooldownTimer`)
- All auth inputs (email, password, both buttons) disabled during login/logout
- `showAuthUi` clears adapter and resets init flag
- `showDownloadUi` resets button state on re-entry

### File Downloads
- `waitForFileReady`: Polls DownloadManager every 500ms, up to 30 seconds
- `downloadingToDevice` flag prevents redownload spam
- `openDownloadedFile` no longer silently re-downloads
- `expectedLocalFile` checks numeric suffixes `(1)`-`(10)` for deduplication

### Organize Files
- `OrganizeManager` is a singleton object (shared utility)
- Scans Downloads, Pictures, DCIM, Movies, Music directories (up to 4 levels deep)
- Categorizes by extension into structured destination folders
- Source app detection via `MediaStore.OWNER_PACKAGE_NAME` (API 29+) with parent folder fallback
- Preview screen with chip filters, checkbox selection, progress bar, results summary
- Moves files via `renameTo()` first, falls back to `copyTo()` + delete
- Loading spinner shown during scan (progress indicator for slow recursive scan)
- Description text below toolbar explains what the feature does
- Empty state uses FrameLayout overlay with `paddingBottom="80dp"` to center icon higher

### ZIP Viewer
- `ZipContentsFragment` reads `.zip` files using `java.util.zip.ZipFile`
- Shows file count, folder count, and total size in info bar below toolbar
- **Extract All**: Toolbar menu button, extracts all non-directory entries to `Downloads/Extracted/{zipname}/`
- **Extract Single File**: Tap any file entry → dialog with Extract and Share options
- **Share Single File**: Extracts to temp location, opens share chooser via `FileProvider`
- Open extracted files/folders directly from completion dialog
- Uses `AlertDialog` for progress (no deprecated `ProgressDialog`)
- MIME type detection for opening shared/extracted files
- `MediaScannerConnection.scanFile` called after extraction so files appear in MediaStore / Recent Files

### Theme System
- **Light mode**: Gold accent (`#C89A3B`), warm ivory bg (`#FBF5EF`) - no red, no purple
- **Dark mode**: Crimson red (`#B94049`), deep plum bg (`#170D25`)
- `accent_crimson` in colors.xml is gold in light mode, crimson in dark mode (used by Crimson theme overlay)
- Theme variants defined in `themes.xml`: Default (Gold), Crimson, Blue, Purple
- Only dark mode toggle exposed in Settings; accent color switching not yet implemented

### App Icon
- `Azazel2.png` used as `ic_launcher_foreground.png`
- Old foreground moved to `ic_launcher_monochrome.png`
- Adaptive icon XMLs updated for both regular and round variants
- Downloader uses `azazel_monochrome.png` with theme tint

### AboutActivity
- Standalone Activity (not part of Navigation Component graphs)
- Launched via **explicit intent** from `SettingsFragment`: `Intent(requireContext(), AboutActivity::class.java)`
- Has a `companion object` `start(context)` factory method for clean invocation
- Displays: app icon, version, description, features list, tech stack
- Uses `parentActivityName` in manifest for up navigation
- Registered in `AndroidManifest.xml` with `Theme.AzazelsExplorer` theme

### Intent Usage
- **Explicit**: `SettingsFragment` → `AboutActivity` via `Intent(context, AboutActivity::class.java)`
- **Implicit**: `ACTION_VIEW` to open files with external apps (FileDetailFragment, BrowserFragment, ZipContentsFragment, DownloadsFragment)
- **Implicit**: `ACTION_SEND` to share files via chooser (FileDetailFragment, BrowserFragment, ZipContentsFragment)
- All share intents use `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION` and `ClipData`

## Build

```bash
.\gradlew.bat compileDebugKotlin
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Material Components | 1.14.0 | Material3 widgets |
| Navigation (Fragment + UI + SafeArgs) | 2.9.8 | Fragment navigation |
| Retrofit + Gson | 2.9.0 | HTTP client + JSON |
| PhotoView | 2.3.0 | Zoomable image viewer |
| Glide | 4.16.0 | Image loading/thumbnailing |
| SwipeRefreshLayout | 1.2.0 | Pull-to-refresh |
| CardView | 1.0.0 | Dashboard cards |
| Lifecycle (ViewModel + Runtime) | 2.7.0 | Coroutine scopes |

## Known Issues / TODO

- [ ] Organize undo not implemented (button shows "not available yet")
- [ ] No landscape layouts (layout-land/ is empty placeholder)
- [ ] Compose UI Text dependency declared but unused
- [ ] No runtime accent color switching UI (variants defined in XML only)
- [ ] Minification disabled in release build
- [ ] `isAlreadyOrganized` checks path substrings which could match false positives

## Git History Context

This project has been actively developed with work across:
- Downloader fixes (polling, spam prevention, session handling)
- Light/dark theme color palette (gold light, crimson dark)
- App icon and monochrome layers
- UI/UX improvements (press feedback, touch targets, pull-to-refresh, empty states)
- Navigation transitions and list animations
- Tablet layout (sw600dp dashboard)
- Share functionality via FileProvider
- ProgressDialog replacement with AlertDialog
- Complete Organize Files redesign (preview-based workflow with loading state)
- ZIP viewer extract functionality (extract all, extract single, share single)
- Organize empty state positioning fix (FrameLayout overlay approach)
- Organize recursive scan (4 levels deep) with loading spinner
- Browser filtered/dashboard view UX: back arrow, category title, "Viewing from Dashboard" subtitle — now works for all quick access categories including folder-based (Documents, Organized Photos)
- Browser filtered view loading fix: `hasLoadedOnce` flag prevents `onResume` race condition
- ZIP extraction scans files into MediaStore so extracted files appear in Recent Files
- AboutActivity added as explicit intent target from Settings (assignment requirement)
