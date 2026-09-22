package com.fuckinlauncher.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.contains
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.net.toUri
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var homeContainer: HorizontalScrollView
    private lateinit var homePages: LinearLayout
    private lateinit var appDrawer: LinearLayout
    private lateinit var appScroll: ScrollView
    private lateinit var appGrid: LinearLayout
    private lateinit var pageIndicator: TextView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView

    private var currentSearchQuery = ""

    private var themePrimaryColor = Color.WHITE
    private var themeCardColor = "#33FFFFFF".toColorInt()
    private var themeSearchColor = "#22FFFFFF".toColorInt()

    private var pageCount = 3
    private var drawerGridColumns = 4

    private val timeHandler = Handler(Looper.getMainLooper())

    private val timeRunnable = object : Runnable {
        override fun run() {
            updateClock()
            timeHandler.postDelayed(this, 1000)
        }
    }

    private val homeGrids =
        mutableListOf<HomeGrid>()

    private val homePackages =
        mutableListOf<MutableList<String>>()

    private var currentPage = 0

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var movingPackage: String? = null
    private var movingPage = -1
    private var movingOriginalIndex = -1
    private var movingOriginalView: View? = null
    private var floatingView: View? = null

    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private var lastAutoPageTime = 0L

    private val prefsName = "launcher_layout"

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        buildLauncher()
        loadHomeLayout()

        currentPage = 0

        homeContainer.post {
            homeContainer.scrollTo(0, 0)
            updatePageIndicator()
        }

        timeHandler.post(timeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(timeRunnable)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        if (floatingView != null) {

            when (event.actionMasked) {

                MotionEvent.ACTION_MOVE -> {

                    floatingView?.x =
                        event.rawX - dragOffsetX

                    floatingView?.y =
                        event.rawY - dragOffsetY

                    handleAutoPageChange(event.rawX)

                    return true
                }

                MotionEvent.ACTION_UP -> {

                    finishMoving(
                        event.rawX,
                        event.rawY
                    )

                    return true
                }

                MotionEvent.ACTION_CANCEL -> {

                    cancelMoving()

                    return true
                }
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchStartX = event.rawX
            touchStartY = event.rawY
        }

        if (
            event.actionMasked == MotionEvent.ACTION_UP &&
            ::appDrawer.isInitialized &&
            appDrawer.isVisible
        ) {

            val deltaY =
                event.rawY - touchStartY

            val deltaX =
                event.rawX - touchStartX

            if (
                !appScroll.canScrollVertically(-1) &&
                deltaY > dp(120) &&
                abs(deltaY) > abs(deltaX)
            ) {

                closeDrawer()
                return true
            }
        }

        return super.dispatchTouchEvent(event)
    }

    // ============================================================
    // BUILD
    // ============================================================

    @Suppress("DEPRECATION")
    private fun buildLauncher() {

        val wallpaperManager =
            WallpaperManager.getInstance(this@MainActivity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

            try {

                @SuppressLint("MissingPermission")
                val colors =
                    wallpaperManager.getWallpaperColors(
                        WallpaperManager.FLAG_SYSTEM
                    )

                if (colors != null) {

                    val primary =
                        colors.primaryColor.toArgb()

                    themePrimaryColor =
                        if (
                            ColorUtils.calculateLuminance(primary) < 0.25
                        ) {
                            Color.WHITE
                        } else {
                            primary
                        }

                    themeCardColor =
                        ColorUtils.setAlphaComponent(
                            themePrimaryColor,
                            0x3A
                        )

                    themeSearchColor =
                        ColorUtils.setAlphaComponent(
                            themePrimaryColor,
                            0x22
                        )
                }

            } catch (_: Exception) {
            }
        }

        root =
            FrameLayout(this).apply {

                try {

                    @SuppressLint("MissingPermission")
                    val wallpaperDrawable =
                        wallpaperManager.drawable

                    if (wallpaperDrawable != null) {
                        background = wallpaperDrawable
                    } else {
                        setBackgroundColor(Color.TRANSPARENT)
                    }

                } catch (_: Exception) {
                    setBackgroundColor(Color.TRANSPARENT)
                }
            }

        val mainLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

        val clockContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    0,
                    dp(60),
                    0,
                    dp(10)
                )
            }

        clockView =
            TextView(this).apply {

                textSize = 54f

                setTextColor(
                    themePrimaryColor
                )

                gravity =
                    Gravity.CENTER

                setShadowLayer(
                    8f,
                    0f,
                    4f,
                    Color.BLACK
                )
            }

        dateView =
            TextView(this).apply {

                textSize = 16f

                setTextColor(
                    themePrimaryColor
                )

                gravity =
                    Gravity.CENTER

                setShadowLayer(
                    6f,
                    0f,
                    2f,
                    Color.BLACK
                )
            }

        clockContainer.addView(clockView)
        clockContainer.addView(dateView)

        mainLayout.addView(clockContainer)

        buildHome()
        buildDrawer()

        mainLayout.addView(
            homeContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            mainLayout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        pageIndicator =
            TextView(this).apply {

                setTextColor(Color.WHITE)

                textSize = 18f

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(12),
                    dp(4),
                    dp(12),
                    dp(4)
                )
            }

        val indicatorParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply {

                gravity =
                    Gravity.BOTTOM or
                            Gravity.CENTER_HORIZONTAL

                bottomMargin =
                    dp(65)
            }

        root.addView(
            pageIndicator,
            indicatorParams
        )

        root.addView(
            appDrawer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        appDrawer.visibility =
            View.INVISIBLE

        appDrawer.post {
            appDrawer.translationY =
                appDrawer.height.toFloat()
        }

        setContentView(root)

        updatePageIndicator()
    }

    // ============================================================
    // HOME
    // ============================================================

    private fun buildHome() {

        homeContainer =
            HomeScrollView(this)

        homeContainer.apply {

            isHorizontalScrollBarEnabled = false

            isFillViewport = true

            overScrollMode =
                View.OVER_SCROLL_NEVER

            setBackgroundColor(
                Color.TRANSPARENT
            )
        }

        homePages =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                setBackgroundColor(
                    Color.TRANSPARENT
                )
            }

        homeGrids.clear()
        homePackages.clear()

        val screenWidth =
            resources.displayMetrics.widthPixels

        repeat(pageCount) {

            homePackages.add(
                mutableListOf()
            )

            val pageFrame =
                FrameLayout(this).apply {
                    setBackgroundColor(
                        Color.TRANSPARENT
                    )
                }

            val grid =
                HomeGrid(this)

            homeGrids.add(grid)

            pageFrame.addView(
                grid,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            homePages.addView(
                pageFrame,
                LinearLayout.LayoutParams(
                    screenWidth,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        homeContainer.removeAllViews()

        homeContainer.addView(
            homePages,
            ViewGroup.LayoutParams(
                screenWidth * pageCount,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    // ============================================================
    // APP DRAWER
    // ============================================================

    @SuppressLint("SetTextI18n")
    private fun buildDrawer() {

        appDrawer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(Color.BLACK)
            }

        val header =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(18),
                    dp(70),
                    dp(18),
                    dp(8)
                )
            }

        val title =
            TextView(this).apply {

                text = "APPS"

                textSize = 22f

                setTextColor(Color.WHITE)

                setOnClickListener {

                    val items =
                        arrayOf(
                            "3 Pages",
                            "4 Pages",
                            "5 Pages",
                            "6 Pages"
                        )

                    AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle(
                            "Configure Home Screens"
                        )
                        .setItems(
                            items
                        ) { _, which ->

                            val newCount =
                                which + 3

                            if (
                                newCount != pageCount
                            ) {

                                pageCount =
                                    newCount

                                buildLauncher()
                                loadHomeLayout()

                                Toast.makeText(
                                    this@MainActivity,
                                    "Workspace updated to $pageCount pages!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                        }
                        .show()
                }
            }

        val settingsButton =
            ImageView(this).apply {

                setImageResource(
                    android.R.drawable.ic_menu_manage
                )

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                setColorFilter(Color.WHITE)

                setOnClickListener {

                    val subOptions =
                        arrayOf(
                            "4 Columns Grid",
                            "5 Columns Grid",
                            "6 Columns Grid"
                        )

                    AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle(
                            "App Drawer Sizing Columns"
                        )
                        .setItems(
                            subOptions
                        ) { _, whichGrid ->

                            drawerGridColumns =
                                whichGrid + 4

                            populateApps()

                            Toast.makeText(
                                this@MainActivity,
                                "App Grid updated to $drawerGridColumns columns!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .show()
                }
            }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(settingsButton)

        appDrawer.addView(header)

        val searchBar =
            EditText(this).apply {

                hint =
                    "Search apps..."

                setHintTextColor(
                    "#88FFFFFF".toColorInt()
                )

                setTextColor(Color.WHITE)

                textSize = 16f

                setPadding(
                    dp(16),
                    dp(10),
                    dp(16),
                    dp(10)
                )

                setSingleLine(true)

                val searchBg =
                    GradientDrawable().apply {

                        setColor(
                            themeSearchColor
                        )

                        cornerRadius =
                            dp(12).toFloat()
                    }

                background =
                    searchBg

                addTextChangedListener(
                    object : TextWatcher {

                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {

                            currentSearchQuery =
                                s?.toString()
                                    ?.trim()
                                    ?.lowercase()
                                    ?: ""

                            populateApps()
                        }

                        override fun afterTextChanged(
                            s: Editable?
                        ) {}
                    }
                )
            }

        val searchParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {

                setMargins(
                    dp(16),
                    0,
                    dp(16),
                    dp(10)
                )
            }

        appDrawer.addView(
            searchBar,
            searchParams
        )

        appScroll =
            ScrollView(this).apply {
                overScrollMode =
                    View.OVER_SCROLL_NEVER
            }

        appGrid =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(30)
                )
            }

        appScroll.addView(appGrid)

        appDrawer.addView(
            appScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        populateApps()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun populateApps() {

        appGrid.removeAllViews()

        var apps =
            packageManager
                .getInstalledApplications(0)
                .filter {

                    it.enabled &&
                            it.packageName != packageName &&
                            packageManager
                                .getLaunchIntentForPackage(
                                    it.packageName
                                ) != null
                }
                .sortedBy {

                    it.loadLabel(
                        packageManager
                    )
                        .toString()
                        .lowercase()
                }

        if (
            currentSearchQuery.isNotEmpty()
        ) {

            apps =
                apps.filter {

                    it.loadLabel(
                        packageManager
                    )
                        .toString()
                        .lowercase()
                        .contains(
                            currentSearchQuery
                        )
                }
        }

        val columns =
            drawerGridColumns

        var row:
                LinearLayout? = null

        for (
        (index, app)
        in apps.withIndex()
        ) {

            if (
                index % columns == 0
            ) {

                row =
                    LinearLayout(this).apply {

                        orientation =
                            LinearLayout.HORIZONTAL

                        gravity =
                            Gravity.CENTER
                    }

                appGrid.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            val cell =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        dp(4),
                        dp(8),
                        dp(4),
                        dp(14)
                    )

                    setOnClickListener {

                        launchApp(
                            app.packageName
                        )
                    }

                    setOnLongClickListener {

                        addAppToCurrentPage(
                            app.packageName
                        )

                        true
                    }
                }

            addAppIconAndName(
                cell,
                app
            )

            row?.addView(
                cell,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    // ============================================================
    // ADD TO HOME
    // ============================================================

    private fun addAppToCurrentPage(
        packageName: String
    ) {

        if (
            homePackages[currentPage]
                .contains(packageName)
        ) {

            Toast.makeText(
                this,
                "Already on this page",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        homePackages[currentPage]
            .add(packageName)

        val app =
            packageManager.getApplicationInfo(
                packageName,
                0
            )

        homeGrids[currentPage]
            .addView(
                createHomeAppView(
                    packageName,
                    app
                )
            )

        homeGrids[currentPage]
            .requestLayout()

        saveHomeLayout()

        closeDrawer()
    }

    // ============================================================
    // HOME APP
    // ============================================================

    private fun createHomeAppView(
        packageName: String,
        app: ApplicationInfo
    ): View {

        val cell =
            LinearLayout(this).apply {

                tag =
                    packageName

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(8),
                    dp(12),
                    dp(8),
                    dp(12)
                )

                val cardBg =
                    GradientDrawable().apply {

                        setColor(
                            themeCardColor
                        )

                        cornerRadius =
                            dp(16).toFloat()
                    }

                background =
                    cardBg

                setOnClickListener {

                    launchApp(
                        packageName
                    )
                }

                // HOLD APP = REMOVE FROM HOME
                // Does NOT uninstall the app.
                setOnLongClickListener {

                    removeFromHome(
                        packageName
                    )

                    true
                }
            }

        addAppIconAndName(
            cell,
            app
        )

        return cell
    }

    // ============================================================
    // REMOVE FROM HOME
    // ============================================================

    private fun removeFromHome(
        packageName: String
    ) {

        var removed =
            false

        for (
        page in 0 until pageCount
        ) {

            val index =
                homePackages[page]
                    .indexOf(packageName)

            if (index >= 0) {

                homePackages[page]
                    .removeAt(index)

                if (
                    index <
                    homeGrids[page].childCount
                ) {

                    homeGrids[page]
                        .removeViewAt(index)
                }

                homeGrids[page]
                    .requestLayout()

                removed =
                    true
            }
        }

        if (removed) {

            saveHomeLayout()

            Toast.makeText(
                this,
                "Removed from Home",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // START DRAG
    // ============================================================

    private fun startMoving(
        view: View,
        packageName: String
    ) {

        if (floatingView != null) {
            return
        }

        movingPackage =
            packageName

        movingOriginalView =
            view

        movingPage =
            findViewPage(view)

        if (movingPage < 0) {

            cancelMoving()

            return
        }

        movingOriginalIndex =
            homeGrids[movingPage]
                .indexOfChild(view)

        val location =
            IntArray(2)

        view.getLocationOnScreen(
            location
        )

        dragOffsetX =
            view.width / 2f

        dragOffsetY =
            view.height / 2f

        view.visibility =
            View.INVISIBLE

        val floating =
            createHomeAppView(
                packageName,
                packageManager
                    .getApplicationInfo(
                        packageName,
                        0
                    )
            )

        floating.alpha =
            0.85f

        root.addView(
            floating,
            FrameLayout.LayoutParams(
                view.width,
                view.height
            )
        )

        floating.x =
            location[0].toFloat()

        floating.y =
            location[1].toFloat()

        floatingView =
            floating
    }

    // ============================================================
    // AUTO PAGE
    // ============================================================

    private fun handleAutoPageChange(
        rawX: Float
    ) {

        val now =
            System.currentTimeMillis()

        if (
            now - lastAutoPageTime < 700
        ) {
            return
        }

        val screenWidth =
            resources.displayMetrics
                .widthPixels

        if (
            rawX >
            screenWidth - dp(60) &&
            currentPage <
            pageCount - 1
        ) {

            lastAutoPageTime =
                now

            goToPage(
                currentPage + 1
            )

            return
        }

        if (
            rawX < dp(60) &&
            currentPage > 0
        ) {

            lastAutoPageTime =
                now

            goToPage(
                currentPage - 1
            )
        }
    }

    // ============================================================
    // DROP
    // ============================================================

    private fun finishMoving(
        rawX: Float,
        rawY: Float
    ) {

        val packageName =
            movingPackage
                ?: return

        val oldPage =
            movingPage

        val oldIndex =
            movingOriginalIndex

        floatingView?.let {
            root.removeView(it)
        }

        floatingView =
            null

        val targetPage =
            currentPage

        val oldGrid =
            homeGrids[oldPage]

        if (
            oldIndex >= 0 &&
            oldIndex < oldGrid.childCount
        ) {

            oldGrid.removeViewAt(
                oldIndex
            )
        }

        homePackages[oldPage]
            .remove(packageName)

        val targetGrid =
            homeGrids[targetPage]

        var targetIndex =
            targetGrid.getDropIndex(
                rawX,
                rawY
            )

        if (
            oldPage == targetPage &&
            targetIndex > oldIndex
        ) {

            targetIndex--
        }

        targetIndex =
            targetIndex.coerceIn(
                0,
                homePackages[targetPage].size
            )

        homePackages[targetPage]
            .add(
                targetIndex,
                packageName
            )

        val app =
            packageManager.getApplicationInfo(
                packageName,
                0
            )

        val newView =
            createHomeAppView(
                packageName,
                app
            )

        targetGrid.addView(
            newView,
            targetIndex
        )

        newView.visibility =
            View.VISIBLE

        targetGrid.requestLayout()

        saveHomeLayout()

        clearMovingState()
    }

    // ============================================================
    // CANCEL DRAG
    // ============================================================

    private fun cancelMoving() {

        floatingView?.let {
            root.removeView(it)
        }

        floatingView =
            null

        movingOriginalView?.visibility =
            View.VISIBLE

        clearMovingState()
    }

    private fun clearMovingState() {

        movingPackage =
            null

        movingPage =
            -1

        movingOriginalIndex =
            -1

        movingOriginalView =
            null

        floatingView =
            null
    }

    // ============================================================
    // FIND PAGE
    // ============================================================

    private fun findViewPage(
        view: View
    ): Int {

        for (
        page in 0 until pageCount
        ) {

            if (
                view in homeGrids[page]
            ) {

                return page
            }
        }

        return -1
    }

    // ============================================================
    // ICON + NAME
    // ============================================================

    private fun addAppIconAndName(
        cell: LinearLayout,
        app: ApplicationInfo
    ) {

        val icon =
            ImageView(this).apply {

                setImageDrawable(
                    app.loadIcon(
                        packageManager
                    )
                )

                scaleType =
                    ImageView.ScaleType.FIT_CENTER
            }

        cell.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(64),
                dp(64)
            )
        )

        val name =
            TextView(this).apply {

                text =
                    app.loadLabel(
                        packageManager
                    ).toString()

                textSize =
                    12f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER

                maxLines =
                    2

                ellipsize =
                    android.text.TextUtils
                        .TruncateAt.END
            }

        cell.addView(name)
    }

    // ============================================================
    // SAVE
    // ============================================================

    private fun saveHomeLayout() {

        getSharedPreferences(
            prefsName,
            MODE_PRIVATE
        ).edit {

            for (
            page in 0 until pageCount
            ) {

                putString(
                    "page_$page",
                    homePackages[page]
                        .joinToString("|")
                )
            }
        }
    }

    // ============================================================
    // LOAD
    // ============================================================

    private fun loadHomeLayout() {

        val prefs =
            getSharedPreferences(
                prefsName,
                MODE_PRIVATE
            )

        for (
        page in 0 until pageCount
        ) {

            val saved =
                prefs.getString(
                    "page_$page",
                    ""
                ) ?: ""

            if (saved.isEmpty()) {
                continue
            }

            val packages =
                saved
                    .split("|")
                    .filter { pkg ->

                        try {

                            packageManager
                                .getApplicationInfo(
                                    pkg,
                                    0
                                )

                            true

                        } catch (
                            _: Exception
                        ) {

                            false
                        }
                    }

            homePackages[page]
                .addAll(packages)

            for (
            packageName
            in homePackages[page]
            ) {

                val app =
                    packageManager
                        .getApplicationInfo(
                            packageName,
                            0
                        )

                homeGrids[page]
                    .addView(
                        createHomeAppView(
                            packageName,
                            app
                        )
                    )
            }
        }
    }

    // ============================================================
    // LAUNCH
    // ============================================================

    private fun launchApp(
        packageName: String
    ) {

        packageManager
            .getLaunchIntentForPackage(
                packageName
            )
            ?.let {
                startActivity(it)
            }
    }

    // ============================================================
    // PAGES
    // ============================================================

    private fun goToPage(
        page: Int
    ) {

        if (
            page !in 0 until pageCount
        ) {
            return
        }

        currentPage =
            page

        val screenWidth =
            resources.displayMetrics
                .widthPixels

        homeContainer.smoothScrollTo(
            screenWidth * currentPage,
            0
        )

        updatePageIndicator()
    }

    // ============================================================
    // CLOCK
    // ============================================================

    private fun updateClock() {

        if (
            !::clockView.isInitialized ||
            !::dateView.isInitialized
        ) {
            return
        }

        val calendar =
            Calendar.getInstance()

        val timeFormat =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            )

        val dateFormat =
            SimpleDateFormat(
                "EEEE, MMMM d",
                Locale.getDefault()
            )

        clockView.text =
            timeFormat.format(
                calendar.time
            )

        dateView.text =
            dateFormat.format(
                calendar.time
            )
    }

    private fun updatePageIndicator() {

        if (
            !::pageIndicator.isInitialized
        ) {
            return
        }

        val dots =
            StringBuilder()

        for (
        i in 0 until pageCount
        ) {

            dots.append(
                if (
                    i == currentPage
                ) {
                    "●"
                } else {
                    "○"
                }
            )

            if (
                i < pageCount - 1
            ) {
                dots.append("   ")
            }
        }

        pageIndicator.text =
            dots.toString()
    }

    // ============================================================
    // DRAWER OPEN / CLOSE
    // ============================================================

    private fun openDrawer() {

        if (
            appDrawer.isVisible &&
            appDrawer.translationY == 0f
        ) {
            return
        }

        appDrawer.visibility =
            View.VISIBLE

        appDrawer.animate()
            .translationY(0f)
            .setDuration(300)
            .setListener(null)
            .start()
    }

    private fun closeDrawer() {

        if (!appDrawer.isVisible) {
            return
        }

        appDrawer.animate()
            .translationY(
                appDrawer.height.toFloat()
            )
            .setDuration(300)
            .setListener(
                object :
                    AnimatorListenerAdapter() {

                    override fun onAnimationEnd(
                        animation: Animator
                    ) {

                        appDrawer.visibility =
                            View.INVISIBLE
                    }
                }
            )
            .start()
    }

    // ============================================================
    // NOTIFICATIONS
    // ============================================================

    @SuppressLint("WrongConstant")
    private fun expandNotificationsPanel() {

        try {

            val statusBarService =
                getSystemService("statusbar")

            val expandMethod =
                statusBarService
                    .javaClass
                    .getMethod(
                        "expandNotificationsPanel"
                    )

            expandMethod.invoke(
                statusBarService
            )

        } catch (_: Exception) {
        }
    }

    // ============================================================
    // DP
    // ============================================================

    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        resources
                            .displayMetrics
                            .density
                ).toInt()
    }

    // ============================================================
    // HOME SWIPE CONTAINER
    // ============================================================

    private inner class HomeScrollView(
        context: android.content.Context
    ) : HorizontalScrollView(context) {

        private var downX = 0f
        private var downY = 0f
        private var gestureStarted = false

        override fun onInterceptTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    downX =
                        event.x

                    downY =
                        event.y

                    gestureStarted =
                        false

                    // IMPORTANT:
                    // Let the home app receive the touch.
                    return false
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        event.x - downX

                    val dy =
                        event.y - downY

                    if (
                        !gestureStarted &&
                        (
                                abs(dx) > dp(30) ||
                                        abs(dy) > dp(30)
                                )
                    ) {

                        gestureStarted =
                            true

                        // Only steal the gesture when
                        // it is actually a swipe.
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    gestureStarted =
                        false
                }
            }

            return false
        }

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (
                event.actionMasked
            ) {

                MotionEvent.ACTION_DOWN -> {

                    downX =
                        event.x

                    downY =
                        event.y

                    return true
                }

                MotionEvent.ACTION_UP -> {

                    val dx =
                        event.x - downX

                    val dy =
                        event.y - downY

                    val ax =
                        abs(dx)

                    val ay =
                        abs(dy)

                    gestureStarted =
                        false

                    when {

                        dy < -dp(100) &&
                                ay > ax -> {

                            openDrawer()

                            return true
                        }

                        dy > dp(100) &&
                                ay > ax -> {

                            expandNotificationsPanel()

                            return true
                        }

                        dx < -dp(100) &&
                                ax > ay -> {

                            goToPage(
                                currentPage + 1
                            )

                            return true
                        }

                        dx > dp(100) &&
                                ax > ay -> {

                            goToPage(
                                currentPage - 1
                            )

                            return true
                        }
                    }

                    return true
                }

                MotionEvent.ACTION_CANCEL -> {

                    gestureStarted =
                        false

                    return true
                }
            }

            return true
        }
    }

    // ============================================================
    // CUSTOM HOME GRID
    // ============================================================

    private inner class HomeGrid(
        context: android.content.Context
    ) : ViewGroup(context) {

        private val columns =
            4

        private val horizontalPadding =
            dp(12)

        private val verticalPadding =
            dp(30)

        private val columnGap =
            dp(2)

        private val rowGap =
            dp(4)

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int
        ) {

            val width =
                MeasureSpec.getSize(
                    widthMeasureSpec
                )

            val cellWidth =
                (
                        width -
                                horizontalPadding * 2 -
                                columnGap *
                                (columns - 1)
                        ) / columns

            for (
            i in 0 until childCount
            ) {

                val child =
                    getChildAt(i)

                child.measure(
                    MeasureSpec.makeMeasureSpec(
                        cellWidth,
                        MeasureSpec.EXACTLY
                    ),
                    MeasureSpec.makeMeasureSpec(
                        dp(105),
                        MeasureSpec.EXACTLY
                    )
                )
            }

            val rows =
                if (isEmpty()) {
                    0
                } else {
                    (
                            childCount +
                                    columns -
                                    1
                            ) / columns
                }

            val neededHeight =
                verticalPadding * 2 +
                        rows * dp(105) +
                        (
                                rows - 1
                                ).coerceAtLeast(0) *
                        rowGap

            val finalHeight =
                maxOf(
                    MeasureSpec.getSize(
                        heightMeasureSpec
                    ),
                    neededHeight
                )

            setMeasuredDimension(
                width,
                finalHeight
            )
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ) {

            val availableWidth =
                width -
                        horizontalPadding * 2 -
                        columnGap *
                        (columns - 1)

            val cellWidth =
                availableWidth /
                        columns

            val cellHeight =
                dp(105)

            for (
            i in 0 until childCount
            ) {

                val child =
                    getChildAt(i)

                val column =
                    i % columns

                val row =
                    i / columns

                val childLeft =
                    horizontalPadding +
                            column *
                            (
                                    cellWidth +
                                            columnGap
                                    )

                val childTop =
                    verticalPadding +
                            row *
                            (
                                    cellHeight +
                                            rowGap
                                    )

                child.layout(
                    childLeft,
                    childTop,
                    childLeft + cellWidth,
                    childTop + cellHeight
                )
            }
        }

        fun getDropIndex(
            rawX: Float,
            rawY: Float
        ): Int {

            if (isEmpty()) {
                return 0
            }

            val location =
                IntArray(2)

            getLocationOnScreen(
                location
            )

            val localX =
                rawX -
                        location[0]

            val localY =
                rawY -
                        location[1]

            val availableWidth =
                width -
                        horizontalPadding * 2 -
                        columnGap *
                        (columns - 1)

            val cellWidth =
                availableWidth /
                        columns

            val cellHeight =
                dp(105)

            val column =
                (
                        (
                                localX -
                                        horizontalPadding
                                ) /
                                (
                                        cellWidth +
                                                columnGap
                                        )
                        ).toInt()
                    .coerceIn(
                        0,
                        columns - 1
                    )

            val row =
                (
                        (
                                localY -
                                        verticalPadding
                                ) /
                                (
                                        cellHeight +
                                                rowGap
                                        )
                        ).toInt()
                    .coerceAtLeast(0)

            var index =
                row *
                        columns +
                        column

            if (
                index > childCount
            ) {
                index =
                    childCount
            }

            return index
        }
    }
}