# 펨코 핫딜 필터 (PWA)

에펨코리아 [핫딜 게시판](https://www.fmkorea.com/hotdeal)을 30분마다 긁어서,
**이전 핫딜보다 싼 것만** 골라 보는 모바일 웹앱입니다.
서버도 비용도 필요 없습니다. GitHub 저장소 + GitHub Pages + GitHub Actions 만으로 0원에 돌아갑니다.

- 목록: 쇼핑몰·가격·배송·카테고리·추천·댓글·"이전보다 OOO원 저렴" 배지
- 필터: 검색어 / 제외 키워드 / 카테고리 칩 / 추천수(전체·3+·5+·10+) / 이전보다 싼 것만 / 종료된 딜 숨김
- 카드 탭 → 최근 6개월 유사 핫딜 비교 목록 + **쇼핑몰 바로가기** / **펨코 원문 보기**
- 홈 화면에 추가하면 앱처럼 뜨고(PWA), 마지막 데이터를 캐시해 오프라인에서도 열립니다.

---

## 1. 올리기 (3단계)

**① 저장소 만들기**
GitHub에서 새 저장소(Public 권장)를 만들고 이 폴더의 내용을 그대로 올립니다.
(웹에서 Upload files 로 끌어다 놓아도 됩니다. `.github` 폴더까지 포함하세요.)

```bash
git init && git add . && git commit -m "feat: 펨코 핫딜 필터"
git branch -M main
git remote add origin https://github.com/<아이디>/<저장소>.git
git push -u origin main
```

**② Pages 켜기**
저장소 → **Settings → Pages** → *Source: Deploy from a branch* → **Branch: `main`, 폴더: `/docs`** → Save.
1~2분 뒤 `https://<아이디>.github.io/<저장소>/` 에서 열립니다.

**③ Actions 허용**
저장소 → **Settings → Actions → General → Workflow permissions** 에서
**Read and write permissions** 를 선택하고 Save.
그다음 **Actions 탭 → crawl-hotdeals → Run workflow** 로 한 번 수동 실행해 보세요.
성공하면 `docs/data/deals.json` 이 새 데이터로 커밋되고, Pages가 자동으로 최신 내용을 서빙합니다.

> 폰에서 열고 브라우저 메뉴의 **홈 화면에 추가** 를 누르면 앱처럼 실행됩니다.

---

## 2. 폴더 구조

```
docs/                     ← GitHub Pages 가 그대로 서빙하는 폴더
  index.html              앱 본체 (모바일 카드 UI, 필터, 비교 펼침)
  manifest.json           PWA 매니페스트
  sw.js                   서비스 워커 (껍데기 캐시 + 데이터 네트워크 우선)
  icons/                  홈 화면 아이콘
  data/deals.json         ← 크롤러가 갱신하는 실제 데이터
scraper/
  crawl.mjs               크롤러 (Playwright/Chromium)
  save-session.mjs        (선택) 로컬에서 세션 쿠키 저장 도우미
  fixtures/               사이트 접속 없이 파서를 점검하는 샘플 HTML
.github/workflows/crawl.yml   30분 주기 자동 크롤 + 커밋
raw/                      이번에 실제로 수집한 상세 페이지 원본 기록(JSON)
```

## 3. 크롤러 직접 돌리기

```bash
cd scraper
npm install
npx playwright install chromium      # 최초 1회
node crawl.mjs --limit 20 --details 20 --out ../docs/data/deals.json
```

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `--limit` | 20 | 목록에서 가져올 딜 수 |
| `--details` | `--limit` 값 | 상세 페이지(비교 목록)를 읽을 딜 수. `0`이면 목록만 |
| `--delay` | 1800 | 상세 페이지 사이 대기(ms). 여기에 0~700ms 랜덤이 더 붙습니다 |
| `--out` | `docs/data/deals.json` | 결과 파일 |
| `--storage` | – | Playwright storageState 파일(세션 쿠키) |
| `--fixture` | – | 사이트 대신 로컬 HTML로 파서만 점검 |
| `--debug-dir` | – | 차단됐을 때 스크린샷/HTML 저장 위치 |

파서 자체는 인터넷 없이 점검할 수 있습니다.

```bash
npm run selftest     # fixtures/list-sample.html 로 목록 파서 확인
```

## 4. 데이터 형식 (`docs/data/deals.json`)

```jsonc
{
  "generated_at": "2026-11-19T02:45:00+09:00",
  "deals": [{
    "id": "10571413775",
    "title": "숯불용 삼겹살 외 부위 별 2kg (1kg+1kg)",
    "mall": "롯데온", "price": "16,500원", "price_krw": 16500,
    "shipping": "무료", "category": "먹거리",
    "upvotes": 4, "comments": 13,
    "posted_at": "2026-11-19T00:06:00+09:00",
    "url": "https://www.fmkorea.com/10571413775",
    "is_ended": false, "ended_reports": 0,
    "shop_url": "https://www.lotteon.com/p/product/LO2785281373",
    "comparison_total": 41,
    "comparisons": [{
      "name": "[네이버] 포크밸리 삼겹살 1kg",
      "price": "18,770원", "price_krw": 18770,
      "diff_text": "본문 상품이 2,270원 더 저렴",
      "is_cheaper": true, "savings_krw": 2270,
      "posted_at": "2026-10-29"
    }],
    "has_comparisons": true,
    "is_cheaper": true,
    "best_savings_krw": 2270
  }]
}
```

- `is_cheaper` / `best_savings_krw` 는 글의 **"유사한 최근 6개월 핫딜들"** 비교 문구(`본문 상품이 N원 더 저렴/비쌈`)에서 계산합니다. 비교 문구가 하나도 없는 글은 `null`.
- `price_krw` 는 원화 표기일 때만 숫자. `제품별0상이`, `$13.97` 처럼 원화가 아니면 `null`.
- `is_ended` 는 사이트가 확정한 판매 종료가 아니라 **글의 '종료 신고' 수** 기준입니다.

---

## 5. 알아둘 제약 (중요)

**에펨코리아는 Cloudflare Turnstile 기반 "보안 시스템"으로 자동 접속을 막습니다.**
크롤러는 이걸 우회하지 않고, 사람 확인이 끝나기를 그냥 기다립니다(`--challenge-timeout`, 기본 90초).

- **집/회사 PC(일반 회선)**: 대개 몇 초 안에 저절로 넘어갑니다. 로컬 실행은 문제없습니다.
- **GitHub Actions(데이터센터 IP)**: 사람 확인 창이 넘어가지 않을 수 있습니다.
  이 경우 크롤러는 **종료 코드 2** 로 끝나고 `deals.json` 을 **건드리지 않습니다**(마지막 정상 데이터 유지).
  Actions 실행 로그에 안내가 남습니다.
- 그때의 대안:
  1. 집 PC에서 `node scraper/crawl.mjs` 로 갱신하고 커밋 (가장 확실)
  2. 집 PC에서 `node scraper/save-session.mjs` 로 `storage-state.json` 을 만들어 두고
     `node scraper/crawl.mjs --storage storage-state.json` 으로 실행.
     이 파일 내용을 저장소 **Settings → Secrets and variables → Actions** 의
     `FMK_STORAGE_STATE` 시크릿으로 넣어두면 **Actions에서도 그 세션으로** 돕니다.
     (세션 쿠키는 보통 일주일 안팎이면 만료되므로, 차단되기 시작하면 다시 저장해 갱신하세요.)
  3. Actions 주기를 30분 → 하루 몇 번으로 줄이고, 로컬 갱신을 기본으로 사용

그 밖에:

- GitHub Actions 예약 실행은 **정확하지 않습니다**. 부하에 따라 5~20분 늦게 돌거나, 아주 드물게 건너뛰기도 합니다. (GitHub 정책)
- **Public 저장소**는 Actions 시간이 무제한 무료, **Private**는 월 2,000분 한도 안에서 돌아갑니다(이 작업은 1회 5분 이내).
- 상세 페이지를 20건 읽으면 1회 실행에 약 1분 안팎이 걸립니다. 상대 서버를 배려해 `--delay` 를 1000ms 아래로 내리지 마세요.
- 사이트 개편으로 목록/비교 영역의 마크업이 바뀌면 파서(`scraper/crawl.mjs` 안의 `parseListInPage`, `parseDetailInPage`)를 손봐야 합니다. 그때는 `--debug-dir` 로 HTML을 떠서 확인하세요.
