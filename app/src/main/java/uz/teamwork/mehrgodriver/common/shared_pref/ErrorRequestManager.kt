package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ErrorRequestManager {
    private const val ERROR_PREF = "myErrorPref"
    private const val errorKey = "my_error_key"

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(ERROR_PREF, Context.MODE_PRIVATE)
        gson = Gson()
    }

    fun <T> saveList(list: List<T>) {
        val editor = sharedPreferences.edit()

        val json = gson.toJson(list)
        editor.putString(errorKey, json)
        editor.apply()
    }

    fun <T> getList(typeToken: TypeToken<List<T>>): List<T>? {
        val json = sharedPreferences.getString(errorKey, null)
        return json?.let {
            gson.fromJson(it, typeToken.type)
        }
    }

    fun <T> addItemToList(item: T, typeToken: TypeToken<List<T>>) {
        val list = getList(typeToken)?.toMutableList() ?: mutableListOf()
        list.add(item)
        saveList(list)
    }

    fun clearAll() {
        val editor = sharedPreferences.edit()

        editor.clear()
        editor.apply()
    }
}