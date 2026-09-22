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
    private var iconSizeDp = 64
    private var showAppLabels = true
    private var drawerAlpha = 0.94f

    private val folderNames = mutableMapOf<String, String>()
    private val folderApps = mutableMapOf<String, MutableList<String>>()

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
    private var movingTrashView: TextView? = null
    private var settingsOverlay: View? = null

    private val prefsName = "launcher_layout"

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)

        loadCustomization()
        loadFolderData()
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

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val deltaY = event.rawY - touchStartY
            val deltaX = event.rawX - touchStartX

            if (::appDrawer.isInitialized && appDrawer.isVisible) {
                if (deltaY > dp(110) && abs(deltaY) > abs(deltaX) * 1.15f) {
                    closeDrawer()
                    return true
                }
            } else if (deltaY < -dp(140) && abs(deltaY) > abs(deltaX) * 1.15f) {
                openDrawer()
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

                setBackgroundColor(Color.argb((drawerAlpha * 255).toInt(), 0, 0, 0))
                alpha = 0f
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

                setOnClickListener { showCustomizationMenu() }
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

                setOnClickListener { showCustomizationMenu() }
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
                        animatePress(this)
                        launchApp(app.packageName)
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

        homeGrids[currentPage].requestLayout()
        animateAddedView(homeGrids[currentPage].getChildAt(homeGrids[currentPage].childCount - 1))
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
                    dp(8),                    dp(12)                )

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

                // Tap = open. Hold = pick up and drag anywhere.
                var holdTriggered = false
                var downX = 0f
                var downY = 0f
                val homeCell = this

                val holdRunnable = Runnable {
                    holdTriggered = true
                    startMoving(homeCell, packageName)
                }

                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            holdTriggered = false
                            downX = event.rawX
                            downY = event.rawY
                            removeCallbacks(holdRunnable)
                            postDelayed(holdRunnable, 600L)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val movedX = abs(event.rawX - downX)
                            val movedY = abs(event.rawY - downY)
                            if (!holdTriggered && (movedX > dp(18) || movedY > dp(18))) {
                                removeCallbacks(holdRunnable)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            removeCallbacks(holdRunnable)
                            if (!holdTriggered && floatingView == null) {
                                animatePress(homeCell)
                                launchApp(packageName)
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            removeCallbacks(holdRunnable)
                            true
                        }
                        else -> true
                    }
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

                homeGrids[page].requestLayout()
                animateHomeRefresh(homeGrids[page])
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

        movingTrashView = TextView(this).apply {
            text = "DROP TO REMOVE"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor("#CC8B1010".toColorInt())
                cornerRadius = dp(24).toFloat()
            }
        }

        root.addView(movingTrashView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(32)
        })
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

    private fun finishMoving(rawX: Float, rawY: Float) {
        val packageName = movingPackage ?: return

        val trash = movingTrashView
        val tl = IntArray(2)
        trash?.getLocationOnScreen(tl)
        val inTrash = trash != null &&
            rawX >= tl[0] && rawX <= tl[0] + trash.width &&
            rawY >= tl[1] && rawY <= tl[1] + trash.height

        floatingView?.let { root.removeView(it) }
        movingTrashView?.let { root.removeView(it) }
        floatingView = null
        movingTrashView = null

        val oldPage = movingPage
        val oldIndex = movingOriginalIndex
        if (oldPage !in 0 until pageCount) {
            clearMovingState()
            return
        }

        if (oldIndex >= 0 && oldIndex < homeGrids[oldPage].childCount) {
            homeGrids[oldPage].removeViewAt(oldIndex)
        }
        homePackages[oldPage].remove(packageName)

        if (inTrash) {
            saveHomeLayout()
            Toast.makeText(this, "Removed from Home", Toast.LENGTH_SHORT).show()
            clearMovingState()
            return
        }

        val targetPage = currentPage
        val targetGrid = homeGrids[targetPage]
        val app = packageManager.getApplicationInfo(packageName, 0)
        val newView = createHomeAppView(packageName, app)

        targetGrid.addView(newView, FrameLayout.LayoutParams(
            dp(iconSizeDp + 28),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        homePackages[targetPage].add(packageName)

        targetGrid.post {
            val gl = IntArray(2)
            targetGrid.getLocationOnScreen(gl)
            val maxX = (targetGrid.width - newView.width).coerceAtLeast(0)
            val maxY = (targetGrid.height - newView.height).coerceAtLeast(0)
            newView.translationX =
                (rawX - gl[0] - newView.width / 2f).coerceIn(0f, maxX.toFloat())
            newView.translationY =
                (rawY - gl[1] - newView.height / 2f).coerceIn(0f, maxY.toFloat())
            animateAddedView(newView)
            saveHomeLayout()
        }

        clearMovingState()
    }

    // ============================================================
    // CANCEL DRAG
    // ============================================================

    private fun cancelMoving() {

        floatingView?.let { root.removeView(it) }
        movingTrashView?.let { root.removeView(it) }
        floatingView = null
        movingTrashView = null
        movingOriginalView?.visibility = View.VISIBLE

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
        app: ApplicationInfo,
        sizeDp: Int = iconSizeDp
    ) {
        val icon = ImageView(this).apply {
            setImageDrawable(app.loadIcon(packageManager))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        cell.addView(icon, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
        if (showAppLabels) {
            val name = TextView(this).apply {
                text = app.loadLabel(packageManager).toString()
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            cell.addView(name, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    // ============================================================
    // SAVE
    // ============================================================

    private fun saveHomeLayout() {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit {
            for (page in 0 until pageCount) putString("page_" + page, homePackages[page].joinToString("|"))
            for ((id, name) in folderNames) {
                putString("folder_name_" + id, name)
                putString("folder_apps_" + id, (folderApps[id] ?: mutableListOf()).joinToString("|"))
            }
            putInt("page_count", pageCount)
            putInt("drawer_columns", drawerGridColumns)
            putInt("icon_size", iconSizeDp)
            putBoolean("show_labels", showAppLabels)
            putFloat("drawer_alpha", drawerAlpha)
        }
    }

    private fun loadCustomization() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        pageCount = prefs.getInt("page_count", 3).coerceIn(3, 6)
        drawerGridColumns = prefs.getInt("drawer_columns", 4).coerceIn(4, 6)
        iconSizeDp = prefs.getInt("icon_size", 64).coerceIn(48, 80)
        showAppLabels = prefs.getBoolean("show_labels", true)
        drawerAlpha = prefs.getFloat("drawer_alpha", 0.94f).coerceIn(0.70f, 1f)
    }

    private fun loadFolderData() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        folderNames.clear()
        folderApps.clear()
        for ((key, value) in prefs.all) if (key.startsWith("folder_name_")) {
            val id = key.removePrefix("folder_name_")
            folderNames[id] = value?.toString() ?: "Folder"
            folderApps[id] = (prefs.getString("folder_apps_" + id, "") ?: "").split("|")
                .filter { it.isNotBlank() }.toMutableList()
        }
    }

    // ============================================================
    // LOAD
    // ============================================================

    private fun loadHomeLayout() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        for (page in 0 until pageCount) {
            val saved = prefs.getString("page_" + page, "") ?: ""
            if (saved.isEmpty()) continue
            for (item in saved.split("|").filter { it.isNotBlank() }) {
                if (item.startsWith("folder:")) {
                    val id = item.removePrefix("folder:")
                    if (!folderNames.containsKey(id)) continue
                    homePackages[page].add(item)
                    homeGrids[page].addView(createFolderView(id))
                } else {
                    try {
                        packageManager.getApplicationInfo(item, 0)
                        homePackages[page].add(item)
                        homeGrids[page].addView(createHomeAppView(item, packageManager.getApplicationInfo(item, 0)))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // ============================================================
    // EDITING / FOLDERS / CUSTOMIZATION
    // ============================================================

    private fun showHomeItemMenu(itemId: String) {
        if (itemId.startsWith("folder:")) { showFolderMenu(itemId.removePrefix("folder:")); return }
        val pages = (0 until pageCount).map { "Page " + (it + 1) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Edit Home App")
            .setItems(arrayOf("Remove from Home","Move to page...","Create folder with this app")) { _, which ->
                when (which) {
                    0 -> removeFromHome(itemId)
                    1 -> AlertDialog.Builder(this).setTitle("Move to page").setItems(pages) { _, page -> moveItemToPage(itemId,page) }.show()
                    2 -> createFolder(itemId)
                }
            }.show()
    }

    private fun moveItemToPage(itemId:String,targetPage:Int) {
        val sourcePage=findItemPage(itemId)
        if(sourcePage<0||sourcePage==targetPage)return
        val index=homePackages[sourcePage].indexOf(itemId)
        homePackages[sourcePage].removeAt(index); homeGrids[sourcePage].removeViewAt(index)
        homePackages[targetPage].add(itemId)
        homeGrids[targetPage].addView(if(itemId.startsWith("folder:")) createFolderView(itemId.removePrefix("folder:")) else createHomeAppView(itemId,packageManager.getApplicationInfo(itemId,0)))
        animateHomeRefresh(homeGrids[sourcePage]); animateAddedView(homeGrids[targetPage].getChildAt(homeGrids[targetPage].childCount-1)); saveHomeLayout()
    }

    private fun createFolder(itemId:String) {
        val input=EditText(this).apply{hint="Folder name";setSingleLine(true);setPadding(dp(18),dp(12),dp(18),dp(12))}
        AlertDialog.Builder(this).setTitle("Create folder").setView(input)
            .setPositiveButton("Create"){_,_-> 
                val name=input.text.toString().trim().ifEmpty{"New Folder"}; val id=System.currentTimeMillis().toString()
                folderNames[id]=name; folderApps[id]=mutableListOf(itemId)
                val page=findItemPage(itemId); val index=if(page>=0)homePackages[page].indexOf(itemId)else-1
                if(page>=0&&index>=0){homePackages[page][index]="folder:"+id;homeGrids[page].removeViewAt(index);homeGrids[page].addView(createFolderView(id),index);animateAddedView(homeGrids[page].getChildAt(index))}
                saveHomeLayout()
            }.setNegativeButton("Cancel",null).show()
    }

    private fun showFolderMenu(folderId:String) {
        val apps=folderApps[folderId]?:mutableListOf(); val name=folderNames[folderId]?:"Folder"
        val labels=apps.mapNotNull{try{packageManager.getApplicationInfo(it,0).loadLabel(packageManager).toString()}catch(_:Exception){null}}.toTypedArray()
        AlertDialog.Builder(this).setTitle(name)
            .setItems(labels.ifEmpty{arrayOf("Folder is empty")}){_,i->if(i<apps.size)launchApp(apps[i])}
            .setPositiveButton("Add apps"){_,_->showAddAppsToFolder(folderId)}
            .setNeutralButton("Rename"){_,_->renameFolder(folderId)}
            .setNegativeButton("Delete folder"){_,_->deleteFolder(folderId)}.show()
    }

    private fun showAddAppsToFolder(folderId:String) {
        val folder=folderApps.getOrPut(folderId){mutableListOf()}
        val installed=packageManager.getInstalledApplications(0).filter{it.enabled&&it.packageName!=packageName&&packageManager.getLaunchIntentForPackage(it.packageName)!=null}.sortedBy{it.loadLabel(packageManager).toString().lowercase()}
        val labels=installed.map{it.loadLabel(packageManager).toString()}.toTypedArray()
        val checked=BooleanArray(installed.size){folder.contains(installed[it].packageName)}
        AlertDialog.Builder(this).setTitle("Apps in "+(folderNames[folderId]?:"Folder"))
            .setMultiChoiceItems(labels,checked){_,which,on->val p=installed[which].packageName;if(on){if(!folder.contains(p))folder.add(p)}else folder.remove(p)}
            .setPositiveButton("Done"){_,_->saveHomeLayout();rebuildHomeViews()}.setNegativeButton("Cancel",null).show()
    }

    private fun renameFolder(folderId:String) {
        val input=EditText(this).apply{setText(folderNames[folderId]?:"Folder");setSingleLine(true);setPadding(dp(18),dp(12),dp(18),dp(12))}
        AlertDialog.Builder(this).setTitle("Rename folder").setView(input).setPositiveButton("Save"){_,_->folderNames[folderId]=input.text.toString().trim().ifEmpty{"Folder"};rebuildHomeViews();saveHomeLayout()}.setNegativeButton("Cancel",null).show()
    }

    private fun deleteFolder(folderId:String) {
        for(page in 0 until pageCount){val i=homePackages[page].indexOf("folder:"+folderId);if(i>=0){homePackages[page].removeAt(i);homeGrids[page].removeViewAt(i)}}
        folderNames.remove(folderId);folderApps.remove(folderId);saveHomeLayout()
    }

    private fun createFolderView(folderId:String):View {
        val cell=LinearLayout(this).apply{
            tag="folder:"+folderId;orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(8),dp(12),dp(8),dp(12))
            background=GradientDrawable().apply{setColor(ColorUtils.setAlphaComponent(themePrimaryColor,0x26));cornerRadius=dp(18).toFloat()}
            setOnClickListener{animatePress(this);showFolderMenu(folderId)}
            setOnLongClickListener{showHomeItemMenu("folder:"+folderId);true}
        }
        val preview=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        (folderApps[folderId]?:mutableListOf()).take(4).forEach{pkg->try{val icon=ImageView(this).apply{setImageDrawable(packageManager.getApplicationInfo(pkg,0).loadIcon(packageManager));scaleType=ImageView.ScaleType.FIT_CENTER};preview.addView(icon,LinearLayout.LayoutParams(dp(30),dp(30)))}catch(_:Exception){}}
        if((folderApps[folderId]?:mutableListOf()).isEmpty()){preview.addView(TextView(this).apply{text="+";textSize=30f;setTextColor(themePrimaryColor);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(64),dp(48)))}
        cell.addView(preview)
        if(showAppLabels)cell.addView(TextView(this).apply{text=folderNames[folderId]?:"Folder";textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
        return cell
    }

    private fun findItemPage(itemId:String):Int{for(page in 0 until pageCount)if(homePackages[page].contains(itemId))return page;return -1}

    private fun rebuildHomeViews(){
        for(page in 0 until pageCount){
            homeGrids[page].removeAllViews()
            for(item in homePackages[page]){
                if(item.startsWith("folder:"))homeGrids[page].addView(createFolderView(item.removePrefix("folder:")))
                else try{homeGrids[page].addView(createHomeAppView(item,packageManager.getApplicationInfo(item,0)))}catch(_:Exception){}
            }
            animateHomeRefresh(homeGrids[page])
        }
    }

    private fun showCustomizationMenu() {
        settingsOverlay?.let { root.removeView(it) }

        val overlay = FrameLayout(this).apply {
            setBackgroundColor("#E6121218".toColorInt())
            elevation = dp(30).toFloat()
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            background = GradientDrawable().apply {
                setColor("#F0181820".toColorInt())
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), "#44FFFFFF".toColorInt())
            }
        }

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { setMargins(dp(12), dp(28), dp(12), dp(28)) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "FUCKIN CONTROL DECK"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val close = TextView(this).apply {
            text = "✕"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { closeSettingsOverlay() }
        }

        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(52), dp(52)))
        panel.addView(header)

        panel.addView(TextView(this).apply {
            text = "YOUR LAUNCHER. YOUR LAYOUT. NO ONE UI SHIT."
            textSize = 11f
            setTextColor("#99FFFFFF".toColorInt())
            setPadding(0, 0, 0, dp(18))
        })

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun section(name: String) {
            content.addView(TextView(this).apply {
                text = name
                textSize = 13f
                setTextColor("#FF8FE8FF".toColorInt())
                setPadding(dp(4), dp(16), dp(4), dp(7))
            })
        }

        fun row(label: String, value: String, action: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    setColor("#18FFFFFF".toColorInt())
                    cornerRadius = dp(16).toFloat()
                }
                setOnClickListener { action() }
            }
            r.addView(TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
            r.addView(TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor("#AAFFFFFF".toColorInt())
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(54)
            ))
            content.addView(r, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)
            ).apply { bottomMargin = dp(8) })
        }

        section("HOME")
        row("Home screens", "$pageCount pages") { choosePageCount() }
        row("App icon size", "$iconSizeDp dp") { chooseIconSize() }
        row("App names", if (showAppLabels) "ON" else "OFF") {
            showAppLabels = !showAppLabels
            saveHomeLayout()
            rebuildHomeViews()
            closeSettingsOverlay()
            showCustomizationMenu()
        }

        section("APP DRAWER")
        row("Drawer columns", "$drawerGridColumns columns") { chooseDrawerColumns() }
        row("Drawer opacity", "${(drawerAlpha * 100).toInt()}%") { chooseDrawerOpacity() }

        section("GESTURES")
        row("Move apps", "HOLD + DRAG ANYWHERE") { closeSettingsOverlay() }
        row("Remove an app", "DRAG TO REMOVE") { closeSettingsOverlay() }
        row("Open drawer", "SWIPE UP") { closeSettingsOverlay() }
        row("Close drawer", "SWIPE DOWN") { closeSettingsOverlay() }

        scroll.addView(content)
        panel.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        overlay.addView(panel, panelParams)
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        settingsOverlay = overlay
    }

    private fun closeSettingsOverlay() {
        settingsOverlay?.let { root.removeView(it) }
        settingsOverlay = null
    }

    private fun chooseDrawerColumns(){val values=arrayOf("4 columns","5 columns","6 columns");AlertDialog.Builder(this).setTitle("App drawer columns").setItems(values){_,which->drawerGridColumns=which+4;saveHomeLayout();populateApps()}.show()}
    private fun chooseIconSize(){val values=arrayOf("Small — 48dp","Medium — 56dp","Large — 64dp","Huge — 72dp","Massive — 80dp");val sizes=intArrayOf(48,56,64,72,80);AlertDialog.Builder(this).setTitle("Icon size").setItems(values){_,which->iconSizeDp=sizes[which];saveHomeLayout();rebuildHomeViews();populateApps()}.show()}
    private fun chooseDrawerOpacity(){val values=arrayOf("70%","80%","90%","94%","100%");val a=floatArrayOf(.70f,.80f,.90f,.94f,1f);AlertDialog.Builder(this).setTitle("Drawer opacity").setItems(values){_,which->drawerAlpha=a[which];appDrawer.setBackgroundColor(Color.argb((drawerAlpha*255).toInt(),0,0,0));saveHomeLayout()}.show()}
    private fun choosePageCount(){val values=arrayOf("3 pages","4 pages","5 pages","6 pages");AlertDialog.Builder(this).setTitle("Home screens").setItems(values){_,which->{val n=which+3;if(n!=pageCount){pageCount=n;saveHomeLayout();rebuildLauncherSmooth()}}}.show()}

    private fun animatePress(view:View){view.animate().cancel();view.animate().scaleX(.94f).scaleY(.94f).setDuration(80).withEndAction{view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()}.start()}
    private fun animateAddedView(view:View?){view?:return;view.alpha=0f;view.scaleX=.86f;view.scaleY=.86f;view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator()).start()}
    private fun animateHomeRefresh(view:View){view.animate().cancel();view.alpha=.65f;view.animate().alpha(1f).setDuration(180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()}
    private fun rebuildLauncherSmooth(){root.animate().alpha(.35f).setDuration(100).withEndAction{buildLauncher();loadHomeLayout();root.animate().alpha(1f).setDuration(220).start()}.start()}

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

        homeContainer.smoothScrollTo(screenWidth*currentPage,0)
        pageIndicator.animate().alpha(.45f).setDuration(90).withEndAction{pageIndicator.animate().alpha(1f).setDuration(180).start()}.start()
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

        appDrawer.visibility=View.VISIBLE
        appDrawer.alpha=0f
        appDrawer.translationY=appDrawer.height.toFloat()*.12f
        appDrawer.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(android.view.animation.DecelerateInterpolator()).setListener(null).start()
    }

    private fun closeDrawer() {

        if (!appDrawer.isVisible) {
            return
        }

        appDrawer.animate().translationY(appDrawer.height.toFloat()*.12f).alpha(0f).setDuration(220).setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).setListener(
                object :
                    AnimatorListenerAdapter() {

                    override fun onAnimationEnd(
                        animation: Animator
                    ) {

                        appDrawer.visibility=View.INVISIBLE
                        appDrawer.alpha=1f
                        appDrawer.translationY=appDrawer.height.toFloat()
                    }
                }
            )
            .start()
    }

    @Deprecated("Use OnBackInvokedDispatcher on newer Android")
    override fun onBackPressed() {
        if (settingsOverlay != null) { closeSettingsOverlay(); return }
        if (::appDrawer.isInitialized && appDrawer.isVisible) { closeDrawer(); return }
        super.onBackPressed()
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
                                abs(dx) > dp(30)
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

    private inner class HomeGrid(context: android.content.Context) : FrameLayout(context) {
        init {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        fun getDropIndex(rawX: Float, rawY: Float): Int {
            if (childCount == 0) return 0
            var best = childCount
            var bestDistance = Float.MAX_VALUE
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val dx = loc[0] + child.x + child.width / 2f - rawX
                val dy = loc[1] + child.y + child.height / 2f - rawY
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            return best
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec)
            )
            for (i in 0 until childCount) {
                measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
                if (child.translationX == 0f && child.translationY == 0f) {
                    val col = i % 4
                    val row = i / 4
                    child.translationX = dp(8 + col * (iconSizeDp + 36)).toFloat()
                    child.translationY = dp(18 + row * (iconSizeDp + 62)).toFloat()
                }
            }
        }
    }

}

}
