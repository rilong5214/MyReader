package com.folioreader.ui.view

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.folioreader.Config
// Corrected R class import for appcompat views if necessary,
// but direct access to androidx.appcompat.R.id is more robust.
// import com.folioreader.R // This R is for the module's own resources
import com.folioreader.util.UiUtil

class FolioSearchView : SearchView {

    companion object {
        @JvmField
        val LOG_TAG: String = FolioSearchView::class.java.simpleName
    }

    private lateinit var searchAutoComplete: SearchView.SearchAutoComplete

    // Corrected constructors to take non-nullable Context
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun init(componentName: ComponentName, config: Config) {
        Log.v(LOG_TAG, "-> init")

        val searchManager: SearchManager = context.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        setSearchableInfo(searchManager.getSearchableInfo(componentName))
        setIconifiedByDefault(false)

        adjustLayout()
        applyTheme(config)
    }

    private fun adjustLayout() {
        Log.v(LOG_TAG, "-> adjustLayout")

        // Hide searchHintIcon by referencing the correct R class
        val searchMagIcon: View = findViewById(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.layoutParams = LinearLayout.LayoutParams(0, 0)

        // Remove left margin of search_edit_frame by referencing the correct R class
        val searchEditFrame: View = findViewById(androidx.appcompat.R.id.search_edit_frame)
        (searchEditFrame.layoutParams as ViewGroup.MarginLayoutParams).leftMargin = 0
    }

    private fun applyTheme(config: Config) {
        Log.v(LOG_TAG, "-> applyTheme")

        // Reference the correct R class
        val searchCloseButton: ImageView = findViewById(androidx.appcompat.R.id.search_close_btn)
        UiUtil.setColorIntToDrawable(config.themeColor, searchCloseButton.drawable)

        // Reference the correct R class
        searchAutoComplete = findViewById(androidx.appcompat.R.id.search_src_text)
        UiUtil.setEditTextCursorColor(searchAutoComplete, config.themeColor)
        UiUtil.setEditTextHandleColor(searchAutoComplete, config.themeColor)
        searchAutoComplete.highlightColor = ColorUtils.setAlphaComponent(config.themeColor, 85)
        if (config.isNightMode) {
            searchAutoComplete.setTextColor(ContextCompat.getColor(context, com.folioreader.R.color.night_title_text_color))
            searchAutoComplete.setHintTextColor(ContextCompat.getColor(context, com.folioreader.R.color.night_text_color))
        } else {
            searchAutoComplete.setHintTextColor(ContextCompat.getColor(context, com.folioreader.R.color.edit_text_hint_color))
        }
    }

    fun setDayMode() {
        // Assuming R.color.black is from your module
        searchAutoComplete.setTextColor(ContextCompat.getColor(context, com.folioreader.R.color.black))
    }

    fun setNightMode() {
        // Assuming R.color.white is from your module
        searchAutoComplete.setTextColor(ContextCompat.getColor(context, com.folioreader.R.color.white))
    }
}