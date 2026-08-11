package uz.teamwork.mehrgodriver.common

import android.content.Context
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.PASSWORD_SIZE
import uz.teamwork.mehrgodriver.common.Constants.PHONE_NUMBER_SIZE
import uz.teamwork.mehrgodriver.common.Constants.VERIFICATION_CODE_SIZE
import uz.teamwork.mehrgodriver.common.model.ValidateResult

object Validate {
    private var valid: Boolean? = null
    private var message: String? = null

    // Auth
    fun login(phoneNumber: String, password: String, context: Context): ValidateResult {
        when {
            phoneNumber.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_phone_number)
            }

            phoneNumber.length < PHONE_NUMBER_SIZE -> {
                valid = false
                message = context.getString(R.string.enter_full_phone_number)
            }

            password.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_password)
            }

            password.length < PASSWORD_SIZE -> {
                valid = false
                message =
                    context.getString(R.string.enter_password_part_1) + " $PASSWORD_SIZE " + context.getString(
                        R.string.enter_password_part_2
                    )
            }

            else -> {
                valid = true
            }
        }

        return ValidateResult(valid!!, message)
    }

    fun signUp(
        phoneNumber: String,
        firstName: String,
        fatherName: String,
        lastName: String,
        password: String,
        confirmPassword: String,
        context: Context
    ): ValidateResult {
        when {
            phoneNumber.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_phone_number)
            }

            phoneNumber.length < PHONE_NUMBER_SIZE -> {
                valid = false
                message = context.getString(R.string.enter_full_phone_number)
            }

            firstName.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_name)
            }

            fatherName.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_father_name)
            }

            lastName.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_last_name)
            }

            password.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_password)
            }

            password.length < PASSWORD_SIZE -> {
                valid = false
                message =
                    context.getString(R.string.enter_password_part_1) + " $PASSWORD_SIZE " + context.getString(
                        R.string.enter_password_part_2
                    )
            }

            confirmPassword.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_password_again)
            }

            password != confirmPassword -> {
                valid = false
                message = context.getString(R.string.check_password_same)
            }

            else -> {
                valid = true
            }
        }

        return ValidateResult(valid!!, message)
    }

    fun passwordRecovery(
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        context: Context
    ): ValidateResult {
        when {
            phoneNumber.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_phone_number)
            }

            phoneNumber.length < PHONE_NUMBER_SIZE -> {
                valid = false
                message = context.getString(R.string.enter_full_phone_number)
            }

            password.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_password)
            }

            password.length < PASSWORD_SIZE -> {
                valid = false
                message =
                    context.getString(R.string.enter_password_part_1) + " $PASSWORD_SIZE " + context.getString(
                        R.string.enter_password_part_2
                    )
            }

            confirmPassword.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_password_again)
            }

            password != confirmPassword -> {
                valid = false
                message = context.getString(R.string.check_password_same)
            }

            else -> {
                valid = true
            }
        }

        return ValidateResult(valid!!, message)
    }

    fun verifyCode(verifyCode: String, context: Context): ValidateResult {
        when {
            verifyCode.isEmpty() -> {
                valid = false
                message = context.getString(R.string.enter_code)

            }

            verifyCode.length < VERIFICATION_CODE_SIZE -> {
                valid = false
                message = context.getString(R.string.enter_full_code)
            }

            else -> {
                valid = true
            }
        }

        return ValidateResult(valid!!, message)
    }
}