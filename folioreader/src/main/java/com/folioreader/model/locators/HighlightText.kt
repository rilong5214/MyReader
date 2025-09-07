package com.folioreader.model.locators

import android.os.Parcel
import android.os.Parcelable
import org.json.JSONObject

data class HighlightText(
    // Add fields that HighlightText should have, e.g.:
    val selectedText: String? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(selectedText)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("selectedText", selectedText)
        // Add other fields to JSON
        return jsonObject
    }

    companion object CREATOR : Parcelable.Creator<HighlightText> {
        override fun createFromParcel(parcel: Parcel): HighlightText {
            return HighlightText(parcel)
        }

        override fun newArray(size: Int): Array<HighlightText?> {
            return arrayOfNulls(size)
        }

        @JvmStatic
        fun fromJson(json: JSONObject?): HighlightText? {
            // Note: If json is null or "selectedText" is not found/null, this returns null.
            // Adjust if a default HighlightText object should be returned instead.
            return json?.optString("selectedText")?.let { HighlightText(it) }
            // Populate other fields from JSON if necessary
        }
    }
}
