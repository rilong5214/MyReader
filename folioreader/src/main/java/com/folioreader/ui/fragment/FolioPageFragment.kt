package com.folioreader.ui.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.folioreader.Config
import com.folioreader.FolioReader
import com.folioreader.R
import com.folioreader.mediaoverlay.MediaController
import com.folioreader.mediaoverlay.MediaControllerCallbacks
import com.folioreader.model.HighLight
import com.folioreader.model.HighlightImpl
import com.folioreader.model.event.*
import com.folioreader.model.locators.ReadLocator
import com.folioreader.model.locators.SearchLocator
import com.folioreader.model.sqlite.HighLightTable
import com.folioreader.ui.activity.FolioActivityCallback
import com.folioreader.ui.base.HtmlTask
import com.folioreader.ui.base.HtmlTaskCallback
import com.folioreader.ui.base.HtmlUtil
import com.folioreader.ui.view.FolioWebView
import com.folioreader.ui.view.LoadingView
import com.folioreader.ui.view.VerticalSeekbar
import com.folioreader.ui.view.WebViewPager
import com.folioreader.util.AppUtil
import com.folioreader.util.HighlightUtil
import com.folioreader.util.UiUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.readium.r2.shared.Link
// import org.readium.r2.shared.Locations
import java.io.Serializable
import java.util.*
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Created by mahavir on 4/2/16.
 */
class FolioPageFragment : Fragment(),
    HtmlTaskCallback, MediaControllerCallbacks, FolioWebView.SeekBarListener {

    companion object {

        @JvmField
        val LOG_TAG: String = FolioPageFragment::class.java.simpleName

        private const val BUNDLE_SPINE_INDEX = "BUNDLE_SPINE_INDEX"
        private const val BUNDLE_BOOK_TITLE = "BUNDLE_BOOK_TITLE"
        private const val BUNDLE_SPINE_ITEM = "BUNDLE_SPINE_ITEM"
        private const val BUNDLE_READ_LOCATOR_CONFIG_CHANGE = "BUNDLE_READ_LOCATOR_CONFIG_CHANGE"
        const val BUNDLE_SEARCH_LOCATOR = "BUNDLE_SEARCH_LOCATOR"

        @JvmStatic
        fun newInstance(spineIndex: Int, bookTitle: String, spineRef: Link, bookId: String): FolioPageFragment {
            val fragment = FolioPageFragment()
            val args = Bundle()
            args.putInt(BUNDLE_SPINE_INDEX, spineIndex)
            args.putString(BUNDLE_BOOK_TITLE, bookTitle)
            args.putString(FolioReader.EXTRA_BOOK_ID, bookId)
            args.putSerializable(BUNDLE_SPINE_ITEM, spineRef)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var uiHandler: Handler
    private var mHtmlString: String? = null
    private val hasMediaOverlay = false
    private var mAnchorId: String? = null
    private var rangy = ""
    private var highlightId: String? = null

    private var lastReadLocator: ReadLocator? = null
    private var outState: Bundle? = null
    private var savedInstanceState: Bundle? = null

    private var mRootView: View? = null

    private var loadingView: LoadingView? = null
    private var mScrollSeekbar: VerticalSeekbar? = null
    var mWebview: FolioWebView? = null
    private var webViewPager: WebViewPager? = null
    private var mPagesLeftTextView: TextView? = null
    private var mMinutesLeftTextView: TextView? = null
    private var mActivityCallback: FolioActivityCallback? = null

    private var mTotalMinutes: Int = 0
    private var mFadeInAnimation: Animation? = null
    private var mFadeOutAnimation: Animation? = null

    lateinit var spineItem: Link
    private var spineIndex = -1
    private var mBookTitle: String? = null
    private var mIsPageReloaded: Boolean = false

    private var highlightStyle: String? = null

    private var mediaController: MediaController? = null
    private var mConfig: Config? = null
    private var mBookId: String? = null
    var searchLocatorVisible: SearchLocator? = null

    private lateinit var chapterUrl: Uri

    val pageName: String
        get() = mBookTitle + "$" + spineItem.href

    private val isCurrentFragment: Boolean
        get() {
            return isAdded && mActivityCallback!!.currentChapterIndex == spineIndex
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        this.savedInstanceState = savedInstanceState
        uiHandler = Handler(Looper.getMainLooper())

        if (activity is FolioActivityCallback)
            mActivityCallback = activity as FolioActivityCallback?

        EventBus.getDefault().register(this)

        spineIndex = requireArguments().getInt(BUNDLE_SPINE_INDEX)
        mBookTitle = requireArguments().getString(BUNDLE_BOOK_TITLE)
        //TODO: Replace with type-safe getSerializable (API 33+) if possible
        spineItem = requireArguments().getSerializable(BUNDLE_SPINE_ITEM) as Link
        mBookId = requireArguments().getString(FolioReader.EXTRA_BOOK_ID)

        chapterUrl = Uri.parse(mActivityCallback?.streamerUrl + spineItem.href!!.substring(1))
        //TODO: Replace with type-safe getParcelable (API 33+) if possible
        searchLocatorVisible = savedInstanceState?.getParcelable(BUNDLE_SEARCH_LOCATOR)

        if (spineItem != null) { // Should be lateinit, so null check might be redundant if initialized properly
            // SMIL Parsing not yet implemented in r2-streamer-kotlin
            //if (spineItem.getProperties().contains("media-overlay")) {
            //    mediaController = new MediaController(getActivity(), MediaController.MediaType.SMIL, this);
            //    hasMediaOverlay = true;
            //} else {
            mediaController = MediaController(activity, MediaController.MediaType.TTS, this)
            mediaController!!.setTextToSpeech(activity)
            //}
        }
        highlightStyle = HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal)
        mRootView = inflater.inflate(R.layout.folio_page_fragment, container, false)
        mPagesLeftTextView = mRootView!!.findViewById<View>(R.id.pagesLeft) as TextView
        mMinutesLeftTextView = mRootView!!.findViewById<View>(R.id.minutesLeft) as TextView

        mConfig = AppUtil.getSavedConfig(context)

        loadingView = mRootView!!.findViewById(R.id.loadingView)
        initSeekbar()
        initAnimations()
        initWebView()
        updatePagesLeftTextBg()

        return mRootView
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when pause/play
     * button is clicked
     *
     * @param event of type [MediaOverlayPlayPauseEvent] contains if paused/played
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun pauseButtonClicked(event: MediaOverlayPlayPauseEvent) {
        if (isAdded && spineItem.href == event.href) { // spineItem is lateinit, should not be null here
            mediaController!!.stateChanged(event)
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when speed
     * change buttons are clicked
     *
     * @param event of type [MediaOverlaySpeedEvent] contains selected speed
     * type HALF,ONE,ONE_HALF and TWO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun speedChanged(event: MediaOverlaySpeedEvent) {
        if (mediaController != null)
            mediaController!!.setSpeed(event.speed)
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered from [MediaControllerFragment.initListeners] when new
     * style is selected on button click.
     *
     * @param event of type [MediaOverlaySpeedEvent] contains selected style
     * of type DEFAULT,UNDERLINE and BACKGROUND.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun styleChanged(event: MediaOverlayHighlightStyleEvent) {
        if (isAdded) {
            highlightStyle = when (event.style) {
                MediaOverlayHighlightStyleEvent.Style.DEFAULT ->
                    HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.Normal)
                MediaOverlayHighlightStyleEvent.Style.UNDERLINE ->
                    HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.DottetUnderline)
                MediaOverlayHighlightStyleEvent.Style.BACKGROUND ->
                    HighlightImpl.HighlightStyle.classForStyle(HighlightImpl.HighlightStyle.TextColor)
            }
            mWebview?.loadUrl(String.format(getString(R.string.setmediaoverlaystyle), highlightStyle))
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     * Function triggered when any EBook configuration is changed.
     *
     * @param reloadDataEvent empty POJO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun reload(reloadDataEvent: ReloadDataEvent) {

        if (isCurrentFragment)
            getLastReadLocator()

        if (isAdded) {
            mWebview?.dismissPopupWindow()
            mWebview?.initViewTextSelection()
            loadingView?.updateTheme()
            loadingView?.show()
            mIsPageReloaded = true
            setHtml(true)
            updatePagesLeftTextBg()
        }
    }

    /**
     * [EVENT BUS FUNCTION]
     *
     *
     * Function triggered when highlight is deleted and page is needed to
     * be updated.
     *
     * @param event empty POJO.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateHighlight(event: UpdateHighlightEvent) {
        if (isAdded) {
            this.rangy = HighlightUtil.generateRangyString(pageName)
            loadRangy(this.rangy)
        }
    }

    fun scrollToAnchorId(href: String) {

        if (!TextUtils.isEmpty(href) && href.indexOf('#') != -1) {
            mAnchorId = href.substring(href.lastIndexOf('#') + 1)
            if (loadingView != null && loadingView?.visibility != View.VISIBLE) {
                loadingView?.show()
                mWebview?.loadUrl(String.format(getString(R.string.go_to_anchor), mAnchorId))
                mAnchorId = null
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun resetCurrentIndex(resetIndex: RewindIndexEvent) {
        if (isCurrentFragment) {
            mWebview?.loadUrl("javascript:rewindCurrentIndex()")
        }
    }

    override fun onReceiveHtml(html: String) {
        if (isAdded) {
            mHtmlString = html
            setHtml(false)
        }
    }

    private fun setHtml(reloaded: Boolean) {
        // spineItem is lateinit, should not be null here
        /*if (!reloaded && spineItem.properties.contains("media-overlay")) {
            mediaController.setSMILItems(SMILParser.parseSMIL(mHtmlString));
            mediaController.setUpMediaPlayer(spineItem.mediaOverlay, spineItem.mediaOverlay.getAudioPath(spineItem.href), mBookTitle);
        }*/
        mConfig = AppUtil.getSavedConfig(context) // context can be null if fragment detached

        val href = spineItem.href
        var path = ""
        val forwardSlashLastIndex = href!!.lastIndexOf('/') // href from lateinit var
        if (forwardSlashLastIndex != -1) {
            path = href.substring(1, forwardSlashLastIndex + 1)
        }

        val mimeType: String =
            if (spineItem.typeLink!!.equals(getString(R.string.xhtml_mime_type), true)) { // typeLink from lateinit var
                getString(R.string.xhtml_mime_type)
            } else {
                getString(R.string.html_mime_type)
            }

        uiHandler.post {
            mWebview?.loadDataWithBaseURL(
                mActivityCallback?.streamerUrl + path,
                HtmlUtil.getHtmlContent(mWebview!!.context, mHtmlString, mConfig!!), // mWebview and mConfig can be null
                mimeType,
                "UTF-8", null
            )
        }
    }

    fun scrollToLast() {

        val isPageLoading = loadingView == null || loadingView?.visibility == View.VISIBLE
        Log.v(LOG_TAG, "-> scrollToLast -> isPageLoading = $isPageLoading")

        if (!isPageLoading) {
            loadingView?.show()
            mWebview?.loadUrl("javascript:scrollToLast()")
        }
    }

    fun scrollToFirst() {

        val isPageLoading = loadingView == null || loadingView?.visibility == View.VISIBLE
        Log.v(LOG_TAG, "-> scrollToFirst -> isPageLoading = $isPageLoading")

        if (!isPageLoading) {
            loadingView?.show()
            mWebview?.loadUrl("javascript:scrollToFirst()")
        }
    }

    @SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
    private fun initWebView() {

        val webViewLayout = mRootView!!.findViewById<FrameLayout>(R.id.webViewLayout)
        mWebview = webViewLayout.findViewById(R.id.folioWebView)
        val localWebview = mWebview ?: return

        localWebview.setParentFragment(this)
        webViewPager = webViewLayout.findViewById(R.id.webViewPager)
        val localWebViewPager = webViewPager

        if (activity is FolioActivityCallback) { // activity can be null
            localWebview.setFolioActivityCallback((activity as FolioActivityCallback?)!!) // Potentially unsafe !!
        }

        setupScrollBar()
        localWebview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val height = floor((localWebview.contentHeight * localWebview.scale).toDouble()).toInt()
            val webViewHeight = localWebview.measuredHeight
            mScrollSeekbar?.maximum = height - webViewHeight
        }

        localWebview.settings.javaScriptEnabled = true
        localWebview.isVerticalScrollBarEnabled = false
        localWebview.settings.allowFileAccess = true
        localWebview.isHorizontalScrollBarEnabled = false

        localWebview.addJavascriptInterface(this, "Highlight")
        localWebview.addJavascriptInterface(this, "FolioPageFragment")

        localWebViewPager?.let { pager ->
            localWebview.addJavascriptInterface(pager, "WebViewPager")
        }

        loadingView?.let { view ->
            localWebview.addJavascriptInterface(view, "LoadingView")
        }
        
        localWebview.addJavascriptInterface(localWebview, "FolioWebView")

        localWebview.setScrollListener(object : FolioWebView.ScrollListener {
            override fun onScrollChange(percent: Int) {
                mScrollSeekbar?.setProgressAndThumb(percent)
                updatePagesLeftText(percent)
            }
        })

        localWebview.webViewClient = webViewClient
        localWebview.webChromeClient = webChromeClient

        localWebview.settings.defaultTextEncodingName = "utf-8"
        HtmlTask(this).execute(chapterUrl.toString())
    }

    private val webViewClient = object : WebViewClient() {

        override fun onPageFinished(view: WebView, url: String) {

            mWebview?.loadUrl("javascript:checkCompatMode()")
            mWebview?.loadUrl("javascript:alert(getReadingTime())")

            if (mActivityCallback!!.direction == Config.Direction.HORIZONTAL) // mActivityCallback can be null
                mWebview?.loadUrl("javascript:initHorizontalDirection()")

            view.loadUrl(
                String.format(
                    getString(R.string.setmediaoverlaystyle),
                    HighlightImpl.HighlightStyle.classForStyle(
                        HighlightImpl.HighlightStyle.Normal
                    )
                )
            )

            val rangy = HighlightUtil.generateRangyString(pageName)
            this@FolioPageFragment.rangy = rangy
            if (!rangy.isEmpty())
                loadRangy(rangy)

            if (mIsPageReloaded) {

                if (searchLocatorVisible != null) {
                    val callHighlightSearchLocator = String.format(
                        getString(R.string.callHighlightSearchLocator),
                        searchLocatorVisible?.locations?.cfi
                    )
                    mWebview?.loadUrl(callHighlightSearchLocator)

                } else if (isCurrentFragment) {
                    // lastReadLocator can be null if getLastReadLocator() wasn't called or failed
                    val cfi = lastReadLocator!!.locations?.cfi // Potential NPE
                    mWebview?.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))

                } else {
                    if (spineIndex == mActivityCallback!!.currentChapterIndex - 1) { // mActivityCallback can be null
                        // Scroll to last, the page before current page
                        mWebview?.loadUrl("javascript:scrollToLast()")
                    } else {
                        // Make loading view invisible for all other fragments
                        loadingView?.hide()
                    }
                }

                mIsPageReloaded = false

            } else if (!TextUtils.isEmpty(mAnchorId)) {
                mWebview?.loadUrl(String.format(getString(R.string.go_to_anchor), mAnchorId))
                mAnchorId = null

            } else if (!TextUtils.isEmpty(highlightId)) {
                mWebview?.loadUrl(String.format(getString(R.string.go_to_highlight), highlightId))
                highlightId = null

            } else if (searchLocatorVisible != null) {
                val callHighlightSearchLocator = String.format(
                    getString(R.string.callHighlightSearchLocator),
                    searchLocatorVisible?.locations?.cfi
                )
                mWebview?.loadUrl(callHighlightSearchLocator)

            } else if (isCurrentFragment) {

                val readLocator: ReadLocator?
                if (savedInstanceState == null) {
                    Log.v(LOG_TAG, "-> onPageFinished -> took from getEntryReadLocator")
                    readLocator = mActivityCallback!!.entryReadLocator // mActivityCallback can be null
                } else {
                    Log.v(LOG_TAG, "-> onPageFinished -> took from bundle")
                     //TODO: Replace with type-safe getParcelable (API 33+) if possible
                    readLocator = savedInstanceState!!.getParcelable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE) // savedInstanceState can be null
                    savedInstanceState!!.remove(BUNDLE_READ_LOCATOR_CONFIG_CHANGE) // Potential NPE
                }

                if (readLocator != null) {
                    val cfi = readLocator.locations?.cfi
                    Log.v(LOG_TAG, "-> onPageFinished -> readLocator -> " + cfi!!) // Potential NPE if cfi is null
                    mWebview?.loadUrl(String.format(getString(R.string.callScrollToCfi), cfi))
                } else {
                    loadingView?.hide()
                }

            } else {

                if (spineIndex == mActivityCallback!!.currentChapterIndex - 1) { // mActivityCallback can be null
                    // Scroll to last, the page before current page
                    mWebview?.loadUrl("javascript:scrollToLast()")
                } else {
                    // Make loading view invisible for all other fragments
                    loadingView?.hide()
                }
            }
        }

        @Deprecated("Overrides deprecated WebViewClient method on older APIs")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

            if (url.isEmpty())
                return true

            val urlOfEpub = mActivityCallback!!.goToChapter(url) // mActivityCallback can be null
            if (!urlOfEpub) {
                // Otherwise, give the default behavior (open in browser)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }

            return true
        }

        // prevent favicon.ico to be loaded automatically
        @Deprecated("Overrides deprecated WebViewClient method on older APIs")
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            if (url.lowercase(Locale.ROOT).contains("/favicon.ico")) {
                try {
                    return WebResourceResponse("image/png", null, null)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
                }

            }
            return null
        }

        // prevent favicon.ico to be loaded automatically
        @SuppressLint("NewApi")
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame
                && request.url.path != null
                && request.url.path!!.endsWith("/favicon.ico") // path can be null
            ) {
                try {
                    return WebResourceResponse("image/png", null, null)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "shouldInterceptRequest failed", e)
                }

            }
            return null
        }
    }

    private val webChromeClient = object : WebChromeClient() {

        override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
            super.onConsoleMessage(cm)
            val msg = cm.message() + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]"
            return FolioWebView.onWebViewConsoleMessage(cm, "WebViewConsole", msg)
        }

        override fun onProgressChanged(view: WebView, progress: Int) {}

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {

            // Check if this `if` block can be dropped?
            if (!this@FolioPageFragment.isVisible)
                return true

            if (TextUtils.isDigitsOnly(message)) {
                try {
                    mTotalMinutes = Integer.parseInt(message)
                } catch (e: NumberFormatException) {
                    mTotalMinutes = 0
                }

            } else {
                // to handle TTS playback when highlight is deleted.
                val p = Pattern.compile("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
                if (!p.matcher(message).matches() && message != "undefined" && isCurrentFragment) {
                    mediaController!!.speakAudio(message) // mediaController can be null
                }
            }

            result.confirm()
            return true
        }
    }

    override fun onStop() {
        super.onStop()
        Log.v(LOG_TAG, "-> onStop -> " + spineItem.href + " -> " + isCurrentFragment) // spineItem lateinit

        mediaController!!.stop() // mediaController can be null
        //TODO save last media overlay item

        if (isCurrentFragment)
            getLastReadLocator()
    }

    fun getLastReadLocator(): ReadLocator? {
        Log.v(LOG_TAG, "-> getLastReadLocator -> " + spineItem.href!!) // spineItem lateinit
        try {
            synchronized(this) {
                mWebview?.loadUrl(getString(R.string.callComputeLastReadCfi))
                (this as java.lang.Object).wait(5000)
            }
        } catch (e: InterruptedException) {
            Log.e(LOG_TAG, "-> ", e)
        }

        return lastReadLocator
    }

    @JavascriptInterface
    fun storeLastReadCfi(cfi: String) {

        synchronized(this) {
            var href = spineItem.href // spineItem lateinit
            if (href == null) href = "" // Redundant due to lateinit
            val created = Date().time
            val folioReaderLocalLocations = com.folioreader.model.locators.Locations()
            folioReaderLocalLocations.cfi = cfi
            lastReadLocator = ReadLocator(href = href, created = created, locations = folioReaderLocalLocations, text = null)
            lastReadLocator?.bookId = mBookId

            val intent = Intent(FolioReader.ACTION_SAVE_READ_LOCATOR)
            intent.putExtra(FolioReader.EXTRA_READ_LOCATOR, lastReadLocator as Parcelable?)
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)

            (this as java.lang.Object).notify()
        }
    }

    @JavascriptInterface
    fun setHorizontalPageCount(horizontalPageCount: Int) {
        Log.v(
            LOG_TAG, "-> setHorizontalPageCount = " + horizontalPageCount
                    + " -> " + spineItem.href // spineItem lateinit
        )

        mWebview?.setHorizontalPageCount(horizontalPageCount)
    }

    fun loadRangy(rangy: String) {
        mWebview?.loadUrl(
            String.format(
                "javascript:if(typeof ssReader !== \"undefined\"){ssReader.setHighlights('%s');}",
                rangy
            )
        )
    }

    private fun setupScrollBar() {
        UiUtil.setColorIntToDrawable(mConfig!!.themeColor, mScrollSeekbar!!.progressDrawable) // mConfig, mScrollSeekbar can be null
        val thumbDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.icons_sroll)
        UiUtil.setColorIntToDrawable(mConfig!!.themeColor, thumbDrawable!!) // mConfig can be null, thumbDrawable can be null
        mScrollSeekbar!!.thumb = thumbDrawable // mScrollSeekbar can be null
    }

    private fun initSeekbar() {
        mScrollSeekbar = mRootView!!.findViewById<View>(R.id.scrollSeekbar) as VerticalSeekbar // mRootView can be null
        mScrollSeekbar!!.progressDrawable // mScrollSeekbar can be null
            .setColorFilter(
                resources // Can be called only when fragment is attached
                    .getColor(R.color.default_theme_accent_color), // Deprecated
                PorterDuff.Mode.SRC_IN
            )
    }

    private fun updatePagesLeftTextBg() {

        if (mConfig!!.isNightMode) { // mConfig can be null
            mRootView!!.findViewById<View>(R.id.indicatorLayout) // mRootView can be null
                .setBackgroundColor(Color.parseColor("#131313"))
        } else {
            mRootView!!.findViewById<View>(R.id.indicatorLayout) // mRootView can be null
                .setBackgroundColor(Color.WHITE)
        }
    }

    private fun updatePagesLeftText(scrollY: Int) {
        try {
            // mWebview can be null throughout this block
            val currentPage = (ceil(scrollY.toDouble() / mWebview!!.webViewHeight) + 1).toInt()
            val totalPages = ceil(mWebview!!.contentHeightVal.toDouble() / mWebview!!.webViewHeight).toInt()
            val pagesRemaining = totalPages - currentPage
            val pagesRemainingStrFormat = if (pagesRemaining > 1)
                getString(R.string.pages_left)
            else
                getString(R.string.page_left)
            val pagesRemainingStr = String.format(
                Locale.US,
                pagesRemainingStrFormat, pagesRemaining
            )

            val minutesRemaining = ceil((pagesRemaining * mTotalMinutes).toDouble() / totalPages).toInt()
            val minutesRemainingStr: String
            if (minutesRemaining > 1) {
                minutesRemainingStr = String.format(
                    Locale.US, getString(R.string.minutes_left),
                    minutesRemaining
                )
            } else if (minutesRemaining == 1) {
                minutesRemainingStr = String.format(
                    Locale.US, getString(R.string.minute_left),
                    minutesRemaining
                )
            } else {
                minutesRemainingStr = getString(R.string.less_than_minute)
            }

            mMinutesLeftTextView!!.text = minutesRemainingStr // mMinutesLeftTextView can be null
            mPagesLeftTextView!!.text = pagesRemainingStr // mPagesLeftTextView can be null
        } catch (exp: java.lang.ArithmeticException) {
            Log.e("divide error", exp.toString())
        } catch (exp: IllegalStateException) {
            Log.e("divide error", exp.toString())
        }

    }

    private fun initAnimations() {
        mFadeInAnimation = AnimationUtils.loadAnimation(activity, R.anim.fadein) // activity can be null
        mFadeInAnimation!!.setAnimationListener(object : Animation.AnimationListener { // mFadeInAnimation can be null
            override fun onAnimationStart(animation: Animation) {
                mScrollSeekbar!!.visibility = View.VISIBLE // mScrollSeekbar can be null
            }

            override fun onAnimationEnd(animation: Animation) {
                fadeOutSeekBarIfVisible()
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
        mFadeOutAnimation = AnimationUtils.loadAnimation(activity, R.anim.fadeout) // activity can be null
        mFadeOutAnimation!!.setAnimationListener(object : Animation.AnimationListener { // mFadeOutAnimation can be null
            override fun onAnimationStart(animation: Animation) {

            }

            override fun onAnimationEnd(animation: Animation) {
                mScrollSeekbar!!.visibility = View.INVISIBLE // mScrollSeekbar can be null
            }

            override fun onAnimationRepeat(animation: Animation) {

            }
        })
    }

    override fun fadeInSeekBarIfInvisible() {
        if (mScrollSeekbar!!.visibility == View.INVISIBLE || mScrollSeekbar!!.visibility == View.GONE) { // mScrollSeekbar can be null
            mScrollSeekbar!!.startAnimation(mFadeInAnimation) // mScrollSeekbar, mFadeInAnimation can be null
        }
    }

    fun fadeOutSeekBarIfVisible() {
        if (mScrollSeekbar!!.visibility == View.VISIBLE) { // mScrollSeekbar can be null
            mScrollSeekbar!!.startAnimation(mFadeOutAnimation) // mScrollSeekbar, mFadeOutAnimation can be null
        }
    }

    override fun onDestroyView() {
        mFadeInAnimation!!.setAnimationListener(null) // mFadeInAnimation can be null
        mFadeOutAnimation!!.setAnimationListener(null) // mFadeOutAnimation can be null
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }

    /**
     * If called, this method will occur after onStop() for applications targeting platforms
     * starting with Build.VERSION_CODES.P. For applications targeting earlier platform versions
     * this method will occur before onStop() and there are no guarantees about whether it will
     * occur before or after onPause()
     *
     * @see Activity.onSaveInstanceState
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.v(LOG_TAG, "-> onSaveInstanceState -> ${spineItem.href}") // spineItem lateinit

        this.outState = outState
        //TODO: Replace with type-safe getParcelable (API 33+) if possible
        outState.putParcelable(BUNDLE_SEARCH_LOCATOR, searchLocatorVisible)
    }

    fun highlight(style: HighlightImpl.HighlightStyle, isAlreadyCreated: Boolean) {
        if (!isAlreadyCreated) {
            mWebview?.loadUrl(
                String.format(
                    "javascript:if(typeof ssReader !== \"undefined\"){ssReader.highlightSelection('%s');}",
                    HighlightImpl.HighlightStyle.classForStyle(style)
                )
            )
        } else {
            mWebview?.loadUrl(
                String.format(
                    "javascript:setHighlightStyle('%s')",
                    HighlightImpl.HighlightStyle.classForStyle(style)
                )
            )
        }
    }

    override fun resetCurrentIndex() {
        if (isCurrentFragment) {
            mWebview?.loadUrl("javascript:rewindCurrentIndex()")
        }
    }

    @JavascriptInterface
    fun onReceiveHighlights(html: String?) {
        if (html != null) {
            rangy = HighlightUtil.createHighlightRangy(
                requireActivity().applicationContext, // Changed from activity!!
                html,
                mBookId, // mBookId can be null
                pageName,
                spineIndex,
                rangy
            )
        }
    }

    override fun highLightText(fragmentId: String) {
        mWebview?.loadUrl(String.format(getString(R.string.audio_mark_id), fragmentId))
    }

    override fun highLightTTS() {
        mWebview?.loadUrl("javascript:alert(getSentenceWithIndex('epub-media-overlay-playing'))")
    }

    @JavascriptInterface
    fun getUpdatedHighlightId(id: String?, style: String) {
        if (id != null) {
            val highlightImpl = HighLightTable.updateHighlightStyle(id, style)
            if (highlightImpl != null) {
                HighlightUtil.sendHighlightBroadcastEvent(
                    requireActivity().applicationContext, // Changed from activity!!
                    highlightImpl,
                    HighLight.HighLightAction.MODIFY
                )
            }
            val rangyString = HighlightUtil.generateRangyString(pageName)
            requireActivity().runOnUiThread { loadRangy(rangyString) } // Changed from activity!!

        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isCurrentFragment) {
            if (outState != null)
                //TODO: Replace with type-safe getParcelable (API 33+) if possible
                outState!!.putSerializable(BUNDLE_READ_LOCATOR_CONFIG_CHANGE,
                    lastReadLocator as Serializable?
                ) // outState can be null
            if (isAdded && !requireActivity().isFinishing && lastReadLocator != null) // Changed from activity!!
                mActivityCallback!!.storeLastReadLocator(lastReadLocator) // mActivityCallback can be null
        }
        if (mWebview != null) mWebview!!.destroy() // mWebview can be null
    }

    override fun onError() {}

    fun scrollToHighlightId(highlightId: String) {
        this.highlightId = highlightId

        if (loadingView != null && loadingView?.visibility != View.VISIBLE) {
            loadingView?.show()
            mWebview?.loadUrl(String.format(getString(R.string.go_to_highlight), highlightId))
            this.highlightId = null
        }
    }

    fun highlightSearchLocator(searchLocator: SearchLocator) {
        Log.v(LOG_TAG, "-> highlightSearchLocator")
        this.searchLocatorVisible = searchLocator

        if (loadingView != null && loadingView?.visibility != View.VISIBLE) {
            loadingView?.show()
            val callHighlightSearchLocator = String.format(
                getString(R.string.callHighlightSearchLocator),
                searchLocatorVisible?.locations?.cfi
            )
            mWebview?.loadUrl(callHighlightSearchLocator)
        }
    }

    fun clearSearchLocator() {
        Log.v(LOG_TAG, "-> clearSearchLocator -> " + spineItem.href!!) // spineItem lateinit
        mWebview?.loadUrl(getString(R.string.callClearSelection))
        searchLocatorVisible = null
    }
}
