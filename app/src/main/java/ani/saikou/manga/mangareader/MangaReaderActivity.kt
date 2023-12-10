package ani.saikou.manga.mangareader

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.KeyEvent.*
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.math.MathUtils.clamp
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.saikou.*
import ani.saikou.anilist.Anilist
import ani.saikou.databinding.ActivityMangaReaderBinding
import ani.saikou.manga.MangaChapter
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.others.ImageViewDialog
import ani.saikou.others.getSerialized
import ani.saikou.parsers.HMangaSources
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaSources
import ani.saikou.settings.CurrentReaderSettings.Companion.applyWebtoon
import ani.saikou.settings.CurrentReaderSettings.Directions.*
import ani.saikou.settings.CurrentReaderSettings.DualPageModes.*
import ani.saikou.settings.CurrentReaderSettings.Layouts.*
import ani.saikou.settings.ReaderSettings
import ani.saikou.settings.UserInterfaceSettings
import com.alexvasilkov.gestures.views.GestureFrameLayout
import com.bumptech.glide.load.Transformation
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.math.min
import kotlin.properties.Delegates

@SuppressLint("SetTextI18n")
class MangaReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMangaReaderBinding
    private val model: MediaDetailsViewModel by viewModels()
    private val scope = lifecycleScope

    private lateinit var media: Media
    private lateinit var chapter: MangaChapter
    private lateinit var chapters: MutableMap<String, MangaChapter>
    private lateinit var chaptersArr: List<String>
    private lateinit var chaptersTitleArr: ArrayList<String>
    private var currentChapterIndex = 0

    private var isContVisible = false
    private var showProgressDialog = true
    private var progressDialog: AlertDialog.Builder? = null
    private var maxChapterPage = 0L
    private var currentChapterPage = 0L

    lateinit var settings: ReaderSettings
    lateinit var uiSettings: UserInterfaceSettings

    private var notchHeight: Int? = null

    private var imageAdapter: BaseImageAdapter? = null

    var sliding = false
    var isAnimating = false

    override fun onAttachedToWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !settings.showSystemBars) {
            val displayCutout = window.decorView.rootWindowInsets.displayCutout
            if (displayCutout != null) {
                if (displayCutout.boundingRects.size > 0) {
                    notchHeight = min(displayCutout.boundingRects[0].width(), displayCutout.boundingRects[0].height())
                    checkNotch()
                }
            }
        }
        super.onAttachedToWindow()
    }

    private fun checkNotch() {
        binding.mangaReaderTopLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = notchHeight ?: return
        }
    }

    private fun hideBars() {
        if (!settings.showSystemBars) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMangaReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mangaReaderBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        onBackPressedDispatcher.addCallback(this) {
            progress { finish() }
        }

        settings = loadData("reader_settings", this) ?: ReaderSettings().apply { saveData("reader_settings", this) }
        uiSettings = loadData("ui_settings", this) ?: UserInterfaceSettings().apply { saveData("ui_settings", this) }
        controllerDuration = (uiSettings.animationSpeed * 200).toLong()

        hideBars()

        var pageSliderTimer = Timer()
        fun pageSliderHide() {
            pageSliderTimer.cancel()
            pageSliderTimer.purge()
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    binding.mangaReaderCont.post {
                        sliding = false
                        handleController(false)
                    }
                }
            }
            pageSliderTimer = Timer()
            pageSliderTimer.schedule(timerTask, 3000)
        }

        binding.mangaReaderSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                sliding = true
                if (settings.default.layout != PAGED)
                    binding.mangaReaderRecycler.scrollToPosition((value.toInt() - 1) / (dualPage { 2 } ?: 1))
                else
                    binding.mangaReaderPager.currentItem = (value.toInt() - 1) / (dualPage { 2 } ?: 1)
                pageSliderHide()
            }
        }

        media = if (model.getMedia().value == null)
            try {
                (intent.getSerialized("media")) ?: return
            } catch (e: Exception) {
                logError(e)
                return
            }
        else model.getMedia().value ?: return
        model.setMedia(media)

        if (settings.autoDetectWebtoon && media.countryOfOrigin != "JP") applyWebtoon(settings.default)
        settings.default = loadData("${media.id}_current_settings") ?: settings.default

        chapters = media.manga?.chapters ?: return
        chapter = chapters[media.manga!!.selectedChapter] ?: return

        model.mangaReadSources = if (media.isAdult) HMangaSources else MangaSources
        binding.mangaReaderSource.visibility = if (settings.showSource) View.VISIBLE else View.GONE
        binding.mangaReaderSource.text = model.mangaReadSources!!.names[media.selected!!.source]

        binding.mangaReaderTitle.text = media.userPreferredName

        chaptersArr = chapters.keys.toList()
        currentChapterIndex = chaptersArr.indexOf(media.manga!!.selectedChapter)

        chaptersTitleArr = arrayListOf()
        chapters.forEach {
            val chapter = it.value
            chaptersTitleArr.add("${if (!chapter.title.isNullOrEmpty() && chapter.title != "null") "" else "Chapter "}${chapter.number}${if (!chapter.title.isNullOrEmpty() && chapter.title != "null") " : " + chapter.title else ""}")
        }

        showProgressDialog = if (settings.askIndividual) loadData<Boolean>("${media.id}_progressDialog") != true else false
        progressDialog =
            if (showProgressDialog && Anilist.userid != null && if (media.isAdult) settings.updateForH else true)
                AlertDialog.Builder(this, R.style.DialogTheme).setTitle("Update progress on anilist?").apply {
                    setMultiChoiceItems(
                        arrayOf("Don't ask again for ${media.userPreferredName}"),
                        booleanArrayOf(false)
                    ) { _, _, isChecked ->
                        if (isChecked) progressDialog = null
                        saveData("${media.id}_progressDialog", isChecked)
                        showProgressDialog = isChecked
                    }
                    setOnCancelListener { hideBars() }
                }
            else null

        //Chapter Change
        fun change(index: Int) {
            saveData("${media.id}_${chaptersArr[currentChapterIndex]}", currentChapterPage, this)
            maxChapterPage = 0
            media.manga!!.selectedChapter = chaptersArr[index]
            model.setMedia(media)
            scope.launch(Dispatchers.IO) { model.loadMangaChapterImages(chapters[chaptersArr[index]]!!, media.selected!!) }
        }

        //ChapterSelector
        binding.mangaReaderChapterSelect.adapter = NoPaddingArrayAdapter(this, R.layout.item_dropdown, chaptersTitleArr)
        binding.mangaReaderChapterSelect.setSelection(currentChapterIndex)
        binding.mangaReaderChapterSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (position != currentChapterIndex) change(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.mangaReaderSettings.setSafeOnClickListener {
            ReaderSettingsDialogFragment.newInstance().show(supportFragmentManager, "settings")
        }

        //Next Chapter
        binding.mangaReaderNextChap.setOnClickListener {
            binding.mangaReaderNextChapter.performClick()
        }
        binding.mangaReaderNextChapter.setOnClickListener {
            if (chaptersArr.size > currentChapterIndex + 1) progress { change(currentChapterIndex + 1) }
            else toastString("Next Chapter Not Found")
        }
        //Prev Chapter
        binding.mangaReaderPrevChap.setOnClickListener {
            binding.mangaReaderPreviousChapter.performClick()
        }
        binding.mangaReaderPreviousChapter.setOnClickListener {
            if (currentChapterIndex > 0) change(currentChapterIndex - 1)
            else toastString("This is the 1st Chapter!")
        }

        val chapterObserverRunnable = Runnable {
            model.getMangaChapter().observe(this) {
                if (it != null) {
                    chapter = it
                    media.selected = model.loadSelected(media)
                    saveData("${media.id}_current_chp", it.number, this)
                    currentChapterIndex = chaptersArr.indexOf(it.number)
                    binding.mangaReaderChapterSelect.setSelection(currentChapterIndex)
                    binding.mangaReaderNextChap.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: ""
                    binding.mangaReaderPrevChap.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: ""

                    applySettings()
                }
            }
        }
        chapterObserverRunnable.run()

        scope.launch(Dispatchers.IO) { model.loadMangaChapterImages(chapter, media.selected!!) }
    }

    private val snapHelper = PagerSnapHelper()

    private fun <T> dualPage(callback: () -> T): T? {
        return when (settings.default.dualPageMode) {
            No        -> null
            Automatic -> {
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) callback.invoke()
                else null
            }
            Force     -> callback.invoke()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun applySettings() {
        saveData("${media.id}_current_settings", settings.default)
        hideBars()

        //true colors
        SubsamplingScaleImageView.setPreferredBitmapConfig(
            if (settings.default.trueColors) Bitmap.Config.ARGB_8888
            else Bitmap.Config.RGB_565
        )

        //keep screen On
        if (settings.default.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.mangaReaderPager.unregisterOnPageChangeCallback(pageChangeCallback)

        currentChapterPage = loadData("${media.id}_${chapter.number}", this) ?: 1

        val chapImages = chapter.images

        if (!chapImages.isNullOrEmpty()) {
            maxChapterPage = chapImages.size.toLong()
            saveData("${media.id}_${chapter.number}_max", maxChapterPage)

            imageAdapter = dualPage { DualPageAdapter(this, chapter) } ?: ImageAdapter(this, chapter)

            if (chapImages.size > 1) {
                binding.mangaReaderSlider.apply {
                    visibility = View.VISIBLE
                    valueTo = maxChapterPage.toFloat()
                    value = clamp(currentChapterPage.toFloat(), 1f, valueTo)
                }
            } else {
                binding.mangaReaderSlider.visibility = View.GONE
            }
            binding.mangaReaderPageNumber.text =
                if (settings.default.hidePageNumbers) "" else "${currentChapterPage}/$maxChapterPage"

        }

        val currentPage = currentChapterPage.toInt()

        if ((settings.default.direction == TOP_TO_BOTTOM || settings.default.direction == BOTTOM_TO_TOP)) {
            binding.mangaReaderSwipy.vertical = true
            if (settings.default.direction == TOP_TO_BOTTOM) {
                binding.BottomSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: "No Chapter"
                binding.TopSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: "No Chapter"
                binding.mangaReaderSwipy.onTopSwiped = {
                    binding.mangaReaderPreviousChapter.performClick()
                }
                binding.mangaReaderSwipy.onBottomSwiped = {
                    binding.mangaReaderNextChapter.performClick()
                }
            } else {
                binding.BottomSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: "No Chapter"
                binding.TopSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: "No Chapter"
                binding.mangaReaderSwipy.onTopSwiped = {
                    binding.mangaReaderNextChapter.performClick()
                }
                binding.mangaReaderSwipy.onBottomSwiped = {
                    binding.mangaReaderPreviousChapter.performClick()
                }
            }
            binding.mangaReaderSwipy.topBeingSwiped = { value ->
                binding.TopSwipeContainer.apply {
                    alpha = value
                    translationY = -height.dp * (1 - min(value, 1f))
                }
            }
            binding.mangaReaderSwipy.bottomBeingSwiped = { value ->
                binding.BottomSwipeContainer.apply {
                    alpha = value
                    translationY = height.dp * (1 - min(value, 1f))
                }
            }
        }
        else {
            binding.mangaReaderSwipy.vertical = false
            if (settings.default.direction == RIGHT_TO_LEFT) {
                binding.LeftSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: "No Chapter"
                binding.RightSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: "No Chapter"
                binding.mangaReaderSwipy.onLeftSwiped = {
                    binding.mangaReaderNextChapter.performClick()
                }
                binding.mangaReaderSwipy.onRightSwiped = {
                    binding.mangaReaderPreviousChapter.performClick()
                }
            }
            else {
                binding.LeftSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex - 1) ?: "No Chapter"
                binding.RightSwipeText.text = chaptersTitleArr.getOrNull(currentChapterIndex + 1) ?: "No Chapter"
                binding.mangaReaderSwipy.onLeftSwiped = {
                    binding.mangaReaderPreviousChapter.performClick()
                }
                binding.mangaReaderSwipy.onRightSwiped = {
                    binding.mangaReaderNextChapter.performClick()
                }
            }
            binding.mangaReaderSwipy.leftBeingSwiped = { value ->
                binding.LeftSwipeContainer.apply {
                    alpha = value
                    translationX = -width.dp * (1 - min(value, 1f))
                }
            }
            binding.mangaReaderSwipy.rightBeingSwiped = { value ->
                binding.RightSwipeContainer.apply {
                    alpha = value
                    translationX = width.dp * (1 - min(value, 1f))
                }
            }
        }

        if (settings.default.layout != PAGED) {

            binding.mangaReaderRecyclerContainer.visibility = View.VISIBLE
            binding.mangaReaderRecyclerContainer.controller.settings.isRotationEnabled = settings.default.rotation

            val detector = GestureDetectorCompat(this, object : GesturesListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (binding.mangaReaderRecycler.findChildViewUnder(e.x, e.y).let { child ->
                            child ?: return@let false
                            val pos = binding.mangaReaderRecycler.getChildAdapterPosition(child)
                            val image = chapImages?.getOrNull(pos) ?: return@let false

                            onImageLongClicked(pos, image) { dialog ->
                                imageAdapter?.loadImage(pos, child as GestureFrameLayout)
                                binding.mangaReaderRecycler.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                dialog.dismiss()
                            }
                        }
                    ) binding.mangaReaderRecycler.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    super.onLongPress(e)
                }

                override fun onSingleClick(event: MotionEvent) {
                    handleController()
                }
            })

            val manager = PreloadLinearLayoutManager(
                this,
                if (settings.default.direction == TOP_TO_BOTTOM || settings.default.direction == BOTTOM_TO_TOP)
                    RecyclerView.VERTICAL
                else
                    RecyclerView.HORIZONTAL,
                !(settings.default.direction == TOP_TO_BOTTOM || settings.default.direction == LEFT_TO_RIGHT)
            )
            manager.preloadItemCount = 5

            binding.mangaReaderPager.visibility = View.GONE

            binding.mangaReaderRecycler.apply {
                clearOnScrollListeners()
                binding.mangaReaderSwipy.child = this
                adapter = imageAdapter
                layoutManager = manager
                setOnTouchListener { _, event ->
                    if(event!=null) detector.onTouchEvent(event) else false
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                        settings.default.apply {
                            if (
                                ((direction == TOP_TO_BOTTOM || direction == BOTTOM_TO_TOP)
                                        && (!v.canScrollVertically(-1) || !v.canScrollVertically(1)))
                                ||
                                ((direction == LEFT_TO_RIGHT || direction == RIGHT_TO_LEFT)
                                        && (!v.canScrollHorizontally(-1) || !v.canScrollHorizontally(1)))
                            ) {
                                handleController(true)
                            } else handleController(false)
                        }
                        updatePageNumber(manager.findLastVisibleItemPosition().toLong() * (dualPage { 2 } ?: 1) + 1)
                        super.onScrolled(v, dx, dy)
                    }
                })
                if ((settings.default.direction == TOP_TO_BOTTOM || settings.default.direction == BOTTOM_TO_TOP))
                    updatePadding(0, 128f.px, 0, 128f.px)
                else
                    updatePadding(128f.px, 0, 128f.px, 0)

                snapHelper.attachToRecyclerView(
                    if (settings.default.layout == CONTINUOUS_PAGED) this
                    else null
                )

                onVolumeUp = {
                    if ((settings.default.direction == TOP_TO_BOTTOM || settings.default.direction == BOTTOM_TO_TOP))
                        smoothScrollBy(0, -500)
                    else
                        smoothScrollBy(-500, 0)
                }

                onVolumeDown = {
                    if ((settings.default.direction == TOP_TO_BOTTOM || settings.default.direction == BOTTOM_TO_TOP))
                        smoothScrollBy(0, 500)
                    else
                        smoothScrollBy(500, 0)
                }

                scrollToPosition(currentPage - 1)
            }
        }
        else {
            binding.mangaReaderRecyclerContainer.visibility = View.GONE
            binding.mangaReaderPager.apply {
                binding.mangaReaderSwipy.child = this
                visibility = View.VISIBLE
                adapter = imageAdapter
                layoutDirection =
                    if (settings.default.direction == BOTTOM_TO_TOP || settings.default.direction == RIGHT_TO_LEFT)
                        View.LAYOUT_DIRECTION_RTL
                    else View.LAYOUT_DIRECTION_LTR
                orientation =
                    if (settings.default.direction == LEFT_TO_RIGHT || settings.default.direction == RIGHT_TO_LEFT)
                        ViewPager2.ORIENTATION_HORIZONTAL
                    else ViewPager2.ORIENTATION_VERTICAL
                registerOnPageChangeCallback(pageChangeCallback)
                offscreenPageLimit = 5

                setCurrentItem(currentPage - 1, false)
            }
            onVolumeUp = {
                binding.mangaReaderPager.currentItem -= 1
            }
            onVolumeDown = {
                binding.mangaReaderPager.currentItem += 1
            }
        }
    }

    private var onVolumeUp: (() -> Unit)? = null
    private var onVolumeDown: (() -> Unit)? = null
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KEYCODE_VOLUME_UP, KEYCODE_DPAD_UP, KEYCODE_PAGE_UP       -> {
                if (event.keyCode == KEYCODE_VOLUME_UP)
                    if (!settings.default.volumeButtons)
                        return false
                if (event.action == ACTION_DOWN) {
                    onVolumeUp?.invoke()
                    true
                } else false
            }
            KEYCODE_VOLUME_DOWN, KEYCODE_DPAD_DOWN, KEYCODE_PAGE_DOWN -> {
                if (event.keyCode == KEYCODE_VOLUME_DOWN)
                    if (!settings.default.volumeButtons)
                        return false
                if (event.action == ACTION_DOWN) {
                    onVolumeDown?.invoke()
                    true
                } else false
            }
            else                                                      -> {
                super.dispatchKeyEvent(event)
            }
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updatePageNumber(position.toLong() * (dualPage { 2 } ?: 1) + 1)
            handleController(position == 0 || position + 1 >= maxChapterPage)
            super.onPageSelected(position)
        }
    }

    private val overshoot = OvershootInterpolator(1.4f)
    private var controllerDuration by Delegates.notNull<Long>()
    private var goneTimer = Timer()
    fun gone() {
        goneTimer.cancel()
        goneTimer.purge()
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
                if (!isContVisible) binding.mangaReaderCont.post {
                    binding.mangaReaderCont.visibility = View.GONE
                    isAnimating = false
                }
            }
        }
        goneTimer = Timer()
        goneTimer.schedule(timerTask, controllerDuration)
    }

    fun handleController(shouldShow: Boolean? = null) {
        if (!sliding) {
            if (!settings.showSystemBars) {
                hideBars()
                checkNotch()
            }
            //horizontal scrollbar
            if (settings.default.horizontalScrollBar) {
                binding.mangaReaderSliderContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                }

                binding.mangaReaderSlider.apply {
                    updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    rotation = 0f
                }

            } else {
                binding.mangaReaderSliderContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    width = 48f.px
                }

                binding.mangaReaderSlider.apply {
                    updateLayoutParams {
                        width = binding.mangaReaderSliderContainer.height - 16f.px
                    }
                    rotation = 90f
                }
            }
            binding.mangaReaderSlider.layoutDirection =
                if (settings.default.direction == RIGHT_TO_LEFT || settings.default.direction == BOTTOM_TO_TOP)
                    View.LAYOUT_DIRECTION_RTL
                else View.LAYOUT_DIRECTION_LTR
            shouldShow?.apply { isContVisible = !this }
            if (isContVisible) {
                isContVisible = false
                if (!isAnimating) {
                    isAnimating = true
                    ObjectAnimator.ofFloat(binding.mangaReaderCont, "alpha", 1f, 0f).setDuration(controllerDuration).start()
                    ObjectAnimator.ofFloat(binding.mangaReaderBottomLayout, "translationY", 0f, 128f)
                        .apply { interpolator = overshoot;duration = controllerDuration;start() }
                    ObjectAnimator.ofFloat(binding.mangaReaderTopLayout, "translationY", 0f, -128f)
                        .apply { interpolator = overshoot;duration = controllerDuration;start() }
                }
                gone()
            } else {
                isContVisible = true
                binding.mangaReaderCont.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(binding.mangaReaderCont, "alpha", 0f, 1f).setDuration(controllerDuration).start()
                ObjectAnimator.ofFloat(binding.mangaReaderTopLayout, "translationY", -128f, 0f)
                    .apply { interpolator = overshoot;duration = controllerDuration;start() }
                ObjectAnimator.ofFloat(binding.mangaReaderBottomLayout, "translationY", 128f, 0f)
                    .apply { interpolator = overshoot;duration = controllerDuration;start() }
            }
        }
    }

    fun updatePageNumber(page: Long) {
        if (currentChapterPage != page) {
            currentChapterPage = page
            saveData("${media.id}_${chapter.number}", page, this)
            binding.mangaReaderPageNumber.text =
                if (settings.default.hidePageNumbers) "" else "${currentChapterPage}/$maxChapterPage"
            if (!sliding) binding.mangaReaderSlider.apply {
                value = clamp(currentChapterPage.toFloat(), 1f, valueTo)
            }
        }
        if (maxChapterPage - currentChapterPage <= 1) scope.launch(Dispatchers.IO) {
            model.loadMangaChapterImages(
                chapters[chaptersArr.getOrNull(currentChapterIndex + 1) ?: return@launch]!!,
                media.selected!!,
                false
            )
        }
    }

    private fun progress(runnable: Runnable) {
        if (maxChapterPage - currentChapterPage <= 1 && Anilist.userid != null) {
            if (showProgressDialog && progressDialog != null) {
                progressDialog?.setCancelable(false)
                    ?.setPositiveButton("Yes") { dialog, _ ->
                        saveData("${media.id}_save_progress", true)
                        updateAnilistProgress(media, media.manga!!.selectedChapter!!)
                        dialog.dismiss()
                        runnable.run()
                    }
                    ?.setNegativeButton("No") { dialog, _ ->
                        saveData("${media.id}_save_progress", false)
                        dialog.dismiss()
                        runnable.run()
                    }
                progressDialog?.show()
            } else {
                if (loadData<Boolean>("${media.id}_save_progress") != false && if (media.isAdult) settings.updateForH else true)
                    updateAnilistProgress(media, media.manga!!.selectedChapter!!)
                runnable.run()
            }
        } else {
            runnable.run()
        }
    }

    fun getTransformation(mangaImage: MangaImage): Transformation<File>? {
        return model.loadTransformation(mangaImage, media.selected!!.source)
    }

    fun onImageLongClicked(pos: Int, image: MangaImage, callback: ((ImageViewDialog) -> Unit)? = null): Boolean {
        if (!settings.default.longClickImage) return false
        val title = "(Page ${pos + 1}) ${chaptersTitleArr.getOrNull(currentChapterIndex)?.replace(" : "," - ") ?: ""} [${media.userPreferredName}]"

        ImageViewDialog.newInstance(title, image.url, true).apply {
            trans = getTransformation(image)
            onReloadPressed = callback
            show(supportFragmentManager, "image")
        }
        return true
    }
}