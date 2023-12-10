package ani.saikou.manga

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.R
import ani.saikou.anime.handleProgress
import ani.saikou.databinding.ItemAnimeWatchBinding
import ani.saikou.databinding.ItemChipBinding
import ani.saikou.loadData
import ani.saikou.loadImage
import ani.saikou.media.Media
import ani.saikou.media.SourceSearchDialogFragment
import ani.saikou.parsers.MangaReadSources
import ani.saikou.px
import com.google.android.material.chip.Chip
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MangaReadAdapter(
    private val media: Media,
    private val fragment: MangaReadFragment,
    private val mangaReadSources: MangaReadSources
) : RecyclerView.Adapter<MangaReadAdapter.ViewHolder>() {

    private var _binding: ItemAnimeWatchBinding? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val bind = ItemAnimeWatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(bind)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        _binding = binding

        //Source Selection
        val source = media.selected!!.source.let { if(it>=mangaReadSources.names.size) 0 else it }
        binding.animeSource.setText(mangaReadSources.names[source])
        mangaReadSources[source].apply {
            binding.animeSourceTitle.text = showUserText
            showUserTextListener = { MainScope().launch { binding.animeSourceTitle.text = it } }
        }
        binding.animeSource.setAdapter(ArrayAdapter(fragment.requireContext(), R.layout.item_dropdown, mangaReadSources.names))
        binding.animeSourceTitle.isSelected = true
        binding.animeSource.setOnItemClickListener { _, _, i, _ ->
            fragment.onSourceChange(i).apply {
                binding.animeSourceTitle.text = showUserText
                showUserTextListener = { MainScope().launch { binding.animeSourceTitle.text = it } }
            }
            fragment.loadChapters(i)
        }

        //Wrong Title
        binding.animeSourceSearch.setOnClickListener {
            SourceSearchDialogFragment().show(fragment.requireActivity().supportFragmentManager, null)
        }

        //Title
        binding.sourceTitle.setText(R.string.chaps)

        //Icons
        binding.animeSourceGrid.visibility = View.GONE
        var reversed = media.selected!!.recyclerReversed
        var style = media.selected!!.recyclerStyle ?: fragment.uiSettings.mangaDefaultView
        binding.animeSourceTop.rotation = if (reversed) -90f else 90f
        binding.animeSourceTop.setOnClickListener {
            reversed = !reversed
            binding.animeSourceTop.rotation = if (reversed) -90f else 90f
            fragment.onIconPressed(style, reversed)
        }
        var selected = when (style) {
            0 -> binding.animeSourceList
            1 -> binding.animeSourceCompact
            else -> binding.animeSourceList
        }
        selected.alpha = 1f
        fun selected(it: ImageView) {
            selected.alpha = 0.33f
            selected = it
            selected.alpha = 1f
        }
        binding.animeSourceList.setOnClickListener {
            selected(it as ImageView)
            style = 0
            fragment.onIconPressed(style, reversed)
        }
        binding.animeSourceCompact.setOnClickListener {
            selected(it as ImageView)
            style = 1
            fragment.onIconPressed(style, reversed)
        }

        //Chapter Handling
        handleChapters()
    }

    //Chips
    @SuppressLint("SetTextI18n")
    fun updateChips(limit: Int, names: Array<String>, arr: Array<Int>, selected: Int = 0) {
        val binding = _binding
        if (binding != null) {
            val screenWidth = fragment.screenWidth.px
            var select: Chip? = null
            for (position in arr.indices) {
                val last = if (position + 1 == arr.size) names.size else (limit * (position + 1))
                val chip =
                    ItemChipBinding.inflate(LayoutInflater.from(fragment.context), binding.animeSourceChipGroup, false).root
                chip.isCheckable = true
                fun selected() {
                    chip.isChecked = true
                    binding.animeWatchChipScroll.smoothScrollTo((chip.left - screenWidth / 2) + (chip.width / 2), 0)
                }
                chip.text = "${names[limit * (position)]} - ${names[last - 1]}"

                chip.setOnClickListener {
                    selected()
                    fragment.onChipClicked(position, limit * (position), last - 1)
                }
                binding.animeSourceChipGroup.addView(chip)
                if (selected == position) {
                    selected()
                    select = chip
                }
            }
            if (select != null)
                binding.animeWatchChipScroll.apply { post { scrollTo((select.left - screenWidth / 2) + (select.width / 2), 0) } }
        }
    }

    fun clearChips() {
        _binding?.animeSourceChipGroup?.removeAllViews()
    }

    @SuppressLint("SetTextI18n")
    fun handleChapters() {
        val binding = _binding
        if (binding != null) {
            if (media.manga?.chapters != null) {
                val chapters = media.manga.chapters!!.keys.toTypedArray()
                val anilistEp = (media.userProgress?:0).plus(1)
                val appEp = loadData<String>("${media.id}_current_chp")?.toIntOrNull() ?: 1
                var continueEp = (if(anilistEp>appEp) anilistEp else appEp).toString()
                if (chapters.contains(continueEp)) {
                    binding.animeSourceContinue.visibility = View.VISIBLE
                    handleProgress(
                        binding.itemEpisodeProgressCont,
                        binding.itemEpisodeProgress,
                        binding.itemEpisodeProgressEmpty,
                        media.id,
                        continueEp
                    )
                    if ((binding.itemEpisodeProgress.layoutParams as LinearLayout.LayoutParams).weight > 0.8f) {
                        val e = chapters.indexOf(continueEp)
                        if (e != -1 && e + 1 < chapters.size) {
                            continueEp = chapters[e + 1]
                        }
                    }
                    val ep = media.manga.chapters!![continueEp]!!
                    binding.itemEpisodeImage.loadImage(media.banner ?: media.cover)
                    binding.animeSourceContinueText.text =
                        "Continue : Chapter ${ep.number}${if (!ep.title.isNullOrEmpty()) "\n${ep.title}" else ""}"
                    binding.animeSourceContinue.setOnClickListener {
                        fragment.onMangaChapterClick(continueEp)
                    }
                    if (fragment.continueEp) {
                        if ((binding.itemEpisodeProgress.layoutParams as LinearLayout.LayoutParams).weight < 0.8f) {
                            binding.animeSourceContinue.performClick()
                            fragment.continueEp = false
                        }

                    }
                }
                else{
                    binding.animeSourceContinue.visibility = View.GONE
                }
                binding.animeSourceProgressBar.visibility = View.GONE
                if (media.manga.chapters!!.isNotEmpty())
                    binding.animeSourceNotFound.visibility = View.GONE
                else
                    binding.animeSourceNotFound.visibility = View.VISIBLE
            } else {
                binding.animeSourceContinue.visibility = View.GONE
                binding.animeSourceNotFound.visibility = View.GONE
                clearChips()
                binding.animeSourceProgressBar.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(val binding: ItemAnimeWatchBinding) : RecyclerView.ViewHolder(binding.root)
}