package ru.ytkab0bp.slicebeam
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import cz.msebera.android.httpclient.Header
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.cloud.CloudController
import ru.ytkab0bp.slicebeam.components.CloudManageDialog
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileConfigSnapshot
import ru.ytkab0bp.slicebeam.config.QidiXSmart5ProfileBundle
import ru.ytkab0bp.slicebeam.events.BeamServerDataUpdatedEvent
import ru.ytkab0bp.slicebeam.events.CloudFeaturesUpdatedEvent
import ru.ytkab0bp.slicebeam.events.CloudLoginStateUpdatedEvent
import ru.ytkab0bp.slicebeam.events.CloudSyncFinishedEvent
import ru.ytkab0bp.slicebeam.slic3r.Native
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.ThemeAwareComposeView
import ru.ytkab0bp.slicebeam.ui.setup.SetupAboutScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupActivityRoot
import ru.ytkab0bp.slicebeam.ui.setup.SetupComposePage
import ru.ytkab0bp.slicebeam.ui.setup.SetupPageNavigationRequest
import ru.ytkab0bp.slicebeam.ui.setup.SetupBoostyScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupCloudBenefits
import ru.ytkab0bp.slicebeam.ui.setup.SetupCloudLevel
import ru.ytkab0bp.slicebeam.ui.setup.SetupCloudProfileScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupFinishScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupIntroScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupMessageDialog
import ru.ytkab0bp.slicebeam.ui.setup.SetupPresetSourceScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupPrinterSelectionScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupFilamentSelectionScreen
import ru.ytkab0bp.slicebeam.ui.setup.SetupProfileOption
import ru.ytkab0bp.slicebeam.ui.setup.SetupProfileSection
import ru.ytkab0bp.slicebeam.ui.setup.SetupProfileSelectionPolicy
import ru.ytkab0bp.slicebeam.utils.IOUtils
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.ViewUtils
class SetupActivity:AppCompatActivity() {
private var setupPages by mutableStateOf<List<SetupComposePage>>(emptyList())
private var cloudManageDialog by mutableStateOf<CloudManageDialog?>(null)
private var setupMessageDialog by mutableStateOf<SetupMessageState?>(null)
private var visibleSetupPageCount by mutableIntStateOf(1)
private var setupPagerInputEnabled by mutableStateOf(true)
private var setupPageNavigationRequest by mutableStateOf<SetupPageNavigationRequest?>(null)
private var setupPageNavigationId = 0
private var currentSetupPage by mutableIntStateOf(0)
private var setupPageInsets by mutableStateOf(PaddingValues())
private var startupBackgroundAlpha by mutableFloatStateOf(1f)
private var setupTitle by mutableStateOf("")
private var titleTranslationYPx by mutableFloatStateOf(0f)
private var titleScale by mutableFloatStateOf(1f)
private var setupTitleColorInt by mutableIntStateOf(Color.WHITE)
private var titleHeightPx by mutableIntStateOf(0)
private var titleTopInsetPx by mutableIntStateOf(0)
private var titleY:Int = 0
private var backgroundProgress:Float = 0.toFloat()
private var boostyProgress:Float = 0.toFloat()
private var client = AsyncHttpClient()
private var repos = ArrayList<ProfilesRepo>()
private var profilesItem: ProfilesItem? = null
private var filamentsItem: FilamentsItem? = null
private lateinit var cloudItem: CloudProfileItem
private var limitProfileFragmentCount = true
private var isLoading:Boolean = false
private var pendingRestoreSetupPage: Int? = null
private lateinit var profileImportLauncher: ActivityResultLauncher<Intent>
private var profilesMap = HashMap<ProfilesRepo, List<Slic3rConfigWrapper>>()
private var isProfilesLoaded:Boolean = false
private var about:Boolean = false
private var boostyOnly:Boolean = false
private var cloudProfile:Boolean = false
private var cloudImport:Boolean = false
private var addPrinter:Boolean = false
private var enabledPrinters = ArrayList<ConfigObject>()
private var enabledFilaments = ArrayList<ConfigObject>()
init{
client.setUserAgent(String.format(Locale.ROOT, "SliceBeam/%s-%d", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
client.setEnableRedirects(true)
client.setLoggingEnabled(false)
}
override fun onCreate(@Nullable savedInstanceState:Bundle?) {
super.onCreate(savedInstanceState)
profileImportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val uri = result.data?.data
    if (SetupProfilePickerResult.shouldImport(result.resultCode, Activity.RESULT_OK, uri != null)) {
        uri?.let(::loadProfileFromPicker)
    }
}
onBackPressedDispatcher.addCallback(this, object:OnBackPressedCallback(true) {
override fun handleOnBackPressed() {
handleSetupBack()
}
})
androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
SliceBeam.EVENT_BUS.registerListener(this)
about = getIntent().getBooleanExtra(EXTRA_ABOUT, false)
boostyOnly = getIntent().getBooleanExtra(EXTRA_BOOSTY_ONLY, false)
cloudProfile = getIntent().getBooleanExtra(EXTRA_CLOUD_PROFILE, false)
cloudImport = getIntent().getBooleanExtra(EXTRA_CLOUD_IMPORT_FROM_SETUP, false)
addPrinter = getIntent().getBooleanExtra(EXTRA_ADD_PRINTER, false)
val restoredSetupPage = savedInstanceState?.getInt(STATE_CURRENT_SETUP_PAGE, 0) ?: 0
limitProfileFragmentCount = savedInstanceState?.getBoolean(STATE_LIMIT_SETUP_PAGES, true) ?: true
if (restoredSetupPage > PROFILES_INDEX && !about && !boostyOnly && !cloudProfile) {
limitProfileFragmentCount = false
currentSetupPage = PROFILES_INDEX
pendingRestoreSetupPage = restoredSetupPage
} else {
currentSetupPage = restoredSetupPage
}
setupTitle = if (cloudProfile) getString(R.string.SettingsCloudManageTitle) else ""
if (SliceBeam.CONFIG != null && !about && !boostyOnly && !cloudProfile && !addPrinter)
{
startActivity(Intent(this, MainActivity::class.java))
finish()
return
}
if (!about && !boostyOnly && !cloudProfile && !addPrinter)
{
setupMessageDialog = SetupMessageState(
    getString(R.string.IntroEarlyAccess),
    getString(R.string.IntroEarlyAccessMessage),
)
}
if (boostyOnly || cloudProfile)
{
backgroundProgress = 1f
}
setItems()
updateStartupBackgroundVisibility()
val rootView = composeSetupRoot(this)
ViewCompat.setOnApplyWindowInsetsListener(rootView, { _, insets ->
    val systemBars = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    )
    val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
    setupPageInsets = PaddingValues(
        start = (systemBars.left / density).dp,
        top = (systemBars.top / density).dp,
        end = (systemBars.right / density).dp,
        bottom = (systemBars.bottom / density).dp,
    )
    titleTopInsetPx = systemBars.top
    insets
})
setContentView(rootView)
// Pre-populate OrcaSlicer as the only profile source
repos.add(
    ProfilesRepo(
        name = "OrcaSlicer",
        description = "Bundled OrcaSlicer printer profiles",
        checked = true,
        localAssets = true,
    ),
)
}
override fun onSaveInstanceState(outState: Bundle) {
outState.putInt(STATE_CURRENT_SETUP_PAGE, setupPageNavigationRequest?.page ?: currentSetupPage)
outState.putBoolean(STATE_LIMIT_SETUP_PAGES, limitProfileFragmentCount)
super.onSaveInstanceState(outState)
}
private data class SetupMessageState(val title: String, val message: String)

private fun showSetupMessage(title: CharSequence, message: CharSequence) {
    if (isFinishing || isDestroyed) return
    setupMessageDialog = SetupMessageState(title.toString(), message.toString())
}

private fun openProfilePicker() {
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
intent.addCategory(Intent.CATEGORY_OPENABLE)
intent.setType("*/*")
intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf<String>("application/octet-stream", "text/plain", "application/x-zip-compressed", "application/zip"))
try
{
profileImportLauncher.launch(intent)
}
catch (e:Exception) {
showSetupMessage(getString(R.string.MenuFileImportProfilesFailed), e.toString())
}
}
private fun loadProfileFromPicker(uri:Uri) {
if (uri == null) return
val fileName = IOUtils.getDisplayName(uri)
if (fileName == null)
{
showSetupMessage(
    getString(R.string.MenuFileImportProfilesFailed),
    getString(R.string.MenuFileOpenFileFailedNullName),
)
return
}
val lowerName = fileName.lowercase(Locale.ROOT)
Toast.makeText(this, R.string.PrinterProfileLoading, Toast.LENGTH_SHORT).show()
if (isOrcaBundleFile(lowerName))
{
loadOrcaProfileBundle(uri)
return
}
if (!lowerName.endsWith(".ini"))
{
showSetupMessage(
    getString(R.string.MenuFileImportProfilesFailed),
    getString(R.string.SetupProfileFileHint),
)
return
}
IOUtils.IO_POOL.submit({ try
{
getContentResolver().openInputStream(uri).use({ `in`->
if (`in` == null)
{
throw FileNotFoundException(java.lang.String.valueOf(uri))
}
saveImportedProfile(Slic3rConfigWrapper(`in`)) })
}
catch (e:Exception) {
showProfileImportError(e)
}
})
}
private fun isOrcaBundleFile(fileName:String):Boolean {
return fileName.endsWith(".orca_printer") || fileName.endsWith(".orca_filament") || fileName.endsWith(".orca_process") || fileName.endsWith(".zip")
}
private fun loadOrcaProfileBundle(uri:Uri) {
IOUtils.IO_POOL.submit({ var copiedArchive:File? = null
val extractDir = File(SliceBeam.getModelCacheDir(), "setup_orca_conv_" + UUID.randomUUID())
try
{
copiedArchive = File.createTempFile("setup_orca_conv_", ".zip", SliceBeam.getModelCacheDir())
getContentResolver().openInputStream(uri).use({ `in`-> FileOutputStream(copiedArchive!!).use({ fos->
if (`in` == null)
{
throw FileNotFoundException(java.lang.String.valueOf(uri))
}
val buffer = ByteArray(10240)
while (true)
{
val c = `in`.read(buffer)
if (c == -1) break
fos.write(buffer, 0, c)
}
}) })
if (!extractDir.mkdirs() && !extractDir.isDirectory())
{
throw IOException("Failed to create temporary extraction directory")
}
val manifest = Native.orca_bundle_read(copiedArchive!!.getAbsolutePath(), extractDir.getAbsolutePath())
val root = JSONObject(manifest)
val bundle = JSONObject(root.getString("bundle_structure_json"))
if (!bundle.optString("bundle_type", "").endsWith("config bundle"))
{
throw IOException(getString(R.string.OrcaConversionNotAConfigBundle))
}
saveImportedProfile(convertOrcaBundleToConfig(root))
}
catch (e:Exception) {
showProfileImportError(e)
}
finally
{
copiedArchive?.delete()
deleteRecursively(extractDir)
} })
}
@Throws(Exception::class)
private fun convertOrcaBundleToConfig(root:JSONObject):Slic3rConfigWrapper {
val bundle = JSONObject(root.getString("bundle_structure_json"))
val files = HashMap<String, String>()
val fileArray = root.getJSONArray("files")
for (i in 0 until fileArray.length())
{
val file = fileArray.getJSONObject(i)
files.put(file.getString("path"), file.getString("content"))
}
val w = Slic3rConfigWrapper()
if (bundle.has("process_config"))
{
val arr = bundle.getJSONArray("process_config")
var names = ArrayList<String>()
val stripped = ArrayList<String>()
for (i in 0 until arr.length())
{
val name = arr.getString(i)
names.add(name)
stripped.add(stripOrcaProfileName(name))
}
addInstalledProfileTitles(stripped, if (SliceBeam.CONFIG != null) SliceBeam.CONFIG!!.printConfigs else null)
for (name in names)
{
val content = files.get(name)
if (content == null) throw FileNotFoundException(name)
w.printConfigs.add(IOUtils.configJsonToIni(JSONObject(content), "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, stripped))
}
resolveBundleInherits(w.printConfigs, BundleProfileFinder({ name -> w.findPrint(name) }), if (SliceBeam.CONFIG != null) { name-> SliceBeam.CONFIG!!.findPrint(name) } else null)
}
if (bundle.has("filament_config"))
{
val arr = bundle.getJSONArray("filament_config")
val names = ArrayList<String>()
val stripped = ArrayList<String>()
for (i in 0 until arr.length())
{
val name = arr.getString(i)
names.add(name)
stripped.add(stripOrcaProfileName(name))
}
addInstalledProfileTitles(stripped, if (SliceBeam.CONFIG != null) SliceBeam.CONFIG!!.filamentConfigs else null)
for (name in names)
{
val content = files.get(name)
if (content == null) throw FileNotFoundException(name)
w.filamentConfigs.add(IOUtils.configJsonToIni(JSONObject(content), "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, stripped))
}
resolveBundleInherits(w.filamentConfigs, BundleProfileFinder({ name -> w.findFilament(name) }), if (SliceBeam.CONFIG != null) { name-> SliceBeam.CONFIG!!.findFilament(name) } else null)
}
if (bundle.has("printer_config"))
{
val arr = bundle.getJSONArray("printer_config")
val names = ArrayList<String>()
val stripped = ArrayList<String>()
for (i in 0 until arr.length())
{
val name = arr.getString(i)
names.add(name)
stripped.add(stripOrcaProfileName(name))
}
addInstalledProfileTitles(stripped, if (SliceBeam.CONFIG != null) SliceBeam.CONFIG!!.printerConfigs else null)
for (name in names)
{
val content = files.get(name)
if (content == null) throw FileNotFoundException(name)
w.printerConfigs.add(IOUtils.configJsonToIni(JSONObject(content), "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, stripped))
}
resolveBundleInherits(w.printerConfigs, BundleProfileFinder({ name -> w.findPrinter(name) }), if (SliceBeam.CONFIG != null) { name-> SliceBeam.CONFIG!!.findPrinter(name) } else null)
}
return w
}
private fun stripOrcaProfileName(path:String):String {
val slash = path.indexOf('/')
val start = if (slash >= 0) slash + 1 else 0
var end = path.lastIndexOf('.')
if (end <= start) end = path.length
return path.substring(start, end)
}
private fun addInstalledProfileTitles(names:MutableList<String>, installed:List<ConfigObject>?) {
if (installed == null) return
for (obj in installed)
{
val title = obj?.title ?: continue
if (!names.contains(title)) names.add(title)
}
}
private fun interface BundleProfileFinder {
fun find(name:String):ConfigObject?
}
@Throws(IOUtils.MissingProfileException::class)
private fun resolveBundleInherits(configs:List<ConfigObject>, bundleFinder:BundleProfileFinder, fallbackFinder:BundleProfileFinder?) {
for (obj in configs)
{
var inherit = obj.get("inherits")
while (inherit != null)
{
var base = bundleFinder.find(inherit)
if (base == null && fallbackFinder != null)
{
base = fallbackFinder.find(inherit)
}
if (base == null)
{
obj.values.remove("inherits")
break
}
obj.values.remove("inherits")
val newMap = HashMap<String, String>()
newMap.putAll(base.values)
newMap.putAll(obj.values)
obj.values = newMap
inherit = obj.values.get("inherits")
}
}
}
@Throws(Exception::class)
private fun saveImportedProfile(imported:Slic3rConfigWrapper) {
if (imported.printConfigs.isEmpty() && imported.filamentConfigs.isEmpty() && imported.printerConfigs.isEmpty())
{
ViewUtils.postOnMainThread {
    showSetupMessage(
        getString(R.string.MenuFileImportProfilesFailed),
        getString(R.string.MenuFileImportProfilesFailedEmpty),
    )
}
return
}
ensureImportedDefaults(imported)
if (SliceBeam.CONFIG == null)
{
val previousConfig = SliceBeam.CONFIG
SliceBeam.CONFIG = imported
if (!SliceBeam.saveConfig()) {
SliceBeam.CONFIG = previousConfig
throw IOException("Could not save imported printer profiles")
}
}
else
{
val existing = SliceBeam.requireConfig()
val snapshot = ProfileConfigSnapshot(existing)
mergeImportedProfiles(imported)
if (!SliceBeam.saveConfig()) {
snapshot.restore()
throw IOException("Could not save imported printer profiles")
}
}
SliceBeam.clearLiveDiffs()
ViewUtils.postOnMainThread({ Toast.makeText(this, R.string.PrinterProfileLoaded, Toast.LENGTH_SHORT).show()
startActivity(Intent(this@SetupActivity, MainActivity::class.java))
finish() })
}
private fun showProfileImportError(e:Exception) {
Log.e(TAG, "Failed to import setup profile", e)
ViewUtils.postOnMainThread {
    showSetupMessage(getString(R.string.MenuFileImportProfilesFailed), e.toString())
}
}
private fun deleteRecursively(file:File) {
if (file == null || !file.exists()) return
if (file.isDirectory())
{
val children = file.listFiles()
if (children != null)
{
for (child in children) deleteRecursively(child)
}
}
 file.delete()
}
private fun ensureImportedDefaults(cfg:Slic3rConfigWrapper) {
if (cfg.presets == null)
{
cfg.presets = ConfigObject()
}
if (cfg.presets!!.get("printer") == null && !cfg.printerConfigs.isEmpty())
{
cfg.presets!!.put("printer", cfg.printerConfigs.get(0).title)
}
if (cfg.presets!!.get("print") == null && !cfg.printConfigs.isEmpty())
{
cfg.presets!!.put("print", cfg.printConfigs.get(0).title)
}
if (cfg.presets!!.get("filament") == null && !cfg.filamentConfigs.isEmpty())
{
var pick = cfg.filamentConfigs.get(0).title
for (fil in cfg.filamentConfigs)
{
val t = fil.title
if (t != null && t.contains("PLA"))
{
pick = t
break
}
}
cfg.presets!!.put("filament", pick)
}
}
private fun mergeImportedProfiles(imported:Slic3rConfigWrapper) {
val existing = SliceBeam.CONFIG
for (p in imported.printerConfigs)
{
if (existing!!.findPrinter(p.title) == null) existing!!.printerConfigs.add(p)
}
for (f in imported.filamentConfigs)
{
if (existing!!.findFilament(f.title) == null) existing!!.filamentConfigs.add(f)
}
for (pr in imported.printConfigs)
{
var dup = false
for (ex in existing!!.printConfigs)
{
if ((ex.title.equals(pr.title) && java.util.Objects.equals(ex.get("compatible_printers"), pr.get("compatible_printers"))))
{
dup = true
break
}
}
if (!dup) existing!!.printConfigs.add(pr)
}
if (existing!!.presets == null)
{
existing!!.presets = ConfigObject()
}
val printer = if (imported.presets != null) imported.presets!!.get("printer") else null
val print = if (imported.presets != null) imported.presets!!.get("print") else null
val filament = if (imported.presets != null) imported.presets!!.get("filament") else null
if (printer != null && existing!!.findPrinter(printer) != null) existing!!.presets!!.put("printer", printer)
if (print != null && existing!!.findPrint(print) != null) existing!!.presets!!.put("print", print)
if (filament != null && existing!!.findFilament(filament) != null) existing!!.presets!!.put("filament", filament)
}
private fun loadLocalProfiles(repo:ProfilesRepo, loadedCount:AtomicInteger, totalNeeded:AtomicInteger, onLoadedAll:Runnable) {
IOUtils.IO_POOL.execute({ val vendorProfiles = ArrayList<Slic3rConfigWrapper>()
try
{
val files = getAssets().list(ORCA_ASSET_DIR)
if (files != null)
{
Arrays.sort(files)
for (file in files)
{
if (!file.endsWith(".ini"))
{
continue
}
try
{
getAssets().open(ORCA_ASSET_DIR + "/" + file).use({ `in`->
val cfg = Slic3rConfigWrapper(`in`)
vendorProfiles.add(cfg) })
}
catch (e:IOException) {
Log.w(TAG, "Failed to load local Orca profile asset " + file, e)
}
}
// The Kobra 3 V2 profile is distributed as an Orca/Prusa JSON
 // bundle. Merge it with its Kobra 3 parent so it behaves like a
 // normal built-in profile in the setup wizard.
 var anycubic:Slic3rConfigWrapper? = null
var kobra3Base:ConfigObject? = null
for (candidate in vendorProfiles)
{
val base = candidate.findPrinter("Anycubic Kobra 3 0.4 nozzle")
if (base != null)
{
anycubic = candidate
kobra3Base = base
break
}
}
val selectedAnycubic = anycubic
val selectedKobra3Base = kobra3Base
if (selectedAnycubic != null && selectedKobra3Base != null)
{
try
{
getAssets().open(
ORCA_ASSET_DIR + "/kobra3v2/printer/Anycubic Kobra 3 v2 0.4 nozzle - Stock.json").use({ `in`->
val json = JSONObject(IOUtils.readString(`in`))
val v2 = IOUtils.configJsonToIni(
json,
"machine",
Slic3rConfigWrapper.PRINTER_CONFIG_KEYS,
Collections.singletonList("Anycubic Kobra 3 v2 0.4 nozzle - Stock"))
val merged = HashMap<String, String>()
merged.putAll(selectedKobra3Base.values)
merged.putAll(v2.values)
merged.remove("inherits")
v2.values = merged
v2.put("printer_model", "Anycubic Kobra 3 V2")
selectedAnycubic.printerConfigs.add(v2)
val v2Model = ConfigObject("Anycubic Kobra 3 V2")
v2Model.put("name", "Anycubic Kobra 3 V2")
v2Model.put("variants", "0.4")
var kobra3Model:ConfigObject? = null
for (model in selectedAnycubic.printerModels)
{
if ("Anycubic Kobra 3".equals(model.title))
{
kobra3Model = model
break
}
}
val selectedKobra3Model = kobra3Model
if (selectedKobra3Model != null && selectedKobra3Model.get("default_materials") != null)
{
v2Model.put("default_materials", selectedKobra3Model.get("default_materials"))
}
selectedAnycubic.printerModels.add(v2Model) })
}
catch (e:Exception) {
Log.w(TAG, "Failed to load bundled Kobra 3 V2 profile", e)
}
}
try {
val bundledXSmart5 = QidiXSmart5ProfileBundle.load(assets)
val qidiVendor = vendorProfiles.firstOrNull { it.vendor?.get("name") == "Qidi" }
if (qidiVendor == null) {
    Log.w(TAG, "Qidi profile vendor is missing; built-in X-Smart 5 profiles are not available in setup")
} else {
    QidiXSmart5ProfileBundle.mergeInto(qidiVendor, bundledXSmart5)
}
} catch (e:Exception) {
Log.w(TAG, "Failed to load bundled Qidi X-Smart 5 profiles", e)
}
}
ViewUtils.postOnMainThread({ profilesMap.put(repo, vendorProfiles)
loadedCount.incrementAndGet()
if (loadedCount.get() == totalNeeded.get())
{
ViewUtils.postOnMainThread(onLoadedAll)
} })
}
catch (e:IOException) {
Log.e(TAG, "Failed to load local OrcaSlicer profile assets", e)
isLoading = false
ViewUtils.postOnMainThread({ Toast.makeText(SliceBeam.INSTANCE, R.string.IntroFailedToLoadRepos, Toast.LENGTH_SHORT).show()
scrollToPrevious()
setupPagerInputEnabled = true })
}
})
}
private fun invalidateTitleY() {
val sc = ViewUtils.lerp((1).toFloat(), 22 / 32f, backgroundProgress)
titleScale = sc
val color = ColorUtils.blendARGB(ThemesRepo.getColor(R.attr.textColorOnAccent), ThemesRepo.getColor(android.R.attr.colorAccent), if (cloudProfile) 0f else backgroundProgress - boostyProgress)
setupTitleColorInt = color
titleTranslationYPx = ViewUtils.lerp(titleY.toFloat(), (ViewUtils.dp(52) - titleHeightPx * sc) / 2f, backgroundProgress)
}
private fun updateStartupBackgroundVisibility() {
var alpha = 1f - backgroundProgress
if (boostyOnly || cloudProfile) alpha = 0f
startupBackgroundAlpha = Math.max(0f, Math.min(1f, alpha))
}
protected override fun onDestroy() {
super.onDestroy()
SliceBeam.EVENT_BUS.unregisterListener(this)
}
@EventHandler(runOnMainThread = true)
fun onDataUpdated(e:BeamServerDataUpdatedEvent) {
if (!about && !cloudProfile)
{
val availabilityChanged = !boostyOnly && ((BOOSTY_INDEX != -1) != BeamServerData.isBoostyAvailable())
if (availabilityChanged)
{
setItems()
}
else
{
setupPages.filterIsInstance<BoostyItem>().firstOrNull()?.refreshSupporters()
}
}
}
@EventHandler(runOnMainThread = true)
fun onCloudSyncFinished(e:CloudSyncFinishedEvent) {
if (cloudProfile && Prefs.getCloudAPIToken() != null && cloudImport)
{
finish()
}
if (!about && !boostyOnly && !cloudProfile)
{
if (Prefs.getCloudAPIToken() != null)
{
limitProfileFragmentCount = false
updateVisibleSetupPageCount()
navigateToPage(setupPages.lastIndex, animated = false)
}
}
}
@EventHandler(runOnMainThread = true)
fun onCloudAuthStateUpdated(e:CloudLoginStateUpdatedEvent) {
if (cloudProfile)
{
cloudItem.refreshState()
}
}
@EventHandler(runOnMainThread = true)
fun onCloudFeaturesUpdated(e:CloudFeaturesUpdatedEvent) {
if (cloudProfile)
{
cloudItem.refreshState()
}
 }
private fun setItems() {
if (cloudProfile)
{
val item = CloudProfileItem()
cloudItem = item
setupPages = listOf(item)
}
else if (boostyOnly)
{
setupPages = listOf(BoostyItem())
}
else if (about)
{
setupPages = listOf(AboutItem())
}
else
{
val profilePage = ProfilesItem()
profilesItem = profilePage
val filamentPage = FilamentsItem()
filamentsItem = filamentPage
val items = arrayListOf<SetupComposePage>(IntroItem(), PresetChoiceItem(), profilePage, filamentPage)
if (BeamServerData.isBoostyAvailable())
{
BOOSTY_INDEX = items.size
items.add(BoostyItem())
}
else
{
BOOSTY_INDEX = -1
}
items.add(FinishItem())
setupPages = items
}
updateVisibleSetupPageCount()
}
private fun updateVisibleSetupPageCount() {
visibleSetupPageCount = when {
about || boostyOnly || cloudProfile -> 1
limitProfileFragmentCount -> minOf(PROFILES_INDEX + 1, setupPages.size)
else -> setupPages.size
}.coerceAtLeast(1)
}
private fun composeSetupRoot(ctx: Context): ThemeAwareComposeView {
val themedView = ThemeAwareComposeView(ctx)
themedView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
themedView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
themedView.setContent {
val revision = themedView.themeRevision
if (revision >= 0) BeamComposeTheme(themeRevision = revision) {
SetupActivityRoot(
pages = setupPages,
pageCount = visibleSetupPageCount,
initialPage = currentSetupPage,
navigationRequest = setupPageNavigationRequest,
userScrollEnabled = setupPagerInputEnabled,
contentInsets = setupPageInsets,
backgroundAlpha = startupBackgroundAlpha,
title = setupTitle,
titleTranslationYPx = titleTranslationYPx,
titleTopInsetPx = titleTopInsetPx,
titleScale = titleScale,
titleColor = setupTitleColorInt,
onRootHeightChanged = { height ->
val nextTitleY = height / 4
if (titleY != nextTitleY)
{
titleY = nextTitleY
invalidateTitleY()
}
},
onTitleHeightChanged = { height ->
if (titleHeightPx != height)
{
titleHeightPx = height
invalidateTitleY()
}
},
onPagerStateChanged = ::onSetupPagerStateChanged,
)
setupMessageDialog?.let { dialog ->
    SetupMessageDialog(
        title = dialog.title,
        message = dialog.message,
        confirmLabel = ctx.getString(android.R.string.ok),
        onDismiss = { if (setupMessageDialog === dialog) setupMessageDialog = null },
    )
}
cloudManageDialog?.let { dialog ->
    dialog.Content {
        if (cloudManageDialog === dialog) cloudManageDialog = null
    }
}
}
}
return themedView
}
private fun onSetupPagerStateChanged(position: Float, page: Int, settled: Boolean) {
currentSetupPage = page
backgroundProgress = if (!boostyOnly && !cloudProfile) position.coerceIn(0f, 1f) else 1f
boostyProgress = when {
boostyOnly -> 1f
BOOSTY_INDEX < 0 -> 0f
position < BOOSTY_INDEX - 1 || position > BOOSTY_INDEX + 1 -> 0f
position < BOOSTY_INDEX -> position - (BOOSTY_INDEX - 1)
else -> (BOOSTY_INDEX + 1 - position).coerceIn(0f, 1f)
}
profilesItem?.updateOverlayAlpha(1f - boostyProgress)
if (settled) {
if (page == PROFILES_INDEX + 1) {
// Printer selection may have changed since this page was last built.
filamentsItem?.refreshIfNeeded()
}
if (page <= PROFILES_INDEX && !limitProfileFragmentCount) {
ViewUtils.postOnMainThread {
if (currentSetupPage <= PROFILES_INDEX && !limitProfileFragmentCount) {
limitProfileFragmentCount = true
updateVisibleSetupPageCount()
}
}
}
if (page == PROFILES_INDEX && !isProfilesLoaded && !isLoading && profilesItem?.useCustomProfile == false) {
loadSetupProfiles()
}
}
invalidateTitleY()
updateStartupBackgroundVisibility()
}

private fun loadSetupProfiles() {
val profilePage = profilesItem ?: return
isLoading = true
setupPagerInputEnabled = false
val loadedCount = AtomicInteger()
val totalNeeded = AtomicInteger()
val onLoadedAll: () -> Unit = {
isProfilesLoaded = true
isLoading = false
setupPagerInputEnabled = true
profilePage.onProfilesLoaded()
pendingRestoreSetupPage?.let { page ->
pendingRestoreSetupPage = null
navigateToPage(page, animated = false)
}
}
for (repo in repos) {
if (!repo.checked) continue
totalNeeded.incrementAndGet()
if (repo.localAssets) {
loadLocalProfiles(repo, loadedCount, totalNeeded, onLoadedAll)
continue
}
client.get(repo.indexUrl, object : AsyncHttpResponseHandler() {
override fun onSuccess(statusCode: Int, headers: Array<Header>, responseBody: ByteArray) {
val vendorProfiles = ArrayList<Slic3rConfigWrapper>()
val onVendorsLoaded = {
profilesMap[repo] = vendorProfiles
loadedCount.incrementAndGet()
if (loadedCount.get() == totalNeeded.get()) ViewUtils.postOnMainThread(onLoadedAll)
}
val loadedVendorsCount = AtomicInteger()
val totalNeededVendors = AtomicInteger()
val loadRunners = ArrayList<Runnable>()
try {
val zis = ZipInputStream(ByteArrayInputStream(responseBody))
while (true) {
val entry = zis.nextEntry ?: break
val version = parseVendorVersion(zis)
val baseUrl = repo.url + "/" + entry.name.substring(0, entry.name.length - 4)
val iniUrl = "$baseUrl/$version.ini"
totalNeededVendors.incrementAndGet()
loadRunners.add(Runnable {
client.get(iniUrl, object : AsyncHttpResponseHandler() {
override fun onSuccess(statusCode: Int, headers: Array<Header>, responseBody: ByteArray) {
loadedVendorsCount.incrementAndGet()
try {
val config = Slic3rConfigWrapper(ByteArrayInputStream(responseBody))
for (printer in config.printerModels) {
printer.get("thumbnail")?.let { printer.thumbnailUrl = "$baseUrl/$it" }
}
vendorProfiles.add(config)
} catch (error: IOException) {
onFailure(statusCode, headers, responseBody, error)
return
}
if (loadedVendorsCount.get() < totalNeededVendors.get()) {
loadRunners[loadedVendorsCount.get()].run()
} else {
onVendorsLoaded()
}
}

override fun onFailure(statusCode: Int, headers: Array<Header>, responseBody: ByteArray, error: Throwable) {
Log.e(TAG, "Failed to load vendor file $iniUrl", error)
isLoading = false
ViewUtils.postOnMainThread {
Toast.makeText(SliceBeam.INSTANCE, R.string.IntroFailedToLoadRepos, Toast.LENGTH_SHORT).show()
scrollToPrevious()
setupPagerInputEnabled = true
}
}
})
})
zis.closeEntry()
}
zis.close()
if (loadRunners.isEmpty()) onVendorsLoaded() else loadRunners[0].run()
} catch (error: IOException) {
Log.e(TAG, "Failed to parse vendor indices", error)
onFailure(statusCode, headers, responseBody, error)
}
}

override fun onFailure(statusCode: Int, headers: Array<Header>, responseBody: ByteArray, error: Throwable) {
isLoading = false
Log.e(TAG, "Failed to load repo", error)
ViewUtils.postOnMainThread {
Toast.makeText(SliceBeam.INSTANCE, R.string.IntroFailedToLoadRepos, Toast.LENGTH_SHORT).show()
scrollToPrevious()
setupPagerInputEnabled = true
}
}
})
}
if (totalNeeded.get() == 0) onLoadedAll()
}
private fun handleSetupBack() {
if (currentSetupPage == PROFILES_INDEX + 1 && profilesItem != null && profilesItem!!.useCustomProfile)
{
navigateToPage(1)
}
else if (currentSetupPage > 0)
{
navigateToPage(currentSetupPage - 1)
}
else
{
finishAfterTransition()
}
}
private fun scrollToNext() {
navigateToPage(currentSetupPage + 1)
}
private fun scrollToPrevious() {
navigateToPage(currentSetupPage - 1)
}
private fun navigateToPage(page: Int, animated: Boolean = true) {
val target = page.coerceIn(0, (visibleSetupPageCount - 1).coerceAtLeast(0))
setupPageNavigationRequest = SetupPageNavigationRequest(++setupPageNavigationId, target, animated)
}
private inner class CloudProfileItem:SetupComposePage {
private var levels by mutableStateOf<List<SetupCloudLevel>>(emptyList())
private var benefits by mutableStateOf(SetupCloudBenefits(-1, -1, -1, 0, null))
private var currentLevel by mutableStateOf<Int?>(null)
private var loggedIn by mutableStateOf(false)
private var loggingIn by mutableStateOf(false)

@Composable
override fun Content() {
SetupCloudProfileScreen(
levels = levels,
benefits = benefits,
currentLevel = currentLevel,
loggedIn = loggedIn,
loggingIn = loggingIn,
onAccountAction = {
if (Prefs.getCloudAPIToken() != null)
{
cloudManageDialog = CloudManageDialog(this@SetupActivity)
}
else
{
CloudController.beginLogin()
}
},
onCancelLogin = { CloudController.cancelLogin() },
onOpenUrl = { url -> openCloudUrl(url) },
)
}

override fun onPageCreated() {
refreshState()
}

fun refreshState() {
loggedIn = Prefs.getCloudAPIToken() != null
loggingIn = !loggedIn && CloudController.isLoggingIn()
levels = CloudController.getUserFeatures()?.levels?.map { level ->
SetupCloudLevel(
level = level.level,
title = level.title.orEmpty(),
price = level.price,
subscribeUrl = level.subscribeOrUpgradeUrl,
manageUrl = level.manageUrl,
)
}.orEmpty()
val features = CloudController.getUserFeatures()
benefits = SetupCloudBenefits(
earlyAccessLevel = features?.earlyAccessLevel ?: -1,
syncRequiredLevel = features?.syncRequiredLevel ?: -1,
aiGeneratorRequiredLevel = features?.aiGeneratorRequiredLevel ?: -1,
aiGeneratorModelsPerMonth = features?.aiGeneratorModelsPerMonth ?: 0,
alreadySubscribedInfoUrl = features?.alreadySubscribedInfoUrl,
)
currentLevel = CloudController.getUserInfo()?.currentLevel
}
}

private fun openCloudUrl(url:String?) {
if (url.isNullOrBlank()) return
try
{
startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
catch (ignored:android.content.ActivityNotFoundException)
{
Toast.makeText(this, R.string.CloudManageLinkOpenFailed, Toast.LENGTH_SHORT).show()
}
}
private fun openBoostySupport() {
try
{
startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://boosty.to/ytkab0bp")))
}
catch (ignored:android.content.ActivityNotFoundException)
{
Toast.makeText(this, R.string.ChangelogSupportLinkOpenFailed, Toast.LENGTH_SHORT).show()
}
catch (ignored:SecurityException)
{
Toast.makeText(this, R.string.ChangelogSupportLinkOpenFailed, Toast.LENGTH_SHORT).show()
}
}
private inner class AboutItem:SetupComposePage {
private var updateStatus by mutableStateOf("")
private var updateRunning = false
private var downloadState by mutableStateOf(ru.ytkab0bp.slicebeam.utils.UpdateDownloadState())
private val versionName = try {
packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
} catch (ignored:PackageManager.NameNotFoundException) { "" }

@Composable
override fun Content() {
SetupAboutScreen(versionName, updateStatus, downloadState, onCheckUpdate = {
    if (!updateRunning) {
        updateRunning = true
        updateStatus = "Suche nach neuer Version …"
        ru.ytkab0bp.slicebeam.utils.GitHubUpdater.check(this@SetupActivity) { message ->
            updateStatus = message
            updateRunning = false
            downloadState = ru.ytkab0bp.slicebeam.utils.GitHubUpdater.downloadState(this@SetupActivity)
        }
    }
}, onInstallUpdate = {
    ru.ytkab0bp.slicebeam.utils.GitHubUpdater.installDownloaded(this@SetupActivity) { message ->
        updateStatus = message
        downloadState = ru.ytkab0bp.slicebeam.utils.GitHubUpdater.downloadState(this@SetupActivity)
    }
}, onRefreshDownload = {
    downloadState = ru.ytkab0bp.slicebeam.utils.GitHubUpdater.downloadState(this@SetupActivity)
}, onClose = { finish() })
}

override fun onPageCreated() {
    downloadState = ru.ytkab0bp.slicebeam.utils.GitHubUpdater.downloadState(this@SetupActivity)
}
}
private inner class IntroItem:SetupComposePage {
@Composable
override fun Content() {
SetupIntroScreen(onNext = { scrollToNext() })
}

override fun onPageCreated() = Unit
}
private inner class PresetChoiceItem:SetupComposePage {
@Composable
override fun Content() {
SetupPresetSourceScreen(
onSelectBuiltIn = ::selectBuiltIn,
onImportProfile = ::openProfilePicker,
onNext = { scrollToNext() },
)
}

override fun onPageCreated() = Unit

private fun selectBuiltIn() {
profilesItem?.updateCustomProfile(false)
}
}
private inner class ProfilesItem:SetupComposePage {
private var sections by mutableStateOf<List<SetupProfileSection>>(emptyList())
private var loading by mutableStateOf(true)
private var overlayAlpha by mutableStateOf(1f)
private val profileByKey = LinkedHashMap<String, ConfigObject>()
var useCustomProfile:Boolean by mutableStateOf(false)
private set

@Composable
override fun Content() {
SetupPrinterSelectionScreen(
sections = sections,
loading = loading,
useCustomProfile = useCustomProfile,
overlayAlpha = overlayAlpha,
onToggle = ::togglePrinter,
onContinue = ::continueToFilaments,
)
}

override fun onPageCreated() {
loading = !isProfilesLoaded && !useCustomProfile
refreshSelection()
}

fun updateOverlayAlpha(alpha:Float) {
overlayAlpha = alpha.coerceIn(0f, 1f)
}

fun updateCustomProfile(enabled:Boolean) {
useCustomProfile = enabled
loading = !isProfilesLoaded && !enabled
refreshSelection()
}

fun onProfilesLoaded() {
loading = false
refreshSelection()
}

private fun togglePrinter(key:String, checked:Boolean) {
if (!profileByKey.containsKey(key)) return
val currentKeys = profileByKey.filterValues(enabledPrinters::contains).keys
val nextKeys = SetupProfileSelectionPolicy.toggle(currentKeys, key, checked)
enabledPrinters.clear()
enabledPrinters.addAll(nextKeys.mapNotNull(profileByKey::get))
refreshSelection()
}

private fun refreshSelection() {
profileByKey.clear()
if (useCustomProfile) {
sections = emptyList()
return
}
val grouped = LinkedHashMap<String, MutableList<Pair<String, ConfigObject>>>()
val vendors = profilesMap.values.flatten().sortedBy {
it.vendor?.values?.get("name").orEmpty().lowercase(Locale.ROOT)
}
var optionIndex = 0
for (wrapper in vendors) {
val vendorName = wrapper.vendor?.values?.get("name").orEmpty().ifBlank { getString(R.string.IntroCustomProfileHeader) }
val options = grouped.getOrPut(vendorName) { ArrayList() }
for (printer in wrapper.printerModels) {
val title = printer.title ?: continue
if (title.startsWith("*") && title.endsWith("*")) continue
val key = "printer_${optionIndex++}"
profileByKey[key] = printer
options.add(key to printer)
}
}
sections = grouped.entries.mapIndexed { sectionIndex, (vendor, options) ->
SetupProfileSection(
key = "printer_vendor_$sectionIndex",
title = vendor,
options = options.map { (key, profile) ->
SetupProfileOption(
key = key,
title = profile.get("name") ?: profile.title.orEmpty(),
thumbnailUrl = profile.thumbnailUrl,
selected = enabledPrinters.contains(profile),
)
},
)
}.filter { it.options.isNotEmpty() }
}

private fun continueToFilaments() {
if (!SetupProfileSelectionPolicy.canContinue(useCustomProfile, enabledPrinters.size)) {
showSetupMessage(
    getString(R.string.IntroNoProfiles),
    getString(R.string.IntroNoProfilesDescription),
)
return
}
limitProfileFragmentCount = false
this@SetupActivity.updateVisibleSetupPageCount()
scrollToNext()
}
}
private inner class FilamentsItem:SetupComposePage {
private val currentFilaments = ArrayList<ConfigObject>()
private val filamentByKey = LinkedHashMap<String, ConfigObject>()
private var sections by mutableStateOf<List<SetupProfileSection>>(emptyList())
private var hasFilaments by mutableStateOf(false)
private var builtFor:String? = null

@Composable
override fun Content() {
SetupFilamentSelectionScreen(
sections = sections,
hasFilaments = hasFilaments,
overlayAlpha = 1f,
onToggle = ::toggleFilament,
onSelectAll = ::selectAll,
onDeselectAll = ::deselectAll,
onContinue = { scrollToNext() },
)
}

override fun onPageCreated() {
refreshIfNeeded()
}

/** Rebuild candidates/defaults only when the selected printer set changes. */
fun refreshIfNeeded() {
val keyBuilder = StringBuilder(if (profilesItem?.useCustomProfile == true) "custom\n" else "preset\n")
for (printer in enabledPrinters) keyBuilder.append(System.identityHashCode(printer)).append('\n')
val key = keyBuilder.toString()
if (key == builtFor) return
builtFor = key
currentFilaments.clear()
filamentByKey.clear()
val vendors = profilesMap.values.flatten().sortedBy {
it.vendor?.values?.get("name").orEmpty().lowercase(Locale.ROOT)
}
val grouped = LinkedHashMap<String, MutableList<Pair<String, ConfigObject>>>()
if (profilesItem?.useCustomProfile != true) {
for (wrapper in vendors) {
val hasSelectedPrinter = wrapper.printerModels.any { enabledPrinters.contains(it) }
val independentLibrary = wrapper.vendor?.get("filament_library") == "1"
if (!SetupProfileSelectionPolicy.shouldShowFilamentVendor(hasSelectedPrinter, independentLibrary)) continue
val vendorName = wrapper.vendor?.values?.get("name").orEmpty().ifBlank { getString(R.string.IntroCustomProfileHeader) }
val options = grouped.getOrPut(vendorName) { ArrayList() }
for (filament in wrapper.filamentConfigs) {
val title = filament.title
if (title.isNullOrBlank() || (title.startsWith("*") && title.endsWith("*"))) continue
val optionKey = "filament_${currentFilaments.size}"
currentFilaments.add(filament)
filamentByKey[optionKey] = filament
options.add(optionKey to filament)
}
}
}
enabledFilaments.clear()
for (printerModel in enabledPrinters) {
val defaults = printerModel.get("default_materials") ?: continue
for (material in defaults.split(';').map(String::trim).filter(String::isNotEmpty)) {
currentFilaments.firstOrNull { material.equals(it.title, ignoreCase = true) }?.let { filament ->
if (!enabledFilaments.contains(filament)) enabledFilaments.add(filament)
}
}
}
sections = grouped.entries.mapIndexed { sectionIndex, (vendor, options) ->
SetupProfileSection(
key = "filament_vendor_$sectionIndex",
title = vendor,
options = options.map { (optionKey, profile) ->
SetupProfileOption(
key = optionKey,
title = profile.get("name") ?: profile.title.orEmpty(),
thumbnailUrl = profile.thumbnailUrl,
selected = enabledFilaments.contains(profile),
)
},
)
}.filter { it.options.isNotEmpty() }
hasFilaments = currentFilaments.isNotEmpty()
}

private fun toggleFilament(key:String, checked:Boolean) {
if (!filamentByKey.containsKey(key)) return
val currentKeys = filamentByKey.filterValues(enabledFilaments::contains).keys
val nextKeys = SetupProfileSelectionPolicy.toggle(currentKeys, key, checked)
enabledFilaments.clear()
enabledFilaments.addAll(nextKeys.mapNotNull(filamentByKey::get))
refreshSelectionState()
}

private fun selectAll() {
enabledFilaments.clear()
enabledFilaments.addAll(SetupProfileSelectionPolicy.selectAll(filamentByKey.keys).mapNotNull(filamentByKey::get))
refreshSelectionState()
}

private fun deselectAll() {
enabledFilaments.removeAll(currentFilaments.toSet())
refreshSelectionState()
}

private fun refreshSelectionState() {
sections = sections.map { section ->
section.copy(options = section.options.map { option ->
option.copy(selected = filamentByKey[option.key]?.let(enabledFilaments::contains) == true)
})
}
}
}
private inner class BoostyItem:SetupComposePage {
private var supporters by mutableStateOf(currentSupporters())

@Composable
override fun Content() {
SetupBoostyScreen(
supporters = supporters,
boostyOnly = boostyOnly,
onOpenSupport = ::openBoostySupport,
onContinue = { if (boostyOnly) finish() else scrollToNext() },
)
}

override fun onPageCreated() = Unit

fun refreshSupporters() {
supporters = currentSupporters()
}

private fun currentSupporters():List<String> = SliceBeam.SERVER_DATA?.boostySubscribers?.shuffled().orEmpty()
}private inner class FinishItem:SetupComposePage {
@Composable
override fun Content() {
SetupFinishScreen(onFinish = { finishSetup() })
}

override fun onPageCreated() = Unit
private fun finishSetup() {
val cfg = Slic3rConfigWrapper()
if (profilesItem?.useCustomProfile == true)
{
val custom = ConfigObject.createCustomPrinterProfile()
custom.profileListType = ConfigObject.PROFILE_LIST_PRINTER
cfg.printerConfigs.add(custom)
val genericFilament = ConfigObject.createCustomFilamentProfile()
cfg.filamentConfigs.add(genericFilament)
val genericPrint = ConfigObject(getString(R.string.IntroCustomProfileName))
genericPrint.profileListType = ConfigObject.PROFILE_LIST_PRINT
cfg.printConfigs.add(genericPrint)
cfg.presets = ConfigObject()
cfg.presets!!.put("printer", custom.title)
cfg.presets!!.put("print", genericPrint.title)
cfg.presets!!.put("filament", genericFilament.title)
}
else
{
for (printerModel in enabledPrinters)
{
val model = printerModel.title
val variants: List<String> = printerModel.get("variants")?.takeUnless { it.isEmpty() }?.split(";") ?: emptyList()
val materials: List<String> = printerModel.get("default_materials")?.takeUnless { it.isEmpty() }?.split(";") ?: emptyList()
for (rawVariant in variants)
{
val variant = rawVariant.trim()
for (wrappers in profilesMap.values)
{
for (w in wrappers)
{
val obj = w.findPrinterVariant(model, variant)
if (obj != null)
{
cfg.printerConfigs.add(obj)
val checker = Slic3rUtils.ConfigChecker(obj.serialize())
val printerName = obj.title
var nozzle = obj.get("printer_variant")
if (nozzle == null) nozzle = Slic3rUtils.firstNozzleDiameter(obj.get("nozzle_diameter"))
for (printConfig in w.printConfigs)
{
val printTitle = printConfig.title ?: continue
if (printTitle.startsWith("*") && printTitle.endsWith("*")) continue
if (!checker.checkCompatibility(printConfig.get("compatible_printers_condition"))) continue
// The bundled profiles carry no per-printer link, so only keep processes whose
 // nozzle (inferred from layer height + line width) matches this printer variant,
 // and tag each with the printer it belongs to so the app can filter per-printer.
 if ((!Slic3rUtils.layerHeightFitsNozzle(printConfig.get("layer_height"), nozzle) || !Slic3rUtils.lineWidthFitsNozzle(printConfig.get("line_width"), nozzle)))
continue
val tagged = ConfigObject(printConfig)
if (android.text.TextUtils.isEmpty(tagged.get("compatible_printers")))
{
tagged.put("compatible_printers", printerName)
}
cfg.printConfigs.add(tagged)
}
checker.release()
}
}
}
}
// Filaments are normally chosen on the wizard's filament page; this
 // default_materials lookup only runs if that page was never built.
 if (enabledFilaments.isEmpty())
{
for (rawMat in materials)
{
val mat = rawMat.trim()
for (wrappers in profilesMap.values)
{
for (w in wrappers)
{
val obj = w.findFilament(mat)
if (obj != null) cfg.filamentConfigs.add(obj)
}
}
}
}
}
for (fil in enabledFilaments)
{
if (cfg.findFilament(fil.title) == null)
{
cfg.filamentConfigs.add(fil)
}
}
cfg.presets = ConfigObject()
if (!cfg.printerConfigs.isEmpty())
{
var foundDefault = false
for (obj in cfg.printerConfigs)
{
if (obj.title!!.contains("0.4"))
{
foundDefault = true
cfg.presets!!.put("printer", obj.title)
break
}
}
if (!foundDefault && !cfg.printerConfigs.isEmpty())
{
cfg.presets!!.put("printer", cfg.printerConfigs.get(0).title)
}
}
val defPrinter = if (cfg.printerConfigs.isEmpty()) null else cfg.findPrinter(cfg.presets!!.get("printer"))
if (defPrinter != null)
{
val checker = Slic3rUtils.ConfigChecker(defPrinter.serialize())
if (defPrinter.get("default_print_profile") != null && cfg.findPrint(defPrinter.get("default_print_profile")) != null)
{
cfg.presets!!.put("print", defPrinter.get("default_print_profile"))
}
else
{
if (!cfg.printConfigs.isEmpty())
{
var foundDefault = false
for (obj in cfg.printConfigs)
{
if (obj.get("layer_height") != null && checker.checkCompatibility(obj.get("compatible_printers_condition")) && java.lang.Float.parseFloat(obj.get("layer_height")) == 0.2f)
{
foundDefault = true
cfg.presets!!.put("print", obj.title)
break
}
}
if (!foundDefault && !cfg.printConfigs.isEmpty())
{
cfg.presets!!.put("print", cfg.printConfigs.get(0).title)
}
}
}
if (defPrinter.get("default_filament_profile") != null && cfg.findFilament(defPrinter.get("default_filament_profile")) != null)
{
cfg.presets!!.put("filament", defPrinter.get("default_filament_profile"))
}
else
{
if (!cfg.filamentConfigs.isEmpty())
{
var foundDefault = false
for (obj in cfg.filamentConfigs)
{
if (obj.title!!.contains("Generic PLA") && checker.checkCompatibility(obj.get("compatible_printers_condition")))
{ // TODO: Slic3rUtils.checkCompatibility(obj.get("compatible_prints_condition"), serialized)
foundDefault = true
cfg.presets!!.put("filament", obj.title)
break
}
}
if (!foundDefault && !cfg.filamentConfigs.isEmpty())
{
cfg.presets!!.put("filament", cfg.filamentConfigs.get(0).title)
}
}
}
checker.release()
}
}
try
{
if (SliceBeam.CONFIG == null)
{
// First-time setup: adopt the freshly built config wholesale.
val previousConfig = SliceBeam.CONFIG
SliceBeam.CONFIG = cfg
if (!SliceBeam.saveConfig()) {
SliceBeam.CONFIG = previousConfig
throw IOException("Could not save the selected printer profiles")
}
}
else if (Prefs.getCloudAPIToken() == null)
{
// Re-running setup with an existing config (e.g. "Add printer"): merge the
 // newly selected presets into what the user already has instead of wiping it.
 val existing = SliceBeam.CONFIG
val snapshot = ProfileConfigSnapshot(existing!!)
for (p in cfg.printerConfigs)
    if (existing!!.findPrinter(p.title) == null) existing!!.printerConfigs.add(p)
for (f in cfg.filamentConfigs)
    if (existing!!.findFilament(f.title) == null) existing!!.filamentConfigs.add(f)
// Processes are tagged per-printer (compatible_printers), so a same-named process
 // for a different printer is NOT a duplicate â€” key on title + compatible_printers.
 for (pr in cfg.printConfigs)
{
var dup = false
for (ex in existing!!.printConfigs)
{
if ((ex.title.equals(pr.title) && java.util.Objects.equals(ex.get("compatible_printers"), pr.get("compatible_printers"))))
{
dup = true
break
}
}
if (!dup) existing!!.printConfigs.add(pr)
}
// Switch the active selection to the newly added printer and its defaults.
 val np = if (cfg.presets != null) cfg.presets!!.get("printer") else null
if (np != null && existing!!.findPrinter(np) != null)
{
existing!!.presets!!.put("printer", np)
val npr = cfg.presets!!.get("print")
if (npr != null && existing!!.findPrint(npr) != null) existing!!.presets!!.put("print", npr)
val nf = cfg.presets!!.get("filament")
if (nf != null && existing!!.findFilament(nf) != null) existing!!.presets!!.put("filament", nf)
}
if (!SliceBeam.saveConfig()) {
snapshot.restore()
throw IOException("Could not save the selected printer profiles")
}
}
// The active selection just changed â€” drop any stale unsaved edits.
 SliceBeam.clearLiveDiffs()
startActivity(Intent(this@SetupActivity, MainActivity::class.java))
finish()
}
catch (e:Exception) {
Log.e(TAG, "Failed to save config", e)
ViewUtils.postOnMainThread {
    showSetupMessage(
        getString(R.string.MenuFileImportProfilesFailed),
        getString(R.string.ConfigSaveFailed),
    )
}
}
}
}

enum class AccentColors private constructor(internal val color:Int) {
DEFAULT(-0xe53a5e), // OrcaSlicer signature blue-green teal
 BLUE(-0xab6d0b),
LIGHT_BLUE(-0x922a06),
RED(-0x16bfaa),
ORANGE(-0xb4d4),
YELLOW(-0x237cf),
PINK(-0xd8f65),
PURPLE(-0x918b1f);
}
private class ProfilesRepo(
    val name: String,
    val description: String = "",
    val url: String = "",
    val indexUrl: String = "",
    var checked: Boolean = false,
    val localAssets: Boolean = false,
)
companion object {
@JvmField
val EXTRA_ABOUT = "about"
@JvmField
val EXTRA_BOOSTY_ONLY = "boosty_only"
@JvmField
val EXTRA_CLOUD_PROFILE = "cloud_profile"
@JvmField
val EXTRA_CLOUD_IMPORT_FROM_SETUP = "cloud_import_from_setup"
@JvmField
val EXTRA_ADD_PRINTER = "add_printer"
private val TAG = "SetupActivity"
private const val STATE_CURRENT_SETUP_PAGE = "setup_current_page"
private const val STATE_LIMIT_SETUP_PAGES = "setup_limit_page_count"
private val REQUEST_CODE_IMPORT_SETUP_PROFILE = 1001
private val ORCA_ASSET_DIR = "orca_profiles"
private val PROFILES_INDEX = 2
private var BOOSTY_INDEX = 3
@Throws(IOException::class)
private fun parseVendorVersion(`in`:InputStream):String? {
val r = BufferedReader(InputStreamReader(`in`))
while (true)
{
val line = r.readLine() ?: return null
if (line.contains(" = ")) continue
return line.substring(0, line.indexOf(' '))
}
}
}
}
