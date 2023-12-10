package ani.saikou.media

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.saikou.anilist.Anilist
import java.text.DateFormat
import java.util.*

class OtherDetailsViewModel : ViewModel() {
    private val character: MutableLiveData<Character> = MutableLiveData(null)
    fun getCharacter(): LiveData<Character> = character
    suspend fun loadCharacter(m: Character) {
        if (character.value == null) character.postValue(Anilist.query.getCharacterDetails(m))
    }

    private val studio: MutableLiveData<Studio> = MutableLiveData(null)
    fun getStudio(): LiveData<Studio> = studio
    suspend fun loadStudio(m: Studio) {
        if (studio.value == null) studio.postValue(Anilist.query.getStudioDetails(m))
    }

    private val calendar: MutableLiveData<Map<String,MutableList<Media>>> = MutableLiveData(null)
    fun getCalendar(): LiveData<Map<String,MutableList<Media>>> = calendar
    suspend fun loadCalendar() {
        val curr = System.currentTimeMillis()/1000
        val res = Anilist.query.recentlyUpdated(false,curr-86400,curr+(86400*6))
        val df = DateFormat.getDateInstance(DateFormat.FULL)
        val map = mutableMapOf<String,MutableList<Media>>()
        val idMap = mutableMapOf<String,MutableList<Int>>()
        res?.forEach {
            val v = it.relation?.split(",")?.map { i-> i.toLong() }!!
            val dateInfo = df.format(Date(v[1]*1000))
            val list = map.getOrPut(dateInfo) { mutableListOf() }
            val idList = idMap.getOrPut(dateInfo) { mutableListOf() }
            it.relation = "Episode ${v[0]}"
            if(!idList.contains(it.id)) {
                idList.add(it.id)
                list.add(it)
            }
        }
        calendar.postValue(map)
    }
}