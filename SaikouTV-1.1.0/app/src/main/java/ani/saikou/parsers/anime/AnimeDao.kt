package ani.saikou.parsers.anime

import android.net.Uri
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
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

class AnimeDao : AnimeParser() {
    override val name = "AnimeDao"
    override val saveName = "anime_dao_bz"
    override val hostUrl = "https://animedao.bz"
    override val isDubAvailableSeparately = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val res = client.get(animeLink).document
        return res.select(".episode_well_link").map {
            Episode(
                it.select(".anime-title").text().substringAfter("Episode "),
                hostUrl + it.attr("href")
            )
        }.reversed()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        var domain = Uri.parse(server.embed.url).host ?: return null
        if (domain.startsWith("www.")) {domain = domain.substring(4)}

        val extractor: VideoExtractor? = when (domain) {
            "filemoon.to", "filemoon.sx"  -> FileMoon(server)
            "rapid-cloud.co"              -> RapidCloud(server)
            "streamtape.com"              -> StreamTape(server)
            "vidstream.pro"               -> VidStreaming(server)
//            "mp4upload.com"               -> Mp4Upload(server)
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
    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        return client.get(episodeLink)
            .document
            .select(".anime_muti_link a")
            .map {
                VideoServer(it.text(), it.attr("data-video"))
            }
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return client.get("$hostUrl/search.html?keyword=$query${if(selectDub) " (Dub)" else ""}").document
            .select(".col-lg-4 a").map {
                ShowResponse(
                    it.attr("title"),
                    hostUrl + it.attr("href"),
                    it.select("img").attr("src")
                )
            }
    }
}