@file:Suppress("DEPRECATION", "OverridingDeprecatedMember", "OVERRIDE_DEPRECATION", "REDUNDANT_ELSE_IN_WHEN", "KotlinConstantConditions")
package com.folioreader.ui.view

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.ActionMode.Callback
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.folioreader.Config
import com.folioreader.Constants
import com.folioreader.R
import com.folioreader.databinding.TextSelectionBinding
import com.folioreader.model.DisplayUnit
import com.folioreader.model.HighLight
import com.folioreader.model.HighlightImpl.HighlightStyle
import com.folioreader.model.sqlite.HighLightTable
import com.folioreader.ui.activity.FolioActivity
import com.folioreader.ui.activity.FolioActivityCallback
import com.folioreader.ui.fragment.DictionaryFragment
import com.folioreader.ui.fragment.FolioPageFragment
import com.folioreader.util.AppUtil
import com.folioreader.util.HighlightUtil
import com.folioreader.util.UiUtil
import dalvik.system.PathClassLoader
import org.json.JSONObject
import org.springframework.util.ReflectionUtils
import java.lang.ref.WeakReference
import java.util.*

class FolioWebView : WebView {

    companion object {
        @JvmField
        val LOG_TAG: String = FolioWebView::class.java.simpleName
        private const val IS_SCROLLING_CHECK_TIMER = 100
        private const val IS_SCROLLING_CHECK_MAX_DURATION = 10000

        @JvmStatic
        fun onWebViewConsoleMessage(cm: ConsoleMessage, logTag: String, msg: String): Boolean {
            when (cm.messageLevel()) {
                ConsoleMessage.MessageLevel.LOG -> {
                    Log.v(logTag, msg)
                    return true
                }
                ConsoleMessage.MessageLevel.DEBUG, ConsoleMessage.MessageLevel.TIP -> {
                    Log.d(logTag, msg)
                    return true
                }
                ConsoleMessage.MessageLevel.WARNING -> {
                    Log.w(logTag, msg)
                    return true
                }
                ConsoleMessage.MessageLevel.ERROR -> {
                    Log.e(logTag, msg)
                    return true
                }
                else -> return false
            }
        }
    }

    private var horizontalPageCount = 0
    private var displayMetrics: DisplayMetrics? = null
    private var density: Float = 0f
    private var mScrollListener: ScrollListener? = null
    private var mSeekBarListener: SeekBarListener? = null
    private lateinit var gestureDetector: GestureDetectorCompat
    private var eventActionDown: MotionEvent? = null
    private var pageWidthCssDp: Int = 0
    private var pageWidthCssPixels: Float = 0f
    private lateinit var webViewPager: WebViewPager
    private lateinit var uiHandler: Handler
    private lateinit var folioActivityCallback: FolioActivityCallback
    private lateinit var parentFragment: FolioPageFragment

    private var actionMode: ActionMode? = null
    private var textSelectionCb: TextSelectionCb? = null
    private var textSelectionCb2: TextSelectionCb2? = null
    private var selectionRect = Rect()
    private val popupRect = Rect()
    private var popupWindow = PopupWindow()
    private var _textSelectionBinding: TextSelectionBinding? = null
    private val textSelectionBinding get() = _textSelectionBinding!!

    private var isScrollingCheckDuration: Int = 0
    private var isScrollingRunnable: Runnable? = null
    private var oldScrollX: Int = 0
    private var oldScrollY: Int = 0
    private var lastTouchAction: Int = 0
    private var destroyed: Boolean = false
    private var handleHeight: Int = 0

    private var lastScrollType: LastScrollType? = null

    val contentHeightVal: Int
        get() = Math.floor((this.contentHeight * this.scale).toDouble()).toInt()

    val webViewHeight: Int
        get() = this.measuredHeight

    private enum class LastScrollType {
        USER, PROGRAMMATIC
    }

    // Listener interfaces
    internal interface ScrollListener {
        fun onScrollChange(percent: Int)
    }

    internal interface SeekBarListener {
        fun fadeInSeekBarIfInvisible()
    }

    @JavascriptInterface
    fun getDirection(): String {
        return folioActivityCallback.direction.toString()
    }

    @JavascriptInterface
    fun getTopDistraction(unitString: String): Int {
        val unit = DisplayUnit.valueOf(unitString)
        return folioActivityCallback.getTopDistraction(unit)
    }

    @JavascriptInterface
    fun getBottomDistraction(unitString: String): Int {
        val unit = DisplayUnit.valueOf(unitString)
        return folioActivityCallback.getBottomDistraction(unit)
    }

    @JavascriptInterface
    fun getViewportRect(unitString: String): String {
        val unit = DisplayUnit.valueOf(unitString)
        val rect = folioActivityCallback.getViewportRect(unit)
        return UiUtil.rectToDOMRectJson(rect)
    }

    @JavascriptInterface
    fun toggleSystemUI() {
        uiHandler.post {
            folioActivityCallback.toggleSystemUI()
        }
    }

    @JavascriptInterface
    fun isPopupShowing(): Boolean {
        return popupWindow.isShowing
    }

    private inner class HorizontalGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            lastScrollType = LastScrollType.USER
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!webViewPager.isScrolling) {
                uiHandler.postDelayed({
                    scrollTo(getScrollXPixelsForPage(webViewPager.currentItem), 0)
                }, 100)
            }
            lastScrollType = LastScrollType.USER
            return true
        }

        override fun onDown(event: MotionEvent): Boolean {
            eventActionDown = MotionEvent.obtain(event)
            super@FolioWebView.onTouchEvent(event) // Call super.onTouchEvent for proper handling
            return true
        }
    }

    private inner class VerticalGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            lastScrollType = LastScrollType.USER
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            lastScrollType = LastScrollType.USER
            return false
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private fun init() {
        Log.v(LOG_TAG, "-> init")
        uiHandler = Handler(Looper.getMainLooper())
        displayMetrics = resources.displayMetrics
        density = displayMetrics!!.density

        gestureDetector = if (folioActivityCallback.direction == Config.Direction.HORIZONTAL) {
            GestureDetectorCompat(context, HorizontalGestureListener())
        } else {
            GestureDetectorCompat(context, VerticalGestureListener())
        }
        initViewTextSelection()
    }

    fun initViewTextSelection() {
        Log.v(LOG_TAG, "-> initViewTextSelection")

        var textSelectionMiddleDrawable: Drawable? = null
        try {
            val typedArray = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textSelectHandle))
            val textSelectHandleResId = typedArray.getResourceId(0, 0)
            typedArray.recycle()

            if (textSelectHandleResId != 0) {
                textSelectionMiddleDrawable = ContextCompat.getDrawable(context, textSelectHandleResId)
            } else {
                Log.w(LOG_TAG, "textSelectHandle attribute not found in theme or returned 0.")
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error getting textSelectHandle drawable from theme attribute.", e)
        }
        
        handleHeight = textSelectionMiddleDrawable?.intrinsicHeight ?: (24 * density).toInt()
        if (textSelectionMiddleDrawable == null) {
            Log.w(LOG_TAG, "Text selection handle drawable could not be loaded, using default height.")
        }

        val config = AppUtil.getSavedConfig(context)!!
        val ctw = if (config.isNightMode) {
            ContextThemeWrapper(context, R.style.FolioNightTheme)
        } else {
            ContextThemeWrapper(context, R.style.FolioDayTheme)
        }

        _textSelectionBinding = TextSelectionBinding.inflate(LayoutInflater.from(ctw), this, false)
        textSelectionBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        textSelectionBinding.yellowHighlight.setOnClickListener { onHighlightColorItemsClicked(HighlightStyle.Yellow, false) }
        textSelectionBinding.greenHighlight.setOnClickListener { onHighlightColorItemsClicked(HighlightStyle.Green, false) }
        textSelectionBinding.blueHighlight.setOnClickListener { onHighlightColorItemsClicked(HighlightStyle.Blue, false) }
        textSelectionBinding.pinkHighlight.setOnClickListener { onHighlightColorItemsClicked(HighlightStyle.Pink, false) }
        textSelectionBinding.underlineHighlight.setOnClickListener { onHighlightColorItemsClicked(HighlightStyle.Underline, false) }

        textSelectionBinding.deleteHighlight.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:clearSelection()")
            loadUrl("javascript:deleteThisHighlight()")
        }

        textSelectionBinding.copySelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:onTextSelectionItemClicked('''${R.id.copySelection}''')")
        }
        textSelectionBinding.shareSelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:onTextSelectionItemClicked('''${R.id.shareSelection}''')")
        }
        textSelectionBinding.defineSelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:onTextSelectionItemClicked('''${R.id.defineSelection}''')")
        }
    }

    @JavascriptInterface
    fun onTextSelectionItemClicked(id: Int, selectedText: String?) {
        uiHandler.post { loadUrl("javascript:clearSelection()") }
        when (id) {
            R.id.copySelection -> {
                UiUtil.copyToClipboard(context, selectedText)
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }
            R.id.shareSelection -> UiUtil.share(context, selectedText)
            R.id.defineSelection -> uiHandler.post { showDictDialog(selectedText) }
        }
    }

    private fun showDictDialog(selectedText: String?) {
        val dictionaryFragment = DictionaryFragment()
        val bundle = Bundle()
        bundle.putString(Constants.SELECTED_WORD, selectedText?.trim())
        dictionaryFragment.arguments = bundle
        // Ensure parentFragment.parentFragmentManager is used for DialogFragments
        if (parentFragment.isAdded) {
             dictionaryFragment.show(parentFragment.parentFragmentManager, DictionaryFragment::class.java.name)
        }
    }

    private fun onHighlightColorItemsClicked(style: HighlightStyle, isAlreadyCreated: Boolean) {
        parentFragment.highlight(style, isAlreadyCreated)
        dismissPopupWindow()
    }

    @JavascriptInterface
    fun deleteThisHighlight(id: String?) {
        if (id.isNullOrEmpty()) return
        val highlightImpl = HighLightTable.getHighlightForRangy(id)
        if (HighLightTable.deleteHighlight(id)) {
            val rangy = HighlightUtil.generateRangyString(parentFragment.pageName)
            uiHandler.post { parentFragment.loadRangy(rangy) }
            if (highlightImpl != null) {
                HighlightUtil.sendHighlightBroadcastEvent(
                    context, highlightImpl,
                    HighLight.HighLightAction.DELETE
                )
            }
        }
    }

    fun setParentFragment(parentFragment: FolioPageFragment) {
        this.parentFragment = parentFragment
    }

    fun setFolioActivityCallback(folioActivityCallback: FolioActivityCallback) {
        this.folioActivityCallback = folioActivityCallback
        init()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) {
            pageWidthCssDp = Math.ceil((measuredWidth / density).toDouble()).toInt()
            pageWidthCssPixels = pageWidthCssDp * density
        }
    }

    internal fun setScrollListener(listener: ScrollListener) { // Changed to internal
        mScrollListener = listener
    }

    internal fun setSeekBarListener(listener: SeekBarListener) { // Changed to internal
        mSeekBarListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        //if (event == null) return false // Should not happen with non-null type, but good practice
        lastTouchAction = event.action
        return if (folioActivityCallback.direction == Config.Direction.HORIZONTAL) {
            computeHorizontalScroll(event)
        } else {
            computeVerticalScroll(event)
        }
    }

    private fun computeVerticalScroll(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun computeHorizontalScroll(event: MotionEvent): Boolean {
        if (!::webViewPager.isInitialized) return super.onTouchEvent(event)
        webViewPager.dispatchTouchEvent(event) // Pass touch events to ViewPager
        val gestureReturn = gestureDetector.onTouchEvent(event)
        return if (gestureReturn) true else super.onTouchEvent(event)
    }

    fun getScrollXDpForPage(page: Int): Int {
        return page * pageWidthCssDp
    }

    fun getScrollXPixelsForPage(page: Int): Int {
        return Math.ceil((page * pageWidthCssPixels).toDouble()).toInt()
    }

    fun setHorizontalPageCount(horizontalPageCount: Int) {
        this.horizontalPageCount = horizontalPageCount
        uiHandler.post {
            // Ensure parent is a View before casting and finding webViewPager
            val parentView = parent as? View
            parentView?.let {
                webViewPager = it.findViewById(R.id.webViewPager)
                webViewPager.setHorizontalPageCount(this@FolioWebView.horizontalPageCount)
            }
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, y)
        lastScrollType = LastScrollType.PROGRAMMATIC
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        mScrollListener?.onScrollChange(t)
        super.onScrollChanged(l, t, oldl, oldt)
        if (lastScrollType == LastScrollType.USER) {
            parentFragment.searchLocatorVisible = null
        }
        lastScrollType = null
    }

    @JavascriptInterface
    fun dismissPopupWindow(): Boolean {
        val wasShowing = popupWindow.isShowing
        if (Looper.getMainLooper().thread == Thread.currentThread()) {
            popupWindow.dismiss()
        } else {
            uiHandler.post { popupWindow.dismiss() }
        }
        selectionRect = Rect()
        isScrollingRunnable?.let { uiHandler.removeCallbacks(it) }
        isScrollingCheckDuration = 0
        return wasShowing
    }

    override fun destroy() {
        super.destroy()
        Log.d(LOG_TAG, "-> destroy")
        dismissPopupWindow()
        destroyed = true
        _textSelectionBinding = null
    }

    private inner class TextSelectionCb : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            evaluateJavascript("javascript:getSelectionRect()") { value ->
                if (value != null && value != "null") {
                    try {
                        val rectJson = JSONObject(value)
                        setSelectionRect(
                            rectJson.getInt("left"), rectJson.getInt("top"),
                            rectJson.getInt("right"), rectJson.getInt("bottom")
                        )
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Error parsing selection rect JSON", e)
                    }
                }
            }
            return false
        }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode) {
            dismissPopupWindow()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private inner class TextSelectionCb2 : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode) {
            dismissPopupWindow()
        }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            evaluateJavascript("javascript:getSelectionRect()") { value ->
                 if (value != null && value != "null") {
                    try {
                        val rectJson = JSONObject(value)
                        setSelectionRect(
                            rectJson.getInt("left"), rectJson.getInt("top"),
                            rectJson.getInt("right"), rectJson.getInt("bottom")
                        )
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Error parsing selection rect JSON onGetContentRect", e)
                    }
                 }
            }
        }
    }

    override fun startActionMode(callback: Callback): ActionMode {
        textSelectionCb = TextSelectionCb()
        actionMode = super.startActionMode(textSelectionCb)
        actionMode?.finish() // Finish immediately to prevent system controls, rely on custom popup
        return actionMode!! // Must return a non-null ActionMode
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun startActionMode(callback: Callback, type: Int): ActionMode {
        textSelectionCb2 = TextSelectionCb2()
        actionMode = super.startActionMode(textSelectionCb2, type)
        actionMode?.finish() // Finish immediately
        return actionMode!! // Must return a non-null ActionMode
    }

    @JavascriptInterface
    fun setSelectionRect(left: Int, top: Int, right: Int, bottom: Int) {
        val currentSelectionRect = Rect()
        currentSelectionRect.left = (left * density).toInt()
        currentSelectionRect.top = (top * density).toInt()
        currentSelectionRect.right = (right * density).toInt()
        currentSelectionRect.bottom = (bottom * density).toInt()

        computeTextSelectionRect(currentSelectionRect)
        uiHandler.post { showTextSelectionPopup() }
    }

    private fun computeTextSelectionRect(currentSelectionRect: Rect) {
        val viewportRect = folioActivityCallback.getViewportRect(DisplayUnit.PX)
        if (!Rect.intersects(viewportRect, currentSelectionRect)) {
            uiHandler.post {
                popupWindow.dismiss()
                isScrollingRunnable?.let { uiHandler.removeCallbacks(it) }
            }
            return
        }

        if (selectionRect == currentSelectionRect) return
        selectionRect = currentSelectionRect

        val aboveSelectionRect = Rect(viewportRect).apply { bottom = selectionRect.top - (8 * density).toInt() }
        val belowSelectionRect = Rect(viewportRect).apply { top = selectionRect.bottom + handleHeight }

        popupRect.left = viewportRect.left
        popupRect.right = popupRect.left + textSelectionBinding.root.measuredWidth
        val popupY: Int = if (belowSelectionRect.contains(popupRect.apply { top = belowSelectionRect.top; bottom = top + textSelectionBinding.root.measuredHeight })) {
            belowSelectionRect.top
        } else if (aboveSelectionRect.contains(popupRect.apply { top = aboveSelectionRect.top; bottom = top + textSelectionBinding.root.measuredHeight })) {
            aboveSelectionRect.bottom - popupRect.height()
        } else {
            selectionRect.top - (textSelectionBinding.root.measuredHeight - selectionRect.height()) / 2
        }

        val popupX = selectionRect.left - (textSelectionBinding.root.measuredWidth - selectionRect.width()) / 2
        popupRect.offsetTo(popupX, popupY)

        if (popupRect.left < viewportRect.left) popupRect.offset(viewportRect.left - popupRect.left, 0)
        if (popupRect.right > viewportRect.right) popupRect.offset(viewportRect.right - popupRect.right, 0)
    }

    private fun showTextSelectionPopup() {
        if (_textSelectionBinding == null) return // Guard against null binding

        popupWindow.dismiss()
        oldScrollX = scrollX
        oldScrollY = scrollY

        isScrollingRunnable = Runnable {
            isScrollingRunnable?.let { uiHandler.removeCallbacks(it) }
            val currentScrollX = scrollX
            val currentScrollY = scrollY
            val inTouchMode = lastTouchAction == MotionEvent.ACTION_DOWN || lastTouchAction == MotionEvent.ACTION_MOVE

            if (oldScrollX == currentScrollX && oldScrollY == currentScrollY && !inTouchMode) {
                if (!destroyed && _textSelectionBinding != null) { // Check binding again before use
                    popupWindow = PopupWindow(textSelectionBinding.root, WRAP_CONTENT, WRAP_CONTENT)
                    popupWindow.isClippingEnabled = false
                    popupWindow.showAtLocation(this@FolioWebView, Gravity.NO_GRAVITY, popupRect.left, popupRect.top)
                }
            } else {
                oldScrollX = currentScrollX
                oldScrollY = currentScrollY
                isScrollingCheckDuration += IS_SCROLLING_CHECK_TIMER
                if (isScrollingCheckDuration < IS_SCROLLING_CHECK_MAX_DURATION && !destroyed) {
                    isScrollingRunnable?.let { uiHandler.postDelayed(it, IS_SCROLLING_CHECK_TIMER.toLong()) }
                }
            }
        }
        isScrollingRunnable?.let { uiHandler.removeCallbacks(it) }
        isScrollingCheckDuration = 0
        if (!destroyed) {
            isScrollingRunnable?.let { uiHandler.postDelayed(it, IS_SCROLLING_CHECK_TIMER.toLong()) }
        }
    }
}
