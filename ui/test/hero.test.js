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

// The version list arrives from the backend about a second after the window opens.
// Reported as "PLAY를 눌러도 아무것도 안 돼": the button was live but had no target,
// so pressing it was a silent no-op.
test('Play is disabled while there is no version to launch', () => {
  renderHero({ mode: 'idle', username: null, canPlay: false }, container);

  expect(container.querySelector('[data-action="play"]').disabled).toBe(true);
});

test('Play is enabled once a version is available', () => {
  renderHero({ mode: 'idle', username: null, canPlay: true }, container);

  expect(container.querySelector('[data-action="play"]').disabled).toBe(false);
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
