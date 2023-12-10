package ani.saikou.parsers.anime.extractors

import ani.saikou.client
import ani.saikou.findBetween
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType

class VidStreaming(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        if(server.embed.url.contains("srcd")) {
            val link = client.get(server.embed.url).text.findBetween("\"file\": '", "',")!!
            return VideoContainer(listOf(Video(null, VideoType.M3U8, link, null)))
        }
        val url = client.get(server.embed.url).document.select("iframe").attr("src")
        if(url.contains("filemoon")) {
            return FileMoon(VideoServer("FileMoon", url)).extract()
        }
        return GogoCDN(VideoServer("GogoCDN", url)).extract()
    }
}