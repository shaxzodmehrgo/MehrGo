package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import uz.teamwork.mehrgodriver.common.Constants.GOOGLE

object MapTypeManager {
    private const val MAP_TYPE_PREF = "myMapTypePref"
    private const val mapTypeKey = "my_map_type_key"
    private const val mapChooseKey = "my_map_choose_key"

    lateinit var sharedPreferences: SharedPreferences

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(MAP_TYPE_PREF, MODE_PRIVATE)
    }

    // Map type
    fun saveType(data: String) {
        val myEdit = sharedPreferences.edit()
        myEdit.putString(mapTypeKey, data)
        myEdit.apply()
    }

    fun getType(): String {
        return sharedPreferences.getString(mapTypeKey, GOOGLE)!!
    }

    // Map choose
    fun saveChoose(mapChoose: Boolean) {
        val myEdit = IntroduceManager.sharedPreferences.edit()
        myEdit.putBoolean(mapChooseKey, mapChoose)
        myEdit.apply()
    }

    fun getChoose(): Boolean {
        // Default ON: auto-open the external navigator unless the driver has explicitly turned it
        // off (once they toggle it, that stored value wins over this default).
        return IntroduceManager.sharedPreferences.getBoolean(mapChooseKey, true)
    }
}