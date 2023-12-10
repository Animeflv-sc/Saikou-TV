package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.defaultHeaders
import ani.saikou.findBetween
import ani.saikou.parsers.*
import ani.saikou.printIt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.random.Random

class StreamSB(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val videos = mutableListOf<Video>()
        val id = server.embed.url.let { it.findBetween("/e/", ".html") ?: it.findBetween("/embed-", ".html") ?: it.split("/e/")[1] }.printIt("id : ")
        val host = server.embed.url.findBetween("https://", "/")
        val jsonLink =
            "https://$host/375664356a494546326c4b797c7c6e756577776778623171737/${encode(id)}"
        val json = client.get(jsonLink, mapOf("Watchsb" to "sbstream")).parsed<Response>()
        if (json.statusCode == 200) {
            videos.add(Video(null, VideoType.M3U8, FileUrl(json.streamData!!.file, defaultHeaders)))
        }
        return VideoContainer(videos)
    }

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private fun encode(id: String): String {
        val code = "${makeId()}||${id}||${makeId()}||streamsb"
        val sb = StringBuilder()
        val arr = code.toCharArray()
        for (j in arr.indices) {
            sb.append(arr[j].code.toString().toInt(10).toString(16))
        }
        return sb.toString()
    }

    private fun makeId(): String {
        val sb = StringBuilder()
        for (j in 0..12) {
            sb.append(alphabet[(floor(Random.nextDouble() * alphabet.length)).toInt()])
        }
        return sb.toString()
    }

    @Serializable
    private data class Response(
        @SerialName("stream_data")
        val streamData: StreamData? = null,
        @SerialName("status_code")
        val statusCode: Int? = null
    )

    @Serializable
    private data class StreamData(
        @SerialName("file") val file: String
    )
}
