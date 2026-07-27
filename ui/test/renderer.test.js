/**
 * @jest-environment jsdom
 */
const { createStore, describeBackendExit } = require('../renderer/renderer');

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

// The window opens about a second before the backend answers. Play must not be
// pressable in that window, and must become pressable the moment it is answered.
test('Play has no target until the version list arrives', () => {
  const store = createStore();
  expect(store.getState().hero.canPlay).toBe(false);

  store.apply({ type: 'profiles', profiles: PROFILES });
  expect(store.getState().hero.canPlay).toBe(true);
});

test('an empty version list leaves Play without a target', () => {
  const store = createStore();
  store.apply({ type: 'profiles', profiles: [] });

  expect(store.getState().selectedId).toBeNull();
  expect(store.getState().hero.canPlay).toBe(false);
});

// A launch in flight must not have its progress replaced by an idle hero just because
// the version list happened to be re-read.
test('a profiles event does not interrupt a launch in progress', () => {
  const store = createStore();
  store.apply({ type: 'progress', stage: 'assets', percent: 40 });
  store.apply({ type: 'profiles', profiles: PROFILES });

  expect(store.getState().hero.mode).toBe('preparing');
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

// hero.js assigns state.percent straight onto a <progress>, which throws on undefined.
// A malformed event must not take the whole renderer down with it.
test('a progress event with no percent still produces a numeric percent', () => {
  const store = createStore();
  store.apply({ type: 'progress', stage: 'assets' });

  expect(store.getState().hero.percent).toBe(0);
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

// A bare exit code tells nobody anything. The JVM already said what was wrong on stderr.
test('a backend failure shows the cause, not just the exit code', () => {
  const store = createStore();
  store.apply({
    type: 'backend_exit',
    code: 1,
    stderr: 'Error: LinkageError occurred\njava.lang.UnsupportedClassVersionError: bad version',
  });

  expect(store.getState().hero.message).toContain('UnsupportedClassVersionError');
});

// The single most likely way this launcher fails on a fresh machine: the PATH java is
// old, and the raw JVM message does not tell the user what to do about it.
test('a Java version mismatch says which environment variable to set', () => {
  const message = describeBackendExit({
    code: 1,
    stderr: 'java.lang.UnsupportedClassVersionError: com/cubeclient/launcher/Main ...',
  });

  expect(message).toContain('CUBECLIENT_JAVA');
  expect(message).toContain('Java 17');
});

test('a failure with no stderr still reports the exit code', () => {
  const message = describeBackendExit({ code: 1 });

  expect(message).toContain('1');
  expect(message).not.toContain('undefined');
});

test('backend_exit with code 0 is not an error', () => {
  const store = createStore();
  store.apply({ type: 'backend_exit', code: 0 });

  expect(store.getState().hero.mode).toBe('idle');
});

// The login backend exits non-zero when the user cancels or the code expires. That is the
// modal's business, not the hero's — and if the backend dies without explaining itself,
// the modal must stop waiting rather than spin on "준비 중" forever.
test('a login backend that dies fails the modal instead of the hero', () => {
  const store = createStore();
  store.setLogin({ phase: 'starting' });
  store.apply({ type: 'backend_exit', code: 1 });

  expect(store.getState().hero.mode).toBe('idle');
  expect(store.getState().login.phase).toBe('failed');
});

// The backend explains itself with an error event first, then exits. The exit must not
// overwrite the explanation with a bare exit code.
test('an exit after a reported failure keeps the backend explanation', () => {
  const store = createStore();
  store.setLogin({ phase: 'starting' });
  store.apply({ type: 'error', stage: 'login', message: 'no Xbox profile' });
  store.apply({ type: 'backend_exit', code: 1 });

  expect(store.getState().login).toEqual({ phase: 'failed', message: 'no Xbox profile' });
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

// The hero greets by name, so the name has to reach it. Recording the username without
// rebuilding the hero state would leave the greeting stale until the next event.
test('signing in updates the greeting the hero renders', () => {
  const store = createStore();
  store.apply({ type: 'login_success', username: 'Mal_itIIyr', uuid: 'abc' });

  expect(store.getState().hero).toEqual({
    mode: 'idle', username: 'Mal_itIIyr', canPlay: false,
  });
});

// An unknown event type must be ignored, not crash the renderer: the backend can grow
// new events at any time and an older UI has to survive meeting one.
test('an unrecognised event leaves the state untouched', () => {
  const store = createStore();
  const before = JSON.stringify(store.getState());

  store.apply({ type: 'some_future_event', whatever: 1 });

  expect(JSON.stringify(store.getState())).toBe(before);
});
