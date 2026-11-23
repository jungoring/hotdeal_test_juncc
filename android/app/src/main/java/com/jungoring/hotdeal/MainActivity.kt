package com.jungoring.hotdeal

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jungoring.hotdeal.data.DealMerge
import com.jungoring.hotdeal.data.DealStore
import com.jungoring.hotdeal.data.FilterPrefs
import com.jungoring.hotdeal.data.Js
import com.jungoring.hotdeal.data.PageLoader
import com.jungoring.hotdeal.data.Parsers
import com.jungoring.hotdeal.model.Deal
import com.jungoring.hotdeal.model.ShopLookup
import com.jungoring.hotdeal.ui.CardFactory
import com.jungoring.hotdeal.ui.CardViews
import com.jungoring.hotdeal.ui.Colors
import com.jungoring.hotdeal.ui.ThumbLoader
import com.jungoring.hotdeal.ui.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    /** 새로고침 한 번에 무엇을 읽을지 */
    private enum class LoadMode {
        /** 새 글만(자동): 저장된 최신 글을 만날 때까지 최대 15페이지 */
        AUTO,
        /** 고른 페이지 수만큼 (3 / 5 / 15) */
        PAGES,
        /** 이전 글 더 불러오기: 마지막으로 읽은 다음 3페이지 */
        DEEP
    }

    private lateinit var ui: Ui
    private lateinit var prefs: FilterPrefs
    private lateinit var cardFactory: CardFactory
    private lateinit var store: DealStore
    private lateinit var thumbs: ThumbLoader

    private lateinit var webView: WebView
    private lateinit var loader: PageLoader

    private lateinit var webHost: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var challengeBar: LinearLayout
    private lateinit var stamp: TextView
    private lateinit var qInput: EditText
    private lateinit var exInput: EditText
    private lateinit var chipsRow: LinearLayout
    private lateinit var votesRow: LinearLayout
    private lateinit var countRow: LinearLayout
    private lateinit var tEnded: TextView
    private lateinit var countText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var pagerTop: LinearLayout
    private lateinit var pagerBottom: LinearLayout
    private lateinit var btnTop: TextView
    private lateinit var btnRefresh: TextView
    private lateinit var btnFilterToggle: TextView
    private lateinit var filterHint: TextView
    private lateinit var filterDetail: LinearLayout
    private lateinit var btnClearQ: TextView
    private lateinit var btnClearEx: TextView

    /** 상세 필터(추천·수집·종료 숨김) 영역을 펼쳐 두었는지 (v1.0.4) */
    private var filtersOpen: Boolean = true

    /** 7일 누적 목록 (최신 글 순) */
    private val deals = ArrayList<Deal>()
    private val holders = LinkedHashMap<String, CardViews>()

    private var loadJob: Job? = null

    /** WebView 는 하나뿐이라 목록 읽기와 쇼핑몰 링크 확인이 겹치지 않게 한 번에 하나씩만 쓴다. */
    private val pageMutex = Mutex()

    private var q: String = ""
    private var ex: String = ""
    private var cat: String = "전체"
    private var minVotes: Int = 0
    private var hideEnded: Boolean = true
    private var scopePages: Int = 0

    /** 지금 보고 있는 목록 페이지 (0부터). 한 페이지에 ITEMS_PER_PAGE 개씩 */
    private var pageIndex: Int = 0

    /** 마지막으로 그린 필터 결과의 전체 페이지 수 (페이지 바 표시용) */
    private var pageCount: Int = 1

    /** 맨 위로 버튼을 띄우기 전에 스크롤이 멈추기를 기다리는 예약 */
    private val topButtonTask = Runnable { revealTopButton() }

    /** 저장된 목록 중 가장 깊게 읽어 둔 페이지 번호 */
    private var deepPages: Int = 0

    private var savedAt: Long? = null
    private var challengeClosed: Boolean = false
    private var lastLoadFailed: Boolean = false
    private var loading: Boolean = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val renderTask = Runnable { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ui = Ui(this)
        prefs = FilterPrefs(this)
        store = DealStore(this)
        thumbs = ThumbLoader(this)
        cardFactory = CardFactory(this, thumbs)

        bindViews()
        setupWebView()
        loadFilterState()
        setupFilterUi()
        restoreCache()
        startLoad(LoadMode.AUTO)
    }

    // ── 화면 준비 ─────────────────────────────────────────

    private fun bindViews() {
        webHost = findViewById(R.id.webHost)
        scroll = findViewById(R.id.scroll)
        challengeBar = findViewById(R.id.challengeBar)
        stamp = findViewById(R.id.stamp)
        qInput = findViewById(R.id.q)
        exInput = findViewById(R.id.ex)
        chipsRow = findViewById(R.id.chipsRow)
        votesRow = findViewById(R.id.votesRow)
        countRow = findViewById(R.id.countRow)
        tEnded = findViewById(R.id.tEnded)
        countText = findViewById(R.id.countText)
        listContainer = findViewById(R.id.listContainer)
        pagerTop = findViewById(R.id.pagerTop)
        pagerBottom = findViewById(R.id.pagerBottom)
        btnTop = findViewById(R.id.btnTop)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnFilterToggle = findViewById(R.id.btnFilterToggle)
        filterHint = findViewById(R.id.filterHint)
        filterDetail = findViewById(R.id.filterDetail)
        btnClearQ = findViewById(R.id.btnClearQ)
        btnClearEx = findViewById(R.id.btnClearEx)

        setupTopButton()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(false)
            builtInZoomControls = false
            userAgentString = UA
            setGeolocationEnabled(false)
        }
        webView.setBackgroundColor(Colors.BG)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                loader.notifyPageFinished()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString() ?: return false
                // 보안 확인 화면 안에서 다른 곳으로 새지 않도록 http(s) 만 WebView 안에서 연다
                return !(target.startsWith("http://") || target.startsWith("https://"))
            }
        }

        webHost.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        loader = PageLoader(webView)
    }

    private fun loadFilterState() {
        q = prefs.q
        ex = prefs.exclude
        cat = prefs.category
        minVotes = prefs.minVotes
        hideEnded = prefs.hideEnded
        scopePages = prefs.scopePages
        filtersOpen = prefs.filtersOpen
    }

    private fun setupFilterUi() {
        qInput.setText(q)
        exInput.setText(ex)
        qInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                q = s?.toString() ?: ""
                prefs.q = q
                pageIndex = 0
                syncClearButtons()
                scheduleRender()
            }
        })
        exInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                ex = s?.toString() ?: ""
                prefs.exclude = ex
                pageIndex = 0
                syncClearButtons()
                scheduleRender()
            }
        })

        // ✕ 지우기 : 한 번에 비우고 바로 이어서 칠 수 있게 포커스는 남긴다
        btnClearQ.setOnClickListener {
            qInput.setText("")
            qInput.requestFocus()
        }
        btnClearEx.setOnClickListener {
            exInput.setText("")
            exInput.requestFocus()
        }

        // 키보드의 '검색 / 완료' 를 누르면 키보드를 내리고 포커스를 풀어 결과를 바로 본다
        val imeListener = TextView.OnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_DONE -> {
                    uiHandler.removeCallbacks(renderTask)
                    render()
                    hideKeyboard()
                    true
                }
                else -> false
            }
        }
        qInput.setOnEditorActionListener(imeListener)
        exInput.setOnEditorActionListener(imeListener)

        // 검색어를 치려고 입력란을 누르면 상세 필터를 잠시 접어 목록 자리를 넓힌다
        val foldForTyping = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && filtersOpen) {
                filtersOpen = false
                syncFilterDetail()
            }
        }
        qInput.setOnFocusChangeListener(foldForTyping)
        exInput.setOnFocusChangeListener(foldForTyping)

        btnFilterToggle.setOnClickListener {
            filtersOpen = !filtersOpen
            prefs.filtersOpen = filtersOpen
            syncFilterDetail()
        }

        btnRefresh.setOnClickListener { startLoad(LoadMode.AUTO) }
        findViewById<TextView>(R.id.btnReset).setOnClickListener { resetFilters() }
        findViewById<TextView>(R.id.btnChallengeClose).setOnClickListener {
            challengeClosed = true
            showChallenge(false)
        }

        tEnded.setOnClickListener {
            hideEnded = !hideEnded
            prefs.hideEnded = hideEnded
            pageIndex = 0
            syncToggles()
            render()
        }

        refreshFilterRows()
        syncToggles()
        syncFilterDetail()
        syncClearButtons()
        render()
    }

    // ── 필터 접기/펼치기 · 입력 지우기 · 키보드 ──────────────

    /**
     * 상세 필터(추천·수집·종료 숨김)를 접거나 펴고, 접었을 때는 지금 걸린 조건을
     * 한 줄로 요약해 보여 준다. (목록이 길어 키보드가 올라오면 목록이 거의 안 보이던 문제)
     */
    private fun syncFilterDetail() {
        filterDetail.visibility = if (filtersOpen) View.VISIBLE else View.GONE
        btnFilterToggle.text = if (filtersOpen) "필터 ▲" else "필터 ▼"
        btnFilterToggle.setTextColor(if (filtersOpen) Colors.DIM else Colors.TXT)

        if (filtersOpen) {
            filterHint.visibility = View.GONE
            return
        }
        val parts = ArrayList<String>()
        if (minVotes > 0) parts.add("추천 " + minVotes + "+")
        parts.add(if (scopePages <= 0) "새 글만" else scopePages.toString() + "p 수집")
        parts.add(if (hideEnded) "종료 숨김" else "종료 포함")
        filterHint.text = parts.joinToString(" · ")
        filterHint.visibility = View.VISIBLE
    }

    /** 입력값이 있을 때만 ✕ 를 띄운다 */
    private fun syncClearButtons() {
        btnClearQ.visibility = if (qInput.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        btnClearEx.visibility = if (exInput.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** 키보드를 내리고 입력 포커스를 풀어 목록이 가려지지 않게 한다 */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(qInput.windowToken, 0)
        exInput.clearFocus()
        qInput.clearFocus()
        currentFocus?.clearFocus()
    }

    // ── 맨 위로 버튼 ─────────────────────────────────────

    /**
     * 스크롤 중에는 숨기고, 스크롤이 멈추고 400ms 뒤에(맨 위 근처가 아닐 때만) 띄운다.
     * 누르면 지금 보고 있는 페이지의 첫 글로 올라간다.
     */
    private fun setupTopButton() {
        btnTop.setOnClickListener {
            scrollToListTop()
            fadeTopButton(false)
        }
        scroll.setOnScrollChangeListener(
            View.OnScrollChangeListener { _, _, scrollY, _, _ -> onListScrolled(scrollY) }
        )
    }

    private fun onListScrolled(scrollY: Int) {
        uiHandler.removeCallbacks(topButtonTask)
        fadeTopButton(false)
        if (!scrolledIntoList(scrollY)) return
        uiHandler.postDelayed(topButtonTask, TOP_BUTTON_DELAY_MS)
    }

    /** 필터 영역이 아니라 목록 속으로 충분히 내려왔는지 (맨 위 근처에서는 버튼을 띄우지 않는다) */
    private fun scrolledIntoList(scrollY: Int): Boolean =
        canScrollList() && scrollY > listContainer.top + ui.dp(TOP_BUTTON_MIN_DP)

    private fun revealTopButton() {
        if (challengeBar.visibility == View.VISIBLE) return
        if (!scrolledIntoList(scroll.scrollY)) return
        if (btnTop.visibility != View.VISIBLE) {
            btnTop.visibility = View.VISIBLE
        }
        fadeTopButton(true)
    }

    private fun fadeTopButton(show: Boolean) {
        if (show) {
            btnTop.animate().cancel()
            btnTop.visibility = View.VISIBLE
            btnTop.animate().alpha(1f).setDuration(170L).start()
        } else {
            if (btnTop.visibility != View.VISIBLE) return
            btnTop.animate().cancel()
            btnTop.animate().alpha(0f).setDuration(130L).withEndAction {
                if (btnTop.alpha <= 0.01f) btnTop.visibility = View.GONE
            }.start()
        }
    }

    /** 목록이 지금 화면보다 길어서 스크롤할 거리가 있는지 */
    private fun canScrollList(): Boolean {
        val child = scroll.getChildAt(0) ?: return false
        return child.height > scroll.height + ui.dp(40f)
    }

    // ── 저장된 7일치 먼저 그리기 ─────────────────────────

    private fun restoreCache() {
        val snapshot = store.load()
        deals.clear()
        deals.addAll(snapshot.deals)
        deepPages = snapshot.deepPages
        savedAt = snapshot.savedAt
        lastLoadFailed = false
        refreshFilterRows()
        render()
        if (deals.isNotEmpty()) stampSummary()
    }

    // ── 수집 ──────────────────────────────────────────────

    private fun startLoad(mode: LoadMode = LoadMode.AUTO) {
        if (loading) return
        loading = true
        loadJob?.cancel()
        challengeClosed = false
        lastLoadFailed = false
        btnRefresh.isEnabled = false
        btnRefresh.alpha = 0.5f

        val knownIds = HashSet<String>()
        for (d in deals) d.id?.let { knownIds.add(it) }
        val hadCache = knownIds.isNotEmpty()

        val startPage: Int
        val endPage: Int
        val stopAtKnown: Boolean
        when (mode) {
            LoadMode.AUTO -> {
                startPage = 1
                // 저장된 목록이 있으면 새 글이 나올 때까지만, 없으면 15페이지까지 채운다
                endPage = AUTO_MAX_PAGES
                stopAtKnown = hadCache
            }
            LoadMode.PAGES -> {
                startPage = 1
                endPage = if (scopePages <= 0) AUTO_MAX_PAGES else scopePages
                stopAtKnown = false
            }
            LoadMode.DEEP -> {
                startPage = (deepPages + 1).coerceAtLeast(1)
                endPage = (deepPages + DEEP_STEP).coerceAtMost(DEEP_MAX_PAGE)
                stopAtKnown = false
            }
        }
        if (mode == LoadMode.DEEP && startPage > DEEP_MAX_PAGE) {
            loading = false
            btnRefresh.isEnabled = true
            btnRefresh.alpha = 1f
            toast("더 내려갈 페이지가 없습니다")
            return
        }

        loadJob = lifecycleScope.launch {
            var lastPage = startPage - 1
            var reachedOldPost = false
            val beforeKeys = HashSet<String>()
            for (d in deals) beforeKeys.add(d.key)

            try {
                for (page in startPage..endPage) {
                    stamp.text = "불러오는 중… " + page + "/" + endPage + "페이지 · 누적 " + deals.size + "건"
                    val url = if (page == 1) LIST_URL else LIST_URL + "?page=" + page
                    var opened = true
                    val pageDeals = pageMutex.withLock {
                        if (!openPage(url)) {
                            opened = false
                            emptyList()
                        } else {
                            fetchDeals()
                        }
                    }
                    if (!opened) break
                    if (pageDeals.isEmpty()) break
                    lastPage = page

                    // '이전 글 더 불러오기' 로 내려갔는데 이 페이지가 전부 7일보다 오래된 글이면
                    // 더 내려가도 쌓을 게 없다 (7일 보관)
                    val cutoff = System.currentTimeMillis() - DealMerge.RETENTION_MS
                    if (mode == LoadMode.DEEP && pageDeals.all { it.ageBase < cutoff }) {
                        reachedOldPost = true
                        break
                    }

                    mergeIn(pageDeals)
                    render()

                    if (stopAtKnown && pageDeals.any { d -> d.id != null && knownIds.contains(d.id) }) break
                    if (pageDeals.size < PAGE_SIZE) break
                    delay(PAGE_GAP_MS)
                }

                if (lastPage >= startPage) deepPages = maxOf(deepPages, lastPage)

                if (deals.isEmpty()) {
                    lastLoadFailed = true
                    stamp.text = if (challengeClosed) {
                        "보안 확인을 통과하지 못해 멈췄습니다"
                    } else {
                        "목록을 읽지 못했습니다"
                    }
                    render()
                    return@launch
                }

                savedAt = System.currentTimeMillis()
                persist()
                val newCount = deals.count { it.key !in beforeKeys }
                // 새로고침(AUTO/PAGES)은 최신 글이 첫 페이지에 오므로 맨 앞으로 돌아가고,
                // '이전 글 더 불러오기'(DEEP)는 보던 페이지를 그대로 두고 뒤 페이지가 늘어난다.
                refreshFilterRows()

                stampSummary(newCount = newCount)
                render()

                if (reachedOldPost) {
                    toast("여기부터는 7일이 지난 글이라 더 쌓지 않습니다 (7일치를 다 채웠습니다)")
                }
            } finally {
                loading = false
                btnRefresh.isEnabled = true
                btnRefresh.alpha = 1f
                // 중간에 멈췄거나 취소돼 '불러오는 중…' 이 남아 있으면 요약 문구로 되돌린다
                if (stamp.text.startsWith("불러오는 중")) stampSummary()
                render()
            }
        }
    }

    /** 새로 읽은 페이지를 7일 누적 목록에 합치고 바로 저장한다. */
    private suspend fun mergeIn(fresh: List<Deal>) {
        val merged = DealMerge.merge(deals, fresh, System.currentTimeMillis())
        deals.clear()
        deals.addAll(merged)
        persist()
    }

    private suspend fun persist() {
        val items = ArrayList(deals)
        val pages = deepPages
        val at = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            store.save(items, pages, at)
        }
    }

    /**
     * 페이지를 연다. 보안 시스템 화면이 나오면 WebView 를 화면에 보여 주고,
     * 사용자가 한 번 통과할 때까지 기다린 뒤 자동으로 이어서 읽는다.
     */
    private suspend fun openPage(url: String): Boolean {
        if (!loader.load(url)) return false
        if (!isBlocked()) return true

        challengeClosed = false
        showChallenge(true)
        var waited = 0L
        while (waited < CHALLENGE_WAIT_MS) {
            if (challengeClosed) {
                showChallenge(false)
                return false
            }
            delay(1500)
            waited += 1500
            if (!isBlocked()) {
                delay(700)
                showChallenge(false)
                // 통과 직후 사이트가 스스로 목록으로 넘어가므로, 원하는 페이지를 한 번 더 연다
                if (!loader.load(url)) return false
                return !isBlocked()
            }
        }
        showChallenge(false)
        return false
    }

    private suspend fun fetchDeals(): List<Deal> {
        // 보안 확인이 저절로 넘어가는 경우를 대비해 몇 초간 더 기다려 본다
        for (attempt in 0 until 8) {
            val raw = loader.eval(Parsers.LIST_JS)
            val json = Js.decode(raw)
            if (json != null) {
                val parsed = Deal.parseList(json)
                if (parsed.isNotEmpty()) return parsed
            }
            delay(700)
        }
        return emptyList()
    }

    private suspend fun isBlocked(): Boolean {
        for (attempt in 0 until 3) {
            val json = Js.decode(loader.eval(Parsers.GUARD_JS))
            if (json != null && json.startsWith("{")) {
                return try {
                    JSONObject(json).optBoolean("blocked", false)
                } catch (e: Exception) {
                    false
                }
            }
            delay(400)
        }
        return false
    }

    private fun showChallenge(show: Boolean) {
        challengeBar.visibility = if (show) View.VISIBLE else View.GONE
        scroll.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            // 보안 확인 화면이 앞에 나오면 목록 위에 떠 있던 버튼도 함께 숨긴다
            uiHandler.removeCallbacks(topButtonTask)
            fadeTopButton(false)
            stamp.text = "보안 확인 대기 중…"
        } else if (!loading) {
            // 목록 수집 중이 아닐 때(쇼핑몰 링크 확인 중 보안 화면이 떴던 경우)는 상태 문구를 되돌린다
            stampSummary()
        }
    }

    // ── 쇼핑몰 / 원문 바로가기 ────────────────────────────

    /**
     * '쇼핑몰 바로가기'.
     *  - 이미 주소를 확인한 글: 바로 외부 앱/브라우저로 연다.
     *  - 아직 모르는 글: 이 글의 상세 페이지 한 건만 읽어 실제 쇼핑몰 주소를 뽑은 뒤 연다.
     *    (본문에 외부 쇼핑몰 링크가 없으면 펨코 원문을 연다)
     */
    private fun openShop(d: Deal) {
        val known = d.shopUrl
        if (!known.isNullOrBlank()) {
            openUrl(known)
            return
        }
        if (d.shopLoading) return
        if (d.shopMissing) {
            openPost(d)
            return
        }

        d.shopLoading = true
        updateCard(d)
        lifecycleScope.launch {
            val result = lookupShop(d)
            d.shopLoading = false
            val resolved = result?.shopUrl
            when {
                !resolved.isNullOrBlank() -> {
                    d.shopUrl = resolved
                    d.shopMissing = false
                    updateCard(d)
                    persist()
                    openUrl(resolved)
                }
                result?.inspected == true -> {
                    d.shopMissing = true
                    updateCard(d)
                    persist()
                    Toast.makeText(
                        this@MainActivity,
                        "이 글에는 쇼핑몰 링크가 없어 펨코 원문을 엽니다",
                        Toast.LENGTH_SHORT
                    ).show()
                    openPost(d)
                }
                else -> {
                    updateCard(d)
                    if (isActive) {
                        Toast.makeText(
                            this@MainActivity,
                            "쇼핑몰 링크를 확인하지 못했습니다. 한 번 더 눌러 주세요",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun openPost(d: Deal) {
        val url = d.url
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "원문 주소가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        openUrl(url)
    }

    /** 글 상세 페이지 한 건을 읽어 쇼핑몰 주소를 뽑는다. (실패하면 null) */
    private suspend fun lookupShop(d: Deal): ShopLookup? {
        val postUrl = d.url ?: return null
        return pageMutex.withLock {
            if (!openPage(postUrl)) return@withLock null
            var last: ShopLookup? = null
            for (attempt in 0 until 4) {
                val json = Js.decode(loader.eval(Parsers.SHOP_JS))
                if (json != null && json.startsWith("{")) {
                    val parsed = Deal.parseShopLookup(json)
                    if (!parsed.shopUrl.isNullOrBlank()) return@withLock parsed
                    if (parsed.inspected) return@withLock parsed
                    last = parsed
                }
                delay(500)
            }
            last
        }
    }

    // ── 필터 ─────────────────────────────────────────────

    private fun matches(d: Deal): Boolean {
        if (cat != "전체" && (d.category ?: "") != cat) return false
        if (minVotes > 0 && (d.upvotes ?: 0) < minVotes) return false
        if (hideEnded && d.isEnded) return false

        val haystack = ((d.title) + " " + (d.mall ?: "") + " " + (d.category ?: "")).lowercase()
        if (q.isNotBlank() && !haystack.contains(q.lowercase())) return false
        if (ex.isNotBlank()) {
            val words = ex.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            for (w in words) {
                if (haystack.contains(w)) return false
            }
        }
        return true
    }

    private fun resetFilters() {
        q = ""
        ex = ""
        cat = "전체"
        minVotes = 0
        hideEnded = true
        prefs.q = q
        prefs.exclude = ex
        prefs.category = cat
        prefs.minVotes = minVotes
        prefs.hideEnded = hideEnded
        qInput.setText("")
        exInput.setText("")
        pageIndex = 0
        refreshFilterRows()
        syncToggles()
        render()
    }

    private fun refreshFilterRows() {
        // 카테고리 칩
        val cats = ArrayList<String>()
        cats.add("전체")
        for (d in deals) {
            val c = d.category
            if (!c.isNullOrBlank() && !cats.contains(c)) cats.add(c)
        }
        if (!cats.contains(cat)) cat = "전체"

        chipsRow.removeAllViews()
        for (c in cats) {
            val chip = chipView(c, cat == c)
            chip.setOnClickListener {
                cat = c
                prefs.category = c
                pageIndex = 0
                refreshFilterRows()
                render()
            }
            chipsRow.addView(chip)
        }

        // 최소 추천수
        votesRow.removeAllViews()
        val voteOptions = listOf(0 to "전체", 3 to "3+", 5 to "5+", 10 to "10+")
        for ((value, label) in voteOptions) {
            val tv = segView(label, minVotes == value)
            tv.setOnClickListener {
                minVotes = value
                prefs.minVotes = value
                pageIndex = 0
                refreshFilterRows()
                render()
            }
            votesRow.addView(tv, weightParams(4))
        }

        // 1회 수집 범위 (새 글만 / 3p / 5p / 15p) — 고르면 바로 그 범위로 다시 읽는다
        countRow.removeAllViews()
        val scopeOptions = listOf(
            0 to "새 글만",
            3 to "3p",
            5 to "5p",
            15 to "15p"
        )
        for ((value, label) in scopeOptions) {
            val tv = segView(label, scopePages == value)
            tv.setOnClickListener {
                if (loading) {
                    toast("불러오는 중입니다. 끝난 뒤에 다시 골라 주세요")
                    return@setOnClickListener
                }
                scopePages = value
                prefs.scopePages = value
                refreshFilterRows()
                startLoad(if (value == 0) LoadMode.AUTO else LoadMode.PAGES)
            }
            countRow.addView(tv, weightParams(4))
        }

        syncFilterDetail()
    }

    private fun syncToggles() {
        styleToggle(tEnded, hideEnded, "종료된 딜 숨김")
        syncFilterDetail()
    }

    private fun styleToggle(tv: TextView, on: Boolean, label: String) {
        tv.text = if (on) "✓ " + label else label
        tv.setTextColor(if (on) Colors.GREEN else Colors.DIM)
        tv.background = if (on) {
            ui.round(Colors.GREEN_BG, 12f, Colors.GREEN_LINE, 1f)
        } else {
            ui.round(Colors.BG2, 12f, Colors.LINE, 1f)
        }
    }

    private fun chipView(label: String, selected: Boolean): TextView {
        val tv = ui.text(14f, if (selected) 0xFFFFFFFF.toInt() else Colors.DIM, true)
        tv.text = label
        tv.setPadding(ui.dp(14f), ui.dp(9f), ui.dp(14f), ui.dp(9f))
        tv.background = if (selected) {
            ui.round(Colors.ACCENT, 999f)
        } else {
            ui.round(Colors.BG2, 999f, Colors.LINE, 1f)
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = ui.dp(6f)
        tv.layoutParams = params
        return tv
    }

    private fun segView(label: String, selected: Boolean): TextView {
        val tv = ui.text(13.5f, if (selected) Colors.TXT else Colors.DIM, true)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, ui.dp(9f), 0, ui.dp(9f))
        tv.background = if (selected) {
            ui.round(Colors.CARD2, 9f, Colors.LINE, 1f)
        } else {
            ui.round(Colors.BG2, 9f)
        }
        return tv
    }

    private fun weightParams(weight: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight.toFloat()).apply {
            marginStart = ui.dp(3f)
            marginEnd = ui.dp(3f)
        }

    // ── 목록 그리기 ───────────────────────────────────────

    private fun render() {
        val filtered = deals.filter { matches(it) }
        holders.clear()
        listContainer.removeAllViews()

        // 필터를 적용한 목록 기준 페이지 나누기 (페이지당 60개)
        pageCount = if (filtered.isEmpty()) 1 else (filtered.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        if (pageIndex > pageCount - 1) pageIndex = pageCount - 1
        if (pageIndex < 0) pageIndex = 0
        val from = pageIndex * ITEMS_PER_PAGE
        val to = (from + ITEMS_PER_PAGE).coerceAtMost(filtered.size)

        countText.text = when {
            filtered.isEmpty() || deals.isEmpty() -> "조건 " + filtered.size + "건 · 누적 " + deals.size + "건"
            pageCount > 1 -> "조건 " + filtered.size + "건 · 누적 " + deals.size + "건 · " +
                (pageIndex + 1) + "/" + pageCount + "쪽"
            else -> "조건 " + filtered.size + "건 · 누적 " + deals.size + "건"
        }

        if (deals.isEmpty()) {
            buildPager(pagerTop, filtered.size, false)
            buildPager(pagerBottom, filtered.size, false)
            if (lastLoadFailed) {
                renderEmpty(
                    "불러오지 못했습니다",
                    if (challengeClosed) {
                        "에펨코리아 보안 확인을 통과하지 못했습니다. ‘↻ 새로고침’으로 다시 시도해 주세요."
                    } else {
                        "에펨코리아 목록을 읽지 못했습니다. 잠시 뒤 ‘↻ 새로고침’을 눌러 다시 시도해 주세요."
                    }
                )
            } else {
                renderEmpty(
                    "핫딜을 불러오는 중…",
                    "처음 한 번은 7일치를 채우느라 조금 걸립니다. 저장된 목록이 없어 새로 받아오는 중입니다."
                )
            }
            return
        }

        val page = if (from < to) filtered.subList(from, to) else emptyList()
        if (page.isEmpty()) {
            renderEmpty(
                "조건에 맞는 딜이 없습니다",
                "검색어를 줄이거나 ‘필터 초기화’를 눌러 누적 " + deals.size + "건을 볼 수 있습니다."
            )
        } else {
            for (d in page) {
                val holder = cardFactory.create()
                bindCard(holder, d)
                holder.body.setOnClickListener { openShop(d) }
                listContainer.addView(holder.root)
                holders[d.key] = holder
            }
        }

        buildPager(pagerTop, filtered.size, from > 0 || to < filtered.size)
        buildPager(pagerBottom, filtered.size, from > 0 || to < filtered.size)
        listContainer.addView(deepLoadButton())
    }

    // ── 페이지 이동 ──────────────────────────────────────

    /**
     * [◀ 1 2 3 4 ▶  ·  1 / 4] — 필터를 적용한 목록 기준으로 페이지를 고른다.
     * 목록 위아래에 같은 바를 하나씩 두어, 스크롤이 어디에 있든 페이지를 넘길 수 있다.
     */
    private fun buildPager(host: LinearLayout, totalItems: Int, show: Boolean) {
        host.removeAllViews()
        if (!show || totalItems <= ITEMS_PER_PAGE) {
            host.visibility = View.GONE
            return
        }
        host.visibility = View.VISIBLE

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val prev = pagerArrow("◀", pageIndex > 0) { goToPage(pageIndex - 1) }
        row.addView(prev)

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val window = 5
        var first = (pageIndex - window / 2).coerceAtLeast(0)
        var last = (first + window).coerceAtMost(pageCount)
        first = (last - window).coerceAtLeast(0)

        if (first > 0) {
            chips.addView(pagerEdge(1, 0))
            if (first > 1) chips.addView(pagerGap())
        }
        for (p in first until last) {
            chips.addView(pagerNumber(p + 1))
        }
        if (last < pageCount) {
            if (last < pageCount - 1) chips.addView(pagerGap())
            chips.addView(pagerEdge(pageCount, pageCount - 1))
        }
        row.addView(
            chips,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val next = pagerArrow("▶", pageIndex < pageCount - 1) { goToPage(pageIndex + 1) }
        row.addView(next)

        host.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val from = pageIndex * ITEMS_PER_PAGE + 1
        val to = ((pageIndex + 1) * ITEMS_PER_PAGE).coerceAtMost(totalItems)
        val caption = ui.text(12f, Colors.DIM2)
        caption.text = (pageIndex + 1).toString() + " / " + pageCount + " 페이지 · " +
            from + "~" + to + "번째 (한 쪽에 " + ITEMS_PER_PAGE + "개)"
        caption.gravity = Gravity.CENTER
        caption.setPadding(0, ui.dp(6f), 0, 0)
        host.addView(
            caption,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun goToPage(target: Int) {
        val clamped = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (clamped == pageIndex) {
            scrollToListTop()
            return
        }
        pageIndex = clamped
        render()
        scrollToListTop()
    }

    /** 페이지를 바꿀 때 목록 맨 위(첫 카드)로 올린다. */
    private fun scrollToListTop() {
        uiHandler.post {
            val header = listContainer.top - ui.dp(6f)
            scroll.smoothScrollTo(0, header.coerceAtLeast(0))
        }
    }

    private fun pagerArrow(label: String, enabled: Boolean, onClick: () -> Unit): TextView {
        val tv = ui.text(14f, if (enabled) Colors.TXT else Colors.DIM2, true)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.width = ui.dp(40f)
        tv.height = ui.dp(38f)
        tv.background = ui.round(Colors.CARD, 10f, Colors.LINE, 1f)
        if (enabled) {
            tv.isClickable = true
            tv.setOnClickListener { onClick() }
        } else {
            tv.alpha = 0.45f
        }
        return tv
    }

    private fun pagerNumber(number: Int): TextView {
        val current = number - 1 == pageIndex
        val tv = ui.text(13.5f, if (current) 0xFFFFFFFF.toInt() else Colors.DIM, current)
        tv.text = number.toString()
        tv.gravity = Gravity.CENTER
        val params = LinearLayout.LayoutParams(ui.dp(34f), ui.dp(34f))
        params.marginStart = ui.dp(3f)
        params.marginEnd = ui.dp(3f)
        tv.layoutParams = params
        tv.background = if (current) {
            ui.round(Colors.ACCENT, 10f)
        } else {
            ui.round(Colors.BG2, 10f, Colors.LINE, 1f)
        }
        tv.isClickable = true
        tv.setOnClickListener { if (!current) goToPage(number - 1) }
        return tv
    }

    /** 첫/마지막 페이지로 바로 가는 끝 번호 */
    private fun pagerEdge(number: Int, target: Int): TextView {
        val tv = pagerNumber(number)
        tv.setOnClickListener { goToPage(target) }
        return tv
    }

    private fun pagerGap(): TextView {
        val tv = ui.text(13f, Colors.DIM2)
        tv.text = "…"
        tv.gravity = Gravity.CENTER
        tv.width = ui.dp(16f)
        return tv
    }

    /** [이전 글 더 불러오기] — 저장된 목록보다 더 내려가 어제·그제 글을 채운다. */
    private fun deepLoadButton(): TextView {
        val next = deepPages + 1
        val last = (deepPages + DEEP_STEP).coerceAtMost(DEEP_MAX_PAGE)
        val label = when {
            loading -> "불러오는 중…"
            next > DEEP_MAX_PAGE -> "이전 글 더 없음"
            else -> "⏮ 이전 글 더 불러오기  (다음 3페이지 · p." + next + "~" + last + ")"
        }
        val tv = footerButton(label) { if (!loading && next <= DEEP_MAX_PAGE) startLoad(LoadMode.DEEP) }
        if (loading || next > DEEP_MAX_PAGE) {
            tv.alpha = 0.5f
            tv.isClickable = false
            tv.setOnClickListener(null)
        }
        return tv
    }

    private fun footerButton(label: String, onClick: () -> Unit): TextView {
        val tv = ui.text(14f, Colors.DIM, true)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(ui.dp(12f), ui.dp(15f), ui.dp(12f), ui.dp(15f))
        tv.background = ui.round(Colors.CARD, 12f, Colors.LINE, 1f)
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = ui.dp(6f)
        params.bottomMargin = ui.dp(6f)
        tv.layoutParams = params
        return tv
    }

    /** 글자를 칠 때마다 카드 40~60장을 다시 그리지 않도록 잠깐 묶었다가 그린다. */
    private fun scheduleRender() {
        uiHandler.removeCallbacks(renderTask)
        uiHandler.postDelayed(renderTask, 180L)
    }

    private fun bindCard(holder: CardViews, d: Deal) {
        cardFactory.bind(
            holder,
            d,
            ago(d.postedAt),
            { openShop(d) },
            { openPost(d) }
        )
    }

    private fun updateCard(d: Deal) {
        val holder = holders[d.key] ?: return
        bindCard(holder, d)
    }

    private fun renderEmpty(title: String, message: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(16f), ui.dp(54f), ui.dp(16f), ui.dp(40f))
        }
        val titleView = ui.text(16f, Colors.DIM, true)
        titleView.text = title
        titleView.gravity = Gravity.CENTER
        box.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val messageView = ui.text(14f, Colors.DIM2, false)
        messageView.text = message
        messageView.gravity = Gravity.CENTER
        messageView.setLineSpacing(ui.dp(4f).toFloat(), 1f)
        messageView.setPadding(0, ui.dp(8f), 0, 0)
        box.addView(messageView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        listContainer.addView(box)
    }

    // ── 상태 표시 / 유틸 ──────────────────────────────────

    private fun stampSummary(newCount: Int = -1) {
        if (deals.isEmpty()) {
            stamp.text = "수집된 딜이 없습니다"
            return
        }
        val when_ = savedAt?.let { fmtClock(it) } ?: "방금"
        val head = when {
            newCount > 0 -> "새 글 " + newCount + "건 추가 · "
            newCount == 0 -> "새 글 없음 · "
            else -> ""
        }
        stamp.text = head + "누적 " + deals.size + "건 · " + spanText() + " · " + when_ + " 갱신"
    }

    /** 저장된 목록이 지금 몇 일치인지 (가장 오래된 글 기준) */
    private fun spanText(): String {
        val oldest = deals.minOfOrNull { it.ageBase } ?: return ""
        val hours = (System.currentTimeMillis() - oldest) / 3_600_000.0
        return when {
            hours < 1.0 -> "1시간 이내"
            hours < 48.0 -> String.format(Locale.KOREA, "%.1f시간치", hours)
            else -> String.format(Locale.KOREA, "%.1f일치", hours / 24.0)
        }
    }

    private fun fmtClock(ts: Long): String =
        SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(ts))

    private fun ago(ts: Long?): String {
        if (ts == null) return "시각 정보 없음"
        val diff = System.currentTimeMillis() - ts
        if (diff < 0) return "방금"
        val minutes = diff / 60_000L
        if (minutes < 1L) return "방금"
        if (minutes < 60L) return minutes.toString() + "분 전"
        val hours = minutes / 60L
        if (hours < 24L) return hours.toString() + "시간 전"
        val days = hours / 24L
        if (days < 7L) return days.toString() + "일 전"
        return SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(ts))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "링크를 열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (challengeBar.visibility == View.VISIBLE) {
            challengeClosed = true
            showChallenge(false)
            return
        }
        super.onBackPressed()
    }

    companion object {
        private const val LIST_URL = "https://www.fmkorea.com/hotdeal"
        private const val CHALLENGE_WAIT_MS = 180_000L

        /** 목록 한 페이지에 보통 20건 (아래 PAGE_SIZE 와 맞춰 둔다) */
        private const val PAGE_SIZE = 20

        /** '새 글만' 새로고침이 훑는 최대 페이지 */
        private const val AUTO_MAX_PAGES = 15

        /** '이전 글 더 불러오기' 한 번에 넘어가는 페이지 수 */
        private const val DEEP_STEP = 3

        /** 이전 글을 아무리 내려가도 여기까지만 (7일 보관이라 대개 그 전에 멈춘다) */
        private const val DEEP_MAX_PAGE = 120

        /** 한 페이지에 보여 주는 글 수 */
        private const val ITEMS_PER_PAGE = 60

        /** 맨 위로 버튼을 띄우는 기준: 목록 첫 카드에서 이만큼 더 내려갔을 때만 */
        private const val TOP_BUTTON_MIN_DP = 200f

        /** 스크롤이 멈춘 뒤 버튼이 나타나기까지 */
        private const val TOP_BUTTON_DELAY_MS = 400L

        private const val PAGE_GAP_MS = 700L

        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
