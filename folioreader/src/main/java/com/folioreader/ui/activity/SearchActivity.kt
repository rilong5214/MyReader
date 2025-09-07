package com.folioreader.ui.activity

import android.app.SearchManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.folioreader.Config
import com.folioreader.R
import com.folioreader.databinding.ActivitySearchBinding
import com.folioreader.model.locators.SearchLocator
import com.folioreader.ui.adapter.ListViewType
import com.folioreader.ui.adapter.OnItemClickListener
import com.folioreader.ui.adapter.SearchAdapter
import com.folioreader.ui.view.FolioSearchView
import com.folioreader.util.AppUtil
import com.folioreader.util.UiUtil
import com.folioreader.viewmodels.SearchViewModel
import java.lang.reflect.Field

class SearchActivity : AppCompatActivity(), OnItemClickListener {

    companion object {
        @JvmField
        val LOG_TAG: String = SearchActivity::class.java.simpleName
        const val BUNDLE_SPINE_SIZE = "BUNDLE_SPINE_SIZE"
        const val BUNDLE_SEARCH_URI = "BUNDLE_SEARCH_URI"
        const val BUNDLE_SAVE_SEARCH_QUERY = "BUNDLE_SAVE_SEARCH_QUERY"
        const val BUNDLE_IS_SOFT_KEYBOARD_VISIBLE = "BUNDLE_IS_SOFT_KEYBOARD_VISIBLE"
        const val BUNDLE_FIRST_VISIBLE_ITEM_INDEX = "BUNDLE_FIRST_VISIBLE_ITEM_INDEX"
    }

    enum class ResultCode(val value: Int) {
        ITEM_SELECTED(2),
        BACK_BUTTON_PRESSED(3)
    }

    private lateinit var binding: ActivitySearchBinding
    private var spineSize: Int = 0
    private lateinit var searchUri: Uri
    private lateinit var searchView: FolioSearchView
    private lateinit var actionBar: ActionBar
    private var collapseButtonView: ImageButton? = null
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var searchAdapterDataBundle: Bundle
    private var savedInstanceStateBundle: Bundle? = null // Renamed from savedInstanceState to avoid confusion with onCreate parameter
    private var softKeyboardVisible: Boolean = true
    private lateinit var searchViewModel: SearchViewModel

    // To get collapseButtonView from toolbar for any click events
    private val toolbarOnLayoutChangeListener: View.OnLayoutChangeListener = object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            v: View?, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
        ) {

            for (i in 0 until binding.toolbar.childCount) {

                val view: View = binding.toolbar.getChildAt(i)
                val contentDescription: String? = view.contentDescription as String?
                if (TextUtils.isEmpty(contentDescription))
                    continue

                if (contentDescription == "Collapse") {
                    Log.v(LOG_TAG, "-> initActionBar -> mCollapseButtonView found")
                    collapseButtonView = view as ImageButton

                    collapseButtonView?.setOnClickListener {
                        Log.v(LOG_TAG, "-> onClick -> collapseButtonView")
                        navigateBack()
                    }

                    binding.toolbar.removeOnLayoutChangeListener(this)
                    return
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(LOG_TAG, "-> onCreate")

        val config: Config = AppUtil.getSavedConfig(this)!!
        if (config.isNightMode) {
            setTheme(R.style.FolioNightTheme)
        } else {
            setTheme(R.style.FolioDayTheme)
        }

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        this.savedInstanceStateBundle = savedInstanceState // Store for onRestoreInstanceState logic if needed elsewhere or later
        init(config)
    }

    private fun init(config: Config) {
        Log.v(LOG_TAG, "-> init")

        setSupportActionBar(binding.toolbar)
        binding.toolbar.addOnLayoutChangeListener(toolbarOnLayoutChangeListener)
        actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setDisplayShowTitleEnabled(false)

        try {
            val fieldCollapseIcon: Field = Toolbar::class.java.getDeclaredField("mCollapseIcon")
            fieldCollapseIcon.isAccessible = true
            val collapseIcon: Drawable = fieldCollapseIcon.get(binding.toolbar) as Drawable
            UiUtil.setColorIntToDrawable(config.themeColor, collapseIcon)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "-> ", e)
        }

        spineSize = intent.getIntExtra(BUNDLE_SPINE_SIZE, 0)
        searchUri = intent.getParcelableExtra<Uri>(BUNDLE_SEARCH_URI)!!

        searchAdapter = SearchAdapter(this)
        searchAdapter.onItemClickListener = this
        linearLayoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = searchAdapter

        searchViewModel = ViewModelProvider(this).get(SearchViewModel::class.java)
        
        // Initialize searchAdapterDataBundle, ensure liveAdapterDataBundle has a value first.
        if (searchViewModel.liveAdapterDataBundle.value == null) {
            searchViewModel.liveAdapterDataBundle.value = Bundle() // Or some default Bundle
        }
        searchAdapterDataBundle = searchViewModel.liveAdapterDataBundle.value!!


        val bundleFromFolioActivity = intent.getBundleExtra(SearchAdapter.DATA_BUNDLE)
        if (bundleFromFolioActivity != null) {
            searchViewModel.liveAdapterDataBundle.value = bundleFromFolioActivity
            searchAdapterDataBundle = bundleFromFolioActivity // already updated via observer, but direct assign is fine
            searchAdapter.changeDataBundle(bundleFromFolioActivity)
            val position = bundleFromFolioActivity.getInt(BUNDLE_FIRST_VISIBLE_ITEM_INDEX)
            Log.d(LOG_TAG, "-> onCreate -> scroll to previous position $position")
            binding.recyclerView.scrollToPosition(position)
        }

        searchViewModel.liveAdapterDataBundle.observe(this, Observer<Bundle> { dataBundle ->
            searchAdapterDataBundle = dataBundle
            searchAdapter.changeDataBundle(dataBundle)
        })
    }

    override fun onNewIntent(intent: Intent) {
        Log.v(LOG_TAG, "-> onNewIntent")
        super.onNewIntent(intent) // It's good practice to call super

        if (intent.hasExtra(BUNDLE_SEARCH_URI)) {
            searchUri = intent.getParcelableExtra<Uri>(BUNDLE_SEARCH_URI)!!
        } else {
            // This else block seems to ensure that the current intent always has these extras.
            // If searchUri was not updated from the new intent, the old one remains.
            // If spineSize was not in the new intent, the old one is added.
            intent.putExtra(BUNDLE_SEARCH_URI, searchUri) 
            intent.putExtra(BUNDLE_SPINE_SIZE, spineSize)
        }

        setIntent(intent)

        if (Intent.ACTION_SEARCH == intent.action)
            handleSearch()
    }

    private fun handleSearch() {
        Log.v(LOG_TAG, "-> handleSearch")

        val query: String? = intent.getStringExtra(SearchManager.QUERY) // Query can be null
        if (query != null) {
            val newDataBundle = Bundle()
            newDataBundle.putString(ListViewType.KEY, ListViewType.PAGINATION_IN_PROGRESS_VIEW.toString())
            newDataBundle.putParcelableArrayList("DATA", ArrayList<SearchLocator>())
            searchViewModel.liveAdapterDataBundle.value = newDataBundle

            searchViewModel.search(spineSize, query)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.v(LOG_TAG, "-> onSaveInstanceState")

        outState.putCharSequence(BUNDLE_SAVE_SEARCH_QUERY, searchView.query)
        outState.putBoolean(BUNDLE_IS_SOFT_KEYBOARD_VISIBLE, softKeyboardVisible)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.v(LOG_TAG, "-> onRestoreInstanceState")
        // The original code assigned to a member 'this.savedInstanceState'.
        // This is typically used in onCreate or onPostCreate.
        // For onRestoreInstanceState, usually, you directly use the passed 'savedInstanceState'.
        // Storing it in 'this.savedInstanceStateBundle' as done in onCreate might be for later use.
        // If it was used in onCreateOptionsMenu, that part should be fine.
        this.savedInstanceStateBundle = savedInstanceState 
    }

    private fun navigateBack() {
        Log.v(LOG_TAG, "-> navigateBack")

        val intent = Intent()
        searchAdapterDataBundle.putInt(
            BUNDLE_FIRST_VISIBLE_ITEM_INDEX,
            linearLayoutManager.findFirstVisibleItemPosition()
        )
        intent.putExtra(SearchAdapter.DATA_BUNDLE, searchAdapterDataBundle)
        intent.putExtra(BUNDLE_SAVE_SEARCH_QUERY, searchView.query)
        setResult(ResultCode.BACK_BUTTON_PRESSED.value, intent)
        finish()
    }

    override fun onBackPressed() {
        Log.v(LOG_TAG, "-> onBackPressed")
        // Default behavior is to call navigateBack() or finish().
        // If this is meant to be custom, ensure it does what's expected.
        // Often, it's super.onBackPressed() or a custom navigation like navigateBack().
        // For now, let's assume navigateBack() is the desired behavior based on collapse button.
        navigateBack() 
        // super.onBackPressed() // Or this, if navigateBack() is too specific.
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean { // Menu is non-null here
        Log.v(LOG_TAG, "-> onCreateOptionsMenu")
        menuInflater.inflate(R.menu.menu_search, menu)

        val config: Config = AppUtil.getSavedConfig(applicationContext)!!
        val itemSearch: MenuItem = menu.findItem(R.id.itemSearch)
        // Ensure itemSearch.icon is not null before using it, though findItem usually guarantees it if ID is valid
        itemSearch.icon?.let { UiUtil.setColorIntToDrawable(config.themeColor, it) }


        searchView = itemSearch.actionView as FolioSearchView
        searchView.init(componentName, config)

        itemSearch.expandActionView()

        val currentSavedInstanceState = this.savedInstanceStateBundle ?: intent.getBundleExtra(SearchAdapter.DATA_BUNDLE)

        if (currentSavedInstanceState != null) {
            searchView.setQuery(
                currentSavedInstanceState.getCharSequence(BUNDLE_SAVE_SEARCH_QUERY),
                false
            )
            // Restore softKeyboardVisible only if BUNDLE_IS_SOFT_KEYBOARD_VISIBLE is present in savedInstanceStateBundle
            if (this.savedInstanceStateBundle?.containsKey(BUNDLE_IS_SOFT_KEYBOARD_VISIBLE) == true) {
                 softKeyboardVisible = this.savedInstanceStateBundle!!.getBoolean(BUNDLE_IS_SOFT_KEYBOARD_VISIBLE)
            }
            if (!softKeyboardVisible)
                AppUtil.hideKeyboard(this)
        } else {
            val searchQuery: CharSequence? = intent.getCharSequenceExtra(BUNDLE_SAVE_SEARCH_QUERY)
            if (!TextUtils.isEmpty(searchQuery)) {
                searchView.setQuery(searchQuery, false)
                AppUtil.hideKeyboard(this)
                softKeyboardVisible = false
            }
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {
                softKeyboardVisible = false
                searchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {

                if (TextUtils.isEmpty(newText)) {
                    Log.v(LOG_TAG, "-> onQueryTextChange -> Empty Query")
                    searchViewModel.cancelAllSearchCalls()
                    searchViewModel.init()

                    val intentBroadcast = Intent(FolioActivity.ACTION_SEARCH_CLEAR)
                    LocalBroadcastManager.getInstance(this@SearchActivity).sendBroadcast(intentBroadcast)
                }
                return false
            }
        })

        itemSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {

            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                Log.v(LOG_TAG, "-> onMenuItemActionCollapse")
                navigateBack()
                return false
            }
        })

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) softKeyboardVisible = true
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // item is non-null

        val itemId = item.itemId

        if (itemId == R.id.itemSearch) {
            Log.v(LOG_TAG, "-> onOptionsItemSelected -> ${item.title}")
            //onSearchRequested()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onItemClick(
        adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>,
        viewHolder: RecyclerView.ViewHolder, position: Int, id: Long
    ) {

        if (adapter is SearchAdapter) {
            if (viewHolder is SearchAdapter.NormalViewHolder) {
                Log.v(LOG_TAG, "-> onItemClick -> " + viewHolder.searchLocator)

                val intentResult = Intent() // Renamed from intent to avoid confusion
                searchAdapterDataBundle.putInt(
                    BUNDLE_FIRST_VISIBLE_ITEM_INDEX,
                    linearLayoutManager.findFirstVisibleItemPosition()
                )
                intentResult.putExtra(SearchAdapter.DATA_BUNDLE, searchAdapterDataBundle)
                intentResult.putExtra(FolioActivity.EXTRA_SEARCH_ITEM, viewHolder.searchLocator as Parcelable)
                intentResult.putExtra(BUNDLE_SAVE_SEARCH_QUERY, searchView.query)
                setResult(ResultCode.ITEM_SELECTED.value, intentResult)
                finish()
            }
        }
    }
}
