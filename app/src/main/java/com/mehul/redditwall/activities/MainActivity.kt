@file:Suppress("DEPRECATION")

package com.mehul.redditwall.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.mehul.redditwall.R
import com.mehul.redditwall.adapters.ImageAdapter
import com.mehul.redditwall.objects.BitURL
import com.mehul.redditwall.objects.RecyclerListener
import com.mehul.redditwall.rest.QueryRequest
import kotlinx.coroutines.*

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var queryString = ""
    private var defaultLoad = ""
    private var search: EditText? = null
    private var hotImages = ArrayList<BitURL>()
    private var topImages = ArrayList<BitURL>()
    private var newImages = ArrayList<BitURL>()
    private var adapter: ImageAdapter? = null
    private var loading: ProgressBar? = null
    private var rootLayout: CoordinatorLayout? = null
    private var bottomLoading: ProgressBar? = null
    private var info: TextView? = null
    private var imageJob: Job? = null
    private var scrollJob: Job? = null
    private var uiScope = CoroutineScope(Dispatchers.Main)
    private var hotChip: Chip? = null
    private var newChip: Chip? = null
    private var topChip: Chip? = null
    private var currentSort: Int = 0
    private var preferences: SharedPreferences? = null

    @InternalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        hotChip = findViewById(R.id.hot_chip)
        newChip = findViewById(R.id.new_chip)
        topChip = findViewById(R.id.top_chip)
        loading = findViewById(R.id.loading)
        rootLayout = findViewById(R.id.main_root)
        info = findViewById(R.id.info)
        bottomLoading = findViewById(R.id.progressBar)
        search = findViewById(R.id.search)
        val imageScroll = findViewById<RecyclerView>(R.id.imageScroll)
        imageScroll.layoutManager = GridLayoutManager(this, 2)
        imageScroll.addOnItemTouchListener(RecyclerListener(this, imageScroll, object : RecyclerListener.OnItemClickListener {
            override fun onLongItemClick(view: View?, position: Int) {}

            override fun onItemClick(view: View, position: Int) {
                var p = position
                val currList: ArrayList<BitURL>? = when (currentSort) {
                    HOT -> hotImages
                    NEW -> newImages
                    else -> topImages
                }
                val limit = currList?.size
                //cancelThreads()
                p = if (p <= 0) 0 else p
                val current = currList!![p]
                val wallIntent = Intent(getCon(), WallActivity::class.java)
                wallIntent.apply {
                    putExtra(WallActivity.WALL_URL, current.getUrl())
                    putExtra(WallActivity.GIF, current.hasGif())
                    putExtra(WallActivity.FROM_FAV, false)
                    putExtra(PostActivity.POST_LINK, current.postLink)
                    putExtra(QUERY, queryString)
                }
                val prevs = ArrayList<BitURL>()
                for (i in (if (p >= 10) p - 10 else 0) until limit!!) {
                    prevs.add(currList[i])
                }
                val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
                val index = if (p >= 10) 10 else p
                val jsonList = gson.toJson(prevs)
                wallIntent.putExtra(WallActivity.INDEX, index)
                wallIntent.putExtra(WallActivity.LIST, jsonList)
                getCon().startActivity(wallIntent)
            }
        }))
        preferences = getSharedPreferences(SharedPrefFile, Context.MODE_PRIVATE)
        val dark = preferences!!.getBoolean(SettingsActivity.DARK, false)
        if (dark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            delegate.applyDayNight()
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            delegate.applyDayNight()
        }
        defaultLoad = preferences!!.getString(SettingsActivity.DEFAULT, "mobilewallpaper").toString()
        changeChipColor(-1)
        when (preferences!!.getInt(SettingsActivity.SORT_METHOD, HOT)) {
            HOT -> {
                adapter = ImageAdapter(this, hotImages)
                currentSort = HOT
                changeChipColor(1)
            }
            NEW -> {
                adapter = ImageAdapter(this, newImages)
                currentSort = NEW
                changeChipColor(0)
            }
            else -> {
                adapter = ImageAdapter(this, topImages)
                currentSort = TOP
                changeChipColor(2)
            }
        }
        imageScroll.adapter = adapter
        hotChip!!.setOnClickListener(this)
        newChip!!.setOnClickListener(this)
        topChip!!.setOnClickListener(this)
        val savedIntent = intent
        defaultLoad = (if (savedIntent.getBooleanExtra(OVERRIDE, false)) {
            savedIntent.getStringExtra(SAVED)
        } else {
            preferences!!.getString(SettingsActivity.DEFAULT, "mobilewallpaper")
        }).toString()
        queryString = defaultLoad
        search!!.hint = defaultLoad
        if (networkAvailable()) {
            imageJob = uiScope.launch {
                loadImages(getCon(), defaultLoad, true, getList())
            }
        } else {
            info!!.visibility = View.VISIBLE
            info!!.text = "No Network"
        }
        imageScroll.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0) {
                    if (imageJob != null && imageJob!!.isActive) {
                        return
                    }

                    if (scrollJob == null) {
                        cancelThreads()
                        scrollJob = uiScope.launch {
                            loadImages(getCon(), if (queryString.isEmpty()) defaultLoad else queryString, false, getList())
                        }
                    } else if (!scrollJob!!.isActive) {
                        cancelThreads()

                        scrollJob = uiScope.launch {
                            loadImages(getCon(), if (queryString.isEmpty()) defaultLoad else queryString, false, getList())
                        }
                    }
                } else {
                    bottomLoading?.visibility = View.INVISIBLE
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val isRunning = scrollJob?.isActive ?: false

                if (!recyclerView.canScrollVertically(1) && isRunning) {
                    bottomLoading?.visibility = View.VISIBLE
                } else if (!recyclerView.canScrollVertically(1) && !isRunning) {
                    bottomLoading?.visibility = View.VISIBLE
                    cancelThreads()

                    scrollJob = uiScope.launch {
                        loadImages(getCon(), if (queryString.isEmpty()) defaultLoad else queryString, false, getList())
                    }
                } else {
                    bottomLoading?.visibility = View.INVISIBLE
                }
            }
        })

        search!!.setOnEditorActionListener(OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch(findViewById(R.id.search_button))
                return@OnEditorActionListener true
            }
            false
        })
    }

    private fun changeChipColor(highlight: Int) {
        newChip!!.setChipBackgroundColorResource(R.color.white)
        hotChip!!.setChipBackgroundColorResource(R.color.white)
        topChip!!.setChipBackgroundColorResource(R.color.white)
        newChip!!.setTextColor(Color.BLACK)
        hotChip!!.setTextColor(Color.BLACK)
        topChip!!.setTextColor(Color.BLACK)
        when (highlight) {
            0 -> {
                newChip!!.setChipBackgroundColorResource(R.color.chip)
                newChip!!.setTextColor(Color.WHITE)
            }
            1 -> {
                hotChip!!.setChipBackgroundColorResource(R.color.chip)
                hotChip!!.setTextColor(Color.WHITE)
            }
            2 -> {
                topChip!!.setChipBackgroundColorResource(R.color.chip)
                topChip!!.setTextColor(Color.WHITE)
            }
            else -> return
        }
    }

    private fun getList(): ArrayList<BitURL> = when (currentSort) {
        NEW -> newImages
        HOT -> hotImages
        else -> topImages
    }

    private fun getCon(): Context {
        return this
    }

    public override fun onDestroy() {
        super.onDestroy()
        Log.e("DESTROY", "DESTROY")
        cancelThreads()
    }

    private fun networkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    @InternalCoroutinesApi
    fun startSearch(view: View) {
        AFTER_HOT = ""
        AFTER_NEW = ""
        AFTER_TOP = ""
        cancelThreads()
        queryString = ""
        loading!!.visibility = View.VISIBLE
        info!!.visibility = View.INVISIBLE
        newImages.clear()
        hotImages.clear()
        topImages.clear()
        adapter!!.notifyDataSetChanged()
        queryString = search!!.text.toString().replace(" ", "")
        val inputManager: InputMethodManager? = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager?.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

        if (networkAvailable()) {
            if (queryString.isNotEmpty()) {
                imageJob = uiScope.launch {
                    loadImages(getCon(), queryString, true, getList())
                }
            } else {
                queryString = defaultLoad
                imageJob = uiScope.launch {
                    loadImages(getCon(), defaultLoad, true, getList())
                }
            }
        } else {
            info!!.visibility = View.VISIBLE
            info!!.text = "No Network"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.saved_subs -> {
                val launchSaved = Intent(this, SavedActivity::class.java)
                startActivity(launchSaved)
                return true
            }
            R.id.settings -> {
                val launchSettings = Intent(this, SettingsActivity::class.java)
                startActivity(launchSettings)
                return true
            }
            R.id.fav_pics -> {
                val launchFav = Intent(this, FavImageActivity::class.java)
                startActivity(launchFav)
                return true
            }
            R.id.recc_subs -> {
                val launchRec = Intent(this, RecActivity::class.java)
                startActivity(launchRec)
                return true
            }
            R.id.history -> {
                val launchHist = Intent(this, HistoryActivity::class.java)
                startActivity(launchHist)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun cancelThreads() {

        if (imageJob != null) {
            loading!!.visibility = View.GONE
            imageJob!!.cancel()
        }

        if (scrollJob != null) {
            bottomLoading!!.visibility = View.GONE
            scrollJob!!.cancel()
        }
    }

    @InternalCoroutinesApi
    private fun runQuery() {
        imageJob = uiScope.launch {
            loadImages(getCon(), if (queryString.isEmpty()) defaultLoad else queryString, true, getList())
        }
    }

    override fun onBackPressed() {
        val confirmExit =
                MaterialAlertDialogBuilder(getCon(), R.style.MyThemeOverlayAlertDialog).apply {
                    setTitle("Exit App")
                    setMessage("Do you want to exit the app?")

                    setPositiveButton("Yes") { _, _ ->
                        (getCon() as Activity).finishAffinity()
                    }
                    setNegativeButton("No") { _, _ ->
                        //Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_SHORT).show()
                        Snackbar.make(rootLayout!!, "Cancelled", Snackbar.LENGTH_SHORT).show()
                    }
                }
        confirmExit.show()
    }

    @InternalCoroutinesApi
    override fun onClick(view: View) {
        val temp: String? = when (currentSort) {
            NEW -> AFTER_NEW
            HOT -> AFTER_HOT
            TOP -> AFTER_TOP
            else -> null
        }
        if (imageJob != null && imageJob!!.isActive && temp == null) {
            //Toast.makeText(this, "Please Wait", Toast.LENGTH_SHORT).show()
            Snackbar.make(rootLayout!!, "Please wait", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (imageJob != null && imageJob!!.isActive || scrollJob != null && scrollJob!!.isActive) {
            cancelThreads()
        }

        adapter!!.notifyDataSetChanged()
        val prefEdit = preferences!!.edit()
        if (view == hotChip) {
            currentSort = HOT
            Log.e("CLICk", "CLICKED HOT")
            prefEdit.putInt(SettingsActivity.SORT_METHOD, HOT)
            prefEdit.apply()
            changeChipColor(1)
            if (hotImages.isNotEmpty()) {
                adapter!!.setList(hotImages)
            } else {
                adapter!!.setList(hotImages)
                runQuery()
            }
        } else if (view == newChip) {
            currentSort = NEW
            Log.e("CLICk", "CLICKED NEW")
            prefEdit.putInt(SettingsActivity.SORT_METHOD, NEW)
            prefEdit.apply()
            changeChipColor(0)
            if (newImages.isNotEmpty()) {
                adapter!!.setList(newImages)
            } else {
                adapter!!.setList(newImages)
                runQuery()
            }
        } else if (view == topChip) {
            currentSort = TOP
            Log.e("CLICk", "CLICKED TOP")
            prefEdit.putInt(SettingsActivity.SORT_METHOD, TOP)
            prefEdit.apply()
            changeChipColor(2)
            if (topImages.isNotEmpty()) {
                adapter!!.setList(topImages)
            } else {
                adapter!!.setList(topImages)
                runQuery()
            }
        }
    }

    @InternalCoroutinesApi
    private suspend fun loadImages(con: Context, query: String, first: Boolean, images: ArrayList<BitURL>) {
        if (first) {
            loading?.visibility = View.VISIBLE
        }
        info?.visibility = View.GONE
        withContext(Dispatchers.IO) {
            val json = QueryRequest.getQueryJson(query, first, con)
            withContext(Dispatchers.Default) {
                QueryRequest.loadImgsFromJSON(json, adapter, con, images, if (first) loading else bottomLoading, first)
            }
        }
        adapter?.notifyDataSetChanged()
        if (first) {
            loading?.visibility = View.GONE
        } else {
            bottomLoading?.visibility = View.INVISIBLE
        }

        if (first && adapter?.itemCount == 0) {
            Log.e("LIST", getList().toString())
            info?.visibility = View.VISIBLE
            info?.text = "Subreddit does not exist or it has no images"
        }
    }

    companion object {
        const val SharedPrefFile = "com.mehul.redditwall"
        const val SAVED = "SAVED"
        const val OVERRIDE = "OVERRIDE"
        const val QUERY = "QUERY"
        const val NEW = 0
        const val HOT = 1
        const val TOP = 2
        var AFTER_NEW: String? = null
        var AFTER_HOT: String? = null
        var AFTER_TOP: String? = null

        public fun getDimensions(con: Context): IntArray {
            val disp = DisplayMetrics()
            (con as Activity).windowManager.defaultDisplay.getMetrics(disp)
            return intArrayOf(disp.widthPixels, disp.heightPixels)
        }
    }
}