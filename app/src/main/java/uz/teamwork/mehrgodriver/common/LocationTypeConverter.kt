package uz.teamwork.mehrgodriver.common

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import uz.teamwork.mehrgodriver.common.model.MyLocation

class LocationTypeConverter {
    @TypeConverter
    fun fromLatLngList(value: List<MyLocation>): String {
        val gson = Gson()
        val type = object : TypeToken<List<MyLocation>>() {}.type
        val copyOfValue = ArrayList(value)
        return gson.toJson(copyOfValue, type)
    }

    @TypeConverter
    fun toLatLngList(value: String): List<MyLocation> {
        val gson = Gson()
        val type = object : TypeToken<List<MyLocation>>() {}.type
        return gson.fromJson(value, type)
    }
}