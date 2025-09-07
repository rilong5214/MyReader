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
    var locations: Locations? = null
    var text: HighlightText? = null
    var pre: String? = null
    var post: String? = null
    lateinit var highlight: HighlightImpl
    lateinit var textSelection: TextSelectionImpl
    var rank: Int = 0

    // Fields for SearchViewModel/SearchAdapter
    var searchItemType: SearchItemType? = null
    var primaryContents: String? = null // For search count or resource title or search result snippet

    // Main constructor
    constructor(
        href: String?,
        title: String?,
        locations: Locations?,
        text: HighlightText?,
        pre: String?,
        post: String?,
        highlight: HighlightImpl,
        textSelection: TextSelectionImpl,
        rank: Int,
        searchItemType: SearchItemType? = null, // Added
        primaryContents: String? = null      // Added
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
        this.searchItemType = searchItemType
        this.primaryContents = primaryContents
    }

    // Public no-arg constructor for SearchViewModel
    constructor() {
        // Initialize lateinit properties with defaults if necessary,
        // though SearchViewModel will set them.
        // For simplicity, we assume SearchViewModel will populate needed fields.
        // If HighlightImpl/TextSelectionImpl had no-arg constructors, they could be set here.
        // As they don't, this constructor relies on subsequent direct field assignments.
        // To make lateinit happy if these are NOT set by SearchViewModel immediately:
        if (!this::highlight.isInitialized) {
            this.highlight = HighlightImpl() // Assuming HighlightImpl() is a valid default
        }
        if (!this::textSelection.isInitialized) {
            this.textSelection = TextSelectionImpl() // Assuming TextSelectionImpl() is a valid default
        }
    }


    // Constructor for R2 Locator mapping in SearchViewModel
    constructor(
        originalLocator: org.readium.r2.shared.Locator,
        primaryContents: String?,
        searchItemType: SearchItemType?
    ) : this() { // Calls the public no-arg constructor first
        this.href = originalLocator.href
        this.title = originalLocator.title
        // We need to map originalLocator.locations and originalLocator.text
        // For now, let's keep them null or provide placeholders
        // this.locations = mapR2Locations(originalLocator.locations)
        // this.text = mapR2HighlightText(originalLocator.text)
        this.primaryContents = primaryContents
        this.searchItemType = searchItemType
        // highlight and textSelection will use defaults from no-arg constructor
        // or SearchViewModel can refine them.
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    constructor(parcel: Parcel) : this() {
        href = parcel.readString()
        title = parcel.readString()
        locations = parcel.readParcelable(Locations::class.java.classLoader)
        text = parcel.readParcelable(HighlightText::class.java.classLoader)
        pre = parcel.readString()
        post = parcel.readString()
        highlight = parcel.readSerializable(HighlightImpl::class.java.classLoader, HighlightImpl::class.java)
            ?: HighlightImpl()
        textSelection = parcel.readSerializable(TextSelectionImpl::class.java.classLoader, TextSelectionImpl::class.java)
            ?: TextSelectionImpl()
        rank = parcel.readInt()
        searchItemType = parcel.readSerializable(SearchItemType::class.java.classLoader, SearchItemType::class.java)
        primaryContents = parcel.readString()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(href)
        parcel.writeString(title)
        parcel.writeParcelable(locations, flags)
        parcel.writeParcelable(text, flags)
        parcel.writeString(pre)
        parcel.writeString(post)
        parcel.writeSerializable(highlight)
        parcel.writeSerializable(textSelection)
        parcel.writeInt(rank)
        parcel.writeSerializable(searchItemType)
        parcel.writeString(primaryContents)
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
            // This fromJson might need updates if searchItemType/primaryContents are in JSON
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

                val highlight: HighlightImpl = jsonObject.optJSONObject("highlight")?.let { HighlightImpl.fromJson(it) } ?: HighlightImpl()
                val textSelection: TextSelectionImpl = jsonObject.optJSONObject("textSelection")?.let { TextSelectionImpl.fromJson(it) } ?: TextSelectionImpl()
                val rank = jsonObject.optInt("rank")

                // Assuming searchItemType and primaryContents are not typically from this generic JSON
                SearchLocator(href, title, locations, text, pre, post, highlight, textSelection, rank, null, null)
            }
        }
    }
}
