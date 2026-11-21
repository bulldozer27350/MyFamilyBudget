'use strict';
/**
 * VideoRecorder — aide à la captation de tutoriels Playwright.
 */

const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

// ─────────────────────────────────────────────────────────────────────────────
// Script injecté dans la page
// L'overlay (curseur, sous-titres) est attaché à document.documentElement
// pour rester STRICTEMENT fixé au viewport, même si document.body ou #root
// subit un transform/zoom.
// ─────────────────────────────────────────────────────────────────────────────
const OVERLAY_JS = /* js */`
(function () {
  if (document.getElementById('__vc_host')) return;

  const STYLE = \`
    #__vc_cursor {
      position: fixed !important;
      z-index: 2147483647 !important;
      width: 20px; height: 20px;
      border-radius: 50%;
      background: rgba(47, 93, 80, 0.90);
      border: 2px solid rgba(255,255,255,0.95);
      box-shadow: 0 1px 8px rgba(0,0,0,0.55);
      pointer-events: none;
      transform: translate(-50%, -50%);
      left: -300px; top: -300px;
    }
    #__vc_subtitle {
      position: fixed !important;
      z-index: 2147483646 !important;
      bottom: 28px !important;
      left: 50% !important;
      transform: translateX(-50%) !important;
      background: rgba(35, 42, 46, 0.90) !important;
      color: #ffffff !important;
      font-family: 'Source Sans Pro', system-ui, -apple-system, sans-serif !important;
      font-size: 15px !important;
      font-weight: 500 !important;
      line-height: 1.55 !important;
      padding: 10px 26px !important;
      border-radius: 7px !important;
      max-width: 82% !important;
      text-align: center !important;
      opacity: 0;
      transition: opacity 0.22s ease-in-out !important;
      pointer-events: none !important;
      box-shadow: 0 4px 18px rgba(0,0,0,0.45) !important;
    }
    #__vc_subtitle.show {
      opacity: 1 !important;
    }
    .vc-hl {
      outline: 2.5px solid #2F5D50 !important;
      outline-offset: 3px !important;
      box-shadow: 0 0 0 5px rgba(47,93,80,0.25) !important;
      border-radius: 5px !important;
      transition: outline 0.2s ease, box-shadow 0.2s ease !important;
    }
    @keyframes __vc_ripple {
      to { transform: translate(-50%,-50%) scale(3.2); opacity: 0; }
    }
  \`;

  const style = document.createElement('style');
  style.id = '__vc_host';
  style.textContent = STYLE;
  (document.head || document.documentElement).appendChild(style);

  // Attachés à document.documentElement pour rester 100% stables
  const cursor = document.createElement('div');
  cursor.id = '__vc_cursor';
  document.documentElement.appendChild(cursor);
  document.documentElement.style.setProperty('cursor', 'none', 'important');

  document.addEventListener('mousemove', e => {
    cursor.style.left = e.clientX + 'px';
    cursor.style.top  = e.clientY + 'px';
  }, { passive: true });

  document.addEventListener('mousedown', e => {
    const r = document.createElement('div');
    r.style.cssText =
      'position:fixed;z-index:2147483645;pointer-events:none;' +
      'width:34px;height:34px;border-radius:50%;' +
      'background:rgba(47,93,80,0.42);' +
      'left:' + e.clientX + 'px;top:' + e.clientY + 'px;' +
      'transform:translate(-50%,-50%) scale(0.3);' +
      'animation:__vc_ripple 0.45s ease-out forwards;';
    document.documentElement.appendChild(r);
    setTimeout(() => r.remove(), 500);
  }, { passive: true });

  const sub = document.createElement('div');
  sub.id = '__vc_subtitle';
  document.documentElement.appendChild(sub);
})();
`;

// ─────────────────────────────────────────────────────────────────────────────
// Détection automatique du serveur actif
// ─────────────────────────────────────────────────────────────────────────────
async function detectServer() {
  if (process.env.E2E_FRONT_URL) {
    const front = process.env.E2E_FRONT_URL.replace(/\/$/, '');
    const api = process.env.E2E_API_URL || (front.includes('/api/v1') ? front : `${front}/api/v1`);
    return { front, api };
  }

  const candidates = [
    { front: 'http://localhost:8080/api/v1', api: 'http://localhost:8080/api/v1', probe: 'http://localhost:8080/api/v1/overview.html' },
    { front: 'http://localhost:8080',        api: 'http://localhost:8080/api/v1', probe: 'http://localhost:8080/overview.html' },
    { front: 'http://localhost:3000',        api: 'http://localhost:8080/api/v1', probe: 'http://localhost:3000/overview.html' },
  ];

  for (const c of candidates) {
    try {
      const res = await fetch(c.probe, { method: 'HEAD', signal: AbortSignal.timeout(700) });
      if (res.ok || res.status === 200 || res.status === 304) {
        console.log(`[VideoRecorder] Serveur détecté : ${c.front}`);
        return { front: c.front, api: c.api };
      }
    } catch (_) {}
  }

  console.log('[VideoRecorder] Aucun serveur actif détecté, cible par défaut : http://localhost:8080/api/v1');
  return { front: 'http://localhost:8080/api/v1', api: 'http://localhost:8080/api/v1' };
}

// ─────────────────────────────────────────────────────────────────────────────
// Classe VideoRecorder
// ─────────────────────────────────────────────────────────────────────────────
class VideoRecorder {
  constructor(page, baseUrl = '') {
    this.page = page;
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  async reinject() {
    try {
      await this.page.evaluate(OVERLAY_JS);
    } catch (_) {}
  }

  // ── Navigation ────────────────────────────────────────────────────────────
  async navigate(targetUrl, waitForSelector) {
    const fullUrl = targetUrl.startsWith('http')
      ? targetUrl
      : `${this.baseUrl}/${targetUrl.replace(/^\//, '')}`;

    await this.page.goto(fullUrl, { waitUntil: 'domcontentloaded' });
    if (waitForSelector) {
      await this.page.waitForSelector(waitForSelector, { state: 'visible', timeout: 15000 }).catch(() => {});
    } else {
      await this.page.waitForTimeout(1500);
    }
    await this.reinject();
    await this.page.waitForTimeout(300);
  }

  // ── Souris (accepte sélecteur string, Locator Playwright, ou {x, y}) ──────
  async moveTo(target, { steps = 25 } = {}) {
    let loc = null;
    let x, y;

    if (typeof target === 'string') {
      loc = this.page.locator(target).first();
    } else if (target && typeof target.boundingBox === 'function') {
      loc = target;
    } else if (target && typeof target.x === 'number' && typeof target.y === 'number') {
      x = target.x;
      y = target.y;
    }

    if (loc) {
      const visible = await loc.waitFor({ state: 'visible', timeout: 6000 }).then(() => true).catch(() => false);
      if (visible) {
        await loc.scrollIntoViewIfNeeded().catch(() => {});
        const box = await loc.boundingBox().catch(() => null);
        if (box) {
          x = Math.round(box.x + box.width  / 2);
          y = Math.round(box.y + box.height / 2);
        }
      }
    }

    if (typeof x !== 'number' || isNaN(x) || typeof y !== 'number' || isNaN(y)) {
      // Si la cible n'a pas pu être localisée précisément, on ne crashe pas le tournage
      return null;
    }

    await this.page.mouse.move(x, y, { steps });
    return { x, y };
  }

  async clickOn(target, { steps = 25, before = 300, after = 350 } = {}) {
    const pos = await this.moveTo(target, { steps });
    if (pos) {
      await this.page.waitForTimeout(before);
      await this.page.mouse.click(pos.x, pos.y);
      await this.page.waitForTimeout(after);
    }
    return pos;
  }

  async click(target, opts) {
    return this.clickOn(target, opts);
  }

  // ── Sous-titres ───────────────────────────────────────────────────────────
  async showSubtitle(text) {
    await this.reinject(); // s'assure que le DOM du sous-titre est présent
    await this.page.evaluate(t => {
      const el = document.getElementById('__vc_subtitle');
      if (el) {
        el.textContent = t;
        el.classList.add('show');
      }
    }, text);
  }

  async hideSubtitle() {
    await this.page.evaluate(() => {
      const el = document.getElementById('__vc_subtitle');
      if (el) el.classList.remove('show');
    });
    await this.page.waitForTimeout(240);
  }

  async subtitle(text, ms) {
    await this.showSubtitle(text);
    await this.page.waitForTimeout(ms);
    await this.hideSubtitle();
    await this.page.waitForTimeout(300);
  }

  // ── Highlight ─────────────────────────────────────────────────────────────
  async highlight(target) {
    try {
      const loc = typeof target === 'string' ? this.page.locator(target) : target;
      await loc.evaluateAll(els => {
        els.forEach(el => el.classList.add('vc-hl'));
      });
    } catch (_) {}
  }

  async clearHighlights() {
    try {
      await this.page.evaluate(() => {
        document.querySelectorAll('.vc-hl').forEach(el => el.classList.remove('vc-hl'));
      });
    } catch (_) {}
  }

  // ── Zoom CSS (appliqué sur #root pour ne pas impacter les overlays) ────────
  async zoomTo(target, factor = 1.35, duration = 700) {
    try {
      const loc = typeof target === 'string' ? this.page.locator(target).first() : target;
      const visible = await loc.waitFor({ state: 'visible', timeout: 4000 }).then(() => true).catch(() => false);
      if (!visible) return;

      await loc.evaluate((el, [f, d]) => {
        const root = document.getElementById('root') || document.body;
        const b = el.getBoundingClientRect();
        const cx = ((b.left + b.right)  / 2 / window.innerWidth  * 100).toFixed(2);
        const cy = ((b.top  + b.bottom) / 2 / window.innerHeight * 100).toFixed(2);
        root.style.transition    = `transform ${d}ms cubic-bezier(0.4,0,0.2,1)`;
        root.style.transformOrigin = `${cx}% ${cy}%`;
        root.style.transform    = `scale(${f})`;
      }, [factor, duration]);
      await this.page.waitForTimeout(duration + 100);
    } catch (_) {}
  }

  async zoomOut(duration = 600) {
    try {
      await this.page.evaluate(d => {
        const root = document.getElementById('root') || document.body;
        root.style.transition = `transform ${d}ms cubic-bezier(0.4,0,0.2,1)`;
        root.style.transform  = 'scale(1)';
      }, duration);
      await this.page.waitForTimeout(duration + 100);
    } catch (_) {}
  }

  // ── Saisie clavier ────────────────────────────────────────────────────────
  async fillField(target, text, { typeDelay = 85, blur = false } = {}) {
    const pos = await this.clickOn(target);
    if (!pos) {
      console.warn('[VideoRecorder] Cible introuvable pour la saisie :', target);
      return;
    }
    await this.page.waitForTimeout(200);

    // Pour un input de type date, Control+a n'est pas géré par le champ et sélectionne
    // tout le document (écran bleu). On utilise target.fill() qui applique la valeur ISO
    // et déclenche les événements proprement sans sélection parasite.
    const inputType = target && typeof target.getAttribute === 'function'
      ? await target.getAttribute('type').catch(() => null)
      : null;

    if (inputType === 'date') {
      await target.fill(String(text));
      await this.page.waitForTimeout(250);
      if (blur) {
        await target.evaluate(el => el.blur()).catch(() => {});
      }
      await this.page.waitForTimeout(400);
      return;
    }

    await this.page.keyboard.press('Control+a');
    await this.page.waitForTimeout(80);
    for (const char of String(text)) {
      await this.page.keyboard.type(char);
      await this.page.waitForTimeout(typeDelay + Math.random() * 20);
    }
    if (blur) await this.page.keyboard.press('Tab');
    await this.page.waitForTimeout(400);
  }

  async pause(ms) {
    await this.page.waitForTimeout(ms);
  }

  async scrollTo(target) {
    try {
      const loc = typeof target === 'string' ? this.page.locator(target).first() : target;
      await loc.scrollIntoViewIfNeeded();
      await this.page.waitForTimeout(400);
    } catch (_) {}
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fonctions de session
// ─────────────────────────────────────────────────────────────────────────────
const DEFAULT_OUTPUT = path.join(__dirname, '..', 'output');

async function createSession({ outputDir = DEFAULT_OUTPUT, width = 1280, height = 900, baseUrl = '' } = {}) {
  if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });

  const browser = await chromium.launch({
    headless: false,
    args: [
      `--window-size=${width},${height}`,
      '--disable-blink-features=AutomationControlled',
    ],
  });

  const context = await browser.newContext({
    viewport: { width, height },
    recordVideo: { dir: outputDir, size: { width, height } },
    locale: 'fr-FR',
  });

  const page = await context.newPage();
  const recorder = new VideoRecorder(page, baseUrl);
  return { browser, context, page, recorder };
}

async function finalizeVideo(context, page, filename, outputDir = DEFAULT_OUTPUT) {
  let tmpPath = null;
  try { tmpPath = await page.video().path(); } catch (_) {}

  await context.close();

  if (tmpPath) {
    await new Promise(r => setTimeout(r, 1200));
    const dest = path.join(outputDir, filename);
    if (fs.existsSync(tmpPath)) {
      fs.copyFileSync(tmpPath, dest);
      console.log(`\n✅  Vidéo enregistrée : ${dest}\n`);
    } else {
      console.warn(`\n⚠️  Fichier temporaire introuvable : ${tmpPath}\n`);
    }
  }
}

module.exports = { VideoRecorder, createSession, finalizeVideo, detectServer };
