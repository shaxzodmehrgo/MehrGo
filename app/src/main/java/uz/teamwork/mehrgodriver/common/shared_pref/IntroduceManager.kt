package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

object IntroduceManager {
    private const val INTRODUCE_PREF = "myIntroducePref"
    private const val introduceKey = "my_introduce_key"

    lateinit var sharedPreferences: SharedPreferences

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(INTRODUCE_PREF, Context.MODE_PRIVATE)
    }

    fun saveIntroduce(introduce: Boolean) {
        val myEdit = sharedPreferences.edit()
        myEdit.putBoolean(introduceKey, introduce)
        myEdit.apply()
    }

    fun getIntroduce(): Boolean? {
        return sharedPreferences.getBoolean(introduceKey, false) ?: null
    }
}