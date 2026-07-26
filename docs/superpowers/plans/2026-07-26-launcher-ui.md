# Launcher UI and Login Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the unstyled test page into the Deepslate launcher shell, and make Microsoft sign-in reachable from it.

**Architecture:** The renderer is split into four pure-DOM component modules that take data and a container and return nothing — no Electron imports — so they stay testable under jsdom. `renderer.js` shrinks to wiring only. The window becomes frameless with a hand-drawn title bar, and three new IPC channels (window controls, login cancel) join the existing ones.

**Tech Stack:** Electron 31, plain JavaScript (no framework, no build step), Jest + jsdom.

## Global Constraints

- **Dark theme only.** A game launcher committing to dark is a deliberate choice; no light theme is built.
- **Color tokens** (exact values from the spec, defined once as CSS custom properties):
  - ground `#0f1216`, panel `#151a20`, border `#232932`
  - text `#e4e8ee`, muted `#8a94a3`
  - accent `#2fa968`, warning `#e0a23c`
- Accent is used on the Play button and nowhere else.
- Corner radius 10–14px.
- **System font stacks only.** No webfont URLs — an offline silent fallback is worse than choosing system fonts outright. Digits use a monospace stack with `font-variant-numeric: tabular-nums`.
- **The UI never says "프로필".** It reads as "account". The user-facing word is **"버전"**. The internal `Profile` record keeps its name.
- **Account and version never share a screen region.** Account lives only in the title bar; version only in the hero dropdown.
- **The access token must never reach the renderer.** `sanitizeForRenderer` in `ui/src/rendererEvents.js` is a type allowlist that already excludes `auth_result`; do not add it, and do not bypass the sanitizer.
- Component modules must not `require('electron')`. They receive data and containers only.
- Only features with a real data source appear. No friends, news, or store placeholders.

## Existing code this plan builds on

Already present and working — do not rewrite:

```js
// ui/src/backendProcess.js
startBackend(jarPath, subcommand, args, onEvent, spawnFn?, javaCommand?, session?)
  -> ChildProcess     // emits {type:'backend_exit',code} and {type:'error',stage:'backend',message}

// ui/src/rendererEvents.js
sanitizeForRenderer(event) -> object|null   // type allowlist + credential-field stripping
RENDERER_VISIBLE_TYPES: Set<string>

// ui/src/authStore.js
new AuthStore(authJsonPath, safeStorage)
  .save({username, uuid, accessToken}) / .load() -> {username,uuid,accessToken}|null / .clear()
```

`ui/preload.js` currently exposes `onBackendEvent`, `launchProfile`, `startLogin`, `openLog`.
`ui/main.js` currently handles `launch-profile`, `start-login`, `open-log`.

Backend progress stages, emitted in this order: `manifest`, `libraries`, `client_jar`, `assets`, `loader`, `runtime`, `launching`. Then `launched`, then `exited` with a code.

---

## File Structure

```
ui/
├─ main.js                        MODIFY  frame:false, window-control + cancel-login IPC
├─ preload.js                     MODIFY  expose window controls and cancelLogin
└─ renderer/
   ├─ index.html                  MODIFY  shell skeleton
   ├─ styles.css                  CREATE  tokens + components
   ├─ renderer.js                 MODIFY  wiring only
   └─ components/
      ├─ hero.js                  CREATE  idle / preparing / running
      ├─ versionMenu.js           CREATE  version dropdown
      ├─ statusBar.js             CREATE  runtime, last run, log
      └─ loginModal.js            CREATE  code, waiting, failure
ui/test/
   ├─ hero.test.js                CREATE
   ├─ versionMenu.test.js         CREATE
   ├─ statusBar.test.js           CREATE
   └─ loginModal.test.js          CREATE
```

Each component exports pure render functions. Tasks 2–5 build them independently; Task 6 wires them.

---

## Task 1: Stylesheet and shell skeleton

**Files:**
- Create: `ui/renderer/styles.css`
- Modify: `ui/renderer/index.html`

**Interfaces:**
- Produces: CSS custom properties on `:root` — `--ground`, `--panel`, `--border`, `--text`, `--muted`, `--accent`, `--warning`, `--radius`, `--font`, `--mono`. Every later task styles through these, never with literal hex.
- Produces: DOM ids later tasks attach to — `#titlebar`, `#account-slot`, `#rail`, `#hero`, `#statusbar`, `#modal-root`.

- [ ] **Step 1: Write the stylesheet**

`ui/renderer/styles.css`:

```css
/*
 * Deepslate. Dark only — a game launcher committing to one world is a choice,
 * not an omission. The accent appears on the Play button and nowhere else.
 */
:root {
  --ground: #0f1216;
  --panel: #151a20;
  --border: #232932;
  --text: #e4e8ee;
  --muted: #8a94a3;
  --accent: #2fa968;
  --warning: #e0a23c;
  --radius: 12px;
  --font: ui-sans-serif, system-ui, "Segoe UI", "Malgun Gothic", sans-serif;
  --mono: ui-monospace, "Cascadia Mono", "SF Mono", Menlo, monospace;
}

* { box-sizing: border-box; }

html, body {
  margin: 0;
  height: 100%;
  background: var(--ground);
  color: var(--text);
  font-family: var(--font);
  font-size: 14px;
  line-height: 1.5;
  overflow: hidden;
  user-select: none;
}

.mono { font-family: var(--mono); font-variant-numeric: tabular-nums; }

/* --- title bar --- */
#titlebar {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 8px 0 14px;
  background: #0b0e11;
  border-bottom: 1px solid var(--border);
  -webkit-app-region: drag;
}
#titlebar .brand { font-weight: 650; letter-spacing: -0.01em; }
#titlebar .spacer { flex: 1; }
#titlebar button { -webkit-app-region: no-drag; }

.winbtn {
  width: 40px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  cursor: pointer;
}
.winbtn:hover { background: #1a2029; color: var(--text); }
.winbtn.close:hover { background: #b3323a; color: #fff; }
.winbtn:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }

.account {
  -webkit-app-region: no-drag;
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--text);
  font: inherit;
  padding: 5px 14px;
  cursor: pointer;
}
.account:hover { border-color: var(--muted); }
.account:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.account[data-signed-in="false"] { color: var(--warning); }

/* --- layout --- */
#shell { display: flex; height: calc(100% - 40px); }

#rail {
  width: 60px;
  flex: none;
  background: #0b0e11;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0;
  gap: 6px;
}
#rail button {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text);
  opacity: 0.5;
  font-size: 17px;
  cursor: pointer;
}
#rail button:hover { opacity: 0.8; }
#rail button[aria-current="page"] { opacity: 1; background: #1a2029; color: var(--accent); }
#rail button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
#rail .grow { flex: 1; }

#main {
  flex: 1;
  min-width: 0;
  padding: 22px 26px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* --- hero --- */
#hero {
  flex: 1;
  border: 1px solid var(--border);
  border-radius: 14px;
  background:
    radial-gradient(120% 90% at 78% 8%, rgba(47, 169, 104, 0.13), transparent 60%),
    linear-gradient(var(--panel), #11151a);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 24px;
  text-align: center;
}
.greeting { font-size: 21px; letter-spacing: -0.02em; }
.greeting b { font-weight: 650; }

.play {
  background: var(--accent);
  color: #06130c;
  border: 0;
  border-radius: var(--radius);
  padding: 15px 46px;
  font: inherit;
  font-size: 19px;
  font-weight: 750;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(47, 169, 104, 0.28);
}
.play:hover { filter: brightness(1.07); }
.play:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }

.stop {
  background: transparent;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--text);
  font: inherit;
  padding: 11px 26px;
  cursor: pointer;
}
.stop:hover { border-color: var(--warning); color: var(--warning); }

.hero-error { color: var(--warning); max-width: 52ch; }

progress {
  width: min(420px, 80%);
  height: 8px;
  appearance: none;
  border: 0;
}
progress::-webkit-progress-bar { background: #0d1116; border-radius: 999px; }
progress::-webkit-progress-value { background: var(--accent); border-radius: 999px; }

/* --- version menu --- */
.version-menu { position: relative; }
.version-trigger {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--border);
  border-radius: 10px;
  color: var(--text);
  font: inherit;
  padding: 9px 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
}
.version-trigger:hover { border-color: var(--muted); }
.version-trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.version-list {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: calc(100% + 6px);
  min-width: 240px;
  background: #10151b;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 5px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  z-index: 10;
}
.version-list button {
  background: transparent;
  border: 0;
  border-radius: 7px;
  color: var(--text);
  font: inherit;
  text-align: left;
  padding: 9px 12px;
  cursor: pointer;
}
.version-list button:hover:not(:disabled) { background: #1a2029; }
.version-list button[aria-current="true"] { color: var(--accent); }
.version-list button:disabled { color: var(--muted); cursor: default; }

/* --- status bar --- */
#statusbar { display: flex; gap: 10px; flex: none; }
.chip {
  flex: 1;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 14px;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
}
.chip .k {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.chip .v { font-size: 13px; }
.chip .v.warn { color: var(--warning); }
.chip button {
  background: transparent;
  border: 0;
  color: var(--accent);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}
.chip button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

/* --- modal --- */
#modal-root:empty { display: none; }
.backdrop {
  position: fixed;
  inset: 0;
  background: rgba(4, 6, 8, 0.72);
  display: grid;
  place-items: center;
  z-index: 100;
}
.modal {
  width: min(460px, 90vw);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 26px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  text-align: center;
}
.modal h2 { margin: 0; font-size: 18px; font-weight: 650; }
.modal p { margin: 0; color: var(--muted); }
.modal .code {
  font-family: var(--mono);
  font-size: 32px;
  letter-spacing: 0.16em;
  padding: 14px;
  background: #0d1116;
  border: 1px solid var(--border);
  border-radius: 10px;
  user-select: text;
}
.modal .row { display: flex; gap: 8px; justify-content: center; }
.modal button {
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 9px;
  color: var(--text);
  font: inherit;
  padding: 10px 18px;
  cursor: pointer;
}
.modal button:hover { border-color: var(--muted); }
.modal button.primary { background: var(--accent); border-color: var(--accent); color: #06130c; font-weight: 650; }
.modal button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.modal .failure { color: var(--warning); }

@media (prefers-reduced-motion: reduce) {
  * { transition: none !important; animation: none !important; }
}
```

- [ ] **Step 2: Write the shell skeleton**

`ui/renderer/index.html`:

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <title>CubeClient</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <div id="titlebar">
    <span class="brand">CubeClient</span>
    <span class="spacer"></span>
    <div id="account-slot"></div>
    <button class="winbtn" id="win-min" title="최소화">&#8211;</button>
    <button class="winbtn" id="win-max" title="최대화">&#9633;</button>
    <button class="winbtn close" id="win-close" title="닫기">&#10005;</button>
  </div>

  <div id="shell">
    <nav id="rail" aria-label="주 메뉴">
      <button aria-current="page" title="홈">&#9636;</button>
      <button title="버전">&#9670;</button>
      <button title="로그">&#9638;</button>
      <span class="grow"></span>
      <button title="설정">&#9881;</button>
    </nav>
    <main id="main">
      <section id="hero"></section>
      <div id="statusbar"></div>
    </main>
  </div>

  <div id="modal-root"></div>

  <script src="components/hero.js"></script>
  <script src="components/versionMenu.js"></script>
  <script src="components/statusBar.js"></script>
  <script src="components/loginModal.js"></script>
  <script src="renderer.js"></script>
</body>
</html>
```

- [ ] **Step 3: Verify the app still starts**

Run from `ui/`, with a JDK 17 on hand:

```bash
CUBECLIENT_JAVA="C:/Users/Skdji/devtools/jdk17/jdk-17.0.19+10/bin/java.exe" npx electron .
```

Expected: a window opens with the dark title bar and icon rail visible. The hero and status bar are empty — components land in later tasks. The four `components/*.js` script tags 404 in the console for now; that is expected until Task 5 and does not block the page.

Close the window manually.

- [ ] **Step 4: Commit**

```bash
git add ui/renderer/styles.css ui/renderer/index.html
git commit -m "Add Deepslate stylesheet and shell skeleton"
```

---

## Task 2: Hero component

**Files:**
- Create: `ui/renderer/components/hero.js`
- Test: `ui/test/hero.test.js`

**Interfaces:**
- Produces: `renderHero(state, container)` where `state` is one of:
  - `{ mode: 'idle', username: string|null }`
  - `{ mode: 'preparing', stage: string, percent: number, detail: string|null }`
  - `{ mode: 'running' }`
  - `{ mode: 'error', message: string }`
  The function replaces the container's children. It renders no version dropdown — Task 3 mounts that separately into `#hero`.
- Produces: buttons carrying `data-action="play"`, `data-action="stop"`, `data-action="retry"`. The wiring task listens for clicks on the container and reads `data-action`; components never bind Electron calls themselves.

- [ ] **Step 1: Write the failing test**

`ui/test/hero.test.js`:

```js
/**
 * @jest-environment jsdom
 */
const { renderHero } = require('../renderer/components/hero');

let container;
beforeEach(() => {
  document.body.innerHTML = '<section id="hero"></section>';
  container = document.getElementById('hero');
});

test('idle shows a greeting with the signed-in name and a Play button', () => {
  renderHero({ mode: 'idle', username: 'Mal_itIIyr' }, container);

  expect(container.textContent).toContain('Mal_itIIyr');
  const play = container.querySelector('[data-action="play"]');
  expect(play).not.toBeNull();
  expect(play.textContent).toBe('PLAY');
});

test('idle without an account still offers Play', () => {
  renderHero({ mode: 'idle', username: null }, container);

  // Signing in is optional: an offline session can still start singleplayer.
  expect(container.querySelector('[data-action="play"]')).not.toBeNull();
  expect(container.textContent).not.toContain('null');
});

test('preparing shows the stage, a progress bar, and no Play button', () => {
  renderHero({ mode: 'preparing', stage: 'assets', percent: 72, detail: '1204 / 4040' }, container);

  expect(container.textContent).toContain('assets');
  expect(container.textContent).toContain('1204 / 4040');
  expect(container.querySelector('progress').value).toBe(72);
  // Letting the user press Play mid-download would spawn a second backend.
  expect(container.querySelector('[data-action="play"]')).toBeNull();
});

test('preparing without a detail line omits it rather than printing null', () => {
  renderHero({ mode: 'preparing', stage: 'manifest', percent: 0, detail: null }, container);

  expect(container.textContent).not.toContain('null');
  expect(container.querySelector('progress').value).toBe(0);
});

test('running offers a stop button instead of play', () => {
  renderHero({ mode: 'running' }, container);

  expect(container.querySelector('[data-action="play"]')).toBeNull();
  expect(container.querySelector('[data-action="stop"]')).not.toBeNull();
});

test('error shows the backend message verbatim and offers retry', () => {
  const message = 'Sign-in was refused at the XSTS step (HTTP 401).';
  renderHero({ mode: 'error', message }, container);

  // The backend already writes human-readable messages; the UI must not reword them.
  expect(container.textContent).toContain(message);
  expect(container.querySelector('[data-action="retry"]')).not.toBeNull();
});

test('each render replaces the previous state rather than stacking', () => {
  renderHero({ mode: 'preparing', stage: 'assets', percent: 10, detail: null }, container);
  renderHero({ mode: 'idle', username: 'Steve' }, container);

  expect(container.querySelectorAll('progress').length).toBe(0);
  expect(container.querySelectorAll('[data-action="play"]').length).toBe(1);
});

// Profile ids and backend messages are untrusted text; building this markup with
// innerHTML would let a crafted value inject nodes.
test('does not interpret state text as HTML', () => {
  renderHero({ mode: 'error', message: '<img src=x onerror=1>' }, container);

  expect(container.querySelector('img')).toBeNull();
  expect(container.textContent).toContain('<img src=x onerror=1>');
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `cd ui && npx jest test/hero.test.js`
Expected: FAIL — `Cannot find module '../renderer/components/hero'`.

- [ ] **Step 3: Implement the component**

`ui/renderer/components/hero.js`:

```js
/**
 * The hero is one region that swaps between four states. Keeping them in a single
 * component means the launcher can never show a Play button and a progress bar at
 * the same time — pressing Play mid-download would spawn a second backend process.
 *
 * Pure DOM: takes state and a container, touches nothing else. No Electron import,
 * so it runs under jsdom.
 */
function renderHero(state, container) {
  container.replaceChildren();

  if (state.mode === 'idle') {
    const greeting = document.createElement('div');
    greeting.className = 'greeting';
    if (state.username) {
      greeting.append('다시 오셨네요, ');
      const name = document.createElement('b');
      name.textContent = state.username;
      greeting.append(name);
    } else {
      greeting.textContent = '플레이할 준비가 됐습니다';
    }
    container.append(greeting, button('PLAY', 'play', 'play'));
    return;
  }

  if (state.mode === 'preparing') {
    const stage = document.createElement('div');
    stage.className = 'greeting';
    stage.textContent = state.stage;
    container.append(stage);

    const bar = document.createElement('progress');
    bar.max = 100;
    bar.value = state.percent;
    container.append(bar);

    if (state.detail) {
      const detail = document.createElement('div');
      detail.className = 'mono';
      detail.style.color = 'var(--muted)';
      detail.textContent = state.detail;
      container.append(detail);
    }
    return;
  }

  if (state.mode === 'running') {
    const label = document.createElement('div');
    label.className = 'greeting';
    label.textContent = '실행 중…';
    container.append(label, button('종료', 'stop', 'stop'));
    return;
  }

  const failure = document.createElement('div');
  failure.className = 'hero-error';
  failure.textContent = state.message;
  container.append(failure, button('다시 시도', 'retry', 'stop'));
}

function button(label, action, className) {
  const element = document.createElement('button');
  element.className = className;
  element.dataset.action = action;
  element.textContent = label;
  return element;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderHero };
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `cd ui && npx jest test/hero.test.js`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/renderer/components/hero.js ui/test/hero.test.js
git commit -m "Add hero component with idle, preparing, running and error states"
```

---

## Task 3: Version menu component

**Files:**
- Create: `ui/renderer/components/versionMenu.js`
- Test: `ui/test/versionMenu.test.js`

**Interfaces:**
- Consumes: profile objects as the backend emits them — `{ id, mcVersion, loader, mods }`.
- Produces: `renderVersionMenu({ profiles, selectedId, open }, container)`. Replaces container children. Trigger button carries `data-action="toggle-versions"`; each option carries `data-action="select-version"` and `data-profile-id="<id>"`. The disabled "add" row carries no action.
- Produces: `formatVersionLabel(profile) -> string`, e.g. `"1.21.4 · Fabric"`.

- [ ] **Step 1: Write the failing test**

`ui/test/versionMenu.test.js`:

```js
/**
 * @jest-environment jsdom
 */
const { renderVersionMenu, formatVersionLabel } = require('../renderer/components/versionMenu');

const PROFILES = [
  { id: 'fabric-1.21', mcVersion: '1.21.4', loader: 'fabric', mods: [] },
  { id: 'manual-test-1.21', mcVersion: '1.21.4', loader: 'vanilla', mods: [] },
];

let container;
beforeEach(() => {
  document.body.innerHTML = '<div id="slot"></div>';
  container = document.getElementById('slot');
});

test('label reads as version and loader, never as a profile id', () => {
  // The word "프로필" reads as "account" to users; the UI speaks in versions.
  expect(formatVersionLabel(PROFILES[0])).toBe('1.21.4 · Fabric');
  expect(formatVersionLabel(PROFILES[1])).toBe('1.21.4 · 바닐라');
});

test('closed menu shows only the selected version on the trigger', () => {
  renderVersionMenu({ profiles: PROFILES, selectedId: 'fabric-1.21', open: false }, container);

  const trigger = container.querySelector('[data-action="toggle-versions"]');
  expect(trigger.textContent).toContain('1.21.4 · Fabric');
  expect(container.querySelectorAll('[data-action="select-version"]').length).toBe(0);
});

test('open menu lists every version with its id attached', () => {
  renderVersionMenu({ profiles: PROFILES, selectedId: 'fabric-1.21', open: true }, container);

  const options = [...container.querySelectorAll('[data-action="select-version"]')];
  expect(options.map((o) => o.dataset.profileId)).toEqual(['fabric-1.21', 'manual-test-1.21']);
});

test('open menu marks the selected version', () => {
  renderVersionMenu({ profiles: PROFILES, selectedId: 'manual-test-1.21', open: true }, container);

  const current = container.querySelector('[aria-current="true"]');
  expect(current.dataset.profileId).toBe('manual-test-1.21');
});

// Adding a version is out of scope; showing an enabled control that does nothing
// is worse than showing a disabled one.
test('the add row is present but disabled', () => {
  renderVersionMenu({ profiles: PROFILES, selectedId: 'fabric-1.21', open: true }, container);

  const add = [...container.querySelectorAll('button')].find((b) => b.textContent.includes('추가'));
  expect(add.disabled).toBe(true);
  expect(add.dataset.action).toBeUndefined();
});

test('an unknown selection falls back to a neutral trigger label', () => {
  renderVersionMenu({ profiles: PROFILES, selectedId: 'gone', open: false }, container);

  const trigger = container.querySelector('[data-action="toggle-versions"]');
  expect(trigger.textContent).toContain('버전 선택');
  expect(trigger.textContent).not.toContain('undefined');
});

test('no versions configured still renders a usable trigger', () => {
  renderVersionMenu({ profiles: [], selectedId: null, open: true }, container);

  expect(container.querySelector('[data-action="toggle-versions"]')).not.toBeNull();
  expect(container.querySelectorAll('[data-action="select-version"]').length).toBe(0);
});

test('does not interpret profile fields as HTML', () => {
  renderVersionMenu({
    profiles: [{ id: 'x', mcVersion: '<img src=x onerror=1>', loader: 'fabric', mods: [] }],
    selectedId: 'x',
    open: true,
  }, container);

  expect(container.querySelector('img')).toBeNull();
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `cd ui && npx jest test/versionMenu.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the component**

`ui/renderer/components/versionMenu.js`:

```js
/**
 * The version switcher. Deliberately not called a "profile" switcher anywhere the
 * user can see: that word reads as "account", and account switching lives in the
 * title bar instead. The two concepts never share a screen region.
 */
const LOADER_LABELS = { vanilla: '바닐라', fabric: 'Fabric' };

function formatVersionLabel(profile) {
  const loader = LOADER_LABELS[profile.loader] || profile.loader;
  return `${profile.mcVersion} · ${loader}`;
}

function renderVersionMenu({ profiles, selectedId, open }, container) {
  container.replaceChildren();

  const wrapper = document.createElement('div');
  wrapper.className = 'version-menu';

  const selected = profiles.find((p) => p.id === selectedId);
  const trigger = document.createElement('button');
  trigger.className = 'version-trigger';
  trigger.dataset.action = 'toggle-versions';
  trigger.setAttribute('aria-expanded', String(open));
  trigger.append(selected ? formatVersionLabel(selected) : '버전 선택', ' ▾');
  wrapper.append(trigger);

  if (open) {
    const list = document.createElement('div');
    list.className = 'version-list';

    for (const profile of profiles) {
      const option = document.createElement('button');
      option.dataset.action = 'select-version';
      option.dataset.profileId = profile.id;
      option.textContent = formatVersionLabel(profile);
      if (profile.id === selectedId) {
        option.setAttribute('aria-current', 'true');
      }
      list.append(option);
    }

    // Kept visible so the absence of the feature is obvious, disabled so it cannot
    // be pressed for nothing.
    const add = document.createElement('button');
    add.disabled = true;
    add.textContent = '+ 버전 추가 (준비 중)';
    list.append(add);

    wrapper.append(list);
  }

  container.append(wrapper);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderVersionMenu, formatVersionLabel };
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `cd ui && npx jest test/versionMenu.test.js`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/renderer/components/versionMenu.js ui/test/versionMenu.test.js
git commit -m "Add version switcher component"
```

---

## Task 4: Status bar and account chip

**Files:**
- Create: `ui/renderer/components/statusBar.js`
- Test: `ui/test/statusBar.test.js`

**Interfaces:**
- Produces: `renderStatusBar({ javaVersion, lastRun, offline }, container)` where `lastRun` is `null`, `{ code: 0 }`, or `{ code: n }`. Log button carries `data-action="open-log"`.
- Produces: `renderAccount({ username }, container)` — a single button carrying `data-action="account"` and `data-signed-in="true"|"false"`.

- [ ] **Step 1: Write the failing test**

`ui/test/statusBar.test.js`:

```js
/**
 * @jest-environment jsdom
 */
const { renderStatusBar, renderAccount } = require('../renderer/components/statusBar');

let container;
beforeEach(() => {
  document.body.innerHTML = '<div id="slot"></div>';
  container = document.getElementById('slot');
});

test('shows the runtime and a log button', () => {
  renderStatusBar({ javaVersion: 21, lastRun: null, offline: false }, container);

  expect(container.textContent).toContain('Java 21');
  expect(container.querySelector('[data-action="open-log"]')).not.toBeNull();
});

test('an unknown runtime reads as a dash rather than null', () => {
  renderStatusBar({ javaVersion: null, lastRun: null, offline: false }, container);

  expect(container.textContent).not.toContain('null');
  expect(container.textContent).toContain('—');
});

test('a clean exit reads as normal, a non-zero exit is marked as a warning', () => {
  renderStatusBar({ javaVersion: 21, lastRun: { code: 0 }, offline: false }, container);
  expect(container.querySelector('.v.warn')).toBeNull();

  renderStatusBar({ javaVersion: 21, lastRun: { code: 1 }, offline: false }, container);
  const warned = container.querySelector('.v.warn');
  expect(warned).not.toBeNull();
  expect(warned.textContent).toContain('1');
});

// An offline session starts the game but every server rejects it. Saying so up front
// is cheaper than the user discovering it at a connect screen.
test('an offline session is called out', () => {
  renderStatusBar({ javaVersion: 21, lastRun: null, offline: true }, container);

  expect(container.textContent).toContain('오프라인');
  expect(container.querySelector('.v.warn')).not.toBeNull();
});

test('signed in, the account chip shows the name', () => {
  renderAccount({ username: 'Mal_itIIyr' }, container);

  const chip = container.querySelector('[data-action="account"]');
  expect(chip.textContent).toContain('Mal_itIIyr');
  expect(chip.dataset.signedIn).toBe('true');
});

test('signed out, the account chip invites sign-in', () => {
  renderAccount({ username: null }, container);

  const chip = container.querySelector('[data-action="account"]');
  expect(chip.textContent).toContain('로그인');
  expect(chip.dataset.signedIn).toBe('false');
});

test('does not interpret the username as HTML', () => {
  renderAccount({ username: '<img src=x onerror=1>' }, container);

  expect(container.querySelector('img')).toBeNull();
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `cd ui && npx jest test/statusBar.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the component**

`ui/renderer/components/statusBar.js`:

```js
/**
 * The status bar carries facts the launcher actually knows: which runtime it
 * provisioned, how the last run ended, and where the log is. The account lives in
 * the title bar instead, so that account and version never share a region.
 */
function renderStatusBar({ javaVersion, lastRun, offline }, container) {
  container.replaceChildren();

  container.append(chip('런타임', javaVersion ? `Java ${javaVersion}` : '—', false));

  let lastRunText = '—';
  let lastRunWarns = false;
  if (lastRun) {
    lastRunWarns = lastRun.code !== 0;
    lastRunText = lastRun.code === 0 ? '정상 종료' : `비정상 종료 (${lastRun.code})`;
  }
  container.append(chip('마지막 실행', lastRunText, lastRunWarns));

  if (offline) {
    container.append(chip('세션', '오프라인 — 서버 접속 불가', true));
  }

  const logChip = document.createElement('div');
  logChip.className = 'chip';
  const key = document.createElement('span');
  key.className = 'k';
  key.textContent = '로그';
  const openLog = document.createElement('button');
  openLog.dataset.action = 'open-log';
  openLog.textContent = '열기';
  logChip.append(key, openLog);
  container.append(logChip);
}

function chip(label, value, warns) {
  const element = document.createElement('div');
  element.className = 'chip';

  const key = document.createElement('span');
  key.className = 'k';
  key.textContent = label;

  const val = document.createElement('span');
  val.className = warns ? 'v warn' : 'v';
  val.textContent = value;

  element.append(key, val);
  return element;
}

function renderAccount({ username }, container) {
  container.replaceChildren();

  const chipButton = document.createElement('button');
  chipButton.className = 'account';
  chipButton.dataset.action = 'account';
  chipButton.dataset.signedIn = String(Boolean(username));
  chipButton.textContent = username || '로그인';
  container.append(chipButton);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderStatusBar, renderAccount };
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `cd ui && npx jest test/statusBar.test.js`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/renderer/components/statusBar.js ui/test/statusBar.test.js
git commit -m "Add status bar and account chip components"
```

---

## Task 5: Login modal

**Files:**
- Create: `ui/renderer/components/loginModal.js`
- Test: `ui/test/loginModal.test.js`

**Interfaces:**
- Produces: `renderLoginModal(state, container)` where `state` is one of:
  - `null` — clears the container (modal closed)
  - `{ phase: 'starting' }`
  - `{ phase: 'code', userCode: string, verificationUri: string }`
  - `{ phase: 'failed', message: string }`
- Produces: buttons carrying `data-action` of `copy-code`, `open-verification`, `cancel-login`, `close-login`, `retry-login`. `open-verification` also carries `data-uri`.

- [ ] **Step 1: Write the failing test**

`ui/test/loginModal.test.js`:

```js
/**
 * @jest-environment jsdom
 */
const { renderLoginModal } = require('../renderer/components/loginModal');

let container;
beforeEach(() => {
  document.body.innerHTML = '<div id="modal-root"></div>';
  container = document.getElementById('modal-root');
});

test('null closes the modal', () => {
  renderLoginModal({ phase: 'starting' }, container);
  renderLoginModal(null, container);

  expect(container.children.length).toBe(0);
});

test('starting shows a waiting message and lets the user back out', () => {
  renderLoginModal({ phase: 'starting' }, container);

  expect(container.textContent).toContain('준비');
  expect(container.querySelector('[data-action="cancel-login"]')).not.toBeNull();
});

test('code phase shows the code and both helper actions', () => {
  renderLoginModal(
    { phase: 'code', userCode: 'RHF7XEH4', verificationUri: 'https://www.microsoft.com/link' },
    container
  );

  expect(container.querySelector('.code').textContent).toBe('RHF7XEH4');
  expect(container.querySelector('[data-action="copy-code"]')).not.toBeNull();

  const open = container.querySelector('[data-action="open-verification"]');
  expect(open.dataset.uri).toBe('https://www.microsoft.com/link');
});

// The device code is the one thing the user must read and retype accurately.
test('the code is selectable text, not an image or a background', () => {
  renderLoginModal(
    { phase: 'code', userCode: 'RHF7XEH4', verificationUri: 'https://www.microsoft.com/link' },
    container
  );

  expect(container.querySelector('.code').textContent.trim()).toBe('RHF7XEH4');
});

test('failure shows the backend message verbatim and offers retry and close', () => {
  const message = 'This Microsoft account has no Xbox profile. Sign in once at https://www.xbox.com';
  renderLoginModal({ phase: 'failed', message }, container);

  // The backend already explains what to fix; rewording it here would lose the fix.
  expect(container.textContent).toContain(message);
  expect(container.querySelector('[data-action="retry-login"]')).not.toBeNull();
  expect(container.querySelector('[data-action="close-login"]')).not.toBeNull();
});

test('each render replaces the previous phase rather than stacking modals', () => {
  renderLoginModal({ phase: 'starting' }, container);
  renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com' },
    container
  );

  expect(container.querySelectorAll('.modal').length).toBe(1);
});

// The token travels on auth_result, which sanitizeForRenderer withholds. Even if a
// token somehow reached this component, it must not be drawn.
test('never renders an access token even if one is present on the state', () => {
  renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com', accessToken: 'SECRET' },
    container
  );

  expect(container.textContent).not.toContain('SECRET');
});

test('does not interpret the failure message as HTML', () => {
  renderLoginModal({ phase: 'failed', message: '<img src=x onerror=1>' }, container);

  expect(container.querySelector('img')).toBeNull();
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `cd ui && npx jest test/loginModal.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the component**

`ui/renderer/components/loginModal.js`:

```js
/**
 * The device-code sign-in modal.
 *
 * It renders only what the sanitizer lets through — user code, verification URI,
 * and failure text. The access token travels on the `auth_result` event, which
 * `sanitizeForRenderer` withholds from the renderer entirely, so it never arrives
 * here. This component reads named fields rather than spreading state, so an
 * unexpected field cannot be drawn by accident.
 */
function renderLoginModal(state, container) {
  container.replaceChildren();
  if (!state) return;

  const backdrop = document.createElement('div');
  backdrop.className = 'backdrop';

  const modal = document.createElement('div');
  modal.className = 'modal';
  modal.setAttribute('role', 'dialog');
  modal.setAttribute('aria-modal', 'true');

  if (state.phase === 'starting') {
    modal.append(
      heading('로그인 준비 중…'),
      paragraph('잠시만 기다려 주세요.'),
      row([button('취소', 'cancel-login')])
    );
  } else if (state.phase === 'code') {
    const code = document.createElement('div');
    code.className = 'code';
    code.textContent = state.userCode;

    const openButton = button('브라우저에서 열기', 'open-verification', true);
    openButton.dataset.uri = state.verificationUri;

    modal.append(
      heading('Microsoft 계정으로 로그인'),
      paragraph('브라우저에서 아래 코드를 입력하세요. 완료될 때까지 기다립니다.'),
      code,
      row([openButton, button('코드 복사', 'copy-code')]),
      row([button('취소', 'cancel-login')])
    );
  } else {
    const failure = document.createElement('p');
    failure.className = 'failure';
    failure.textContent = state.message;

    modal.append(
      heading('로그인하지 못했습니다'),
      failure,
      row([button('다시 시도', 'retry-login', true), button('닫기', 'close-login')])
    );
  }

  backdrop.append(modal);
  container.append(backdrop);
}

function heading(text) {
  const element = document.createElement('h2');
  element.textContent = text;
  return element;
}

function paragraph(text) {
  const element = document.createElement('p');
  element.textContent = text;
  return element;
}

function row(children) {
  const element = document.createElement('div');
  element.className = 'row';
  element.append(...children);
  return element;
}

function button(label, action, primary = false) {
  const element = document.createElement('button');
  if (primary) element.className = 'primary';
  element.dataset.action = action;
  element.textContent = label;
  return element;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderLoginModal };
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `cd ui && npx jest test/loginModal.test.js`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/renderer/components/loginModal.js ui/test/loginModal.test.js
git commit -m "Add device-code login modal component"
```

---

## Task 6: Frameless window and new IPC channels

**Files:**
- Modify: `ui/main.js`
- Modify: `ui/preload.js`

**Interfaces:**
- Produces: IPC channels `window-minimize`, `window-maximize`, `window-close`, `cancel-login`, `copy-to-clipboard`, `open-external`.
- Produces: preload API additions — `minimize()`, `maximize()`, `close()`, `cancelLogin()`, `copyToClipboard(text)`, `openExternal(url)`. Existing `onBackendEvent`, `launchProfile`, `startLogin`, `openLog` keep their signatures.

- [ ] **Step 1: Make the window frameless and add the channels**

In `ui/main.js`, change the `BrowserWindow` construction to add `frame: false` and a minimum size:

```js
  const win = new BrowserWindow({
    width: 1000,
    height: 650,
    minWidth: 860,
    minHeight: 560,
    // The title bar is drawn in the renderer so it can carry the account chip and
    // match the rest of the shell.
    frame: false,
    backgroundColor: '#0f1216',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      // The renderer handles profile data from a user-editable file; keep it out of Node.
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
```

Add these handlers inside `createWindow`, after the existing `ipcMain.on('open-log', ...)` block:

```js
  ipcMain.on('window-minimize', () => win.minimize());
  ipcMain.on('window-maximize', () => {
    if (win.isMaximized()) win.unmaximize();
    else win.maximize();
  });
  ipcMain.on('window-close', () => win.close());

  ipcMain.on('copy-to-clipboard', (_event, text) => {
    if (typeof text === 'string') clipboard.writeText(text);
  });

  ipcMain.on('open-external', (_event, url) => {
    // Only the Microsoft device-login page is ever opened from here. Without this
    // check the renderer could ask the OS to open any URL or local path.
    if (typeof url !== 'string') return;
    let parsed;
    try {
      parsed = new URL(url);
    } catch (err) {
      return;
    }
    if (parsed.protocol !== 'https:') return;
    if (parsed.hostname !== 'www.microsoft.com' && parsed.hostname !== 'microsoft.com') return;
    shell.openExternal(url);
  });
```

Track the login process so it can be cancelled. Replace the existing `start-login` handler with:

```js
  let loginProcess = null;

  ipcMain.on('start-login', () => {
    if (loginProcess) return;
    loginProcess = startBackend(JAR_PATH, 'login', [], (event) => {
      if (event.type === 'backend_exit') loginProcess = null;
      send(event);
    }, undefined, javaCommand);
  });

  ipcMain.on('cancel-login', () => {
    if (loginProcess) {
      loginProcess.kill();
      loginProcess = null;
    }
  });
```

Extend the electron import at the top of the file:

```js
const { app, BrowserWindow, ipcMain, safeStorage, shell, clipboard } = require('electron');
```

- [ ] **Step 2: Extend the preload bridge**

`ui/preload.js`:

```js
const { contextBridge, ipcRenderer } = require('electron');

// The only surface the renderer gets. It can receive (already sanitized) backend
// events and ask the main process to act — it cannot spawn processes, read
// auth.json, or touch the filesystem itself, and it never receives an access token.
contextBridge.exposeInMainWorld('cubeclient', {
  onBackendEvent: (callback) => {
    ipcRenderer.on('backend-event', (_event, data) => callback(data));
  },
  launchProfile: (profileId) => ipcRenderer.send('launch-profile', profileId),
  startLogin: () => ipcRenderer.send('start-login'),
  cancelLogin: () => ipcRenderer.send('cancel-login'),
  openLog: (profileId) => ipcRenderer.send('open-log', profileId),
  copyToClipboard: (text) => ipcRenderer.send('copy-to-clipboard', text),
  openExternal: (url) => ipcRenderer.send('open-external', url),
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close: () => ipcRenderer.send('window-close'),
});
```

- [ ] **Step 3: Confirm the existing suite still passes**

Nothing here is unit-tested (it is all Electron main-process glue), but the change must not break the modules that are.

Run: `cd ui && npx jest`
Expected: PASS — every test from Tasks 2–5 plus the four pre-existing suites.

- [ ] **Step 4: Commit**

```bash
git add ui/main.js ui/preload.js
git commit -m "Make the window frameless and add window, clipboard and login-cancel channels"
```

---

## Task 7: Wire the shell together

**Files:**
- Modify: `ui/renderer/renderer.js`
- Test: `ui/test/renderer.test.js` (replaces the existing file's contents)

**Interfaces:**
- Consumes: `renderHero`, `renderVersionMenu`, `renderStatusBar`, `renderAccount`, `renderLoginModal` from Tasks 2–5; the preload API from Task 6.
- Produces: `createStore(initialState)` returning `{ getState, apply(event), select(profileId), toggleVersions(), setLogin(state) }` — a pure reducer over backend events, testable without Electron.

- [ ] **Step 1: Write the failing test**

Replace the whole of `ui/test/renderer.test.js`:

```js
/**
 * @jest-environment jsdom
 */
const { createStore } = require('../renderer/renderer');

const PROFILES = [
  { id: 'fabric-1.21', mcVersion: '1.21.4', loader: 'fabric', mods: [] },
  { id: 'manual-test-1.21', mcVersion: '1.21.4', loader: 'vanilla', mods: [] },
];

test('starts idle, signed out, with nothing selected', () => {
  const store = createStore();
  const state = store.getState();

  expect(state.hero.mode).toBe('idle');
  expect(state.username).toBeNull();
  expect(state.selectedId).toBeNull();
});

test('a profiles event selects the first version so Play always has a target', () => {
  const store = createStore();
  store.apply({ type: 'profiles', profiles: PROFILES });

  expect(store.getState().profiles).toHaveLength(2);
  expect(store.getState().selectedId).toBe('fabric-1.21');
});

test('a later profiles event keeps the current selection if it still exists', () => {
  const store = createStore();
  store.apply({ type: 'profiles', profiles: PROFILES });
  store.select('manual-test-1.21');
  store.apply({ type: 'profiles', profiles: PROFILES });

  expect(store.getState().selectedId).toBe('manual-test-1.21');
});

test('progress moves the hero into preparing', () => {
  const store = createStore();
  store.apply({ type: 'progress', stage: 'assets', percent: 72 });

  expect(store.getState().hero).toEqual({
    mode: 'preparing', stage: 'assets', percent: 72, detail: null,
  });
});

test('launched moves the hero into running, exited moves it back to idle', () => {
  const store = createStore();
  store.apply({ type: 'launched' });
  expect(store.getState().hero.mode).toBe('running');

  store.apply({ type: 'exited', code: 0 });
  expect(store.getState().hero.mode).toBe('idle');
  expect(store.getState().lastRun).toEqual({ code: 0 });
});

test('a non-zero exit is recorded but still returns the hero to idle', () => {
  const store = createStore();
  store.apply({ type: 'launched' });
  store.apply({ type: 'exited', code: 1 });

  expect(store.getState().hero.mode).toBe('idle');
  expect(store.getState().lastRun).toEqual({ code: 1 });
});

test('an error moves the hero into error with the backend message', () => {
  const store = createStore();
  store.apply({ type: 'error', stage: 'launch', message: 'unknown profile: x' });

  expect(store.getState().hero).toEqual({ mode: 'error', message: 'unknown profile: x' });
});

// A login failure belongs in the modal, not the hero — the hero is about the game.
test('an error during login goes to the modal instead of the hero', () => {
  const store = createStore();
  store.setLogin({ phase: 'starting' });
  store.apply({ type: 'error', stage: 'login', message: 'no Xbox profile' });

  expect(store.getState().hero.mode).toBe('idle');
  expect(store.getState().login).toEqual({ phase: 'failed', message: 'no Xbox profile' });
});

test('a device code opens the modal on the code phase', () => {
  const store = createStore();
  store.apply({
    type: 'device_code', userCode: 'RHF7XEH4', verificationUri: 'https://www.microsoft.com/link',
  });

  expect(store.getState().login).toEqual({
    phase: 'code', userCode: 'RHF7XEH4', verificationUri: 'https://www.microsoft.com/link',
  });
});

test('login_success closes the modal and records the account', () => {
  const store = createStore();
  store.setLogin({ phase: 'starting' });
  store.apply({ type: 'login_success', username: 'Mal_itIIyr', uuid: 'abc' });

  expect(store.getState().login).toBeNull();
  expect(store.getState().username).toBe('Mal_itIIyr');
});

// sanitizeForRenderer already withholds auth_result. If that ever regressed, the
// store must still refuse to hold a token.
test('the store never keeps an access token', () => {
  const store = createStore();
  store.apply({ type: 'login_success', username: 'Steve', uuid: 'u', accessToken: 'SECRET' });

  expect(JSON.stringify(store.getState())).not.toContain('SECRET');
});

test('offline is true until an account is known', () => {
  const store = createStore();
  expect(store.getState().offline).toBe(true);

  store.apply({ type: 'login_success', username: 'Steve', uuid: 'u' });
  expect(store.getState().offline).toBe(false);
});

test('backend_exit with a non-zero code surfaces as a hero error', () => {
  const store = createStore();
  store.apply({ type: 'backend_exit', code: 1 });

  expect(store.getState().hero.mode).toBe('error');
});

test('backend_exit with code 0 is not an error', () => {
  const store = createStore();
  store.apply({ type: 'backend_exit', code: 0 });

  expect(store.getState().hero.mode).toBe('idle');
});

test('toggling the version menu flips it open and closed', () => {
  const store = createStore();
  expect(store.getState().versionsOpen).toBe(false);

  store.toggleVersions();
  expect(store.getState().versionsOpen).toBe(true);

  store.toggleVersions();
  expect(store.getState().versionsOpen).toBe(false);
});

test('selecting a version closes the menu', () => {
  const store = createStore();
  store.apply({ type: 'profiles', profiles: PROFILES });
  store.toggleVersions();
  store.select('manual-test-1.21');

  expect(store.getState().selectedId).toBe('manual-test-1.21');
  expect(store.getState().versionsOpen).toBe(false);
});
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `cd ui && npx jest test/renderer.test.js`
Expected: FAIL — `createStore` is not exported.

- [ ] **Step 3: Rewrite the renderer**

Replace the whole of `ui/renderer/renderer.js`:

```js
/**
 * Wiring only. All drawing lives in components/, and all state transitions live in
 * createStore — a pure reducer with no Electron dependency, so the event handling
 * can be tested under jsdom without a window.
 */
function createStore() {
  const state = {
    profiles: [],
    selectedId: null,
    versionsOpen: false,
    username: null,
    offline: true,
    javaVersion: null,
    lastRun: null,
    hero: { mode: 'idle', username: null },
    login: null,
  };

  function idle() {
    state.hero = { mode: 'idle', username: state.username };
  }

  function apply(event) {
    switch (event.type) {
      case 'profiles':
        state.profiles = event.profiles;
        // Keep the current pick if it survived; otherwise fall to the first so the
        // Play button always has a target.
        if (!state.profiles.some((p) => p.id === state.selectedId)) {
          state.selectedId = state.profiles.length ? state.profiles[0].id : null;
        }
        break;

      case 'progress':
        state.hero = {
          mode: 'preparing',
          stage: event.stage,
          percent: event.percent,
          detail: event.detail || null,
        };
        break;

      case 'launched':
        state.hero = { mode: 'running' };
        break;

      case 'exited':
        state.lastRun = { code: event.code };
        idle();
        break;

      case 'device_code':
        state.login = {
          phase: 'code',
          userCode: event.userCode,
          verificationUri: event.verificationUri,
        };
        break;

      case 'login_success':
        // Only the two fields are read. Spreading the event would risk holding
        // anything else it happened to carry.
        state.username = event.username;
        state.offline = false;
        state.login = null;
        idle();
        break;

      case 'error':
        if (event.stage === 'login' || state.login) {
          state.login = { phase: 'failed', message: event.message };
        } else {
          state.hero = { mode: 'error', message: event.message };
        }
        break;

      case 'backend_exit':
        if (event.code !== 0 && state.hero.mode !== 'error') {
          state.hero = { mode: 'error', message: `백엔드가 코드 ${event.code}(으)로 종료됐습니다` };
        }
        break;

      default:
        break;
    }
  }

  return {
    getState: () => state,
    apply,
    select(profileId) {
      state.selectedId = profileId;
      state.versionsOpen = false;
    },
    toggleVersions() {
      state.versionsOpen = !state.versionsOpen;
    },
    setLogin(login) {
      state.login = login;
    },
  };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { createStore };
}

// Electron-only wiring. window.cubeclient is exposed by preload.js and is absent
// under jsdom, so none of this runs during tests.
if (typeof window !== 'undefined' && window.cubeclient) {
  const api = window.cubeclient;
  const store = createStore();

  const heroEl = document.getElementById('hero');
  const statusEl = document.getElementById('statusbar');
  const accountEl = document.getElementById('account-slot');
  const modalEl = document.getElementById('modal-root');

  function draw() {
    const s = store.getState();

    renderHero(s.hero, heroEl);
    // The version switcher sits under the hero's own content, and only when the
    // hero is idle — during a download or a run there is nothing to switch to.
    if (s.hero.mode === 'idle') {
      const slot = document.createElement('div');
      heroEl.append(slot);
      renderVersionMenu(
        { profiles: s.profiles, selectedId: s.selectedId, open: s.versionsOpen },
        slot
      );
    }

    renderStatusBar(
      { javaVersion: s.javaVersion, lastRun: s.lastRun, offline: s.offline },
      statusEl
    );
    renderAccount({ username: s.username }, accountEl);
    renderLoginModal(s.login, modalEl);
  }

  document.getElementById('win-min').addEventListener('click', () => api.minimize());
  document.getElementById('win-max').addEventListener('click', () => api.maximize());
  document.getElementById('win-close').addEventListener('click', () => api.close());

  document.body.addEventListener('click', (domEvent) => {
    const target = domEvent.target.closest('[data-action]');
    if (!target) return;
    const s = store.getState();

    switch (target.dataset.action) {
      case 'play':
        if (s.selectedId) api.launchProfile(s.selectedId);
        break;
      case 'stop':
      case 'retry':
        store.apply({ type: 'exited', code: 0 });
        draw();
        break;
      case 'toggle-versions':
        store.toggleVersions();
        draw();
        break;
      case 'select-version':
        store.select(target.dataset.profileId);
        draw();
        break;
      case 'open-log':
        if (s.selectedId) api.openLog(s.selectedId);
        break;
      case 'account':
        if (!s.username) {
          store.setLogin({ phase: 'starting' });
          draw();
          api.startLogin();
        }
        break;
      case 'copy-code':
        if (s.login && s.login.phase === 'code') api.copyToClipboard(s.login.userCode);
        break;
      case 'open-verification':
        api.openExternal(target.dataset.uri);
        break;
      case 'retry-login':
        store.setLogin({ phase: 'starting' });
        draw();
        api.startLogin();
        break;
      case 'cancel-login':
        api.cancelLogin();
        store.setLogin(null);
        draw();
        break;
      case 'close-login':
        store.setLogin(null);
        draw();
        break;
      default:
        break;
    }
  });

  api.onBackendEvent((event) => {
    store.apply(event);
    draw();
  });

  draw();
}
```

- [ ] **Step 4: Run the whole UI suite**

Run: `cd ui && npx jest`
Expected: PASS — 8 hero + 8 version menu + 7 status bar + 8 login modal + 16 store + the pre-existing `authStore` (7), `backendProcess` (6) and `rendererEvents` (5) suites.

- [ ] **Step 5: Commit**

```bash
git add ui/renderer/renderer.js ui/test/renderer.test.js
git commit -m "Wire the shell: pure store over backend events, components for drawing"
```

---

## Task 8: Manual verification

**Files:** none — this task changes no code.

This is the only place window dragging, the version dropdown, and the real sign-in path get exercised. None of it is reachable from jsdom.

- [ ] **Step 1: Build the backend jar**

```bash
cd backend && ./gradlew jar
```

Expected: `backend/build/libs/cubeclient-launcher-backend.jar` exists.

- [ ] **Step 2: Start the launcher**

```bash
cd ui && CUBECLIENT_JAVA="C:/Users/Skdji/devtools/jdk17/jdk-17.0.19+10/bin/java.exe" npx electron .
```

- [ ] **Step 3: Walk the shell**

Confirm each, and note any that fail rather than fixing them silently:

1. The window has no OS title bar; the drawn one is visible.
2. Dragging the title bar moves the window. Dragging over the account chip or the window buttons does **not** move it.
3. Minimize, maximize (and restore), and close all work.
4. The version dropdown opens, lists both configured versions, and marks the current one. `+ 버전 추가` is visibly disabled.
5. Selecting the other version closes the menu and updates the trigger label.
6. The status bar shows the log button; pressing it opens `latest.log` for the selected version.

- [ ] **Step 4: Walk a launch**

Press PLAY. Confirm:

1. The hero switches to the progress view and the stage text changes through `manifest`, `libraries`, `client_jar`, `assets`, `loader`, `runtime`, `launching`.
2. The Play button is gone while preparing — there is no way to start a second launch.
3. The hero shows `실행 중…` once the game window appears.
4. Closing the game returns the hero to idle and the status bar reads `정상 종료`.

- [ ] **Step 5: Walk a sign-in**

Press the account chip. Confirm:

1. The modal opens on the waiting phase, then shows a code.
2. `코드 복사` puts the code on the clipboard (paste it somewhere to check).
3. `브라우저에서 열기` opens the Microsoft page in the default browser.
4. Completing sign-in in the browser closes the modal and the title bar shows the account name.
5. The status bar no longer shows the offline warning.
6. **The access token appears nowhere in the window.** Open DevTools (`Ctrl+Shift+I`), and in the console run `document.body.innerText.includes('eyJ')` — it must print `false`.

- [ ] **Step 6: Walk a sign-in failure**

Press the account chip again after signing out is not available in this scope, so instead: cancel a fresh sign-in mid-flow and confirm the modal closes and no backend process is left running (`Get-Process java` should show only the game, if any).

- [ ] **Step 7: Commit nothing, report findings**

This task produces no commit. Report which of the checks above passed and which did not.

---

## Self-review notes

Checked against `docs/superpowers/specs/2026-07-26-launcher-ui-design.md`:

- Color tokens, single-theme decision, system fonts — Task 1.
- Title bar with account, icon rail of four, hero, status bar — Tasks 1, 2, 4.
- Hero's three states plus an error state the spec implies through its error-handling section — Task 2.
- Version wording, account/version separation, disabled add row — Tasks 3, 4.
- Six-step login flow including cancel — Tasks 5, 6, 7.
- Token isolation — asserted in Tasks 5 and 7, and manually in Task 8 Step 5.6.
- Component split with no Electron imports — Tasks 2–5.
- Out-of-scope items (version editor, settings contents, multiple accounts, token refresh) are not built; the add row is disabled and the settings rail button is inert.

Known gap carried forward: `javaVersion` is never set by any event, so the runtime chip always reads `—`. The backend does not currently emit the Java version it provisioned. Left as-is rather than inventing an event; the chip degrades to a dash, which Task 4's test pins.
