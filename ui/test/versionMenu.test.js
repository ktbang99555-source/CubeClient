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
