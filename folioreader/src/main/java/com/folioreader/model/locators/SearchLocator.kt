package com.folioreader.model.locators

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import com.folioreader.model.highlight.HighlightImpl
import com.folioreader.model.highlight.TextSelectionImpl
import org.json.JSONObject

@Suppress("DEPRECATION")
class SearchLocator : Parcelable {
    var href: String? = null
    var title: String? = null
    var locations: Locations? = null // Now refers to local Locations.kt
    var text: HighlightText? = null    // Now refers to local HighlightText.kt
    var pre: String? = null
    var post: String? = null
    lateinit var highlight: HighlightImpl
    lateinit var textSelection: TextSelectionImpl
    var rank: Int = 0

    constructor(
        href: String?,
        title: String?,
        locations: Locations?,
        text: HighlightText?,
        pre: String?,
        post: String?,
        highlight: HighlightImpl, // Expects non-null
        textSelection: TextSelectionImpl, // Expects non-null
        rank: Int
    ) {
        this.href = href
        this.title = title
        this.locations = locations
        this.text = text
        this.pre = pre
        this.post = post
        this.highlight = highlight
        this.textSelection = textSelection
        this.rank = rank
    }

    private constructor() {
        // Default constructor for Parcelable, lateinit properties will be initialized by parcel.readSerializable or constructor
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    constructor(parcel: Parcel) : this() {
        href = parcel.readString()
        title = parcel.readString()
        locations = parcel.readParcelable(Locations::class.java.classLoader)
        text = parcel.readParcelable(HighlightText::class.java.classLoader)
        pre = parcel.readString()
        post = parcel.readString()
        // Ensure non-null assignment for lateinit properties
        highlight = parcel.readSerializable(HighlightImpl::class.java.classLoader, HighlightImpl::class.java)
            ?: throw NullPointerException("Null HighlightImpl from Parcel") // Or handle more gracefully
        textSelection = parcel.readSerializable(TextSelectionImpl::class.java.classLoader, TextSelectionImpl::class.java)
            ?: throw NullPointerException("Null TextSelectionImpl from Parcel") // Or handle more gracefully
        rank = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(href)
        parcel.writeString(this.title)
        parcel.writeParcelable(locations, flags)
        parcel.writeParcelable(text, flags)
        parcel.writeString(pre)
        parcel.writeString(post)
        parcel.writeSerializable(highlight)
        parcel.writeSerializable(textSelection)
        parcel.writeInt(rank)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SearchLocator> {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun createFromParcel(parcel: Parcel): SearchLocator {
            return SearchLocator(parcel)
        }

        override fun newArray(size: Int): Array<SearchLocator?> {
            return arrayOfNulls(size)
        }

        @JvmStatic
        fun fromJson(jsonString: String?): SearchLocator? {
            return if (jsonString == null) {
                null
            } else {
                val jsonObject = JSONObject(jsonString)
                val href = jsonObject.optString("href")
                val title = jsonObject.optString("title")

                val locationsJson = jsonObject.optJSONObject("locations")
                val locations = if (locationsJson != null) Locations.fromJson(locationsJson) else null

                val textJson = jsonObject.optJSONObject("text")
                val text = if (textJson != null) HighlightText.fromJson(textJson) else null

                val pre = jsonObject.optString("pre")
                val post = jsonObject.optString("post")

                // Corrected logic to ensure non-null for constructor
                val highlightJson = jsonObject.optJSONObject("highlight")
                val highlight: HighlightImpl = highlightJson?.let { HighlightImpl.fromJson(it) } ?: HighlightImpl() // Provides a default if null

                val textSelectionJson = jsonObject.optJSONObject("textSelection")
                val textSelection: TextSelectionImpl = textSelectionJson?.let { TextSelectionImpl.fromJson(it) } ?: TextSelectionImpl() // Provides a default if null

                val rank = jsonObject.optInt("rank")

                SearchLocator(href, title, locations, text, pre, post, highlight, textSelection, rank)
            }
        }
    }
}
