/**
 * @jest-environment jsdom
 */
const { renderProfiles, renderProgress } = require('../renderer/renderer');

test('renderProfiles creates one card per profile with Play button', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProfiles(
    [
      { id: 'latest-1.21', mcVersion: '1.21.4', loader: 'fabric', mods: [] },
      { id: 'hypixel-1.8.9', mcVersion: '1.8.9', loader: 'legacyfabric', mods: [] },
    ],
    container
  );

  const cards = container.querySelectorAll('.profile-card');
  expect(cards.length).toBe(2);
  expect(cards[0].dataset.profileId).toBe('latest-1.21');
  expect(cards[0].textContent).toContain('1.21.4');
  expect(cards[0].querySelector('button').textContent).toBe('Play');
  expect(cards[1].dataset.profileId).toBe('hypixel-1.8.9');
  expect(cards[1].textContent).toContain('1.8.9');
});

test('renderProfiles replaces previous content rather than appending', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProfiles([{ id: 'a', mcVersion: '1.21.4', loader: 'fabric', mods: [] }], container);
  renderProfiles([{ id: 'b', mcVersion: '1.8.9', loader: 'legacyfabric', mods: [] }], container);

  const cards = container.querySelectorAll('.profile-card');
  expect(cards.length).toBe(1);
  expect(cards[0].dataset.profileId).toBe('b');
});

// Profile ids and versions come from a user-editable JSON file. Building this markup by
// string concatenation would make a crafted id able to inject nodes; assert we don't.
test('renderProfiles does not interpret profile fields as HTML', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProfiles(
    [{ id: '<img src=x onerror=1>', mcVersion: '<b>1.21.4</b>', loader: 'fabric', mods: [] }],
    container
  );

  expect(container.querySelector('img')).toBeNull();
  expect(container.querySelector('b')).toBeNull();
  expect(container.textContent).toContain('<b>1.21.4</b>');
});

test('renderProgress shows stage label and progress bar value', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProgress('libraries', 42, container);

  expect(container.textContent).toContain('libraries');
  const progressEl = container.querySelector('progress');
  expect(progressEl.value).toBe(42);
  expect(progressEl.max).toBe(100);
});

test('renderProgress replaces the previous stage rather than stacking', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProgress('manifest', 0, container);
  renderProgress('client_jar', 60, container);

  expect(container.querySelectorAll('progress').length).toBe(1);
  expect(container.textContent).toContain('client_jar');
  expect(container.textContent).not.toContain('manifest');
});
