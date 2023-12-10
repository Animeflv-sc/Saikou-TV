package ani.saikou.parsers

import android.net.Uri
import ani.saikou.*
import ani.saikou.others.MalSyncBackup
import ani.saikou.parsers.anime.extractors.ALions
import ani.saikou.parsers.anime.extractors.AWish
import ani.saikou.parsers.anime.extractors.DoodStream
import ani.saikou.parsers.anime.extractors.FileMoon
import ani.saikou.parsers.anime.extractors.GogoCDN
import ani.saikou.parsers.anime.extractors.Mp4Upload
import ani.saikou.parsers.anime.extractors.OkRu
import ani.saikou.parsers.anime.extractors.RapidCloud
import ani.saikou.parsers.anime.extractors.StreamTape
import ani.saikou.parsers.anime.extractors.VidStreaming
import kotlin.properties.Delegates

/**
 * An abstract class for creating a new Source
 *
 * Most of the functions & variables that need to be overridden are abstract
 * **/
abstract class AnimeParser : BaseParser() {

    /**
     * Takes ShowResponse.link & ShowResponse.extra (if you added any) as arguments & gives a list of total episodes present on the site.
     * **/
    abstract suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode>

    /**
     * Takes Episode.link as a parameter
     *
     * This returns a Map of "Video Server's Name" & "Link/Data" of all the Video Servers present on the site, which can be further used by loadVideoServers() & loadSingleVideoServer()
     * **/
    abstract suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer>


    /**
     * This function will receive **url of the embed** & **name** of a Video Server present on the site to host the episode.
     *
     *
     * Create a new VideoExtractor for the video server you are trying to scrape, if there's not one already.
     *
     *
     * (Some sites might not have separate video hosts. In that case, just create a new VideoExtractor for that particular site)
     *
     *
     * returns a **VideoExtractor** containing **`server`**, the app will further load the videos using `extract()` function inside it
     *
     * **Example for Site with multiple Video Servers**
     * ```
    val domain = Uri.parse(server.embed.url).host ?: ""
    val extractor: VideoExtractor? = when {
    "fembed" in domain   -> FPlayer(server)
    "sb" in domain       -> StreamSB(server)
    "streamta" in domain -> StreamTape(server)
    else                 -> null
    }
    return extractor
    ```
     * You can use your own way to get the Extractor for reliability.
     * if there's only extractor, you can directly return it.
     * **/

    open suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        var domain = Uri.parse(server.embed.url).host ?: return null
        if (domain.startsWith("www.")) {domain = domain.substring(4)}

        val extractor: VideoExtractor? = when (domain) {
            "filemoon.to", "filemoon.sx"  -> FileMoon(server)
            "rapid-cloud.co"              -> RapidCloud(server)
            "streamtape.com"              -> StreamTape(server)
            "vidstream.pro"               -> VidStreaming(server)
            "mp4upload.com"               -> Mp4Upload(server)
            "playtaku.net","goone.pro"    -> GogoCDN(server)
            "alions.pro"                  -> ALions(server)
            "awish.pro"                   -> AWish(server)
            "dood.wf"                     -> DoodStream(server)
            "ok.ru"                       -> OkRu(server)
            "streamlare.com"              -> null // streamlare.com/e/vJ41zYN1aQblwA3g
            else                          -> {
                println("$name : No extractor found for: $domain | ${server.embed.url}")
                null
            }
        }

        return extractor
    }



    /**
     * If the Video Servers support preloading links for the videos
     * typically depends on what Video Extractor is being used
     * **/
    open val allowsPreloading = true

    /**
     * This Function used when there "isn't" a default Server set by the user, or when user wants to switch the Server
     *
     * Doesn't need to be overridden, if the parser is following the norm.
     * **/

    open suspend fun loadByVideoServers(episodeUrl: String, extra: Any?, callback: (VideoExtractor) -> Unit) {
        tryWithSuspend {
            loadVideoServers(episodeUrl, extra).asyncMap {
                getVideoExtractor(it)?.apply {
                    tryWithSuspend {
                        load()
                    }
                    callback.invoke(this)
                }
            }
        }
    }

    /**
     * This Function used when there "is" a default Server set by the user, only loads a Single Server for faster response.
     *
     * Doesn't need to be overridden, if the parser is following the norm.
     * **/
    open suspend fun loadSingleVideoServer(serverName: String, episodeUrl: String, extra: Any?): VideoExtractor? {
        return tryWithSuspend {
            loadVideoServers(episodeUrl, extra).apply {
                find { it.name == serverName }?.also {
                    return@tryWithSuspend getVideoExtractor(it)?.apply {
                        load()
                    }
                }
            }
            null
        }
    }


    /**
     * Many sites have Dub & Sub anime as separate Shows
     *
     * make this `true`, if they are separated else `false`
     *
     * **NOTE : do not forget to override `search` if the site does not support only dub search**
     * **/
    open val isDubAvailableSeparately by Delegates.notNull<Boolean>()

    /**
     * The app changes this, depending on user's choice.
     * **/
    open var selectDub = false

    /**
     * Name used to get Shows Directly from MALSyncBackup's github dump
     *
     * Do not override if the site is not present on it.
     * **/
    open val malSyncBackupName = ""

    /**
     * Overridden to add MalSyncBackup support for Anime Sites
     * **/
    override suspend fun loadSavedShowResponse(mediaId: Int): ShowResponse? {
        checkIfVariablesAreEmpty()
        val dub = if (isDubAvailableSeparately) "_${if (selectDub) "dub" else "sub"}" else ""
        var loaded = loadData<ShowResponse>("${saveName}${dub}_$mediaId")
        if (loaded == null && malSyncBackupName.isNotEmpty())
            loaded = MalSyncBackup.get(mediaId, malSyncBackupName, selectDub)?.also { saveShowResponse(mediaId, it, true) }
        return loaded
    }

    override fun saveShowResponse(mediaId: Int, response: ShowResponse?, selected: Boolean) {
        if (response != null) {
            checkIfVariablesAreEmpty()
            setUserText("${if (selected) "Selected" else "Found"} : ${response.name}")
            val dub = if (isDubAvailableSeparately) "_${if (selectDub) "dub" else "sub"}" else ""
            saveData("${saveName}${dub}_$mediaId", response)
        }
    }
}

/**
 * A class for containing Episode data of a particular parser
 * **/
data class Episode(
    /**
     * Number of the Episode in "String",
     *
     * useful in cases where episode is not a number
     * **/
    val number: String,

    /**
     * Link that links to the episode page containing videos
     * **/
    val link: String,

    //Self-Descriptive
    val title: String? = null,
    val thumbnail: FileUrl? = null,
    val description: String? = null,
    val isFiller: Boolean = false,

    /**
     * In case, you want to pass extra data
     * **/
    val extra: Any? = null,
) {
    constructor(
        number: String,
        link: String,
        title: String? = null,
        thumbnail: String,
        description: String? = null,
        isFiller: Boolean = false,
        extra: Any? = null
    ) : this(number, link, title, FileUrl(thumbnail), description, isFiller, extra)
}