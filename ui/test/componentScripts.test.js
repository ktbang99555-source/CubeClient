/**
 * @jest-environment jsdom
 *
 * The other component tests `require()` each file, which gives every module its own
 * scope. The real page does not: index.html loads all four as classic <script> tags into
 * one shared global. Anything a component declares at top level — including a helper it
 * considers private — is visible to, and can be clobbered by, the next script.
 *
 * This suite loads them the way the browser does, so a name collision fails here instead
 * of in a running launcher.
 */
const fs = require('fs');
const path = require('path');

const COMPONENT_DIR = path.join(__dirname, '..', 'renderer', 'components');

// The order index.html loads them in. Order is the whole point: a collision only shows
// up as the later script overwriting the earlier one's helper.
const SCRIPTS = ['hero.js', 'versionMenu.js', 'statusBar.js', 'loginModal.js'];

// Loaded once, like a page load. Re-running them would hit `const` redeclaration errors
// that are an artefact of this harness, not of the product.
beforeAll(() => {
  for (const file of SCRIPTS) {
    const element = document.createElement('script');
    element.textContent = fs.readFileSync(path.join(COMPONENT_DIR, file), 'utf8');
    document.head.appendChild(element);
  }
});

beforeEach(() => {
  document.body.innerHTML = '<section id="hero"></section><div id="slot"></div>';
});

test('every component exposes its render functions on the page', () => {
  expect(typeof window.renderHero).toBe('function');
  expect(typeof window.renderVersionMenu).toBe('function');
  expect(typeof window.formatVersionLabel).toBe('function');
  expect(typeof window.renderStatusBar).toBe('function');
  expect(typeof window.renderAccount).toBe('function');
  expect(typeof window.renderLoginModal).toBe('function');
});

// hero.js and loginModal.js each had a top-level helper called `button`, with different
// third parameters — a class name in one, a boolean in the other. Loaded together, the
// last one won and the Play button silently came out styled as a modal button.
test('the Play button keeps its own class when every component is loaded together', () => {
  const hero = document.getElementById('hero');

  window.renderHero({ mode: 'idle', username: 'Steve' }, hero);

  const play = hero.querySelector('[data-action="play"]');
  expect(play.className).toBe('play');
  expect(play.textContent).toBe('PLAY');
});

test('the stop and retry buttons keep their class too', () => {
  const hero = document.getElementById('hero');

  window.renderHero({ mode: 'running' }, hero);
  expect(hero.querySelector('[data-action="stop"]').className).toBe('stop');

  window.renderHero({ mode: 'error', message: '실패' }, hero);
  expect(hero.querySelector('[data-action="retry"]').className).toBe('stop');
});

test('the modal still builds its own buttons correctly alongside the others', () => {
  const slot = document.getElementById('slot');

  window.renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://www.microsoft.com/link' },
    slot
  );

  expect(slot.querySelector('[data-action="open-verification"]').className).toBe('primary');
  expect(slot.querySelector('[data-action="copy-code"]').className).toBe('');
});

test('the version menu and status bar are unaffected by the shared scope', () => {
  const slot = document.getElementById('slot');

  window.renderVersionMenu(
    { profiles: [{ id: 'a', mcVersion: '1.21.4', loader: 'fabric', mods: [] }], selectedId: 'a', open: true },
    slot
  );
  expect(slot.querySelector('[data-action="toggle-versions"]').className).toBe('version-trigger');

  window.renderStatusBar({ javaVersion: 21, lastRun: { code: 0 }, offline: false }, slot);
  expect(slot.querySelectorAll('.chip').length).toBe(3);
});
