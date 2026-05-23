import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import legacy from '@vitejs/plugin-legacy';
import path from 'path';
import {defineConfig, Plugin} from 'vite';
import {transform} from 'lightningcss';
import {readFileSync, writeFileSync, existsSync, copyFileSync} from 'fs';

// ---------------------------------------------------------------------------
// CSS compat pipeline: transforms Tailwind v4 output to Chrome 44 (Android 6)
// ---------------------------------------------------------------------------

/** Strip @layer { } wrappers (Chrome 99+, Chrome 44 ignores them entirely) */
function stripAtLayer(css: string): string {
  let out = '', i = 0;
  while (i < css.length) {
    const m = css.slice(i).match(/@layer\s+[\w-]+\s*\{/);
    if (!m || m.index === undefined) { out += css.slice(i); break; }
    out += css.slice(i, i + m.index);
    let pos = i + m.index + m[0].length, depth = 1, inStr = false, ch = '';
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

/** Remove :where() / :is() from selectors by counting balanced parentheses */
function stripPseudoWhereIs(css: string): string {
  // Handle both :where( and :is(
  for (const prefix of [':where(', ':is(']) {
    let out = '';
    let i = 0;
    while (i < css.length) {
      const idx = css.indexOf(prefix, i);
      if (idx === -1) { out += css.slice(i); break; }
      out += css.slice(i, idx);
      // Find matching closing paren
      let pos = idx + prefix.length;
      let depth = 1;
      while (pos < css.length && depth > 0) {
        if (css[pos] === '(') depth++;
        else if (css[pos] === ')') depth--;
        pos++;
      }
      // Extract inner content (skip the wrapping parens)
      const inner = css.slice(idx + prefix.length, pos - 1);
      out += inner;
      i = pos;
    }
    css = out;
  }
  return css;
}

/** Replace color-mix() with a safe fallback for Chrome 44 */
function stripColorMix(css: string): string {
  // Pattern 1: color-mix(in XXX, currentcolor N%, transparent) → currentcolor
  css = css.replace(/color-mix\(in\s+(?:oklab|lab)\s*,\s*currentcolor\s+\d+%\s*,\s*transparent\s*\)/g, 'currentcolor');
  
  // Pattern 2: color-mix(in XXX, var(--X) N%, transparent) → var(--X)
  css = css.replace(/color-mix\(in\s+(?:oklab|lab)\s*,\s*(var\(--[\w-]+\))\s+\d+%\s*,\s*transparent\s*\)/g, '$1');
  
  // Pattern 3: color-mix(in XXX, X, X) → X (identity)
  css = css.replace(/color-mix\(in\s+(?:oklab|lab)\s*,\s*([^,]+?)\s*,\s*\1\s*\)/g, '$1');
  
  // Pattern 4: any remaining color-mix → extract first color arg as fallback
  css = css.replace(/color-mix\(in\s+(?:oklab|lab)\s*,\s*([^,]+?)\s*,[^)]*\)/g, '$1');
  
  return css;
}

/**
 * Strip @property rules (Chrome 85+). Chrome 44 will ignore @property,
 * but these define animation-friendly interpolations for Tailwind classes.
 * Removing them means CSS variables still work, just without smooth interpolation.
 */
function stripAtProperty(css: string): string {
  return css.replace(/@property\s+--[\w-]+\s*\{[^}]*\}/g, '');
}

function cssCompatPlugin(): Plugin {
  return {
    name: 'css-compat-chrome44',
    apply: 'build',
    enforce: 'post',
    generateBundle(_opts, bundle) {
      for (const [fileName, asset] of Object.entries(bundle)) {
        if (fileName.endsWith('.css') && asset.type === 'asset' && typeof asset.source === 'string') {
          let css = asset.source as string;

          // Step 1: lightningcss FIRST → oklch→rgb + overall downgrade
          const result = transform({
            filename: fileName,
            code: Buffer.from(css),
            targets: { chrome: 44 << 16 },
            minify: true,
          });
          css = new TextDecoder().decode(result.code);

          // Step 2: strip @layer blocks (AFTER lightningcss — it may reorder them)
          css = stripAtLayer(css);
          // Step 3: strip :where() and :is() from selectors
          css = stripPseudoWhereIs(css);
          // Step 4: strip @property rules  
          css = stripAtProperty(css);
          // Step 5: strip color-mix() to safe fallbacks
          css = stripColorMix(css);

          console.log(`[css-compat] ${fileName}: ${asset.source.length} → ${css.length} chars`);
          asset.source = css;
        }
      }
    },
  };
}

// ---------------------------------------------------------------------------
// HTML compat plugin: rewrites the generated index.html to work on Chrome 48
// (Android 6 WebView) by removing type=module/nomodule/crossorigin and using
// a plain sequential script loading approach.
// ---------------------------------------------------------------------------
function htmlCompatPlugin(): Plugin {
  return {
    name: 'html-compat-chrome48',
    apply: 'build',
    enforce: 'post',
    closeBundle() {
      // ── Copy Proxy polyfill for Chrome 48 (Proxy = Chrome 49+) ──
      const distAssets = path.resolve(__dirname, 'dist', 'assets');
      const proxySrc = path.resolve(__dirname, 'node_modules', 'proxy-polyfill', 'proxy.min.js');
      const proxyDst = path.join(distAssets, 'proxy.min.js');
      copyFileSync(proxySrc, proxyDst);
      console.log('[html-compat] Copied proxy-polyfill to dist/assets/proxy.min.js');

      const htmlPath = path.resolve(__dirname, 'dist', 'index.html');
      if (!existsSync(htmlPath)) return;
      
      let html = readFileSync(htmlPath, 'utf8');

      // Extract the CSS link and asset filenames
      const cssMatch = html.match(/<link[^>]+href="(\/assets\/[^"]+\.css)"/);
      const polyfillsSrc = html.match(/id="vite-legacy-polyfill"[^>]+src="([^"]+)"/);
      const legacyEntrySrc = html.match(/id="vite-legacy-entry"[^>]+data-src="([^"]+)"/);

      const cssHref = cssMatch ? cssMatch[1] : '/assets/index.css';
      const polyfillPath = polyfillsSrc ? polyfillsSrc[1] : '';
      const legacyPath = legacyEntrySrc ? legacyEntrySrc[1] : '';

      // Extract the diagnostic script (everything between <body> and the first <script nomodule>)
      const bodyMatch = html.match(/<body>([\s\S]*?)<script nomodule/);
      const diagHtml = bodyMatch ? bodyMatch[1] : '';

      // Build a completely new, simple HTML that works on ALL browsers
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
var errors=[],shown=false,diag=document.getElementById('__diag');
window.onerror=function(m,u,l,c,e){errors.push((e&&e.stack)||(m+' at '+u+':'+l))};
function showDiag(){
if(shown)return;
if(document.getElementById('root').children.length>0)return;
shown=true;
var h='<b>⚠ PAGE LOAD FAILED</b><br><br>';
h+='<span style="color:#aaa">Browser: '+navigator.userAgent+'</span><br><br>';
if(errors.length>0){h+='<b>JS Errors:</b><br>';for(var i=0;i<errors.length;i++)h+='• <span style="color:#ff8888">'+errors[i].replace(/</g,'&lt;')+'</span><br>'}
else{h+='<span style="color:#ffa500">No JS errors detected.</span><br>'+'<span style="color:#aaa">Possible causes: script failed to load, blocked by firewall, or WebView issue.</span>'}
diag.innerHTML=h;diag.style.display='block'}
setTimeout(showDiag,5000);
window.addEventListener('DOMContentLoaded',function(){if(errors.length>0)setTimeout(showDiag,1000)})
})();
</script>
<!-- Step 0: Polyfill ES6 Proxy (Chrome 49+, Chrome 48 needs this) -->
<script src="/assets/proxy.min.js"></script>
<!-- Step 1: Load SystemJS + core-js polyfills -->
<script src="${polyfillPath}"></script>
<!-- Step 2: Once polyfills loaded, boot the app via SystemJS -->
<script>
(function(){
if(typeof System==='undefined'){
  document.getElementById('__diag').innerHTML='<b>⚠ FATAL</b><br><br>SystemJS failed to load from:<br><span style="color:#ff8888">'+'${polyfillPath}'+'</span><br><br>Check that the file exists in the APK assets.';
  document.getElementById('__diag').style.display='block';
  return;
}
System.import('${legacyPath}').catch(function(err){
  document.getElementById('__diag').innerHTML='<b>⚠ APP LOAD FAILED</b><br><br><span style="color:#ff8888">'+String(err).replace(/</g,'&lt;')+'</span>';
  document.getElementById('__diag').style.display='block';
});
})();
</script>
</body>
</html>`;

      writeFileSync(htmlPath, newHtml, 'utf8');
      console.log(`[html-compat] Rewrote index.html: removed module/nomodule, use plain <script> cascade`);
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
