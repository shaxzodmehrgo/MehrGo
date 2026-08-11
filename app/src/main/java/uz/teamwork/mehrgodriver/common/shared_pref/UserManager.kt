package uz.teamwork.mehrgodriver.common.shared_pref

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import uz.teamwork.mehrgodriver.domain.model.User

object UserManager {
    private const val USER_PREF = "myUserPref"
    private const val userKey = "my_user_key"

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson

    fun init(application: Application) {
        sharedPreferences = application.getSharedPreferences(USER_PREF, Context.MODE_PRIVATE)
        gson = Gson()
    }

    fun saveUser(user: User) {
        val myEdit = sharedPreferences.edit()

        val jsonString = gson.toJson(user)
        myEdit.putString(userKey, jsonString)
        myEdit.apply()
    }

    fun getUser(): User? {
        val value = sharedPreferences.getString(userKey, null)
        return gson.fromJson(value, User::class.java) ?: null
    }

    fun getUserId(): Int {
        val value = sharedPreferences.getString(userKey, null)
        val user = gson.fromJson(value, User::class.java) ?: null
        return user?.id ?: -1
    }

    fun getToken(): String? {
        val value = sharedPreferences.getString(userKey, null)
        val user = gson.fromJson(value, User::class.java) ?: null
        return user?.authKey
    }

    fun getBearerToken(): String {
        val value = sharedPreferences.getString(userKey, null)
        val user = gson.fromJson(value, User::class.java) ?: null
        return "Bearer ${user?.authKey}"
    }

    fun getStatusValue(): Int? {
        val value = sharedPreferences.getString(userKey, null)
        val user = gson.fromJson(value, User::class.java) ?: null
        return user?.status?.valueNumber
    }

    fun getBalance(): Int {
        val value = sharedPreferences.getString(userKey, null)
        val user = gson.fromJson(value, User::class.java) ?: null
        return user?.balance ?: 0
    }

    fun deleteUser() {
        val myEdit = sharedPreferences.edit()
        myEdit.clear().apply()
    }
}