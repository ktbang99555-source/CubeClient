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
    container.append(greeting, heroButton('PLAY', 'play', 'play'));
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
    container.append(label, heroButton('종료', 'stop', 'stop'));
    return;
  }

  const failure = document.createElement('div');
  failure.className = 'hero-error';
  failure.textContent = state.message;
  container.append(failure, heroButton('다시 시도', 'retry', 'stop'));
}

// Named for its component, not for what it builds: index.html loads every component as a
// classic <script> into one shared global, so a bare `button` here and a bare `button` in
// another component silently overwrite each other. componentScripts.test.js guards this.
function heroButton(label, action, className) {
  const element = document.createElement('button');
  element.className = className;
  element.dataset.action = action;
  element.textContent = label;
  return element;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderHero };
}
