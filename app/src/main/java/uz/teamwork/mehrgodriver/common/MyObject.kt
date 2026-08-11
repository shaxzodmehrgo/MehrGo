package uz.teamwork.mehrgodriver.common

import android.os.Parcel
import android.os.Parcelable

data class MyObject(
    val id: Int,
    val name: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MyObject> {
        override fun createFromParcel(parcel: Parcel): MyObject {
            return MyObject(parcel)
        }

        override fun newArray(size: Int): Array<MyObject?> {
            return arrayOfNulls(size)
        }
    }
}