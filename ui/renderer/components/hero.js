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

    // Signing in is how ownership of the game is established. Offering Play without it
    // would launch an offline session, which starts singleplayer for someone who may
    // never have bought Minecraft — that is what the official launcher's mandatory
    // sign-in exists to prevent, and this launcher will not be a way around it.
    if (!state.username) {
      greeting.textContent = 'Microsoft 계정으로 로그인하면 시작할 수 있습니다';
      container.append(greeting, heroButton('로그인', 'account', 'play'));
      return;
    }

    greeting.append('다시 오셨네요, ');
    const name = document.createElement('b');
    name.textContent = state.username;
    greeting.append(name);

    const play = heroButton('PLAY', 'play', 'play');
    // The version list arrives from the backend about a second after the window opens.
    // Until then there is nothing to launch, and an enabled button that silently does
    // nothing reads as a broken launcher — which is exactly how it was reported.
    if (state.canPlay === false) {
      play.disabled = true;
      play.title = '버전을 불러오는 중입니다';
    }
    container.append(greeting, play);
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
