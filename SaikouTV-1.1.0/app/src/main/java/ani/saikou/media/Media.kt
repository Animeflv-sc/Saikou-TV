package ani.saikou.media

import ani.saikou.anilist.api.FuzzyDate
import ani.saikou.anilist.api.MediaEdge
import ani.saikou.anilist.api.MediaList
import ani.saikou.anilist.api.MediaType
import ani.saikou.anime.Anime
import ani.saikou.manga.Manga
import java.io.Serializable
import ani.saikou.anilist.api.Media as ApiMedia

data class Media(
    val anime: Anime? = null,
    val manga: Manga? = null,
    val id: Int,

    var idMAL: Int? = null,
    var typeMAL: String? = null,

    val name: String?,
    val nameRomaji: String,
    val userPreferredName: String,

    var cover: String? = null,
    val banner: String? = null,
    var relation: String? = null,
    var popularity: Int? = null,

    var isAdult: Boolean,
    var isFav: Boolean = false,
    var notify: Boolean = false,

    var userListId: Int? = null,
    var isListPrivate: Boolean = false,
    var userProgress: Int? = null,
    var userStatus: String? = null,
    var userScore: Int = 0,
    var userRepeat: Int = 0,
    var userUpdatedAt: Long? = null,
    var userStartedAt: FuzzyDate = FuzzyDate(),
    var userCompletedAt: FuzzyDate = FuzzyDate(),
    var inCustomListsOf: MutableMap<String, Boolean>?= null,
    var userFavOrder: Int? = null,

    val status: String? = null,
    var format: String? = null,
    var source: String? = null,
    var countryOfOrigin: String? = null,
    val meanScore: Int? = null,
    var genres: ArrayList<String> = arrayListOf(),
    var tags: ArrayList<String> = arrayListOf(),
    var description: String? = null,
    var synonyms: ArrayList<String> = arrayListOf(),
    var trailer: String? = null,
    var startDate: FuzzyDate? = null,
    var endDate: FuzzyDate? = null,

    var characters: ArrayList<Character>? = null,
    var prequel: Media? = null,
    var sequel: Media? = null,
    var relations: ArrayList<Media>? = null,
    var recommendations: ArrayList<Media>? = null,

    var vrvId: String? = null,
    var crunchySlug: String? = null,

    var nameMAL: String? = null,
    var shareLink: String? = null,
    var selected: Selected? = null,

    var cameFromContinue: Boolean = false
) : Serializable {

    constructor(apiMedia: ApiMedia) : this(
        id = apiMedia.id,
        idMAL = apiMedia.idMal,
        popularity = apiMedia.popularity,
        name = apiMedia.title!!.english,
        nameRomaji = apiMedia.title!!.romaji,
        userPreferredName = apiMedia.title!!.userPreferred,
        cover = apiMedia.coverImage?.large,
        banner = apiMedia.bannerImage,
        status = apiMedia.status.toString(),
        isFav = apiMedia.isFavourite!!,
        isAdult = apiMedia.isAdult ?: false,
        isListPrivate = apiMedia.mediaListEntry?.private ?: false,
        userProgress = apiMedia.mediaListEntry?.progress,
        userScore = apiMedia.mediaListEntry?.score?.toInt() ?: 0,
        userStatus = apiMedia.mediaListEntry?.status?.toString(),
        meanScore = apiMedia.meanScore,
        anime = if (apiMedia.type == MediaType.ANIME) Anime(
            totalEpisodes = apiMedia.episodes,
            nextAiringEpisode = apiMedia.nextAiringEpisode?.episode?.minus(1)
        ) else null,
        manga = if (apiMedia.type == MediaType.MANGA) Manga(totalChapters = apiMedia.chapters) else null,
    )

    constructor(mediaList: MediaList) : this(mediaList.media!!) {
        this.userProgress = mediaList.progress
        this.isListPrivate = mediaList.private ?: false
        this.userScore = mediaList.score?.toInt() ?: 0
        this.userStatus = mediaList.status?.toString()
        this.userUpdatedAt = mediaList.updatedAt?.toLong()
    }

    constructor(mediaEdge: MediaEdge) : this(mediaEdge.node!!) {
        this.relation = mediaEdge.relationType?.toString()
    }

    fun mainName() = nameMAL ?: name ?: nameRomaji
    fun alName() = name ?: nameRomaji
    fun mangaName() = if (countryOfOrigin != "JP") mainName() else nameRomaji
}