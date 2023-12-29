package ani.saikou

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.DatePickerDialog
import android.app.DownloadManager
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources.getSystem
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.*
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat.getExternalFilesDirs
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.math.MathUtils.clamp
import androidx.core.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.saikou.anilist.Anilist
import ani.saikou.anilist.Genre
import ani.saikou.anilist.api.FuzzyDate
import ani.saikou.anime.Episode
import ani.saikou.databinding.ItemCountDownBinding
import ani.saikou.media.Media
import ani.saikou.others.DisabledReports
import ani.saikou.parsers.ShowResponse
import ani.saikou.settings.UserInterfaceSettings
import ani.saikou.tv.TVMainActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.internal.ViewUtils
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.joery.animatedbottombar.AnimatedBottomBar
import java.io.*
import java.lang.reflect.Field
import java.util.*
import kotlin.math.*


var statusBarHeight = 0
var navBarHeight = 0
val Int.dp: Float get() = (this / getSystem().displayMetrics.density)
val Float.px: Int get() = (this * getSystem().displayMetrics.density).toInt()

lateinit var bottomBar: AnimatedBottomBar
var selectedOption = 1

object Refresh {
    fun all() {
        for (i in activity) {
            activity[i.key]!!.postValue(true)
        }
    }

    val activity = mutableMapOf<Int, MutableLiveData<Boolean>>()
}

fun currActivity(): Activity? {
    return App.currentActivity()
}

var loadMedia: Int? = null
var loadIsMAL = false

fun logger(e: Any?, print: Boolean = true) {
    if (print)
        println(e)
}

fun saveData(fileName: String, data: Any?, activity: Context? = null) {
    tryWith {
        val a = activity ?: currActivity()
        if (a != null) {
            val fos: FileOutputStream = a.openFileOutput(fileName, Context.MODE_PRIVATE)
            val os = ObjectOutputStream(fos)
            os.writeObject(data)
            os.close()
            fos.close()
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> loadData(fileName: String, activity: Context? = null, toast: Boolean = true): T? {
    val a = activity ?: currActivity()
    try {
        if (a?.fileList() != null)
            if (fileName in a.fileList()) {
                val fileIS: FileInputStream = a.openFileInput(fileName)
                val objIS = ObjectInputStream(fileIS)
                val data = objIS.readObject() as T
                objIS.close()
                fileIS.close()
                return data
            }
    } catch (e: Exception) {
        if (toast) toastString("Error loading data $fileName")
        e.printStackTrace()
    }
    return null
}

fun initActivity(a: Activity) {
    val window = a.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val uiSettings = loadData<UserInterfaceSettings>("ui_settings", toast = false) ?: UserInterfaceSettings().apply {
        saveData("ui_settings", this)
    }
    uiSettings.darkMode.apply {
        AppCompatDelegate.setDefaultNightMode(
            when (this) {
                true  -> AppCompatDelegate.MODE_NIGHT_YES
                false -> AppCompatDelegate.MODE_NIGHT_NO
                else  -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
    if (uiSettings.immersiveMode) {
        if (navBarHeight == 0) {
            ViewCompat.getRootWindowInsets(window.decorView.findViewById(android.R.id.content))?.apply {
                navBarHeight = this.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            }
        }
        a.hideStatusBar()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && statusBarHeight == 0 && a.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            window.decorView.rootWindowInsets?.displayCutout?.apply {
                if (boundingRects.size > 0) {
                    statusBarHeight = min(boundingRects[0].width(), boundingRects[0].height())
                }
            }
        }
    } else
        if (statusBarHeight == 0) {
            val windowInsets = ViewCompat.getRootWindowInsets(window.decorView.findViewById(android.R.id.content))
            if (windowInsets != null) {
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                statusBarHeight = insets.top
                navBarHeight = insets.bottom
            }
        }
}

@Suppress("DEPRECATION")
fun Activity.hideSystemBars() {
    window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
}

@Suppress("DEPRECATION")
fun Activity.hideStatusBar() {
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
}

open class BottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun onStart() {
        super.onStart()
        if (this.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
            val behavior = BottomSheetBehavior.from(requireView().parent as View)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        val ft = manager.beginTransaction()
        ft.add(this, tag)
        ft.commitAllowingStateLoss()
    }
}

fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return tryWith {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cap = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            return@tryWith if (cap != null) {
                when {
                    cap.hasTransport(TRANSPORT_BLUETOOTH) ||
                            cap.hasTransport(TRANSPORT_CELLULAR) ||
                            cap.hasTransport(TRANSPORT_ETHERNET) ||
                            cap.hasTransport(TRANSPORT_LOWPAN) ||
                            cap.hasTransport(TRANSPORT_USB) ||
                            cap.hasTransport(TRANSPORT_VPN) ||
                            cap.hasTransport(TRANSPORT_WIFI) ||
                            cap.hasTransport(TRANSPORT_WIFI_AWARE) -> true
                    else                                           -> false
                }
            } else false
        } else true
    } ?: false
}

fun startMainActivity(activity: Activity) {
    activity.finishAffinity()
    activity.startActivity(
        Intent(
            activity,
            if (isOnTV(activity)) TVMainActivity::class.java else MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}


class DatePickerFragment(activity: Activity, var date: FuzzyDate = FuzzyDate().getToday()) : DialogFragment(),
    DatePickerDialog.OnDateSetListener {
    var dialog: DatePickerDialog

    init {
        val c = Calendar.getInstance()
        val year = date.year ?: c.get(Calendar.YEAR)
        val month = if (date.month != null) date.month!! - 1 else c.get(Calendar.MONTH)
        val day = date.day ?: c.get(Calendar.DAY_OF_MONTH)
        dialog = DatePickerDialog(activity, this, year, month, day)
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
        date = FuzzyDate(year, month + 1, day)
    }
}

class InputFilterMinMax(private val min: Double, private val max: Double, private val status: AutoCompleteTextView? = null) :
    InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        try {
            val input = (dest.toString() + source.toString()).toDouble()
            if (isInRange(min, max, input)) return null
        } catch (nfe: NumberFormatException) {
            logger(nfe.stackTraceToString())
        }
        return ""
    }

    @SuppressLint("SetTextI18n")
    private fun isInRange(a: Double, b: Double, c: Double): Boolean {
        if (c == b) {
            status?.setText("COMPLETED", false)
            status?.parent?.requestLayout()
        }
        return if (b > a) c in a..b else c in b..a
    }
}


class ZoomOutPageTransformer(private val uiSettings: UserInterfaceSettings) : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        if (position == 0.0f && uiSettings.layoutAnimations) {
            setAnimation(view.context, view, uiSettings, 300, floatArrayOf(1.3f, 1f, 1.3f, 1f), 0.5f to 0f)
            ObjectAnimator.ofFloat(view, "alpha", 0f, 1.0f).setDuration((200 * uiSettings.animationSpeed).toLong()).start()
        }
    }
}

fun setAnimation(
    context: Context,
    viewToAnimate: View,
    uiSettings: UserInterfaceSettings,
    duration: Long = 150,
    list: FloatArray = floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f),
    pivot: Pair<Float, Float> = 0.5f to 0.5f
) {
    if (uiSettings.layoutAnimations) {
        val anim = ScaleAnimation(
            list[0],
            list[1],
            list[2],
            list[3],
            Animation.RELATIVE_TO_SELF,
            pivot.first,
            Animation.RELATIVE_TO_SELF,
            pivot.second
        )
        anim.duration = (duration * uiSettings.animationSpeed).toLong()
        anim.setInterpolator(context, R.anim.over_shoot)
        viewToAnimate.startAnimation(anim)
    }
}


class FadingEdgeRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun isPaddingOffsetRequired(): Boolean {
        return !clipToPadding
    }

    override fun getLeftPaddingOffset(): Int {
        return if (clipToPadding) 0 else -paddingLeft
    }

    override fun getTopPaddingOffset(): Int {
        return if (clipToPadding) 0 else -paddingTop
    }

    override fun getRightPaddingOffset(): Int {
        return if (clipToPadding) 0 else paddingRight
    }

    override fun getBottomPaddingOffset(): Int {
        return if (clipToPadding) 0 else paddingBottom
    }
}

fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    if (lhs == rhs) {
        return 0
    }
    if (lhs.isEmpty()) {
        return rhs.length
    }
    if (rhs.isEmpty()) {
        return lhs.length
    }

    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1

    var cost = Array(lhsLength) { it }
    var newCost = Array(lhsLength) { 0 }

    for (i in 1 until rhsLength) {
        newCost[0] = i

        for (j in 1 until lhsLength) {
            val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1

            newCost[j] = min(min(costInsert, costDelete), costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
    }

    return cost[lhsLength - 1]
}

fun List<ShowResponse>.sortByTitle(string: String): List<ShowResponse> {
    val list = this.toMutableList()
    list.sortByTitle(string)
    return list
}

fun MutableList<ShowResponse>.sortByTitle(string: String) {
    val temp: MutableMap<Int, Int> = mutableMapOf()
    for (i in 0 until this.size) {
        temp[i] = levenshtein(string.lowercase(), this[i].name.lowercase())
    }
    val c = temp.toList().sortedBy { (_, value) -> value }.toMap()
    val a = ArrayList(c.keys.toList().subList(0, min(this.size, 25)))
    val b = c.values.toList().subList(0, min(this.size, 25))
    for (i in b.indices.reversed()) {
        if (b[i] > 18 && i < a.size) a.removeAt(i)
    }
    val temp2 = this.toMutableList()
    this.clear()
    for (i in a.indices) {
        this.add(temp2[a[i]])
    }
}

fun String.findBetween(a: String, b: String): String? {
    val start = this.indexOf(a)
    val end = if (start != -1) this.indexOf(b, start) else return null
    return if (end != -1) this.subSequence(start, end).removePrefix(a).removeSuffix(b).toString() else null
}

fun ImageView.loadImage(url: String?, size: Int = 0) {
    if (!url.isNullOrEmpty()) {
        loadImage(FileUrl(url), size)
    }
}

fun ImageView.loadImage(file: FileUrl?, size: Int = 0) {
    if (file?.url?.isNotEmpty() == true) {
        tryWith {
            val glideUrl = GlideUrl(file.url) { file.headers }
            Glide.with(this.context).load(glideUrl).transition(withCrossFade()).override(size).into(this)
        }
    }
}

class SafeClickListener(
    private var defaultInterval: Int = 1000,
    private val onSafeCLick: (View) -> Unit
) : View.OnClickListener {

    private var lastTimeClicked: Long = 0

    override fun onClick(v: View) {
        if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
            return
        }
        lastTimeClicked = SystemClock.elapsedRealtime()
        onSafeCLick(v)
    }
}

fun View.setSafeOnClickListener(onSafeClick: (View) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

suspend fun getSize(file: FileUrl): Double? {
    return tryWithSuspend {
        client.head(file.url, file.headers, timeout = 1000).size?.toDouble()?.div(1024 * 1024)
    }
}

suspend fun getSize(file: String): Double? {
    return getSize(FileUrl(file))
}


class App : MultiDexApplication() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    init {
        instance = this
    }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)

        Firebase.crashlytics.setCrashlyticsCollectionEnabled(!DisabledReports)
        initializeNetwork(baseContext)

    }

    companion object {
        private var instance: App? = null
        fun currentActivity(): Activity? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity
        }
    }
}

class FTActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    var currentActivity: Activity? = null
    override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
    override fun onActivityStarted(p0: Activity) {
        currentActivity = p0
    }

    override fun onActivityResumed(p0: Activity) {
        currentActivity = p0
    }

    override fun onActivityPaused(p0: Activity) {}
    override fun onActivityStopped(p0: Activity) {}
    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
    override fun onActivityDestroyed(p0: Activity) {}
}

@SuppressLint("ViewConstructor")
class ExtendedTimeBar(
    context: Context,
    attrs: AttributeSet?
) : DefaultTimeBar(context, attrs) {
    private var enabled = false
    private var forceDisabled = false
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        super.setEnabled(!forceDisabled && this.enabled)
    }

    fun setForceDisabled(forceDisabled: Boolean) {
        this.forceDisabled = forceDisabled
        isEnabled = enabled
    }
}

abstract class GesturesListener : GestureDetector.SimpleOnGestureListener() {
    private var timer: Timer? = null //at class level;
    private val delay: Long = 200

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        processSingleClickEvent(e)
        return super.onSingleTapUp(e)
    }

    override fun onLongPress(e: MotionEvent) {
        processLongClickEvent(e)
        super.onLongPress(e)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        processDoubleClickEvent(e)
        return super.onDoubleTap(e)
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        onScrollYClick(distanceY)
        onScrollXClick(distanceX)
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    private fun processSingleClickEvent(e: MotionEvent) {
        val handler = Handler(Looper.getMainLooper())
        val mRunnable = Runnable {
            onSingleClick(e)
        }
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    handler.post(mRunnable)
                }
            }, delay)
        }
    }

    private fun processDoubleClickEvent(e: MotionEvent) {
        timer?.apply {
            cancel()
            purge()
        }
        onDoubleClick(e)
    }

    private fun processLongClickEvent(e: MotionEvent) {
        timer?.apply {
            cancel()
            purge()
        }
        onLongClick(e)
    }

    open fun onSingleClick(event: MotionEvent) {}
    open fun onDoubleClick(event: MotionEvent) {}
    open fun onScrollYClick(y: Float) {}
    open fun onScrollXClick(y: Float) {}
    open fun onLongClick(event: MotionEvent) {}
}

fun View.circularReveal(ex: Int, ey: Int, subX: Boolean, time: Long) {
    ViewAnimationUtils.createCircularReveal(
        this,
        if (subX) (ex - x.toInt()) else ex,
        ey - y.toInt(),
        0f,
        max(height, width).toFloat()
    ).setDuration(time).start()
}

fun openLinkInBrowser(link: String?) {
    tryWith {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        currActivity()?.startActivity(intent)
    }
}

fun download(activity: Activity, episode: Episode, animeTitle: String) {
    val manager = activity.getSystemService(AppCompatActivity.DOWNLOAD_SERVICE) as DownloadManager
    val extractor = episode.extractors?.find { it.server.name == episode.selectedExtractor } ?: return
    val video =
        if (extractor.videos.size > episode.selectedVideo) extractor.videos[episode.selectedVideo] else return
    val regex = "[\\\\/:*?\"<>|]".toRegex()
    val aTitle = animeTitle.replace(regex, "")
    val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(video.url.url))

    video.url.headers.forEach {
        request.addRequestHeader(it.key, it.value)
    }

    val title = "Episode ${episode.number}${if (episode.title != null) " - ${episode.title}" else ""}".replace(regex, "")
    val name = "$title${if (video.size != null) "(${video.size}p)" else ""}.mp4"
    CoroutineScope(Dispatchers.IO).launch {
        try {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            val arrayOfFiles = getExternalFilesDirs(activity, null)
            if (loadData<Boolean>("sd_dl") == true && arrayOfFiles.size > 1 && arrayOfFiles[0] != null && arrayOfFiles[1] != null) {
                val parentDirectory = arrayOfFiles[1].toString() + "/Anime/${aTitle}/"
                val direct = File(parentDirectory)
                if (!direct.exists()) direct.mkdirs()
                request.setDestinationUri(Uri.fromFile(File("$parentDirectory$name")))
            } else {
                val direct = File(Environment.DIRECTORY_DOWNLOADS + "/Saikou/Anime/${aTitle}/")
                if (!direct.exists()) direct.mkdirs()
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "/Saikou/Anime/${aTitle}/$name"
                )
            }
            request.setTitle("$title:$aTitle")
            manager.enqueue(request)
            toast("Started Downloading\n$title : $aTitle")
        } catch (e: SecurityException) {
            toast("Please give permission to access Files & Folders from Settings, & Try again.")
        } catch (e: Exception) {
            toast(e.toString())
        }
    }
}

fun saveImageToDownloads(title: String, bitmap: Bitmap, context: Context) {
    FileProvider.getUriForFile(
        context,
        BuildConfig.APPLICATION_ID + ".provider",
        saveImage(
            bitmap,
            Environment.getExternalStorageDirectory().absolutePath + "/" + Environment.DIRECTORY_DOWNLOADS,
            title
        ) ?: return
    )
}

fun shareImage(title: String, bitmap: Bitmap, context: Context) {

    val contentUri = FileProvider.getUriForFile(
        context,
        BuildConfig.APPLICATION_ID + ".provider",
        saveImage(bitmap, context.cacheDir.absolutePath, title) ?: return
    )

    val intent = Intent(Intent.ACTION_SEND)
    intent.type = "image/png"
    intent.putExtra(Intent.EXTRA_TEXT, title)
    intent.putExtra(Intent.EXTRA_STREAM, contentUri)
    context.startActivity(Intent.createChooser(intent, "Share $title"))
}

fun saveImage(image: Bitmap, path: String, imageFileName: String): File? {
    val imageFile = File(path, "$imageFileName.png")
    return tryWith {
        val fOut: OutputStream = FileOutputStream(imageFile)
        image.compress(Bitmap.CompressFormat.PNG, 0, fOut)
        fOut.close()
        scanFile(imageFile.absolutePath, currActivity()!!)
        toast("Saved to:\n$path")
        imageFile
    }
}

private fun scanFile(path: String, context: Context) {
    MediaScannerConnection.scanFile(context, arrayOf(path), null) { p, _ ->
        logger("Finished scanning $p")
    }
}

fun updateAnilistProgress(media: Media, number: String) {
    if (Anilist.userid != null) {
        CoroutineScope(Dispatchers.IO).launch {
            val a = number.toFloatOrNull()?.roundToInt()
            if (a != media.userProgress) {
                Anilist.mutation.editList(
                    media.id,
                    a,
                    status = if (media.userStatus == "REPEATING") media.userStatus else "CURRENT"
                )
                toast("Setting progress to $a")
            }
            media.userProgress = a
            Refresh.all()
        }
    } else {
        toast("Please Login into anilist account!")
    }
}

class MediaPageTransformer : ViewPager2.PageTransformer {
    private fun parallax(view: View, position: Float) {
        if (position > -1 && position < 1) {
            val width = view.width.toFloat()
            view.translationX = -(position * width * 0.8f)
        }
    }

    override fun transformPage(view: View, position: Float) {

        val bannerContainer = view.findViewById<View>(R.id.itemCompactBanner)
        parallax(bannerContainer, position)
    }
}

class NoGestureSubsamplingImageView(context: Context?, attr: AttributeSet?) :
    SubsamplingScaleImageView(context, attr) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }
}

fun copyToClipboard(string: String, toast: Boolean = true) {
    val activity = currActivity() ?: return
    val clipboard = getSystemService(activity, ClipboardManager::class.java)
    val clip = ClipData.newPlainText("label", string)
    clipboard?.setPrimaryClip(clip)
    if (toast) toastString("Copied \"$string\"")
}

@SuppressLint("SetTextI18n")
fun countDown(media: Media, view: ViewGroup) {
    if (media.anime?.nextAiringEpisode != null && media.anime.nextAiringEpisodeTime != null && (media.anime.nextAiringEpisodeTime!! - System.currentTimeMillis() / 1000) <= 86400 * 7.toLong()) {
        val v = ItemCountDownBinding.inflate(LayoutInflater.from(view.context), view, false)
        view.addView(v.root, 0)
        v.mediaCountdownText.text = "Episode ${media.anime.nextAiringEpisode!! + 1} will be released in"
        object : CountDownTimer((media.anime.nextAiringEpisodeTime!! + 10000) * 1000 - System.currentTimeMillis(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val a = millisUntilFinished / 1000
                v.mediaCountdown.text =
                    "${a / 86400} days ${a % 86400 / 3600} hrs ${a % 86400 % 3600 / 60} mins ${a % 86400 % 3600 % 60} secs"
            }

            override fun onFinish() {
                v.mediaCountdownContainer.visibility = View.GONE
                toastString("Congrats Vro")
            }
        }.start()
    }
}

fun MutableMap<String, Genre>.checkId(id: Int): Boolean {
    this.forEach {
        if (it.value.id == id) {
            return false
        }
    }
    return true
}

fun MutableMap<String, Genre>.checkGenreTime(genre: String): Boolean {
    if (containsKey(genre))
        return (System.currentTimeMillis() - get(genre)!!.time) >= (1000 * 60 * 60 * 24 * 7)
    return true
}

fun setSlideIn(uiSettings: UserInterfaceSettings) = AnimationSet(false).apply {
    if (uiSettings.layoutAnimations) {
        var animation: Animation = AlphaAnimation(0.0f, 1.0f)
        animation.duration = (500 * uiSettings.animationSpeed).toLong()
        animation.interpolator = AccelerateDecelerateInterpolator()
        addAnimation(animation)

        animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 1.0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0f
        )

        animation.duration = (750 * uiSettings.animationSpeed).toLong()
        animation.interpolator = OvershootInterpolator(1.1f)
        addAnimation(animation)
    }
}

fun setSlideUp(uiSettings: UserInterfaceSettings) = AnimationSet(false).apply {
    if (uiSettings.layoutAnimations) {
        var animation: Animation = AlphaAnimation(0.0f, 1.0f)
        animation.duration = (500 * uiSettings.animationSpeed).toLong()
        animation.interpolator = AccelerateDecelerateInterpolator()
        addAnimation(animation)

        animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1.0f,
            Animation.RELATIVE_TO_SELF, 0f
        )

        animation.duration = (750 * uiSettings.animationSpeed).toLong()
        animation.interpolator = OvershootInterpolator(1.1f)
        addAnimation(animation)
    }
}

class EmptyAdapter(private val count: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return EmptyViewHolder(View(parent.context))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}

    override fun getItemCount(): Int = count

    inner class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

fun toast(string: String?, activity: Activity? = null) {
    if (string != null) {
        (activity ?: currActivity())?.apply {
            runOnUiThread {
                Toast.makeText(this, string, Toast.LENGTH_SHORT).show()
            }
        }
        logger(string)
    }
}

fun toastString(s: String?, activity: Activity? = null, clipboard: String? = null) {
    if (s != null) {
        (activity ?: currActivity())?.apply {
            if (!isOnTV(this)) {
                runOnUiThread {
                    val snackBar = Snackbar.make(
                        window.decorView.findViewById(android.R.id.content),
                        s,
                        Snackbar.LENGTH_LONG
                    )
                    snackBar.view.apply {
                        updateLayoutParams<FrameLayout.LayoutParams> {
                            gravity = (Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                            width = WRAP_CONTENT
                        }
                        translationY = -(navBarHeight.dp + 32f)
                        translationZ = 32f
                        updatePadding(16f.px, right = 16f.px)
                        setOnClickListener {
                            snackBar.dismiss()
                        }
                        setOnLongClickListener {
                            copyToClipboard(clipboard ?: s, false)
                            toast("Copied to Clipboard")
                            true
                        }
                    }
                    snackBar.show()
                }
            }
        }
        logger(s)
    }
}

open class NoPaddingArrayAdapter<T>(context: Context, layoutId: Int, items: List<T>) : ArrayAdapter<T>(context, layoutId, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        view.setPadding(0, view.paddingTop, view.paddingRight, view.paddingBottom)
        (view as TextView).setTextColor(Color.WHITE)
        return view
    }
}

@SuppressLint("ClickableViewAccessibility")
class SpinnerNoSwipe : androidx.appcompat.widget.AppCompatSpinner {
    private var mGestureDetector: GestureDetector? = null

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        mGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return performClick()
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mGestureDetector!!.onTouchEvent(event)
        return true
    }
}

@SuppressLint("RestrictedApi")
class CustomBottomNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BottomNavigationView(context, attrs) {
    init {
        ViewUtils.doOnApplyWindowInsets(
            this
        ) { view, insets, initialPadding ->
            initialPadding.bottom = 0
            updateLayoutParams<MarginLayoutParams> { bottomMargin = navBarHeight }
            initialPadding.applyToView(view)
            insets
        }
    }
}

fun getCurrentBrightnessValue(context: Context): Float {
    fun getMax(): Int {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val fields: Array<Field> = powerManager.javaClass.declaredFields
        for (field in fields) {
            if (field.name.equals("BRIGHTNESS_ON")) {
                field.isAccessible = true
                return try {
                    field.get(powerManager)?.toString()?.toInt() ?: 255
                } catch (e: IllegalAccessException) {
                    255
                }
            }
        }
        return 255
    }

    fun getCur(): Float {
        return Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 127).toFloat()
    }

    return brightnessConverter(getCur() / getMax(), true)
}

fun brightnessConverter(it: Float, fromLog: Boolean) =
    clamp(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            if (fromLog) log2((it * 256f)) * 12.5f / 100f else 2f.pow(it * 100f / 12.5f) / 256f
        else it, 0.001f, 1f
    )


fun checkCountry(context: Context): Boolean {
    val telMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return when (telMgr.simState) {
        TelephonyManager.SIM_STATE_ABSENT -> {
            val tz = TimeZone.getDefault().id
            tz.equals("Asia/Kolkata", ignoreCase = true)
        }
        TelephonyManager.SIM_STATE_READY  -> {
            val countryCodeValue = telMgr.networkCountryIso
            countryCodeValue.equals("in", ignoreCase = true)
        }
        else                              -> false
    }
}

fun isOnTV(activity: Activity): Boolean {
    val uiModeManager = activity.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}