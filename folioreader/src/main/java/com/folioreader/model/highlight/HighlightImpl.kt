package com.folioreader.model.highlight

import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.util.*

class HighlightImpl : Highlight, Serializable {
    override var id: Int = 0
    override var bookId: String = ""
    override var content: String = ""
    override var date: Date = Date()
    override var pageIndex: Int = 0
    override var pageId: String = ""
    override var type: Highlight.HighlightStyle = Highlight.HighlightStyle.Normal
    override var rangy: String = ""
    override var note: String = ""
    override var uuid: String = UUID.randomUUID().toString()
    override var contentPre: String? = null
    override var contentPost: String? = null

    constructor()

    constructor(
        id: Int,
        bookId: String,
        content: String,
        date: Date,
        pageIndex: Int,
        pageId: String,
        type: Highlight.HighlightStyle,
        rangy: String,
        note: String,
        uuid: String,
        contentPre: String?,
        contentPost: String?
    ) {
        this.id = id
        this.bookId = bookId
        this.content = content
        this.date = date
        this.pageIndex = pageIndex
        this.pageId = pageId
        this.type = type
        this.rangy = rangy
        this.note = note
        this.uuid = uuid
        this.contentPre = contentPre
        this.contentPost = contentPost
    }

    fun toJson(): JSONObject? {
        val jsonObject = JSONObject()
        try {
            jsonObject.put(Highlight.ID, id)
            jsonObject.put(Highlight.BOOK_ID, bookId)
            jsonObject.put(Highlight.CONTENT, content)
            jsonObject.put(Highlight.CREATED_DATE, date.time)
            jsonObject.put(Highlight.PAGE_INDEX, pageIndex)
            jsonObject.put(Highlight.PAGE_ID, pageId)
            jsonObject.put(Highlight.TYPE, type.styleIdentifier)
            jsonObject.put(Highlight.RANGY, rangy)
            jsonObject.put(Highlight.NOTE, note)
            jsonObject.put(Highlight.UUID, uuid)
            jsonObject.put(Highlight.CONTENT_PRE, contentPre)
            jsonObject.put(Highlight.CONTENT_POST, contentPost)
        } catch (e: JSONException) {
            // Log error or handle exception
            return null
        }
        return jsonObject
    }

    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject?): HighlightImpl? {
            if (json == null) return null
            val highlight = HighlightImpl()
            highlight.id = json.optInt(Highlight.ID)
            highlight.bookId = json.optString(Highlight.BOOK_ID)
            highlight.content = json.optString(Highlight.CONTENT)
            highlight.date = Date(json.optLong(Highlight.CREATED_DATE))
            highlight.pageIndex = json.optInt(Highlight.PAGE_INDEX)
            highlight.pageId = json.optString(Highlight.PAGE_ID)
            val style = Highlight.HighlightStyle.fromString(json.optString(Highlight.TYPE))
            if (style != null) {
                highlight.type = style
            }
            highlight.rangy = json.optString(Highlight.RANGY)
            highlight.note = json.optString(Highlight.NOTE)
            highlight.uuid = json.optString(Highlight.UUID, UUID.randomUUID().toString()) // Provide default if missing
            highlight.contentPre = json.optString(Highlight.CONTENT_PRE, null)
            highlight.contentPost = json.optString(Highlight.CONTENT_POST, null)
            return highlight
        }
    }
}
