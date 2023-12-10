package ani.saikou.anilist.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Query{
    @Serializable
    data class Viewer(
        @SerialName("data")
        val data : Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Viewer")
            val user: ani.saikou.anilist.api.User?
        )
    }
    @Serializable
    data class Media(
        @SerialName("data")
        val data :  Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Media")
            val media: ani.saikou.anilist.api.Media?
        )
    }

    @Serializable
    data class Page(
        @SerialName("data")
        val data : Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Page")
            val page : ani.saikou.anilist.api.Page?
        )
    }
//    data class AiringSchedule(
//        val data : Data?
//    ){
//        data class Data(
//            val AiringSchedule: ani.saikou.anilist.api.AiringSchedule?
//        )
//    }

    @Serializable
    data class Character(
        @SerialName("data")
        val data :  Data?
    ){

        @Serializable
        data class Data(
            @SerialName("Character")
            val character: ani.saikou.anilist.api.Character?
        )
    }

    @Serializable
    data class Studio(
        @SerialName("data")
        val data: Data?
    ){
        @Serializable
        data class Data(
            @SerialName("Studio")
            val studio: ani.saikou.anilist.api.Studio?
        )
    }

//    data class MediaList(
//        val data: Data?
//    ){
//        data class Data(
//            val MediaList: ani.saikou.anilist.api.MediaList?
//        )
//    }

    @Serializable
    data class MediaListCollection(
        @SerialName("data")
        val data : Data?
    ){
        @Serializable
        data class Data(
            @SerialName("MediaListCollection")
            val mediaListCollection: ani.saikou.anilist.api.MediaListCollection?
        )
    }

    @Serializable
    data class GenreCollection(
        @SerialName("data")
        val data: Data
    ){
        @Serializable
        data class Data(
            @SerialName("GenreCollection")
            val genreCollection: List<String>?
        )
    }

    @Serializable
    data class MediaTagCollection(
        @SerialName("data")
        val data: Data
    ){
        @Serializable
        data class Data(
            @SerialName("MediaTagCollection")
            val mediaTagCollection: List<MediaTag>?
        )
    }

    @Serializable
    data class User(
        @SerialName("data")
        val data: Data
    ){
        @Serializable
        data class Data(
            @SerialName("User")
            val user: ani.saikou.anilist.api.User?
        )
    }
}

//data class WhaData(
//    val Studio: Studio?,
//
//    // Follow query
//    val Following: User?,
//
//    // Follow query
//    val Follower: User?,
//
//    // Thread query
//    val Thread: Thread?,
//
//    // Recommendation query
//    val Recommendation: Recommendation?,
//
//    // Like query
//    val Like: User?,

//    // Review query
//    val Review: Review?,
//
//    // Activity query
//    val Activity: ActivityUnion?,
//
//    // Activity reply query
//    val ActivityReply: ActivityReply?,

//    // Comment query
//    val ThreadComment: List<ThreadComment>?,

//    // Notification query
//    val Notification: NotificationUnion?,

//    // Media Trend query
//    val MediaTrend: MediaTrend?,

//    // Provide AniList markdown to be converted to html (Requires auth)
//    val Markdown: ParsedMarkdown?,

//    // SiteStatistics: SiteStatistics
//    val AniChartUser: AniChartUser?,
//)
