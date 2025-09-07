package com.folioreader.model.highlight

import java.util.*

interface Highlight {
    var id: Int
    var bookId: String
    var content: String
    var date: Date
    var pageIndex: Int
    var pageId: String
    var type: HighlightStyle
    var rangy: String
    var note: String
    var uuid: String
    var contentPre: String?
    var contentPost: String?

    enum class HighlightStyle(val styleIdentifier: String) {
        Yellow("highlight-yellow"),
        Green("highlight-green"),
        Blue("highlight-blue"),
        Pink("highlight-pink"),
        Underline("highlight-underline"),
        Normal(""); // For text selection that is not a highlight

        companion object {
            @JvmStatic
            fun fromString(styleIdentifier: String?): HighlightStyle? {
                return values().find { it.styleIdentifier == styleIdentifier }
            }
        }
    }

    // Companion object for constants (optional, but good practice)
    companion object {
        const val ID = "id"
        const val BOOK_ID = "book_id"
        const val CONTENT = "content"
        const val PAGE_INDEX = "page_index"
        const val PAGE_ID = "page_id"
        const val RANGY = "rangy"
        const val NOTE = "note"
        const val TYPE = "type"
        const val CREATED_DATE = "created_date"
        const val UUID = "uuid"
        const val CONTENT_PRE = "content_pre"
        const val CONTENT_POST = "content_post"
    }
}
