package com.folioreader.model.locators

import android.os.Parcel
import android.os.Parcelable
import org.json.JSONObject

data class Locations(
    // Add fields that Locations should have, e.g.:
    var cfi: String? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(cfi)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("cfi", cfi)
        // Add other fields to JSON
        return jsonObject
    }

    companion object CREATOR : Parcelable.Creator<Locations> {
        override fun createFromParcel(parcel: Parcel): Locations {
            return Locations(parcel)
        }

        override fun newArray(size: Int): Array<Locations?> {
            return arrayOfNulls(size)
        }

        @JvmStatic
        fun fromJson(json: JSONObject?): Locations? {
            // Note: If json is null or "cfi" is not found/null, this returns null.
            // Adjust if a default Locations object should be returned instead.
            return json?.optString("cfi")?.let { Locations(it) }
            // Populate other fields from JSON if necessary
        }
    }
}