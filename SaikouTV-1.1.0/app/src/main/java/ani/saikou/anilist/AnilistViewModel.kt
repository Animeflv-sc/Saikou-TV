package ani.saikou.anilist

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.saikou.loadData
import ani.saikou.media.Media
import ani.saikou.others.AppUpdater
import ani.saikou.toastString


suspend fun getUserId(update: Runnable) {
    if (Anilist.userid == null && Anilist.token != null) {
        if (Anilist.query.getUserData())
            update.run()
        else
            toastString("Error loading Data")
    } else update.run()
}

class AnilistHomeViewModel : ViewModel() {
    private val listImages: MutableLiveData<ArrayList<String?>> = MutableLiveData<ArrayList<String?>>(arrayListOf())
    fun getListImages(): LiveData<ArrayList<String?>> = listImages
    suspend fun setListImages() = listImages.postValue(Anilist.query.getBannerImages())

    private val animeContinue: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getAnimeContinue(): LiveData<ArrayList<Media>> = animeContinue
    suspend fun setAnimeContinue() = animeContinue.postValue(Anilist.query.continueMedia("ANIME"))

    private val animeFav: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getAnimeFav(): LiveData<ArrayList<Media>> = animeFav
    suspend fun setAnimeFav() = animeFav.postValue(Anilist.query.favMedia(true))

    private val animePlanned: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getAnimePlanned(): LiveData<ArrayList<Media>> = animePlanned
    suspend fun setAnimePlanned() = animePlanned.postValue(Anilist.query.continueMedia("ANIME",true))

    private val mangaContinue: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getMangaContinue(): LiveData<ArrayList<Media>> = mangaContinue
    suspend fun setMangaContinue() = mangaContinue.postValue(Anilist.query.continueMedia("MANGA"))

    private val mangaFav: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getMangaFav(): LiveData<ArrayList<Media>> = mangaFav
    suspend fun setMangaFav() = mangaFav.postValue(Anilist.query.favMedia(false))

    private val mangaPlanned: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getMangaPlanned(): LiveData<ArrayList<Media>> = mangaPlanned
    suspend fun setMangaPlanned() = mangaPlanned.postValue(Anilist.query.continueMedia("MANGA",true))

    private val recommendation: MutableLiveData<ArrayList<Media>> = MutableLiveData<ArrayList<Media>>(null)
    fun getRecommendation(): LiveData<ArrayList<Media>> = recommendation
    suspend fun setRecommendation() = recommendation.postValue(Anilist.query.recommendations())

    suspend fun loadMain(context: FragmentActivity) {
        Anilist.getSavedToken(context)
        if(loadData<Boolean>("check_update") != false) AppUpdater.check(context)
        genres.postValue(Anilist.query.getGenresAndTags(context))
    }

    val empty = MutableLiveData<Boolean>(null)

    var loaded: Boolean = false
    val genres: MutableLiveData<Boolean?> = MutableLiveData(null)
}

class AnilistAnimeViewModel : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val type = "ANIME"
    private val trending: MutableLiveData<MutableList<Media>> = MutableLiveData<MutableList<Media>>(null)
    fun getTrending(): LiveData<MutableList<Media>> = trending
    suspend fun loadTrending(i: Int) {
        val (season, year) = Anilist.currentSeasons[i]
        trending.postValue(
            Anilist.query.search(
                type,
                perPage = 12,
                sort = "Trending",
                season = season,
                seasonYear = year,
                hd = true
            )?.results
        )
    }

    private val updated: MutableLiveData<MutableList<Media>> = MutableLiveData<MutableList<Media>>(null)
    fun getUpdated(): LiveData<MutableList<Media>> = updated
    suspend fun loadUpdated() = updated.postValue(Anilist.query.recentlyUpdated())

    private val animePopular = MutableLiveData<SearchResults?>(null)
    fun getPopular(): LiveData<SearchResults?> = animePopular
    suspend fun loadPopular(
        type: String,
        search_val: String? = null,
        genres: ArrayList<String>? = null,
        sort: String = "Popular",
        onList: Boolean = true,
    ) {
        animePopular.postValue(
            Anilist.query.search(
                type,
                search = search_val,
                onList = if (onList) null else false,
                sort = sort,
                genres = genres
            )
        )
    }


    suspend fun loadNextPage(r: SearchResults) = animePopular.postValue(
        Anilist.query.search(
            r.type,
            r.page + 1,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.format,
            r.isAdult,
            r.onList
        )
    )

    var loaded: Boolean = false
}

class AnilistMangaViewModel : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val type = "MANGA"
    private val trending: MutableLiveData<MutableList<Media>> = MutableLiveData<MutableList<Media>>(null)
    fun getTrending(): LiveData<MutableList<Media>> = trending
    suspend fun loadTrending() =
        trending.postValue(Anilist.query.search(type, perPage = 10, sort = "Trending", hd = true)?.results)

    private val updated: MutableLiveData<MutableList<Media>> = MutableLiveData<MutableList<Media>>(null)
    fun getTrendingNovel(): LiveData<MutableList<Media>> = updated
    suspend fun loadTrendingNovel() =
        updated.postValue(Anilist.query.search(type, perPage = 10, sort = "Trending", format = "NOVEL")?.results)

    private val mangaPopular = MutableLiveData<SearchResults?>(null)
    fun getPopular(): LiveData<SearchResults?> = mangaPopular
    suspend fun loadPopular(
        type: String,
        search_val: String? = null,
        genres: ArrayList<String>? = null,
        sort: String = "Popular",
        onList: Boolean = true,
    ) {
        mangaPopular.postValue(
            Anilist.query.search(
                type,
                search = search_val,
                onList = if (onList) null else false,
                sort = sort,
                genres = genres
            )
        )
    }


    suspend fun loadNextPage(r: SearchResults) = mangaPopular.postValue(
        Anilist.query.search(
            r.type,
            r.page + 1,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.format,
            r.isAdult,
            r.onList,
            r.excludedGenres,
            r.excludedTags,
            r.seasonYear,
            r.season
        )
    )

    var loaded: Boolean = false
}

class AnilistSearch : ViewModel() {
    var searched = false
    var notSet = true
    lateinit var searchResults: SearchResults
    private val result: MutableLiveData<SearchResults?> = MutableLiveData<SearchResults?>(null)

    fun getSearch(): LiveData<SearchResults?> = result
    suspend fun loadSearch(r: SearchResults) = result.postValue(
        Anilist.query.search(
            r.type,
            r.page,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.format,
            r.isAdult,
            r.onList,
            r.excludedGenres,
            r.excludedTags,
            r.seasonYear,
            r.season
        )
    )

    suspend fun loadNextPage(r: SearchResults) = result.postValue(
        Anilist.query.search(
            r.type,
            r.page + 1,
            r.perPage,
            r.search,
            r.sort,
            r.genres,
            r.tags,
            r.format,
            r.isAdult,
            r.onList,
            r.excludedGenres,
            r.excludedTags,
            r.seasonYear,
            r.season
        )
    )
}

class GenresViewModel : ViewModel() {
    var genres: MutableMap<String, String>? = null
    var done = false
    var doneListener: (() -> Unit)? = null
    suspend fun loadGenres(genre: ArrayList<String>, listener: (Pair<String, String>) -> Unit) {
        if (genres == null) {
            genres = mutableMapOf()
            Anilist.query.getGenres(genre) {
                genres!![it.first] = it.second
                listener.invoke(it)
                if (genres!!.size == genre.size) {
                    done = true
                    doneListener?.invoke()
                }
            }
        }
    }
}