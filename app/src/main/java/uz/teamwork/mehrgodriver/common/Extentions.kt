package uz.teamwork.mehrgodriver.common

import android.app.Activity
import android.widget.Toast
import androidx.fragment.app.Fragment
import uz.teamwork.mehrgodriver.common.services.AutoOfferService

fun Activity.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Fragment.showToast(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

fun AutoOfferService.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}