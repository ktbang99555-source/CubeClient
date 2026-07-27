/**
 * Wiring only. All drawing lives in components/, and all state transitions live in
 * createStore — a pure reducer with no Electron dependency, so the event handling can be
 * tested under jsdom without a window.
 */
/**
 * Turns a failed backend exit into something a person can act on.
 *
 * When the JVM dies before reaching our code — wrong Java version, missing jar — it says
 * why on stderr and writes nothing to stdout, so the exit code is all the UI used to
 * have. "백엔드가 코드 1(으)로 종료됐습니다" tells nobody anything; the first line of
 * stderr usually names the actual problem.
 */
function describeBackendExit(event) {
  const base = `백엔드가 코드 ${event.code}(으)로 종료됐습니다`;
  if (!event.stderr) return base;

  const lines = event.stderr.split('\n').map((line) => line.trim()).filter(Boolean);
  if (!lines.length) return base;

  // Scan every captured line, not just the first: the java launcher reports a version
  // mismatch as a generic "Error: LinkageError occurred" with the real cause on the
  // next line, while a direct JVM throw puts it first. Only checking line one missed
  // the commonest failure on a machine whose PATH java is old.
  const versionMismatch = lines.find((line) => line.includes('UnsupportedClassVersionError'));
  if (versionMismatch) {
    return `${base} — 실행에 쓰인 Java가 너무 낮은 버전입니다. CUBECLIENT_JAVA 환경변수를 Java 17 이상으로 지정하세요.\n\n${versionMismatch}`;
  }

  return `${base}\n\n${lines[0]}`;
}

function createStore() {
  const state = {
    profiles: [],
    selectedId: null,
    versionsOpen: false,
    username: null,
    offline: true,
    javaVersion: null,
    lastRun: null,
    hero: { mode: 'idle', username: null, canPlay: false },
    login: null,
  };

  function idle() {
    // canPlay travels with the hero state so the button can say "not yet" instead of
    // being pressable and doing nothing while the version list is still on its way.
    state.hero = { mode: 'idle', username: state.username, canPlay: state.selectedId !== null };
  }

  function apply(event) {
    switch (event.type) {
      case 'profiles':
        state.profiles = event.profiles;
        // Keep the current pick if it survived; otherwise fall to the first so the Play
        // button always has a target.
        if (!state.profiles.some((p) => p.id === state.selectedId)) {
          state.selectedId = state.profiles.length ? state.profiles[0].id : null;
        }
        // The hero carries canPlay, so it has to be rebuilt when a target appears.
        if (state.hero.mode === 'idle') idle();
        break;

      case 'progress':
        state.hero = {
          mode: 'preparing',
          stage: event.stage,
          // The hero assigns this straight onto a <progress>, which throws on a
          // non-number. A malformed event must not take the renderer down.
          percent: typeof event.percent === 'number' ? event.percent : 0,
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
        // Only the two fields are read. Spreading the event would risk holding anything
        // else it happened to carry.
        state.username = event.username;
        state.offline = false;
        state.login = null;
        // Rebuild the hero so the greeting picks up the name straight away instead of
        // staying stale until the next event.
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
        if (event.code === 0) break;
        if (state.login) {
          // A sign-in that died without explaining itself — cancelled, expired, crashed.
          // Leaving the modal on its waiting message would spin forever. An explanation
          // the backend already sent is better than an exit code, so keep that.
          if (state.login.phase !== 'failed') {
            state.login = {
              phase: 'failed',
              message: `로그인이 코드 ${event.code}(으)로 종료됐습니다`,
            };
          }
        } else if (state.hero.mode !== 'error') {
          state.hero = { mode: 'error', message: describeBackendExit(event) };
        }
        break;

      default:
        // The backend can grow new event types at any time; an older UI has to survive
        // meeting one.
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
  module.exports = { createStore, describeBackendExit };
}

// Electron-only wiring. window.cubeclient is exposed by preload.js and is absent under
// jsdom, so none of this runs during tests.
if (typeof window !== 'undefined' && window.cubeclient) {
  const api = window.cubeclient;
  const store = createStore();

  const heroEl = document.getElementById('hero');
  const statusEl = document.getElementById('statusbar');
  const accountEl = document.getElementById('account-slot');
  const modalEl = document.getElementById('modal-root');

  const draw = () => {
    const s = store.getState();

    renderHero(s.hero, heroEl);
    // The version switcher sits under the hero's own content, and only when the hero is
    // idle — during a download or a run there is nothing to switch to.
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
  };

  document.getElementById('win-min').addEventListener('click', () => api.minimize());
  document.getElementById('win-max').addEventListener('click', () => api.maximize());
  document.getElementById('win-close').addEventListener('click', () => api.close());

  document.body.addEventListener('click', (domEvent) => {
    const target = domEvent.target.closest('[data-action]');
    if (!target) return;
    const s = store.getState();

    switch (target.dataset.action) {
      case 'play':
        // Never without an account: an offline session would start the game for someone
        // who does not own it. The main process refuses too — this is the polite half.
        if (s.username && s.selectedId) api.launchProfile(s.selectedId);
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
        // The main process checks the host before handing anything to the OS.
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

  // Escape closes the sign-in modal. It lives here rather than in the modal component
  // because that component is a pure render function called on every phase change —
  // attaching a listener there would register a new one per render and leak. One
  // document-level listener, owned by the wiring, is the whole story.
  document.addEventListener('keydown', (domEvent) => {
    if (domEvent.key !== 'Escape') return;
    if (!store.getState().login) return;
    api.cancelLogin();
    store.setLogin(null);
    draw();
  });

  api.onBackendEvent((event) => {
    store.apply(event);
    draw();
    // Mirrored into the main process's debug log when CUBECLIENT_DEBUG is set. Without
    // it, "the window shows nothing" and "the window never got the event" look identical
    // from outside.
    const s = store.getState();
    console.log(`[state] after ${event.type}: versions=${s.profiles.length}`
      + ` selected=${s.selectedId} hero=${s.hero.mode}`);
  });

  draw();
}
