package ani.saikou.parsers.anime

import android.net.Uri
import ani.saikou.FileUrl
import ani.saikou.asyncMap
import ani.saikou.client
import ani.saikou.parsers.*
import ani.saikou.parsers.anime.extractors.ALions
import ani.saikou.parsers.anime.extractors.AWish
import ani.saikou.parsers.anime.extractors.DoodStream
import ani.saikou.parsers.anime.extractors.FileMoon
import ani.saikou.parsers.anime.extractors.GogoCDN
import ani.saikou.parsers.anime.extractors.Mp4Upload
import ani.saikou.parsers.anime.extractors.OkRu
import ani.saikou.parsers.anime.extractors.RapidCloud
import ani.saikou.parsers.anime.extractors.StreamSB
import ani.saikou.parsers.anime.extractors.StreamTape
import ani.saikou.parsers.anime.extractors.VidStreaming
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.net.URLEncoder

@Suppress("BlockingMethodInNonBlockingContext")
class Kaido : AnimeParser() {

    override val name = "Kaido"
    override val saveName = "kaido_to"
    override val hostUrl = "https://kaido.to"
    override val isDubAvailableSeparately = false

    private val header = mapOf("X-Requested-With" to "XMLHttpRequest", "referer" to hostUrl)

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val res = client.get("$hostUrl/ajax/episode/list/$animeLink", header).parsed<HtmlResponse>()
        val element = Jsoup.parse(res.html ?: return listOf())
        return element.select(".detail-infor-content > div > a").map {
            val title = it.attr("title")
            val num = it.attr("data-number").replace("\n", "")
            val id = it.attr("data-id")
            val filler = it.attr("class").contains("ssl-item-filler")

            Episode(number = num, link = id, title = title, isFiller = filler)
        }
    }

    private val embedHeaders = mapOf("referer" to "$hostUrl/")

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val res = client.get("$hostUrl/ajax/episode/servers?episodeId=$episodeLink", header).parsed<HtmlResponse>()
        val element = Jsoup.parse(res.html ?: return listOf())

        return element.select("div.server-item").asyncMap {
            val serverName = "${it.attr("data-type").uppercase()} - ${it.text()}"
            val link =
                client.get("$hostUrl/ajax/episode/sources?id=${it.attr("data-id")}", header).parsed<SourceResponse>().link
            VideoServer(serverName, FileUrl(link, embedHeaders))
        }
    }
    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        var domain = Uri.parse(server.embed.url).host ?: return null
        if (domain.startsWith("www.")) {domain = domain.substring(4)}

        val extractor: VideoExtractor? = when (domain) {
            "filemoon.to", "filemoon.sx"  -> FileMoon(server)
//            "rapid-cloud.co"              -> RapidCloud(server)
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


//    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
//        val domain = Uri.parse(server.embed.url).host ?: return null
//        val extractor: VideoExtractor? = when {
//            "Vidstream" in domain    -> AniWave.Extractor(server)
//            "MyCloud" in domain      -> AniWave.Extractor(server)
//            "Streamtape" in domain    -> StreamTape(server)
//            "Filemoon" in domain    -> FileMoon(server)
//            "Mp4upload" in domain     -> Mp4Upload(server)
//            else                 -> null
//        }
//        return extractor
//    }

    override suspend fun search(query: String): List<ShowResponse> {

        var url = URLEncoder.encode(query, "utf-8")
        if (query.startsWith("$!")) {
            val a = query.replace("$!", "").split(" | ")
            url = URLEncoder.encode(a[0], "utf-8") + a[1]
        }

        val document = client.get("${hostUrl}/search?keyword=$url").document

        return document.select(".film_list-wrap > .flw-item > .film-poster").map {
            val link = it.select("a").attr("data-id")
            val title = it.select("a").attr("title")
            val cover = it.select("img").attr("data-src")
            ShowResponse(title, link, FileUrl(cover))
        }
    }

    @Serializable
    data class SourceResponse(
        @SerialName("link") val link: String
    )

    @Serializable
    private data class HtmlResponse(
        @SerialName("status") val status: Boolean,
        @SerialName("html") val html: String? = null,
    )

}