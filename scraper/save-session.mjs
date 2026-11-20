#!/usr/bin/env node
/**
 * 세션 저장 도우미 (로컬 PC에서만 사용)
 * -------------------------------------
 * 에펨코리아는 데이터센터 IP에서 '사람인지 확인' 창을 띄웁니다.
 * 집/회사 PC처럼 사람이 쓰는 회선에서는 창이 저절로 넘어가는데,
 * 그 상태의 쿠키를 저장해 두면 이후 크롤러가 그 세션으로 접속할 수 있습니다.
 *
 *   node scraper/save-session.mjs
 *   → 브라우저 창이 뜨면 기다립니다. 목록이 보이면 자동으로 storage-state.json 저장.
 *   node scraper/crawl.mjs --storage storage-state.json
 */
import { chromium } from 'playwright';
import { writeFile } from 'node:fs/promises';

const OUT = process.argv[2] || 'storage-state.json';
const WAIT_MS = 5 * 60 * 1000;

const browser = await chromium.launch({ headless: false, args: ['--no-sandbox'] });
const context = await browser.newContext({
  locale: 'ko-KR',
  timezoneId: 'Asia/Seoul',
  viewport: { width: 1280, height: 900 },
});
const page = await context.newPage();
await page.goto('https://www.fmkorea.com/hotdeal', { waitUntil: 'domcontentloaded' });

const started = Date.now();
let ok = false;
while (Date.now() - started < WAIT_MS) {
  const title = await page.title().catch(() => '');
  if (!/보안 시스템/.test(title)) {
    ok = true;
    break;
  }
  await new Promise((r) => setTimeout(r, 3000));
}

if (!ok) {
  console.error('보안 확인이 넘어가지 않았습니다. 창에서 확인 절차를 마친 뒤 다시 실행해 주세요.');
  await browser.close();
  process.exit(2);
}

const state = await context.storageState();
await writeFile(OUT, JSON.stringify(state, null, 2));
console.log(`저장 완료: ${OUT} (쿠키 ${state.cookies.length}개)`);
await browser.close();
