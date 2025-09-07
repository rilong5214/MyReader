package com.folioreader.model.locators

import android.os.Parcel
import android.os.Parcelable
import com.folioreader.model.highlight.HighlightImpl // Corrected: ensure this path is right
import com.folioreader.model.highlight.TextSelectionImpl // Corrected: ensure this path is right
import com.folioreader.util.AppUtil // Keep if used
import org.json.JSONObject

@Suppress("DEPRECATION")
class ReadLocator : Parcelable {

    var href: String? = null
    var created: Long = 0
    var locations: Locations? = null // Now refers to local Locations.kt
    var text: HighlightText? = null    // Now refers to local HighlightText.kt
    var highlight: HighlightImpl? = null
    var textSelection: TextSelectionImpl? = null
    var rangy: String? = null
    var bookId: String? = null
    var pageId: String? = null
    var pageNo = 0
    var note: String? = null

    constructor()

    constructor(href: String?, created: Long, locations: Locations?, text: HighlightText?) {
        this.href = href
        this.created = created
        this.locations = locations
        this.text = text
    }

    constructor(parcel: Parcel) : this() {
        href = parcel.readString()
        created = parcel.readLong()
        locations = parcel.readParcelable(Locations::class.java.classLoader)
        text = parcel.readParcelable(HighlightText::class.java.classLoader)
        highlight = parcel.readSerializable(HighlightImpl::class.java.classLoader, HighlightImpl::class.java) as HighlightImpl?
        textSelection = parcel.readSerializable(TextSelectionImpl::class.java.classLoader, TextSelectionImpl::class.java) as TextSelectionImpl?
        rangy = parcel.readString()
        bookId = parcel.readString()
        pageId = parcel.readString()
        pageNo = parcel.readInt()
        note = parcel.readString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(href)
        dest.writeLong(created)
        dest.writeParcelable(locations, flags)
        dest.writeParcelable(text, flags)
        dest.writeSerializable(highlight)
        dest.writeSerializable(textSelection)
        dest.writeString(rangy)
        dest.writeString(bookId)
        dest.writeString(pageId)
        dest.writeInt(pageNo)
        dest.writeString(note)
    }

    fun toJson(): JSONObject? {
        val jsonObject = JSONObject()
        jsonObject.put("href", href)
        jsonObject.put("created", created)
        locations?.let { jsonObject.put("locations", it.toJson()) }
        text?.let { jsonObject.put("text", it.toJson()) }
        highlight?.let { jsonObject.put("highlight", it.toJson()) }
        textSelection?.let { jsonObject.put("textSelection", it.toJson()) }
        jsonObject.put("rangy", rangy)
        jsonObject.put("bookId", bookId)
        jsonObject.put("pageId", pageId)
        jsonObject.put("pageNo", pageNo)
        jsonObject.put("note", note)
        return jsonObject
    }

    override fun toString(): String {
        return AppUtil.toJson(this)
    }

    companion object CREATOR : Parcelable.Creator<ReadLocator> {
        override fun createFromParcel(parcel: Parcel): ReadLocator {
            return ReadLocator(parcel)
        }

        override fun newArray(size: Int): Array<ReadLocator?> {
            return arrayOfNulls(size)
        }

        @JvmStatic
        fun fromJson(jsonString: String?): ReadLocator? {
            return if (jsonString == null) {
                null
            } else {
                val jsonObject = JSONObject(jsonString)
                val readLocator = ReadLocator()
                readLocator.href = jsonObject.optString("href")
                readLocator.created = jsonObject.optLong("created")

                val locationsJson = jsonObject.optJSONObject("locations")
                if (locationsJson != null) {
                    readLocator.locations = Locations.fromJson(locationsJson)
                }

                val textJson = jsonObject.optJSONObject("text")
                if (textJson != null) {
                    readLocator.text = HighlightText.fromJson(textJson)
                }
                val highlightJson = jsonObject.optJSONObject("highlight")
                if (highlightJson != null) {
                    readLocator.highlight = HighlightImpl.fromJson(highlightJson)
                }

                val textSelectionJson = jsonObject.optJSONObject("textSelection")
                if (textSelectionJson != null) {
                    readLocator.textSelection = TextSelectionImpl.fromJson(textSelectionJson)
                }

                readLocator.rangy = jsonObject.optString("rangy")
                readLocator.bookId = jsonObject.optString("bookId")
                readLocator.pageId = jsonObject.optString("pageId")
                readLocator.pageNo = jsonObject.optInt("pageNo")
                readLocator.note = jsonObject.optString("note")
                readLocator
            }
        }
    }
}
