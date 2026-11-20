#!/usr/bin/env node
/**
 * 에펨코리아 핫딜 크롤러
 * ---------------------
 * https://www.fmkorea.com/hotdeal 목록을 읽고, 각 글의 상세 페이지에서
 *  - 쇼핑몰 바로가기 링크
 *  - '유사한 최근 6개월 핫딜들' 비교 목록
 *  - '종료 신고' 수
 * 를 뽑아 JSON 하나로 저장합니다.
 *
 * 사용 예)
 *   node scraper/crawl.mjs                       # 목록 20건 + 상세 전부
 *   node scraper/crawl.mjs --limit 20 --details 20
 *   node scraper/crawl.mjs --fixture fixtures/list-sample.html   # 파서 자체 점검
 *
 * 종료 코드
 *   0  성공 (JSON 저장)
 *   2  에펨코리아 보안 시스템(Turnstile)을 통과하지 못해 중단 — 파일을 건드리지 않음
 *   3  목록에서 핫딜을 하나도 찾지 못함 (사이트 구조 변경 의심)
 */
import { chromium } from 'playwright';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';

const BASE = 'https://www.fmkorea.com';
const LIST_URL = `${BASE}/hotdeal`;
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

// ---------------------------------------------------------------- args
function getArg(name, fallback = null) {
  const i = process.argv.indexOf(`--${name}`);
  if (i === -1) return fallback;
  const v = process.argv[i + 1];
  return v && !v.startsWith('--') ? v : 'true';
}

const LIMIT = Number(getArg('limit', 20));
const DETAILS = Number(getArg('details', LIMIT)); // 상세 페이지를 읽을 글 수 (0 이면 목록만)
const DELAY_MS = Number(getArg('delay', 1800)); // 상세 페이지 사이 정중한 간격
const OUT = getArg('out', 'docs/data/deals.json');
const FIXTURE = getArg('fixture', null);
const DEBUG_DIR = getArg('debug-dir', null);
const STORAGE = getArg('storage', null); // Playwright storageState JSON (save-session.mjs 산출물)
const CHALLENGE_TIMEOUT_MS = Number(getArg('challenge-timeout', 90000));

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.error('[crawl]', ...a);

// ------------------------------------------------------- in-page parsers
/** 목록 페이지 파서 (브라우저 안에서 실행됨) */
function parseListInPage(origin) {
  const clean = (s) => (s || '').replace(/\s+/g, ' ').trim();
  const rows = [...document.querySelectorAll('li')].filter((li) => li.querySelector('.hotdeal_info'));

  return rows.map((li) => {
    const titleLink = li.querySelector('h3.title a[href]') || li.querySelector('h3 a[href]');
    const href = titleLink ? titleLink.getAttribute('href') : '';
    const id = (href.match(/(\d{6,})/) || [])[1] || null;

    const titleEl = li.querySelector('h3.title .ellipsis-target') || titleLink;
    let title = clean(titleEl ? titleEl.textContent : '');
    // 목록 제목에는 댓글 수가 [3] 형태로 섞여 들어오는 경우가 있어 제거
    title = title.replace(/\s*\[\d+\]\s*$/, '').trim();

    const info = li.querySelector('.hotdeal_info');
    const infoText = clean(info ? info.textContent : '');
    const mall = (infoText.match(/쇼핑몰\s*:\s*([^/]+?)\s*(?:\/|$)/) || [])[1];
    const price = (infoText.match(/가격\s*:\s*([^/]+?)\s*(?:\/|$)/) || [])[1];
    const shipping = (infoText.match(/배송\s*:\s*([^/]+?)\s*$/) || [])[1];

    const category = clean(li.querySelector('span.category a')?.textContent || '');
    const upvotesRaw = clean(li.querySelector('.pc_voted_count .count')?.textContent || '');
    const upvotes = upvotesRaw === '' ? null : Number(upvotesRaw.replace(/[^\-\d]/g, ''));

    const commentEl = li.querySelector('a[href*="#comment"], .comment_count');
    let comments = null;
    if (commentEl) {
      const m = clean(commentEl.textContent).match(/\d+/);
      if (m) comments = Number(m[0]);
    }
    if (comments === null) {
      const m = clean(li.innerText).match(/\[(\d+)\]\s*쇼핑몰\s*:/);
      if (m) comments = Number(m[1]);
    }

    const regdate = clean(li.querySelector('span.regdate')?.textContent || '');
    const author = clean(li.querySelector('span.author')?.textContent || '').replace(/^\/\s*/, '');

    // 목록에서 '종료' 표시가 붙는 경우를 잡아 둔다 (없으면 상세의 종료 신고로 판단)
    const endedBadge = [...li.querySelectorAll('span, em, strong')].some(
      (el) => clean(el.textContent) === '종료' && el.children.length === 0
    );

    return {
      id,
      title,
      mall: mall || null,
      price: price && price !== '/' ? price : null,
      shipping: shipping || null,
      category: category || null,
      upvotes,
      comments,
      regdate,
      author: author || null,
      url: id ? `${origin}/${id}` : null,
      ended_badge: endedBadge,
    };
  });
}

/** 상세 페이지 파서 (브라우저 안에서 실행됨) */
function parseDetailInPage() {
  const clean = (s) => (s || '').replace(/\s+/g, ' ').trim();
  const out = {
    posted_at: null,
    shop_url: null,
    comparison_total: null,
    ended_reports: 0,
    comparisons: [],
  };

  // 작성 시각: 본문 상단의 YYYY.MM.DD HH:MM
  const head = clean(document.body.innerText.slice(0, 4000));
  const dm = head.match(/(20\d{2})\.(\d{2})\.(\d{2})\s+(\d{2}):(\d{2})/);
  if (dm) out.posted_at = `${dm[1]}-${dm[2]}-${dm[3]}T${dm[4]}:${dm[5]}:00+09:00`;

  // 본문 정보 표: 링크 / 쇼핑몰 / 상품명 / 가격 / 배송 / (종료 신고)
  for (const tr of document.querySelectorAll('tr')) {
    const headCell = tr.querySelector('th');
    if (!headCell) continue;
    const label = clean(headCell.textContent);
    const rowText = clean(tr.innerText);
    if (label === '링크') {
      const a = tr.querySelector('a[href^="http"]');
      if (a) out.shop_url = a.getAttribute('href');
    } else if (label === '종료 신고') {
      const m = rowText.match(/(\d+)\s*명/);
      out.ended_reports = m ? Number(m[1]) : 1;
    }
  }
  // 링크가 표 밖에 있는 구형 글 대비 (본문 첫 외부 링크)
  if (!out.shop_url) {
    const a = [...document.querySelectorAll('article a[href^="http"]')].find(
      (el) => !/fmkorea\.com/.test(el.getAttribute('href'))
    );
    if (a) out.shop_url = a.getAttribute('href');
  }

  // '유사한 최근 6개월 핫딜들'
  const ul = [...document.querySelectorAll('ul')].find((u) =>
    /유사한 최근 6개월 핫딜들/.test(u.innerText || '')
  );
  if (!ul) return out;

  for (const li of [...ul.children]) {
    if (li.tagName !== 'LI') continue;
    const text = clean(li.innerText);
    if (/유사한 최근 6개월/.test(text)) continue;

    const totalM = text.match(/총\s*(\d+)\s*개/);
    if (/^더\s*보기/.test(text)) {
      if (totalM) out.comparison_total = Number(totalM[1]);
      continue;
    }

    const link = li.querySelector('a');
    const name = clean(link ? link.innerText : text.split('\n')[0]);
    const priceText = (text.match(/가격\s*:\s*([0-9,]+\s*원|[$][\d.,]+|다양|가격다양함|[^\s]+)/) || [])[1] || null;
    const diffText =
      (text.match(/본문 상품이\s*[\d,]+\s*원\s*더\s*(?:저렴|비쌈)/) || [])[0] || null;
    const posted = (text.match(/등록일\s*:\s*(\d{4}-\d{2}-\d{2})/) || [])[1] || null;

    let price_krw = null;
    const won = priceText && priceText.match(/([\d,]+)\s*원/);
    if (won) price_krw = Number(won[1].replace(/,/g, ''));

    let is_cheaper = null;
    let savings_krw = null;
    if (diffText) {
      const num = Number(diffText.replace(/[^\d]/g, ''));
      if (/더\s*저렴/.test(diffText)) {
        is_cheaper = true;
        savings_krw = num;
      } else {
        is_cheaper = false;
        savings_krw = -num;
      }
    }

    out.comparisons.push({
      name,
      price: priceText,
      price_krw,
      diff_text: diffText,
      is_cheaper,
      savings_krw,
      posted_at: posted,
    });
  }

  // '더 보기' 항목이 DOM에 없을 때는 문구에서 총 개수만 긁어온다
  if (out.comparison_total === null) {
    const m = clean(ul.innerText).match(/총\s*(\d+)\s*개/);
    if (m) out.comparison_total = Number(m[1]);
  }
  return out;
}

// ------------------------------------------------------------ challenge
/**
 * '에펨코리아 보안 시스템'(Cloudflare Turnstile)은 사람 확인이 끝나면 스스로
 * 목록 화면으로 넘어간다. 여기서는 우회하지 않고, 그냥 통과를 기다린다.
 */
async function waitForChallengeClear(page) {
  const started = Date.now();
  let waited = 0;
  while (Date.now() - started < CHALLENGE_TIMEOUT_MS) {
    const title = await page.title().catch(() => '');
    const blocked = /보안 시스템/.test(title) || (await page.$("input[name='cf-turnstile-response']")) !== null;
    if (!blocked) return true;
    if (waited % 15000 === 0) log(`보안 확인 대기 중… (${Math.round((Date.now() - started) / 1000)}s)`);
    await sleep(2000);
    waited += 2000;
  }
  return false;
}

async function dumpDebug(page) {
  if (!DEBUG_DIR) return;
  await mkdir(DEBUG_DIR, { recursive: true });
  await page.screenshot({ path: path.join(DEBUG_DIR, 'blocked.png'), fullPage: false }).catch(() => {});
  await writeFile(path.join(DEBUG_DIR, 'blocked.html'), await page.content().catch(() => '')).catch(() => {});
  log(`디버그 산출물: ${DEBUG_DIR}/blocked.png, blocked.html`);
}

// --------------------------------------------------------------- crawl
async function crawlList(page) {
  await page.goto(LIST_URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
  if (!(await waitForChallengeClear(page))) {
    await dumpDebug(page);
    return { blocked: true, deals: [] };
  }
  // 목록이 실제로 그려질 때까지 조금 기다린다
  await page.waitForSelector('.hotdeal_info', { timeout: 20000 }).catch(() => {});
  const deals = await page.evaluate(parseListInPage, BASE);
  return { blocked: false, deals };
}

async function crawlDetail(page, deal) {
  await page.goto(deal.url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  if (!(await waitForChallengeClear(page))) return { blocked: true };
  await page.waitForSelector("text=유사한 최근 6개월 핫딜들", { timeout: 8000 }).catch(() => {});
  return page.evaluate(parseDetailInPage);
}

/** regdate(02:21 / 11-18 23:33 / 2026.11.18 …)를 절대 시각으로 환산 */
function resolvePostedAt(deal, now = new Date()) {
  if (deal.posted_at) return deal.posted_at;
  const reg = (deal.regdate || '').trim();
  const pad = (n) => String(n).padStart(2, '0');
  const fmt = (d) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(
      d.getMinutes()
    )}:00+09:00`;
  let m = reg.match(/^(\d{2}):(\d{2})$/);
  if (m) {
    // 오늘 시각으로 표기된 글. 크롤 시각보다 미래면 전날로 본다.
    const d = new Date(now);
    d.setHours(Number(m[1]), Number(m[2]), 0, 0);
    if (d.getTime() > now.getTime() + 60_000) d.setDate(d.getDate() - 1);
    return fmt(d);
  }
  m = reg.match(/^(\d{2})[-.](\d{2})\s+(\d{2}):(\d{2})$/);
  if (m) {
    const d = new Date(now);
    d.setMonth(Number(m[1]) - 1, Number(m[2]));
    d.setHours(Number(m[3]), Number(m[4]), 0, 0);
    if (d.getTime() > now.getTime() + 60_000) d.setFullYear(d.getFullYear() - 1);
    return fmt(d);
  }
  m = reg.match(/^(20\d{2})[-.](\d{2})[-.](\d{2})/);
  if (m) return `${m[1]}-${m[2]}-${m[3]}T00:00:00+09:00`;
  return null;
}

function priceToKrw(text) {
  if (!text) return null;
  const won = text.match(/([\d,]+)\s*원/);
  if (won) return Number(won[1].replace(/,/g, ''));
  return null; // '제품별0상이', '$13.97' 처럼 원화로 환산할 수 없는 표기는 null
}

function summarize(deal) {
  const comparisons = deal.comparisons || [];
  const cheaper = comparisons.filter((c) => c.is_cheaper === true && (c.savings_krw || 0) > 0);
  const best = cheaper.reduce((m, c) => Math.max(m, c.savings_krw), 0);
  const seen = comparisons.filter((c) => c.diff_text);
  const is_ended = deal.ended_reports > 0 || deal.ended_badge === true;
  return {
    ...deal,
    price_krw: priceToKrw(deal.price),
    is_ended,
    has_comparisons: comparisons.length > 0,
    is_cheaper: seen.length ? cheaper.length > 0 : null,
    best_savings_krw: best || null,
  };
}

async function main() {
  const startedAt = new Date();
  const browser = await chromium.launch({
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
    headless: true,
  });
  const context = await browser.newContext({
    ...(STORAGE ? { storageState: STORAGE } : {}),
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    userAgent: UA,
    viewport: { width: 1280, height: 900 },
  });
  const page = await context.newPage();

  let list;
  if (FIXTURE) {
    log(`fixture 모드: ${FIXTURE}`);
    const html = await readFile(FIXTURE, 'utf8');
    await page.setContent(html, { waitUntil: 'domcontentloaded' });
    list = { blocked: false, deals: (await page.evaluate(parseListInPage, BASE)).slice(0, LIMIT) };
  } else {
    list = await crawlList(page);
    list.deals = list.deals.slice(0, LIMIT);
  }

  if (list.blocked) {
    await browser.close();
    log(
      '보안 시스템을 통과하지 못했습니다. (에펨코리아 Turnstile은 데이터센터 IP에서 사람 확인을 요구합니다)\n' +
        '   → 로컬 PC에서 다시 실행하거나, 저장된 세션(스토리지 상태)으로 실행해 보세요. README.md 참고.'
    );
    process.exit(2);
  }
  if (!list.deals.length) {
    await browser.close();
    log('핫딜 목록을 찾지 못했습니다. 사이트 구조가 바뀌었는지 확인하세요.');
    process.exit(3);
  }
  log(`목록 ${list.deals.length}건 수집`);

  const deals = [];
  let blocked = false;
  for (let i = 0; i < list.deals.length; i++) {
    const deal = list.deals[i];
    let detail = null;
    if (i < DETAILS && !FIXTURE) {
      await sleep(DELAY_MS + Math.floor(Math.random() * 700));
      try {
        detail = await crawlDetail(page, deal);
      } catch (err) {
        log(`  ${deal.id} 상세 실패: ${err.message}`);
      }
      if (detail && detail.blocked) {
        blocked = true;
        break;
      }
      if (detail) log(`  ${deal.id} 상세 OK (비교 ${detail.comparisons.length}건)`);
    }
    const merged = {
      ...deal,
      ...(detail || {}),
      id: deal.id,
      title: deal.title,
      mall: deal.mall,
      price: deal.price,
      shipping: deal.shipping,
      category: deal.category,
      upvotes: deal.upvotes,
      comments: deal.comments,
      url: deal.url,
      posted_at: resolvePostedAt({ ...deal, ...(detail || {}) }, startedAt),
    };
    deals.push(summarize(merged));
  }

  if (blocked) {
    await browser.close();
    log('상세 페이지 수집 중 보안 확인에 막혀 중단했습니다. 기존 JSON은 그대로 둡니다.');
    process.exit(2);
  }
  await browser.close();

  const withComparisons = deals.filter((d) => d.has_comparisons).length;
  const payload = {
    generated_at: new Date().toISOString(),
    source: 'https://www.fmkorea.com/hotdeal',
    crawl: {
      list_items: deals.length,
      detail_pages: deals.filter((d) => d.has_comparisons || d.shop_url).length,
      cheaper_count: deals.filter((d) => d.is_cheaper === true).length,
      engine: 'playwright-chromium',
      notes:
        '에펨코리아 핫딜 목록과 각 글의 “유사한 최근 6개월 핫딜들” 비교 블록에서 수집. ' +
        '가격 표기가 “제품별0상이”, “$13.97”처럼 원화가 아니면 price_krw는 null.',
    },
    deals,
  };

  await mkdir(path.dirname(OUT), { recursive: true });
  await writeFile(OUT, JSON.stringify(payload, null, 2) + '\n', 'utf8');
  log(`저장: ${OUT} (딜 ${deals.length}건 / 비교 데이터 ${withComparisons}건)`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

export { parseListInPage, parseDetailInPage, resolvePostedAt, summarize };
