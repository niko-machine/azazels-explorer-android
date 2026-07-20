# Frontend TODO & Implementation Guide

For whoever's picking up the remaining frontend work. Each item below includes what to
build and how, not just a checkbox — follow them roughly in order, since a couple build on
each other (permissions before real file reading, real file reading before folder nav).

Assumes the skeleton already exists: nav graph, all three fragments, both Intents, and
Downloads wired to the backend are done and working. If any of that isn't in place yet,
stop and get it working first — everything here builds on top of it.

---

## 1. Runtime storage permissions

Do this first — nothing else here works without it.

`AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

`BrowserFragment.kt` — request on first load, handle denial without crashing:

```kotlin
private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) loadFiles() else showPermissionDeniedState()
}

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    checkPermissionAndLoad()
}

private fun checkPermissionAndLoad() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (Environment.isExternalStorageManager()) {
            loadFiles()
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        }
    } else {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ->
                loadFiles()
            else -> permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

private fun showPermissionDeniedState() {
    // swap RecyclerView for a simple "grant permission to browse files" message + button
}
```

`MANAGE_EXTERNAL_STORAGE` requires sending the user to a system settings screen (can't be
granted via a normal permission dialog) — that's why the API 30+ branch above uses an
`Intent` instead of `permissionLauncher`. Re-check `onResume()` too, since the user grants
it in Settings and comes back to the app rather than getting a callback:

```kotlin
override fun onResume() {
    super.onResume()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        loadFiles()
    }
}
```

---

## 2. Real FileRepository (replacing the hardcoded sample list)

```kotlin
// data/FileRepository.kt
class FileRepository {
    fun listFiles(directory: File): List<File> {
        return directory.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }
}
```

Sorting directories first, then alphabetically, is the standard file-explorer convention —
worth keeping as the default even after you add the sort/filter UI in Section 4.

Update `BrowserFragment.kt` to use it instead of `FakeFile`:

```kotlin
private val repository = FileRepository()
private var currentDir: File = Environment.getExternalStorageDirectory()

private fun loadFiles() {
    val files = repository.listFiles(currentDir)
    rv.adapter = FileAdapter(files) { file -> onFileClicked(file) }
}
```

Update `FileAdapter` to bind against `File` instead of `FakeFile` — same structure as
before, just swap the type and use `file.name` / `file.absolutePath`.

---

## 3. Folder navigation

Tapping a file should go to File Detail (existing behavior). Tapping a folder should
browse into it instead — same fragment, new directory.

```kotlin
private fun onFileClicked(file: File) {
    if (file.isDirectory) {
        currentDir = file
        loadFiles()
        // optional: push directory name onto a back-stack list so a back button
        // can pop up a level instead of exiting the fragment
    } else {
        val action = BrowserFragmentDirections
            .actionBrowserToDetail(filePath = file.absolutePath, fileName = file.name)
        findNavController().navigate(action)
    }
}
```

Reusing the same fragment instance and just reloading its RecyclerView is simpler than
adding a new nav graph destination per directory level — no argument passing needed for
this part, just update `currentDir` and refresh.

For a proper "up one level" back button, track a simple stack:

```kotlin
private val dirStack = ArrayDeque<File>()

private fun navigateInto(dir: File) {
    dirStack.addLast(currentDir)
    currentDir = dir
    loadFiles()
}

fun navigateUp(): Boolean {
    if (dirStack.isEmpty()) return false
    currentDir = dirStack.removeLast()
    loadFiles()
    return true
}
```

Hook `navigateUp()` into the system back button via
`requireActivity().onBackPressedDispatcher.addCallback` inside the fragment, only
intercepting when `dirStack` isn't empty (otherwise let the normal back behavior — exiting
to the previous fragment — happen).

---

## 4. Sorting and filtering

Add a simple overflow menu or spinner to `fragment_browser.xml` (a `Spinner` or three-dot
`ImageButton` triggering a `PopupMenu` both work fine):

```kotlin
enum class SortOrder { NAME, DATE, SIZE, TYPE }

private var currentSort = SortOrder.NAME

private fun applySort(files: List<File>): List<File> = when (currentSort) {
    SortOrder.NAME -> files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    SortOrder.DATE -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified() }))
    SortOrder.SIZE -> files.sortedWith(compareBy({ !it.isDirectory }, { -it.length() }))
    SortOrder.TYPE -> files.sortedWith(compareBy({ !it.isDirectory }, { it.extension.lowercase() }))
}
```

Call `applySort(repository.listFiles(currentDir))` instead of the raw list when populating
the adapter. Filtering (e.g. a search box) can reuse the same pattern — filter the list
before sorting, feed the result to the adapter.

---

## 5. Empty state and loading indicator

Add to `fragment_browser.xml`, as siblings of the `RecyclerView` inside a
`ConstraintLayout` wrapper (or `FrameLayout`, either works since they're just stacked and
toggled):

```xml
<ProgressBar
    android:id="@+id/progress_bar"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:visibility="gone" />

<TextView
    android:id="@+id/tv_empty"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="This folder is empty"
    android:visibility="gone" />
```

```kotlin
private fun loadFiles() {
    progressBar.visibility = View.VISIBLE
    val files = applySort(repository.listFiles(currentDir))
    progressBar.visibility = View.GONE
    tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    rvFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
    rvFiles.adapter = FileAdapter(files) { file -> onFileClicked(file) }
}
```

`File.listFiles()` is synchronous and usually fast enough not to need a background thread
for typical folder sizes — the progress bar here is mostly for very large directories or
slower storage. If a folder feels sluggish to open, move the `listFiles()` call into
`viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { ... }` and switch back to the
main thread before touching the adapter.

---

## 6. Real file stats in FilePropertiesActivity

Replace the stub with actual `File` stats:

```kotlin
class FilePropertiesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_properties)

        val path = intent.getStringExtra("filePath") ?: return
        val file = File(path)

        val sizeText = formatFileSize(file.length())
        val dateText = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            .format(Date(file.lastModified()))

        findViewById<TextView>(R.id.tv_properties).text = """
            Name: ${file.name}
            Path: ${file.absolutePath}
            Size: $sizeText
            Modified: $dateText
            Readable: ${file.canRead()}
            Writable: ${file.canWrite()}
        """.trimIndent()
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.1f GB", mb / 1024.0)
    }
}
```

---

## 7. UI polish — item_file.xml, item_job.xml

Nothing structural needed here, mostly visual judgment calls — a few concrete suggestions:

- Add a small `ImageView` icon per row in `item_file.xml`, switched based on
  `file.extension` (e.g. a folder icon, image icon, video icon, generic-file icon as
  fallback) — a simple `when` block mapping common extensions to drawable resources is
  enough, no need for a full MIME-type library
- Add consistent padding/elevation values across `item_file.xml` and `item_job.xml` so the
  two RecyclerViews feel like the same app rather than two different ones
- Consider a subtitle line under the file name (size + modified date) using the same
  `formatFileSize` helper from Section 6

---

## 8. Job list progress + retry

In `JobAdapter`, add a retry button that re-fires the same request:

```kotlin
class JobAdapter(
    private val jobs: MutableList<DownloadJob>,
    private val onRetry: (DownloadJob) -> Unit
) : RecyclerView.Adapter<JobAdapter.ViewHolder>() {
    // ... existing ViewHolder / onCreateViewHolder ...

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val job = jobs[position]
        holder.status.text = job.status
        holder.retryButton.visibility = if (job.status == "failed") View.VISIBLE else View.GONE
        holder.retryButton.setOnClickListener { onRetry(job) }
    }
}
```

`onRetry` in `DownloadsFragment` just re-runs the same `createJob` + `pollJob` flow you
already have from the skeleton, reusing the original URL (keep it stored on the
`DownloadJob` model, or track it separately alongside the job list).

---

## 9. Re-test Share button against real files

No new code — once Section 2 is done, real files will exist at the paths passed into
`FileDetailFragment`, so the `shareFile()` existence check will pass naturally instead of
showing the "doesn't exist" toast it currently shows against fake sample data. Worth a
quick manual retest once Sections 1–2 are in, just to confirm the Intent flow still works
end to end with real files.

---

## Known limitation to be aware of

YouTube downloads are currently disabled on the backend — see `NOTES.md` for the full
story. Nothing on the frontend needs to change because of this; just don't be surprised if
a YouTube URL in the Downloads tab comes back `failed`. Test with any other supported site
instead.
