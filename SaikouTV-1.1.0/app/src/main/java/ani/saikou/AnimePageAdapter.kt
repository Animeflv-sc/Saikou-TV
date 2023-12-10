package ani.saikou

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.saikou.anilist.Anilist
import ani.saikou.databinding.ItemAnimePageBinding
import ani.saikou.media.CalendarActivity
import ani.saikou.media.MediaAdaptor
import ani.saikou.media.SearchActivity
import ani.saikou.settings.SettingsDialogFragment
import ani.saikou.settings.UserInterfaceSettings

class AnimePageAdapter : RecyclerView.Adapter<AnimePageAdapter.AnimePageViewHolder>() {
    val ready = MutableLiveData(false)
    lateinit var binding: ItemAnimePageBinding
    private var trendHandler: Handler? = null
    private lateinit var trendRun: Runnable
    var trendingViewPager: ViewPager2? = null
    private var uiSettings: UserInterfaceSettings = loadData("ui_settings") ?: UserInterfaceSettings()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimePageViewHolder {
        val binding = ItemAnimePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimePageViewHolder, position: Int) {
        binding = holder.binding
        trendingViewPager = binding.animeTrendingViewPager

        binding.animeTitleContainer.updatePadding(top = statusBarHeight)

        if (uiSettings.smallView) binding.animeTrendingContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = (-108f).px
        }

        updateAvatar()

        binding.animeSearchBar.hint = "ANIME"
        binding.animeSearchBarText.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, SearchActivity::class.java).putExtra("type", "ANIME"),
                null
            )
        }

        binding.animeSearchBar.setEndIconOnClickListener {
            binding.animeSearchBarText.performClick()
        }

        binding.animeUserAvatar.setSafeOnClickListener {
            SettingsDialogFragment().show((it.context as AppCompatActivity).supportFragmentManager, "dialog")
        }

        listOf(
            binding.animePreviousSeason,
            binding.animeThisSeason,
            binding.animeNextSeason
        ).forEachIndexed { i, it ->
            it.setSafeOnClickListener { onSeasonClick.invoke(i) }
            it.setOnLongClickListener { onSeasonLongClick.invoke(i) }
        }

        binding.animeGenreImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/anime/banner/16498-8jpFCOcDmneX.jpg")
        binding.animeCalendarImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/anime/banner/125367-hGPJLSNfprO3.jpg")

        binding.animeGenre.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, GenreActivity::class.java).putExtra("type", "ANIME"),
                null
            )
        }
        binding.animeCalendar.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, CalendarActivity::class.java),
                null
            )
        }

        binding.animeIncludeList.setOnCheckedChangeListener { _, isChecked ->
            onIncludeListClick.invoke(isChecked)
        }
        if (ready.value == false)
            ready.postValue(true)
    }

    lateinit var onSeasonClick : ((Int)->Unit)
    lateinit var onSeasonLongClick : ((Int)->Boolean)
    lateinit var onIncludeListClick : ((Boolean)->Unit)

    override fun getItemCount(): Int = 1

    fun updateHeight() {
        trendingViewPager!!.updateLayoutParams { height += statusBarHeight }
    }

    fun updateTrending(adaptor: MediaAdaptor) {
        binding.animeTrendingProgressBar.visibility = View.GONE
        binding.animeTrendingViewPager.adapter = adaptor
        binding.animeTrendingViewPager.offscreenPageLimit = 3
        binding.animeTrendingViewPager.getChildAt(0).overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        binding.animeTrendingViewPager.setPageTransformer(MediaPageTransformer())

        trendHandler = Handler(Looper.getMainLooper())
        trendRun = Runnable {
            binding.animeTrendingViewPager.currentItem = binding.animeTrendingViewPager.currentItem + 1
        }
        binding.animeTrendingViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    trendHandler!!.removeCallbacks(trendRun)
                    trendHandler!!.postDelayed(trendRun, 4000)
                }
            }
        )

        binding.animeTrendingViewPager.layoutAnimation = LayoutAnimationController(setSlideIn(uiSettings), 0.25f)
        binding.animeTitleContainer.startAnimation(setSlideUp(uiSettings))
        binding.animeListContainer.layoutAnimation = LayoutAnimationController(setSlideIn(uiSettings), 0.25f)
        binding.animeSeasonsCont.layoutAnimation = LayoutAnimationController(setSlideIn(uiSettings), 0.25f)
    }

    fun updateRecent(adaptor: MediaAdaptor) {
        binding.animeUpdatedProgressBar.visibility = View.GONE
        binding.animeUpdatedRecyclerView.adapter = adaptor
        binding.animeUpdatedRecyclerView.layoutManager =
            LinearLayoutManager(binding.animeUpdatedRecyclerView.context, LinearLayoutManager.HORIZONTAL, false)
        binding.animeUpdatedRecyclerView.visibility = View.VISIBLE

        binding.animeRecently.visibility = View.VISIBLE
        binding.animeRecently.startAnimation(setSlideUp(uiSettings))
        binding.animeUpdatedRecyclerView.layoutAnimation = LayoutAnimationController(setSlideIn(uiSettings), 0.25f)
        binding.animePopular.visibility = View.VISIBLE
        binding.animePopular.startAnimation(setSlideUp(uiSettings))
    }

    fun updateAvatar() {
        if (Anilist.avatar != null && ready.value == true) {
            binding.animeUserAvatar.loadImage(Anilist.avatar)
        }
    }

    inner class AnimePageViewHolder(val binding: ItemAnimePageBinding) : RecyclerView.ViewHolder(binding.root)
}
