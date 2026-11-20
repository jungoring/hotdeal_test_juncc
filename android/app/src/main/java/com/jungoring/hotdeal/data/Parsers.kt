package com.jungoring.hotdeal.data

/**
 * WebView 안에서 실행하는 파서들.
 * 기존 scraper/crawl.mjs 의 parseListInPage / parseDetailInPage 와 같은 선택자를 쓴다.
 * (2026-11-20 실제 에펨코리아 목록·상세 페이지에서 결과 확인)
 */
object Parsers {

    /** 보안 시스템(Cloudflare Turnstile) 화면인지 확인 */
    val GUARD_JS: String = """
        (function(){
          try{
            var t = document.title || '';
            var hasTurnstile = !!document.querySelector("input[name='cf-turnstile-response']")
              || !!document.querySelector('#challenge-form')
              || !!document.querySelector('.cf-browser-verification')
              || !!document.querySelector('#cf-please-wait');
            var blocked = /보안 시스템/.test(t) || hasTurnstile;
            return JSON.stringify({ blocked: blocked, title: t });
          }catch(e){
            return JSON.stringify({ blocked: false, title: '', error: String(e) });
          }
        })()
    """.trimIndent()

    /** 핫딜 목록 파서 → JSON 배열 문자열 */
    val LIST_JS: String = """
        (function(){
          function clean(s){ return (s||'').replace(/\s+/g,' ').trim(); }
          var out = [];
          var lis = document.querySelectorAll('li');
          for (var i=0;i<lis.length;i++){
            var li = lis[i];
            if (!li.querySelector('.hotdeal_info')) continue;
            var titleLink = li.querySelector('h3.title a[href]') || li.querySelector('h3 a[href]');
            var href = titleLink ? titleLink.getAttribute('href') : '';
            var id = null;
            var im = (href || '').match(/(\d{6,})/);
            if (im) id = im[1];
            var titleEl = li.querySelector('h3.title .ellipsis-target') || titleLink;
            var title = clean(titleEl ? titleEl.textContent : '');
            title = title.replace(/\s*\[\d+\]\s*$/, '').trim();
            var info = li.querySelector('.hotdeal_info');
            var infoText = clean(info ? info.textContent : '');
            var mall = null, price = null, shipping = null;
            var m1 = infoText.match(/쇼핑몰\s*:\s*([^/]+?)\s*(?:\/|${'$'})/);
            if (m1) mall = clean(m1[1]);
            var m2 = infoText.match(/가격\s*:\s*([^/]+?)\s*(?:\/|${'$'})/);
            if (m2) price = clean(m2[1]);
            var m3 = infoText.match(/배송\s*:\s*([^/]+?)\s*${'$'}/);
            if (m3) shipping = clean(m3[1]);
            if (price === '/') price = null;
            var catEl = li.querySelector('span.category a');
            var category = clean(catEl ? catEl.textContent : '');
            var upvotes = null;
            var vEl = li.querySelector('.pc_voted_count .count');
            if (vEl) {
              var vt = clean(vEl.textContent).replace(/[^0-9]/g,'');
              if (vt !== '') upvotes = parseInt(vt,10);
            }
            var comments = null;
            var cEl = li.querySelector('a[href*="#comment"]') || li.querySelector('.comment_count');
            if (cEl) {
              var cm = clean(cEl.textContent).match(/\d+/);
              if (cm) comments = parseInt(cm[0],10);
            }
            if (comments === null) {
              var cm2 = clean(li.innerText).match(/\[(\d+)\]\s*쇼핑몰\s*:/);
              if (cm2) comments = parseInt(cm2[1],10);
            }
            var regEl = li.querySelector('span.regdate');
            var regdate = clean(regEl ? regEl.textContent : '');
            var auEl = li.querySelector('span.author');
            var author = clean(auEl ? auEl.textContent : '').replace(/^\/\s*/, '');
            var ended = false;
            var els = li.querySelectorAll('span, em, strong');
            for (var j=0;j<els.length;j++){
              var e = els[j];
              if (e.children.length === 0 && clean(e.textContent) === '종료') { ended = true; break; }
            }
            out.push({
              id: id, title: title, mall: mall, price: price, shipping: shipping,
              category: category, upvotes: upvotes, comments: comments,
              regdate: regdate, author: author, ended_badge: ended
            });
          }
          return JSON.stringify(out);
        })()
    """.trimIndent()

    /** 글 상세 파서 → JSON 객체 문자열 */
    val DETAIL_JS: String = """
        (function(){
          function clean(s){ return (s||'').replace(/\s+/g,' ').trim(); }
          var out = {
            posted_at: null, shop_url: null, comparison_total: null,
            ended_reports: 0, ended_text: false, comparisons: []
          };
          try{
            var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';
            out.ended_text = /종료된 핫딜입니다/.test(bodyText);

            var head = clean(bodyText.slice(0, 4000));
            var dm = head.match(/(20\d{2})\.(\d{2})\.(\d{2})\s+(\d{2}):(\d{2})/);
            if (dm) out.posted_at = dm[1] + '.' + dm[2] + '.' + dm[3] + ' ' + dm[4] + ':' + dm[5];

            var trs = document.querySelectorAll('tr');
            for (var i=0;i<trs.length;i++){
              var tr = trs[i];
              var th = tr.querySelector('th');
              if (!th) continue;
              var label = clean(th.textContent);
              var rowText = clean(tr.innerText);
              if (label === '링크') {
                var a = tr.querySelector('a[href^="http"]');
                if (a) out.shop_url = a.getAttribute('href');
              } else if (label === '종료 신고') {
                var em = rowText.match(/(\d+)\s*명/);
                out.ended_reports = em ? parseInt(em[1],10) : 1;
              }
            }
            if (!out.shop_url) {
              var arts = document.querySelectorAll('article a[href^="http"]');
              for (var j=0;j<arts.length;j++){
                var href = arts[j].getAttribute('href') || '';
                if (href.indexOf('fmkorea.com') === -1) { out.shop_url = href; break; }
              }
            }

            var uls = document.querySelectorAll('ul');
            var ul = null;
            for (var k=0;k<uls.length;k++){
              if ((uls[k].innerText || '').indexOf('유사한 최근 6개월 핫딜들') !== -1) ul = uls[k];
            }
            if (ul) {
              var kids = ul.children;
              for (var n=0;n<kids.length;n++){
                var li = kids[n];
                if (li.tagName !== 'LI') continue;
                var text = clean(li.innerText);
                if (text.indexOf('유사한 최근 6개월') !== -1) continue;
                var totalM = text.match(/총\s*(\d+)\s*개/);
                if (/^더\s*보기/.test(text)) {
                  if (totalM) out.comparison_total = parseInt(totalM[1],10);
                  continue;
                }
                var link = li.querySelector('a');
                var name = clean(link ? link.innerText : text.split('\n')[0]);
                var pm = text.match(/가격\s*:\s*([0-9,]+\s*원|[$][\d.,]+|다양|가격다양함|[^\s]+)/);
                var priceText = pm ? pm[1] : null;
                var dmm = text.match(/본문 상품이\s*[\d,]+\s*원\s*더\s*(?:저렴|비쌈)/);
                var diffText = dmm ? dmm[0] : null;
                var pd = text.match(/등록일\s*:\s*(\d{4}-\d{2}-\d{2})/);
                var posted = pd ? pd[1] : null;
                var priceKrw = null;
                if (priceText) {
                  var won = priceText.match(/([\d,]+)\s*원/);
                  if (won) priceKrw = parseInt(won[1].replace(/,/g,''),10);
                }
                var isCheaper = null, savings = null;
                if (diffText) {
                  var num = parseInt(diffText.replace(/[^0-9]/g,''),10);
                  if (/더\s*저렴/.test(diffText)) { isCheaper = true; savings = num; }
                  else { isCheaper = false; savings = -num; }
                }
                out.comparisons.push({
                  name: name, price: priceText, price_krw: priceKrw,
                  diff_text: diffText, is_cheaper: isCheaper,
                  savings_krw: savings, posted_at: posted
                });
              }
              if (out.comparison_total === null) {
                var tm = clean(ul.innerText).match(/총\s*(\d+)\s*개/);
                if (tm) out.comparison_total = parseInt(tm[1],10);
              }
            }
          }catch(e){
            out.error = String(e);
          }
          return JSON.stringify(out);
        })()
    """.trimIndent()
}
