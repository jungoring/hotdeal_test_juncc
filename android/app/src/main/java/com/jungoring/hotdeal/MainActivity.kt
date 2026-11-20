package com.jungoring.hotdeal

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
import com.jungoring.hotdeal.data.FilterPrefs
import com.jungoring.hotdeal.data.Js
import com.jungoring.hotdeal.data.PageLoader
import com.jungoring.hotdeal.data.Parsers
import com.jungoring.hotdeal.model.Deal
import com.jungoring.hotdeal.model.DealDetail
import com.jungoring.hotdeal.ui.CardFactory
import com.jungoring.hotdeal.ui.CardViews
import com.jungoring.hotdeal.ui.Colors
import com.jungoring.hotdeal.ui.Ui
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var ui: Ui
    private lateinit var prefs: FilterPrefs
    private lateinit var cardFactory: CardFactory

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
    private lateinit var tCheaper: TextView
    private lateinit var tEnded: TextView
    private lateinit var countText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var btnRefresh: TextView

    private val deals = ArrayList<Deal>()
    private val holders = LinkedHashMap<String, CardViews>()
    private val expanded = HashSet<String>()
    private val detailQueue = ArrayDeque<Deal>()

    private var loadJob: Job? = null
    private var detailJob: Job? = null

    private var q: String = ""
    private var ex: String = ""
    private var cat: String = "전체"
    private var minVotes: Int = 0
    private var cheaperOnly: Boolean = false
    private var hideEnded: Boolean = true
    private var collectCount: Int = 20

    private var snapshotAt: Long? = null
    private var challengeClosed: Boolean = false
    private var lastLoadFailed: Boolean = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val renderTask = Runnable { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ui = Ui(this)
        prefs = FilterPrefs(this)
        cardFactory = CardFactory(this)

        bindViews()
        setupWebView()
        loadFilterState()
        setupFilterUi()
        startLoad()
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
        tCheaper = findViewById(R.id.tCheaper)
        tEnded = findViewById(R.id.tEnded)
        countText = findViewById(R.id.countText)
        listContainer = findViewById(R.id.listContainer)
        btnRefresh = findViewById(R.id.btnRefresh)
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
        cheaperOnly = prefs.cheaperOnly
        hideEnded = prefs.hideEnded
        collectCount = prefs.count
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
                scheduleRender()
            }
        })
        exInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                ex = s?.toString() ?: ""
                prefs.exclude = ex
                scheduleRender()
            }
        })

        btnRefresh.setOnClickListener { startLoad() }
        findViewById<TextView>(R.id.btnReset).setOnClickListener { resetFilters() }
        findViewById<TextView>(R.id.btnChallengeClose).setOnClickListener {
            challengeClosed = true
            showChallenge(false)
        }

        tCheaper.setOnClickListener {
            cheaperOnly = !cheaperOnly
            prefs.cheaperOnly = cheaperOnly
            syncToggles()
            render()
        }
        tEnded.setOnClickListener {
            hideEnded = !hideEnded
            prefs.hideEnded = hideEnded
            syncToggles()
            render()
        }

        refreshFilterRows()
        syncToggles()
        render()
    }

    // ── 수집 ──────────────────────────────────────────────

    private fun startLoad() {
        detailQueue.clear()
        detailJob?.cancel()
        detailJob = null
        loadJob?.cancel()
        challengeClosed = false
        lastLoadFailed = false
        snapshotAt = null
        stamp.text = "에펨코리아에서 불러오는 중…"
        btnRefresh.isEnabled = false
        btnRefresh.alpha = 0.5f

        loadJob = lifecycleScope.launch {
            try {
                val pages = (collectCount + 19) / 20
                val collected = ArrayList<Deal>()
                val seen = HashSet<String>()

                for (page in 1..pages) {
                    val url = if (page == 1) LIST_URL else LIST_URL + "?page=" + page
                    if (!openPage(url)) {
                        break
                    }
                    val pageDeals = fetchDeals()
                    if (pageDeals.isEmpty()) break
                    for (d in pageDeals) {
                        if (seen.add(d.key)) collected.add(d)
                        if (collected.size >= collectCount) break
                    }
                    if (collected.size >= collectCount) break
                    if (pageDeals.size < 20) break
                    delay(800)
                }

                deals.clear()
                deals.addAll(collected.take(collectCount))
                snapshotAt = System.currentTimeMillis()
                expanded.clear()
                refreshFilterRows()

                if (deals.isEmpty()) {
                    lastLoadFailed = true
                    if (challengeClosed) {
                        stamp.text = "보안 확인을 통과하지 못해 멈췄습니다"
                    } else {
                        stamp.text = "목록을 읽지 못했습니다"
                    }
                    render()
                    return@launch
                }

                render()
                stampLoaded(false)
                enqueueDetails(deals.toList(), false)
            } finally {
                btnRefresh.isEnabled = true
                btnRefresh.alpha = 1f
            }
        }
    }

    /**
     * 목록 페이지를 연다. 보안 시스템 화면이 나오면 WebView 를 화면에 보여 주고,
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
            stamp.text = "보안 확인 대기 중…"
        }
    }

    // ── 상세(6개월 비교) 순차 수집 ─────────────────────────

    private fun enqueueDetails(items: List<Deal>, toFront: Boolean) {
        val pending = items.filter { !it.detailLoaded && !it.url.isNullOrBlank() }
        if (pending.isEmpty()) return
        if (toFront) {
            for (i in pending.indices.reversed()) detailQueue.addFirst(pending[i])
        } else {
            detailQueue.addAll(pending)
        }
        pumpDetails()
    }

    private fun pumpDetails() {
        if (detailJob?.isActive == true) return
        if (detailQueue.isEmpty()) {
            stampLoaded(true)
            return
        }
        detailJob = lifecycleScope.launch {
            var failed = false
            while (isActive && detailQueue.isNotEmpty()) {
                val d = detailQueue.removeFirst()
                if (d.detailLoaded) continue
                if (!fetchDetail(d)) {
                    failed = true
                    break
                }
                delay(DETAIL_DELAY_MS)
            }
            detailJob = null
            if (isActive) stampLoaded(true)
            // 막혀서 멈춘 게 아니라면, 기다리는 사이에 대기열에 새로 들어온 건을 이어서 읽는다
            if (!failed && detailQueue.isNotEmpty()) pumpDetails()
        }
    }

    /** @return 계속 진행해도 되면 true (막혔거나 사용자가 닫으면 false) */
    private suspend fun fetchDetail(d: Deal): Boolean {
        val url = d.url
        if (url.isNullOrBlank()) {
            d.detailLoaded = true
            return true
        }
        if (!loader.load(url)) {
            d.detailLoaded = true
            return true
        }
        if (isBlocked()) {
            challengeClosed = false
            showChallenge(true)
            var waited = 0L
            while (waited < CHALLENGE_WAIT_MS && isBlocked()) {
                if (challengeClosed) break
                delay(1500)
                waited += 1500
            }
            showChallenge(false)
            if (challengeClosed || isBlocked()) return false
            if (!loader.load(url)) {
                d.detailLoaded = true
                return true
            }
        }

        var detail: DealDetail? = null
        for (attempt in 0 until 3) {
            val json = Js.decode(loader.eval(Parsers.DETAIL_JS))
            if (json != null && json.startsWith("{")) {
                val parsed = Deal.parseDetail(json)
                detail = parsed
                if (parsed.comparisons.isNotEmpty()) break
            }
            delay(600)
        }
        val loaded = detail
        if (loaded != null) {
            val wasMatching = matches(d)
            d.applyDetail(loaded)
            if (matches(d) != wasMatching) {
                render()
            } else {
                updateCard(d)
            }
            stampLoaded(false)
        } else {
            d.detailLoaded = true
        }
        return true
    }

    // ── 필터 ─────────────────────────────────────────────

    private fun matches(d: Deal): Boolean {
        if (cat != "전체" && (d.category ?: "") != cat) return false
        if (minVotes > 0 && (d.upvotes ?: 0) < minVotes) return false
        if (cheaperOnly && d.isCheaper != true) return false
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
        cheaperOnly = false
        hideEnded = true
        prefs.q = q
        prefs.exclude = ex
        prefs.category = cat
        prefs.minVotes = minVotes
        prefs.cheaperOnly = cheaperOnly
        prefs.hideEnded = hideEnded
        qInput.setText("")
        exInput.setText("")
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
                refreshFilterRows()
                render()
            }
            votesRow.addView(tv, weightParams(4))
        }

        // 수집 건수 (바꾸면 바로 다시 수집)
        countRow.removeAllViews()
        for (n in intArrayOf(20, 40, 60)) {
            val tv = segView(n.toString() + "건", collectCount == n)
            tv.setOnClickListener {
                if (collectCount != n) {
                    collectCount = n
                    prefs.count = n
                    refreshFilterRows()
                    startLoad()
                }
            }
            countRow.addView(tv, weightParams(3))
        }
    }

    private fun syncToggles() {
        styleToggle(tCheaper, cheaperOnly, "이전보다 싼 것만")
        styleToggle(tEnded, hideEnded, "종료된 딜 숨김")
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
        val tv = ui.text(13f, if (selected) 0xFFFFFFFF.toInt() else Colors.DIM, true)
        tv.text = label
        tv.setPadding(ui.dp(13f), ui.dp(7f), ui.dp(13f), ui.dp(7f))
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
        val tv = ui.text(12.5f, if (selected) Colors.TXT else Colors.DIM, true)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, ui.dp(7f), 0, ui.dp(7f))
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

        countText.text = filtered.size.toString() + "건 / 전체 " + deals.size + "건"

        if (deals.isEmpty()) {
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
                    "잠시만 기다려 주세요. 오래 걸리면 ‘↻ 새로고침’을 눌러 다시 시도할 수 있습니다."
                )
            }
            return
        }
        if (filtered.isEmpty()) {
            renderEmpty(
                "조건에 맞는 딜이 없습니다",
                "필터를 조금 풀어보세요. ‘이전보다 싼 것만’을 끄면 전체 " + deals.size + "건을 볼 수 있습니다."
            )
            return
        }

        for (d in filtered) {
            val holder = cardFactory.create()
            bindCard(holder, d)
            holder.body.setOnClickListener { toggle(d) }
            listContainer.addView(holder.root)
            holders[d.key] = holder
        }
    }

    /** 글자를 칠 때마다 카드 40~60장을 다시 그리지 않도록 잠깐 묶었다가 그린다. */
    private fun scheduleRender() {
        uiHandler.removeCallbacks(renderTask)
        uiHandler.postDelayed(renderTask, 180L)
    }

    private fun bindCard(holder: CardViews, d: Deal) {
        cardFactory.bind(holder, d, expanded.contains(d.key), ago(d.postedAt)) { url ->
            openUrl(url)
        }
    }

    private fun updateCard(d: Deal) {
        val holder = holders[d.key] ?: return
        bindCard(holder, d)
    }

    private fun toggle(d: Deal) {
        val key = d.key
        val wasExpanded = expanded.contains(key)
        if (wasExpanded) {
            expanded.remove(key)
        } else {
            expanded.add(key)
            if (!d.detailLoaded) enqueueDetails(listOf(d), true)
        }
        updateCard(d)
    }

    private fun renderEmpty(title: String, message: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(16f), ui.dp(54f), ui.dp(16f), ui.dp(40f))
        }
        val titleView = ui.text(15f, Colors.DIM, true)
        titleView.text = title
        titleView.gravity = Gravity.CENTER
        box.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val messageView = ui.text(13f, Colors.DIM2, false)
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

    private fun stampLoaded(finished: Boolean) {
        if (deals.isEmpty()) {
            stamp.text = "수집된 딜이 없습니다"
            return
        }
        val detailCount = deals.count { it.detailLoaded }
        val when_ = snapshotAt?.let { fmtClock(it) } ?: "방금"
        val tail = if (finished) " 완료" else " 수집 중…"
        stamp.text = when_ + " 기준 · " + deals.size + "건 · 비교 " + detailCount + "/" + deals.size + tail
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
        private const val DETAIL_DELAY_MS = 1200L
        private const val CHALLENGE_WAIT_MS = 180_000L
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
