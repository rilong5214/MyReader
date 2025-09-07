package com.folioreader.model.highlight

import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

class TextSelectionImpl : TextSelection, Serializable {
    override var id: Int = 0
    override var pageId: String = ""
    override var rangy: String = ""
    override var content: String = ""
    override var style: Highlight.HighlightStyle = Highlight.HighlightStyle.Normal

    constructor()

    constructor(
        id: Int,
        pageId: String,
        rangy: String,
        content: String,
        style: Highlight.HighlightStyle
    ) {
        this.id = id
        this.pageId = pageId
        this.rangy = rangy
        this.content = content
        this.style = style
    }

    fun toJson(): JSONObject? {
        val jsonObject = JSONObject()
        try {
            jsonObject.put(TextSelection.ID, id)
            jsonObject.put(TextSelection.PAGE_ID, pageId)
            jsonObject.put(TextSelection.RANGY, rangy)
            jsonObject.put(TextSelection.CONTENT, content)
            jsonObject.put(TextSelection.STYLE, style.styleIdentifier)
        } catch (e: JSONException) {
            // Log error or handle exception
            return null
        }
        return jsonObject
    }

    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject?): TextSelectionImpl? {
            if (json == null) return null
            val textSelection = TextSelectionImpl()
            textSelection.id = json.optInt(TextSelection.ID)
            textSelection.pageId = json.optString(TextSelection.PAGE_ID)
            textSelection.rangy = json.optString(TextSelection.RANGY)
            textSelection.content = json.optString(TextSelection.CONTENT)
            val styleEnum = Highlight.HighlightStyle.fromString(json.optString(TextSelection.STYLE))
            if (styleEnum != null) {
                textSelection.style = styleEnum
            }
            return textSelection
        }
    }
}
