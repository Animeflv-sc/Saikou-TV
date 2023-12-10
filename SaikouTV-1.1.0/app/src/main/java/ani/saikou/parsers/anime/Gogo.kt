package ani.saikou.parsers.anime

import android.net.Uri
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.DoodStream
import ani.saikou.parsers.anime.extractors.FileMoon
import ani.saikou.parsers.anime.extractors.GogoCDN
import ani.saikou.parsers.anime.extractors.Mp4Upload
import ani.saikou.parsers.anime.extractors.RapidCloud
import ani.saikou.parsers.anime.extractors.OkRu
import ani.saikou.parsers.anime.extractors.StreamSB
import ani.saikou.parsers.anime.extractors.StreamTape
import ani.saikou.parsers.anime.extractors.VidStreaming
import ani.saikou.parsers.anime.extractors.ALions
import ani.saikou.parsers.anime.extractors.AWish




class Gogo : AnimeParser() {
    override val name = "Gogo"
    override val saveName = "gogo_anime_hu"
    override val hostUrl = "https://gogoanime3.net"
    override val malSyncBackupName = "Gogoanime"
    override val isDubAvailableSeparately = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val list = mutableListOf<Episode>()

        val pageBody = client.get("$hostUrl/category/$animeLink").document
        val lastEpisode = pageBody.select("ul#episode_page > li:last-child > a").attr("ep_end").toString()
        val animeId = pageBody.select("input#movie_id").attr("value").toString()

        val epList = client
            .get("https://ajax.gogo-load.com/ajax/load-list-episode?ep_start=0&ep_end=$lastEpisode&id=$animeId").document
            .select("ul > li > a").reversed()
        epList.forEach {
            val num = it.select(".name").text().replace("EP", "").trim()
            list.add(Episode(num, hostUrl + it.attr("href").trim()))
        }

        return list
    }

    private fun httpsIfy(text: String): String {
        return if (text.take(2) == "//") "https:$text"
        else text
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val list = mutableListOf<VideoServer>()
        client.get(episodeLink).document.select("div.anime_muti_link > ul > li").forEach {
            val name = it.select("a").text().replace("Choose this server", "")
            val url = httpsIfy(it.select("a").attr("data-video"))
            val embed = FileUrl(url, mapOf("referer" to hostUrl))

            list.add(VideoServer(name, embed))
        }
        return list
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

    override suspend fun search(query: String): List<ShowResponse> {
        val encoded = encode(query + if (selectDub) " (Dub)" else "")
        val list = mutableListOf<ShowResponse>()
        client.get("$hostUrl/search.html?keyword=$encoded").document
            .select(".last_episodes > ul > li div.img > a").forEach {
                val link = it.attr("href").toString().replace("/category/", "")
                val title = it.attr("title")
                val cover = it.select("img").attr("src")
                list.add(ShowResponse(title, link, cover))
            }
        return list
    }
}
