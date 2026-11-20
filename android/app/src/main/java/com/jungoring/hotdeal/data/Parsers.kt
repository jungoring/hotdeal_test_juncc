package com.jungoring.hotdeal.data

/**
 * WebView 안에서 실행하는 파서들.
 * 기존 scraper/crawl.mjs 의 parseListInPage 와 같은 선택자를 쓴다.
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
            title = title.replace(/\s*\[\d+\]\s*${'$'}/, '').trim();
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

    /**
     * 글 상세 페이지에서 '쇼핑몰 바로가기' 주소만 뽑는다 → {"shop_url":..., "inspected":true}
     *
     * 에펨코 핫딜 글은 본문 위 표의 '링크' 행에 제휴 링크가 들어 있다.
     *  - a.hotdeal_url  (예: <a class="hotdeal_url" href="https://link.fmkorea.org/link.php?url=...">
     *                       https://link.coupang.com/a/xxxx</a>)
     *  - 또는  <tr><th>링크</th><td><a href="...">...</a></td></tr>
     * href 는 link.fmkorea.org/link.php?url=... 같은 리다이렉트 래퍼인 경우가 많아
     * url 파라미터를 풀어서 실제 쇼핑몰 주소로 돌려준다. 화면에 보이는 주소(anchor text)가
     * 잘리지 않은 http(s) 주소면 그것을 우선 쓴다.
     */
    val SHOP_JS: String = """
        (function(){
          function clean(s){ return (s||'').replace(/\s+/g,' ').trim(); }

          function unwrap(u){
            var out = u || '';
            for (var i=0;i<4;i++){
              if (!out) break;
              var isWrapper = /link\.fmkorea\.(org|com)/i.test(out) || /\/link\.php/i.test(out);
              if (!isWrapper) break;
              var m = out.match(/[?&]url=([^&]+)/i);
              if (!m) break;
              var dec = m[1];
              try { dec = decodeURIComponent(m[1].replace(/\+/g, ' ')); } catch(e){ dec = m[1]; }
              if (!dec || dec === out) break;
              out = dec;
            }
            return out;
          }

          function external(u){
            if (!u) return null;
            var t = clean(u);
            if (!/^https?:\/\//i.test(t)) return null;
            if (/^https?:\/\/([a-z0-9.-]+\.)?fmkorea\.(com|org)/i.test(t)) return null;
            return t;
          }

          var out = { shop_url: null, inspected: false, source: null };
          try{
            var cand = [];
            var hot = document.querySelectorAll('a.hotdeal_url');
            for (var h=0;h<hot.length;h++) cand.push(hot[h]);

            var rows = document.querySelectorAll('tr');
            var sawTable = false;
            for (var i=0;i<rows.length;i++){
              var th = rows[i].querySelector('th');
              if (!th) continue;
              var label = clean(th.textContent);
              if (label.indexOf('쇼핑몰') === 0 || label.indexOf('가격') === 0 || label.indexOf('상품명') === 0) sawTable = true;
              if (label.indexOf('링크') >= 0) {
                sawTable = true;
                var a1 = rows[i].querySelector('td a[href]') || rows[i].querySelector('a[href]');
                if (a1) cand.push(a1);
              }
            }

            var pick = null;
            for (var j=0;j<cand.length;j++){
              var el = cand[j];
              var href = el.getAttribute('href') || '';
              var text = clean(el.textContent);
              var fromText = external(text);
              var fromHref = external(unwrap(href));
              var textCut = /…|\.\.\./.test(text);
              if (fromText && !textCut) { pick = fromText; break; }
              if (fromHref) { pick = fromHref; break; }
              if (fromText) { pick = fromText; break; }
            }

            if (!pick) {
              var scope = document.querySelector('.xe_content') || document.querySelector('article') || document.body;
              var all = scope.querySelectorAll('a[href]');
              for (var k=0;k<all.length;k++){
                var u = external(unwrap(all[k].getAttribute('href') || '')) || external(clean(all[k].textContent));
                if (u) { pick = u; break; }
              }
            }

            out.shop_url = pick;
            out.inspected = sawTable;
          }catch(e){
            out.error = String(e);
          }
          return JSON.stringify(out);
        })()
    """.trimIndent()
}
