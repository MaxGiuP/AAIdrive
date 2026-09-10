package io.github.maxgiup.aaidrive.setup

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** The installer prompts stay in this visible activity; the retained model only verifies files. */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val AAIDRIVE = "me.hufman.androidautoidrive"
        private const val HEADUNIT = "com.andrerinas.headunitrevived"
        private const val PROJECTION = "io.github.maxgiup.aaidrive.projection"
        private const val ANDROID_AUTO = "com.google.android.projection.gearhead"
        private const val MAPS = "com.google.android.apps.maps"
    }

    private lateinit var model: SetupModel
    private lateinit var content: LinearLayout
    private var resumed = false
    private val installLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Package installers sometimes return RESULT_CANCELED even after installing. Query facts.
        model.installationReturned()
    }
    private val sourceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.permissionReturned(canInstallPackages())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[SetupModel::class.java]
        model.initialize(savedInstanceState)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(content) }
        val padding = dp(20)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.setPadding(padding, padding, padding, padding)
        setContentView(scroll)
        model.changes.observe(this) {
            render()
            driveFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        render()
        // A user may have resolved a conflict in App Settings while setup was hidden.
        // Refreshing an idle sequence never starts another installer prompt.
        if (model.apps.isNotEmpty() && !model.loading && model.flow.phase == InstallPhase.IDLE) model.refresh()
        driveFlow()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        model.save(outState)
        super.onSaveInstanceState(outState)
    }

    private fun canInstallPackages(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        packageManager.canRequestPackageInstalls()

    /** Every transition is recorded before launching, so recreation never repeats a prompt. */
    private fun driveFlow() {
        if (!resumed || model.loading) return
        when (model.flow.phase) {
            InstallPhase.NEED_PERMISSION -> {
                if (canInstallPackages()) {
                    model.permissionReturned(true)
                } else {
                    model.waitForPermission()
                    try {
                        sourceLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")))
                    } catch (e: RuntimeException) {
                        model.fail(getString(R.string.cannot_open))
                    }
                }
            }
            InstallPhase.READY_TO_INSTALL -> {
                val prepared = model.prepared ?: return
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", prepared.file)
                    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    intent.clipData = ClipData.newRawUri(prepared.app.name, uri)
                    model.waitForInstaller()
                    installLauncher.launch(intent)
                } catch (e: RuntimeException) {
                    model.fail(getString(R.string.cannot_open))
                }
            }
            else -> Unit
        }
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        text(getString(R.string.title), 27f, true)
        text(getString(R.string.intro))
        text(getString(R.string.offline))
        text(getString(R.string.components), 21f, true)
        if (model.loading || model.flow.phase == InstallPhase.PREPARING || model.flow.phase == InstallPhase.CHECKING_RESULT) {
            content.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)))
        }
        val status = when {
            model.loading -> getString(R.string.checking)
            model.flow.phase == InstallPhase.PREPARING -> getString(R.string.preparing, model.pendingApp()?.name ?: "APK")
            model.flow.phase == InstallPhase.CHECKING_RESULT -> getString(R.string.checking)
            model.flow.phase == InstallPhase.WAITING_INSTALL -> getString(R.string.waiting_install)
            model.flow.phase == InstallPhase.WAITING_PERMISSION -> getString(R.string.waiting_permission)
            else -> model.message
        }
        status?.let { text(it, bold = true).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE } }
        val idle = !model.loading && model.flow.phase == InstallPhase.IDLE
        val installable = model.apps.any { model.statuses[it.id]?.state in setOf(InstalledAppState.MISSING, InstalledAppState.UPDATE) }
        button(R.string.install_all, idle && installable) { model.beginAll() }
        model.apps.forEach { app ->
            text(app.name, 18f, true)
            text(getString(R.string.package_version, app.versionName), 13f)
            val installed = model.statuses[app.id]
            text(installed?.message ?: getString(R.string.status_pending))
            when (installed?.state) {
                InstalledAppState.MISSING -> button(R.string.install, idle) { model.begin(app.id, false) }
                InstalledAppState.UPDATE -> button(R.string.update, idle) { model.begin(app.id, false) }
                InstalledAppState.CONFLICT -> button(R.string.app_settings) { appSettings(app.packageName) }
                InstalledAppState.READY -> button(R.string.open) { openPackage(app.packageName) }
                else -> Unit
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            text(getString(R.string.source_help))
            button(R.string.allow_source, idle) { model.requestSourcePermission() }
        }
        button(R.string.refresh, !model.loading && model.flow.phase !in setOf(InstallPhase.PREPARING, InstallPhase.CHECKING_RESULT)) {
            if (model.flow.phase == InstallPhase.WAITING_INSTALL) model.installationReturned(confirmedReturn = false)
            else if (model.flow.phase == InstallPhase.WAITING_PERMISSION) model.permissionReturned(canInstallPackages())
            else model.refresh()
        }
        if (!model.loading && model.flow.phase != InstallPhase.IDLE) {
            button(R.string.cancel_setup) { model.cancel() }
        }

        text(getString(R.string.phone_setup), 21f, true)
        text(getString(R.string.step_aaidrive))
        button(R.string.open_aaidrive, model.ready(AAIDRIVE)) { openPackage(AAIDRIVE) }
        text(getString(R.string.step_google))
        googleApp(ANDROID_AUTO, getString(R.string.android_auto))
        googleApp(MAPS, getString(R.string.google_maps))
        button(R.string.android_auto_settings) {
            val settings = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(ANDROID_AUTO,
                "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"))
            if (!openIntent(settings, false)) openIntent(Intent(Settings.ACTION_SETTINGS))
        }
        button(R.string.phone_settings) { openIntent(Intent(Settings.ACTION_SETTINGS)) }
        text(getString(R.string.step_headunit))
        button(R.string.open_headunit, model.ready(HEADUNIT)) { openPackage(HEADUNIT) }
        text(getString(R.string.step_projection))
        button(R.string.open_projection, model.ready(PROJECTION)) { openPackage(PROJECTION) }
        text(getString(R.string.limitations))
        button(R.string.help) { showAsset(R.string.help, "SETUP.md") }
        button(R.string.licenses) { showAsset(R.string.licenses, "LICENSES.txt") }
    }

    private fun text(value: String, size: Float = 15f, bold: Boolean = false): TextView = TextView(this).also {
        it.text = value
        it.textSize = size
        if (bold) it.setTypeface(it.typeface, Typeface.BOLD)
        it.setPadding(0, dp(if (bold) 16 else 6), 0, dp(6))
        content.addView(it, LinearLayout.LayoutParams(-1, -2))
    }

    private fun button(label: Int, enabled: Boolean = true, action: () -> Unit) {
        button(getString(label), enabled, action)
    }

    private fun button(label: String, enabled: Boolean = true, action: () -> Unit) {
        content.addView(Button(this).apply {
            text = label
            isAllCaps = false
            isEnabled = enabled
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun googleApp(packageName: String, label: String) {
        val app = try { packageManager.getApplicationInfo(packageName, 0) } catch (_: PackageManager.NameNotFoundException) { null }
        val message = when {
            app == null -> getString(R.string.google_app_missing, label)
            !app.enabled -> getString(R.string.google_app_disabled, label)
            else -> getString(R.string.google_app_installed, label)
        }
        button(message) {
            if (app != null) appSettings(packageName)
            else if (!openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")), false)) {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }
    }

    private fun appSettings(packageName: String) {
        openIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null || !openIntent(intent, false)) appSettings(packageName)
    }

    private fun openIntent(intent: Intent, reportFailure: Boolean = true): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            if (reportFailure) Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show()
            false
        } catch (_: SecurityException) {
            if (reportFailure) Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun showAsset(title: Int, asset: String) {
        model.readHelp(asset) { contents ->
            if (!isFinishing && !isDestroyed) {
                val body = TextView(this).apply {
                    text = contents
                    textSize = 14f
                    setTextIsSelectable(true)
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                }
                AlertDialog.Builder(this).setTitle(title)
                    .setView(ScrollView(this).apply { addView(body) })
                    .setPositiveButton(R.string.close, null).show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal enum class InstallPhase { IDLE, NEED_PERMISSION, WAITING_PERMISSION, PREPARING, READY_TO_INSTALL, WAITING_INSTALL, CHECKING_RESULT }

/** Facts from PackageManager determine success; an Activity result code never does. */
internal class InstallFlow {
    var phase = InstallPhase.IDLE
    var pendingId: String? = null
    var installAll = false

    fun begin(id: String, all: Boolean) {
        pendingId = id
        installAll = all
        phase = InstallPhase.NEED_PERMISSION
    }

    fun finish(status: InstalledAppState?): Boolean {
        val continueAll = status == InstalledAppState.READY && installAll
        pendingId = null
        phase = InstallPhase.IDLE
        installAll = continueAll
        return continueAll
    }

    fun cancel() {
        pendingId = null
        installAll = false
        phase = InstallPhase.IDLE
    }

    fun save(bundle: Bundle) {
        bundle.putString("install_phase", phase.name)
        bundle.putString("pending_app", pendingId)
        bundle.putBoolean("install_all", installAll)
    }

    fun restore(bundle: Bundle?) {
        phase = runCatching { InstallPhase.valueOf(bundle?.getString("install_phase") ?: "IDLE") }.getOrDefault(InstallPhase.IDLE)
        pendingId = bundle?.getString("pending_app")
        installAll = bundle?.getBoolean("install_all") ?: false
    }
}

/** No activity or installer launch is retained here; IO survives ordinary activity rotations. */
class SetupModel(application: Application) : AndroidViewModel(application) {
    val changes = MutableLiveData(0)
    internal val flow = InstallFlow()
    var apps = emptyList<BundledApp>()
        private set
    var statuses = emptyMap<String, InstalledAppStatus>()
        private set
    var prepared: PreparedApk? = null
        private set
    var loading = true
        private set
    var message: String? = null
        private set
    private val repository = BundleInstallerRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var initialized = false
    @Volatile private var generation = 0
    @Volatile private var cleared = false
    private var job: Future<*>? = null
    private var returnedBeforeLoad = false
    private var installerHasReturned = false

    fun initialize(savedState: Bundle?) {
        if (initialized) return
        initialized = true
        flow.restore(savedState)
        installerHasReturned = savedState?.getBoolean("installer_returned") ?: false
        val expected = generation
        job = executor.submit {
            try {
                val catalog = repository.loadCatalog()
                val inspected = catalog.associate { it.id to repository.inspect(it) }
                deliver(expected) {
                    apps = catalog
                    statuses = inspected
                    loading = false
                    when {
                        returnedBeforeLoad || flow.phase == InstallPhase.CHECKING_RESULT -> finishInstall()
                        flow.phase == InstallPhase.PREPARING || flow.phase == InstallPhase.READY_TO_INSTALL -> preparePending()
                        else -> {
                            if (flow.phase == InstallPhase.IDLE && apps.all { statuses[it.id]?.state == InstalledAppState.READY }) {
                                message = getApplication<Application>().getString(R.string.all_ready)
                            }
                            notifyChange()
                        }
                    }
                }
            } catch (e: Exception) {
                deliver(expected) { loading = false; fail(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun save(bundle: Bundle) {
        flow.save(bundle)
        bundle.putBoolean("installer_returned", installerHasReturned)
    }

    fun pendingApp(): BundledApp? = apps.firstOrNull { it.id == flow.pendingId }
    fun ready(packageName: String): Boolean = apps.any { it.packageName == packageName && statuses[it.id]?.state == InstalledAppState.READY }

    fun beginAll() {
        val next = apps.firstOrNull { statuses[it.id]?.state in setOf(InstalledAppState.MISSING, InstalledAppState.UPDATE) }
        if (next == null) {
            flow.cancel()
            message = getApplication<Application>().getString(if (apps.isNotEmpty() && apps.all { statuses[it.id]?.state == InstalledAppState.READY }) R.string.all_ready else R.string.needs_attention)
            notifyChange()
        } else begin(next.id, true)
    }

    fun begin(id: String, all: Boolean) {
        if (loading || flow.phase != InstallPhase.IDLE) return
        if (statuses[id]?.state !in setOf(InstalledAppState.MISSING, InstalledAppState.UPDATE)) return
        message = null
        flow.begin(id, all)
        notifyChange()
    }

    fun requestSourcePermission() {
        if (flow.phase != InstallPhase.IDLE) return
        message = null
        flow.phase = InstallPhase.NEED_PERMISSION
        notifyChange()
    }

    fun waitForPermission() {
        flow.phase = InstallPhase.WAITING_PERMISSION
        notifyChange()
    }

    fun permissionReturned(granted: Boolean) {
        if (flow.phase !in setOf(InstallPhase.NEED_PERMISSION, InstallPhase.WAITING_PERMISSION)) return
        if (!granted) {
            flow.cancel()
            message = getApplication<Application>().getString(R.string.permission_denied)
            notifyChange()
        } else if (flow.pendingId == null) {
            flow.cancel()
            notifyChange()
        } else {
            flow.phase = InstallPhase.PREPARING
            if (!loading) preparePending()
        }
    }

    private fun preparePending() {
        val app = pendingApp() ?: return fail("The requested component is missing from this bundle")
        val expected = generation
        flow.phase = InstallPhase.PREPARING
        notifyChange()
        job = executor.submit {
            try {
                val verified = repository.prepare(app) { cleared || generation != expected || Thread.currentThread().isInterrupted }
                deliver(expected) {
                    prepared = verified
                    flow.phase = InstallPhase.READY_TO_INSTALL
                    notifyChange()
                }
            } catch (_: CancellationException) {
                // Cancellation is explicit and never restarts the installation sequence.
            } catch (e: Exception) {
                deliver(expected) { fail(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun waitForInstaller() {
        installerHasReturned = false
        flow.phase = InstallPhase.WAITING_INSTALL
        notifyChange()
    }

    fun installationReturned(confirmedReturn: Boolean = true) {
        if (flow.phase !in setOf(InstallPhase.WAITING_INSTALL, InstallPhase.CHECKING_RESULT)) return
        installerHasReturned = installerHasReturned || confirmedReturn
        flow.phase = InstallPhase.CHECKING_RESULT
        if (loading) returnedBeforeLoad = true else refresh(afterInstall = true)
    }

    fun refresh(afterInstall: Boolean = false) {
        if (loading) return
        loading = true
        if (!afterInstall) message = null
        val expected = generation
        notifyChange()
        job = executor.submit {
            try {
                val inspected = apps.associate { it.id to repository.inspect(it) }
                deliver(expected) {
                    statuses = inspected
                    loading = false
                    if (afterInstall) finishInstall() else notifyChange()
                }
            } catch (e: Exception) {
                deliver(expected) { loading = false; fail(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun finishInstall() {
        returnedBeforeLoad = false
        val app = pendingApp()
        val status = app?.let { statuses[it.id]?.state }
        val continueAll = flow.finish(status)
        val completed = app ?: prepared?.app
        prepared = null
        // Manual checks can happen in multi-window while the installer still reads the URI.
        if (completed != null && (installerHasReturned || status == InstalledAppState.READY)) {
            executor.execute { runCatching { repository.discard(completed) } }
        }
        installerHasReturned = false
        if (continueAll) beginAll()
        else {
            message = getApplication<Application>().getString(if (status == InstalledAppState.READY) R.string.installed else R.string.install_incomplete, app?.name ?: "App")
            notifyChange()
        }
    }

    fun cancel() {
        generation++
        job?.cancel(true)
        loading = false
        prepared = null
        flow.cancel()
        message = getApplication<Application>().getString(R.string.cancelled)
        if (apps.isEmpty()) {
            initialized = false
            loading = true
            initialize(null)
        } else notifyChange()
    }

    fun fail(reason: String) {
        flow.cancel()
        prepared = null
        message = getApplication<Application>().getString(R.string.error, reason)
        notifyChange()
    }

    fun readHelp(asset: String, onResult: (String) -> Unit) {
        executor.execute {
            val text = runCatching { getApplication<Application>().assets.open(asset).bufferedReader().use { it.readText() } }
                .getOrElse { getApplication<Application>().getString(R.string.error, it.message ?: "Help is unavailable") }
            if (!cleared) handler.post { if (!cleared) onResult(text) }
        }
    }

    private fun notifyChange() {
        changes.value = (changes.value ?: 0) + 1
    }

    private fun deliver(expected: Int, action: () -> Unit) {
        handler.post { if (!cleared && generation == expected) action() }
    }

    override fun onCleared() {
        cleared = true
        generation++
        executor.shutdownNow()
    }
}
