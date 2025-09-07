package com.folioreader.model.highlight

interface TextSelection {
    var id: Int
    var pageId: String
    var rangy: String
    var content: String
    var style: Highlight.HighlightStyle // Use the enum from Highlight interface

    // Companion object for constants
    companion object {
        const val ID = "id"
        const val PAGE_ID = "page_id"
        const val RANGY = "rangy"
        const val CONTENT = "content"
        const val STYLE = "style"
    }
}
