import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import legacy from '@vitejs/plugin-legacy';
import path from 'path';
import {defineConfig, Plugin} from 'vite';
import {transform} from 'lightningcss';
import {readFileSync, writeFileSync, existsSync, copyFileSync} from 'fs';

// ---------------------------------------------------------------------------
// CSS compat pipeline: transforms Tailwind v4 output to Chrome 44 (Android 6)
// while keeping it working on Chrome 130+ (Android 16)
// ---------------------------------------------------------------------------

/**
 * Strip @supports blocks — keep inner rules, discard the wrapper.
 *
 * WHY:
 * - Tailwind v4 gates its entire CSS variable init behind:
 *   @supports (((-webkit-hyphens:none)) and (not (margin-trim:inline)))
 *          or ((-moz-orient:inline) and (not (color:rgb(from red r g b))))
 *   On Chrome 130+ this evaluates to FALSE → ALL styles lost.
 * - Also handles many @supports (color:red){...} opacity-fallback blocks —
 *   stripping them is safe because the inner var() already takes precedence
 *   from the flat CSS cascade (later-in-file wins).
 */
function stripAtSupports(css: string): string {
  let out = '';
  let i = 0;

  while (i < css.length) {
    const idx = css.indexOf('@supports', i);
    if (idx === -1) { out += css.slice(i); break; }

    out += css.slice(i, idx);

    // Find opening brace of the @supports block
    const openBrace = css.indexOf('{', idx);
    if (openBrace === -1) { out += css.slice(idx); break; }

    // Match braces to find closing }
    let depth = 1;
    let pos = openBrace + 1;
    while (pos < css.length && depth > 0) {
      if (css[pos] === '{') depth++;
      else if (css[pos] === '}') depth--;
      pos++;
    }

    // Inner content (excludes the @supports (condition){ and final })
    out += css.slice(openBrace + 1, pos - 1);
    i = pos;
  }

  return out;
}

/**
 * Remove ::backdrop from selector lists.
 *
 * WHY:
 * - Chrome 44 (Android 6) does NOT support ::backdrop (Chrome 47+).
 * - In CSS, an unrecognized pseudo-element in a selector list
 *   causes the ENTIRE rule to be discarded (pre-Chrome 88 behavior).
 * - Tailwind v4 puts ::backdrop in the universal reset selector:
 *   *,:before,:after,::backdrop
 *   → Discarding this rule wipes ALL CSS variables → no styles.
 * - Removing ,::backdrop yields *,:before,:after which is safe.
 */
function stripBackdrop(css: string): string {
  return css
    .replace(/,::backdrop/g, '')
    .replace(/::backdrop,/g, '');
}

/**
 * Strip @layer wrappers (Chrome 99+).
 * Chrome 44 would ignore @layer anyway, but keeping rules inside @layer
 * that get discarded by other issues is worse. Stripping ensures rules
 * participate in the normal cascade (later wins ≈ correct for TW order).
 */
function stripAtLayer(css: string): string {
  let out = '', i = 0;
  while (i < css.length) {
    const m = css.slice(i).match(/@layer\s+[\w-]+\s*\{/);
    if (!m || m.index === undefined) { out += css.slice(i); break; }
    out += css.slice(i, i + m.index);
    let pos = i + m.index + m[0].length, depth = 1;
    let inStr = false, ch = '';
    while (pos < css.length && depth > 0) {
      const c = css[pos];
      if (inStr) { if (c === ch && css[pos-1] !== '\\') inStr = false; }
      else if (c === '"' || c === "'") { inStr = true; ch = c; }
      else if (c === '{') depth++;
      else if (c === '}') { depth--; if (depth === 0) break; }
      pos++;
    }
    out += css.slice(i + m.index + m[0].length, pos);
    i = pos + 1;
  }
  return out.replace(/@layer\s+\w+\s*;/g, '');
}

/**
 * Remove :where() / :is() from selectors by counting balanced parentheses.
 *
 * WHY:
 * - Chrome 44 does NOT support :where() or :is() (Chrome 88+).
 * - An unrecognized pseudo-class with parens causes the selector to be
 *   invalid, discarding the entire rule (pre-Chrome 88).
 * - Stripping them preserves the inner selectors, changing specificity
 *   but maintaining the cascade via source order.
 */
function stripPseudoWhereIs(css: string): string {
  for (const prefix of [':where(', ':is(']) {
    let out = '';
    let i = 0;
    while (i < css.length) {
      const idx = css.indexOf(prefix, i);
      if (idx === -1) { out += css.slice(i); break; }
      out += css.slice(i, idx);
      let pos = idx + prefix.length;
      let depth = 1;
      while (pos < css.length && depth > 0) {
        if (css[pos] === '(') depth++;
        else if (css[pos] === ')') depth--;
        pos++;
      }
      const inner = css.slice(idx + prefix.length, pos - 1);
      out += inner;
      i = pos;
    }
    css = out;
  }
  return css;
}

/**
 * Replace color-mix() with safe fallbacks for Chrome 44.
 */
function stripColorMix(css: string): string {
  css = css.replace(
    /color-mix\(in\s+(?:oklab|lab)\s*,\s*currentcolor\s+\d+%\s*,\s*transparent\s*\)/g,
    'currentcolor'
  );
  css = css.replace(
    /color-mix\(in\s+(?:oklab|lab)\s*,\s*(var\(--[\w-]+\))\s+\d+%\s*,\s*transparent\s*\)/g,
    '$1'
  );
  css = css.replace(
    /color-mix\(in\s+(?:oklab|lab)\s*,\s*([^,]+?)\s*,\s*\1\s*\)/g,
    '$1'
  );
  css = css.replace(
    /color-mix\(in\s+(?:oklab|lab)\s*,\s*([^,]+?)\s*,[^)]*\)/g,
    '$1'
  );
  return css;
}

/**
 * Strip @property rules (Chrome 85+).
 * Removing them means CSS variables still work, just without interpolation.
 */
function stripAtProperty(css: string): string {
  return css.replace(/@property\s+--[\w-]+\s*\{[^}]*\}/g, '');
}

function cssCompatPlugin(): Plugin {
  return {
    name: 'css-compat-all',
    apply: 'build',
    enforce: 'post',
    generateBundle(_opts, bundle) {
      for (const [fileName, asset] of Object.entries(bundle)) {
        if (fileName.endsWith('.css') && asset.type === 'asset' && typeof asset.source === 'string') {
          let css = asset.source as string;

          // Step 1: lightningcss → oklch→rgb + modern→Chrome44 downgrade
          const result = transform({
            filename: fileName,
            code: Buffer.from(css),
            targets: { chrome: 44 << 16 },
            minify: true,
          });
          css = new TextDecoder().decode(result.code);

          // Step 2: Strip @supports wrappers (fixes Android 16 blank page)
          css = stripAtSupports(css);

          // Step 3: Remove ::backdrop from selectors (fixes Android 6 blank page)
          css = stripBackdrop(css);

          // Step 4: Strip @layer blocks
          css = stripAtLayer(css);

          // Step 5: Strip :where() and :is() from selectors
          css = stripPseudoWhereIs(css);

          // Step 6: Strip @property rules
          css = stripAtProperty(css);

          // Step 7: Replace color-mix() with fallbacks
          css = stripColorMix(css);

          console.log(`[css-compat] ${fileName}: ${asset.source.length} → ${css.length} chars`);
          asset.source = css;
        }
      }
    },
  };
}

// ---------------------------------------------------------------------------
// HTML compat plugin: dual-path loading for old & new browsers
//
// - Modern browsers (Chrome 61+): native ESM via <script type="module">
// - Legacy browsers (Chrome 44–60): SystemJS polyfill + core-js
//
// Detection: 'noModule' in HTMLScriptElement → Chrome 61+
// ---------------------------------------------------------------------------
function htmlCompatPlugin(): Plugin {
  return {
    name: 'html-compat-dual',
    apply: 'build',
    enforce: 'post',
    closeBundle() {
      const distAssets = path.resolve(__dirname, 'dist', 'assets');

      // Copy Proxy polyfill (Chrome 49+, need for Chrome 44–48)
      const proxySrc = path.resolve(__dirname, 'node_modules', 'proxy-polyfill', 'proxy.min.js');
      const proxyDst = path.join(distAssets, 'proxy.min.js');
      copyFileSync(proxySrc, proxyDst);
      console.log('[html-compat] Copied proxy-polyfill');

      const htmlPath = path.resolve(__dirname, 'dist', 'index.html');
      if (!existsSync(htmlPath)) return;

      let html = readFileSync(htmlPath, 'utf8');

      // Extract paths from plugin-legacy output
      const cssHref = (html.match(/<link[^>]+href="(\/assets\/[^"]+\.css)"/) || [])[1] || '';
      // Main entry: first type=module script with src=index- (NOT polyfills=)
      const modernEntry = (html.match(/<script type="module"[^>]*src="(\/assets\/index-[^"]+\.js)"/) || [])[1] || '';
      const polyfillPath = (html.match(/id="vite-legacy-polyfill"[^>]+src="([^"]+)"/) || [])[1] || '';
      const legacyPath = (html.match(/id="vite-legacy-entry"[^>]+data-src="([^"]+)"/) || [])[1] || '';

      const newHtml = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<title>万能电视频道直播</title>
<link rel="stylesheet" href="${cssHref}">
</head>
<body>
<div id="root"></div>
<div id="__diag" style="position:fixed;top:0;left:0;right:0;padding:24px 32px;background:#1a0000;color:#ff4444;font-size:14px;z-index:999999;font-family:monospace;display:none;"></div>
<script>
(function(){
var errors=[],shown=false,diag=document.getElementById('__diag'),root=document.getElementById('root');
window.onerror=function(m,u,l,c,e){errors.push((e&&e.stack)||(m))};
function fail(msg){
  if(shown)return;
  shown=true;
  diag.innerHTML='<b>⚠ '+msg+'</b><br><br><span style="color:#aaa">UA: '+navigator.userAgent+'</span><br>'+errors.map(function(e){return'• <span style="color:#ff8888">'+String(e).replace(/</g,'&lt;')+'</span>'}).join('<br>');
  diag.style.display='block';
}
function loadScript(src,cb){
  var s=document.createElement('script');
  s.src=src;
  s.onload=cb;
  s.onerror=function(){fail('Failed to load: '+src)};
  document.head.appendChild(s);
}
// Detect ESM support: 'noModule' prop exists on Chrome 61+
var isModern='noModule' in document.createElement('script');
if(isModern){
  // ── Modern path: native ES modules ──
  var s=document.createElement('script');
  s.type='module';
  s.src='${modernEntry}';
  s.onerror=function(){fail('ESM entry failed to load')};
  document.head.appendChild(s);
}else{
  // ── Legacy path: polyfills → SystemJS → app ──
  loadScript('/assets/proxy.min.js',function(){
    loadScript('${polyfillPath}',function(){
      if(typeof System==='undefined'){fail('SystemJS missing');return}
      System.import('${legacyPath}').catch(function(e){fail(String(e))});
    });
  });
}
// Timeout: if nothing rendered after 8s, show diag
setTimeout(function(){
  if(root.children.length===0&&!shown)fail('Page load timeout — check network or WebView')
},8000);
})();
</script>
</body>
</html>`;

      writeFileSync(htmlPath, newHtml, 'utf8');
      console.log('[html-compat] Rewrote index.html: dual-path (ESM + SystemJS)');
    },
  };
}

// ---------------------------------------------------------------------------
// Vite config
// ---------------------------------------------------------------------------
export default defineConfig(() => {
  return {
    plugins: [
      react(),
      tailwindcss(),
      legacy({
        targets: ['chrome 44'],
        modernPolyfills: [
          'es.promise', 'es.promise.finally',
          'es.symbol', 'es.symbol.description', 'es.symbol.iterator',
          'es.array.from', 'es.array.iterator', 'es.array.includes',
          'es.array.find', 'es.array.find-index', 'es.array.fill',
          'es.object.assign', 'es.object.entries', 'es.object.values', 'es.object.from-entries',
          'es.object.keys', 'es.object.get-own-property-descriptor',
          'es.string.includes', 'es.string.starts-with', 'es.string.ends-with',
          'es.string.repeat', 'es.string.trim',
          'es.map', 'es.set', 'es.weak-map', 'es.weak-set',
          'es.number.is-finite', 'es.number.is-integer', 'es.number.is-nan',
          'web.dom-collections.iterator', 'web.dom-collections.for-each',
          'web.url', 'web.url-search-params',
          'web.structured-clone',
        ],
        renderLegacyChunks: true,
        polyfills: true,
      }),
      cssCompatPlugin(),
      htmlCompatPlugin(),
    ],
    resolve: {
      alias: { '@': path.resolve(__dirname, '.') },
    },
    build: {
      modulePreload: false,
    },
    server: {
      hmr: process.env.DISABLE_HMR !== 'true',
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});
