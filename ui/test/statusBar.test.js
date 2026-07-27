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

// A malformed exit event must not surface as the word "undefined" in the UI.
// Its sibling components already hold this line; this one should too.
test('an exit with no code reads as unknown rather than undefined', () => {
  renderStatusBar({ javaVersion: 21, lastRun: {}, offline: false }, container);

  expect(container.textContent).not.toContain('undefined');
  expect(container.textContent).toContain('비정상 종료 (알 수 없음)');
});

// Without this, a regression that dropped replaceChildren() would leave the previous
// render's chips in place and every other assertion here would still pass.
test('each render replaces the previous chips rather than stacking', () => {
  renderStatusBar({ javaVersion: 21, lastRun: { code: 1 }, offline: true }, container);
  const firstCount = container.querySelectorAll('.chip').length;

  renderStatusBar({ javaVersion: 21, lastRun: { code: 1 }, offline: true }, container);

  expect(container.querySelectorAll('.chip').length).toBe(firstCount);
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
