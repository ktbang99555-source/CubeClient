const fs = require('fs');
const os = require('os');
const path = require('path');
const { AuthStore } = require('../src/authStore');

/** Stands in for Electron's safeStorage with a reversible, obviously-not-real transform. */
function fakeSafeStorage({ available = true } = {}) {
  return {
    isEncryptionAvailable: () => available,
    encryptString: (plain) => Buffer.from('ENC:' + plain, 'utf8'),
    decryptString: (buf) => {
      const s = buf.toString('utf8');
      if (!s.startsWith('ENC:')) throw new Error('cannot decrypt');
      return s.slice(4);
    },
  };
}

let dir;
let authPath;

beforeEach(() => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cubeclient-auth-'));
  authPath = path.join(dir, 'auth.json');
});

afterEach(() => {
  fs.rmSync(dir, { recursive: true, force: true });
});

test('round-trips the session', () => {
  const store = new AuthStore(authPath, fakeSafeStorage());

  store.save({ username: 'Steve', uuid: 'abc123', accessToken: 'MC_TOKEN' });

  expect(store.load()).toEqual({ username: 'Steve', uuid: 'abc123', accessToken: 'MC_TOKEN' });
});

// The entire point of choosing safeStorage over a plain file.
test('never writes the raw access token to disk', () => {
  const store = new AuthStore(authPath, fakeSafeStorage());

  store.save({ username: 'Steve', uuid: 'abc123', accessToken: 'MC_TOKEN' });

  const onDisk = fs.readFileSync(authPath, 'utf8');
  expect(onDisk).not.toContain('MC_TOKEN');
  expect(JSON.parse(onDisk).accessToken).toBeUndefined();
});

test('refuses to save when the OS has no encryption backend', () => {
  const store = new AuthStore(authPath, fakeSafeStorage({ available: false }));

  expect(() => store.save({ username: 'Steve', uuid: 'abc', accessToken: 'MC_TOKEN' })).toThrow(
    /unavailable/i
  );
  expect(fs.existsSync(authPath)).toBe(false);
});

test('returns null when nothing has been stored', () => {
  expect(new AuthStore(authPath, fakeSafeStorage()).load()).toBeNull();
});

// Ciphertext written by a different OS user or machine cannot be decrypted; that must read as
// "not logged in" rather than crashing the launcher on startup.
test('returns null when the stored ciphertext cannot be decrypted', () => {
  fs.writeFileSync(
    authPath,
    JSON.stringify({
      username: 'Steve',
      uuid: 'abc',
      accessTokenEncrypted: Buffer.from('GARBAGE', 'utf8').toString('base64'),
    })
  );

  expect(new AuthStore(authPath, fakeSafeStorage()).load()).toBeNull();
});

test('returns null when auth.json is corrupt', () => {
  fs.writeFileSync(authPath, '{ not json');

  expect(new AuthStore(authPath, fakeSafeStorage()).load()).toBeNull();
});

test('clear removes the stored session', () => {
  const store = new AuthStore(authPath, fakeSafeStorage());
  store.save({ username: 'Steve', uuid: 'abc', accessToken: 'MC_TOKEN' });

  store.clear();

  expect(store.load()).toBeNull();
});
