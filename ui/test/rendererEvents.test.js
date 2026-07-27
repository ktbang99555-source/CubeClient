const { sanitizeForRenderer } = require('../src/rendererEvents');

// backend_noise carries a raw, unvalidated line straight off the backend's stdout. It
// exists so the main process can log it while debugging; the renderer has no business
// receiving it, and the allowlist is what keeps it out.
test('the raw noise event is not forwarded to the renderer', () => {
  expect(sanitizeForRenderer({ type: 'backend_noise', line: 'whatever' })).toBeNull();
});

// The single most important guarantee in this file: a credential must never reach the
// renderer, which is the process that handles untrusted profile data and web content.
test('strips the access token from auth_result', () => {
  const forwarded = sanitizeForRenderer({
    type: 'auth_result',
    username: 'Steve',
    uuid: 'abc123',
    accessToken: 'MC_TOKEN',
  });

  expect(JSON.stringify(forwarded)).not.toContain('MC_TOKEN');
  expect(forwarded).toEqual({ type: 'login_success', username: 'Steve', uuid: 'abc123' });
});

test('forwards ordinary UI events unchanged', () => {
  const progress = { type: 'progress', stage: 'assets', percent: 72 };
  expect(sanitizeForRenderer(progress)).toEqual(progress);

  const profiles = { type: 'profiles', profiles: [{ id: 'a' }] };
  expect(sanitizeForRenderer(profiles)).toEqual(profiles);
});

// Allowlist, not denylist: an event type nobody has vetted stays out by default.
test('withholds unknown event types', () => {
  expect(sanitizeForRenderer({ type: 'some_future_event', secret: 'x' })).toBeNull();
});

test('withholds malformed events', () => {
  expect(sanitizeForRenderer(null)).toBeNull();
  expect(sanitizeForRenderer({})).toBeNull();
  expect(sanitizeForRenderer({ type: 42 })).toBeNull();
});

// Defence in depth. The type allowlist is the primary control, but if a credential is ever
// attached to an allowlisted event, it must still be stripped rather than forwarded.
test('strips credential-shaped fields even from allowlisted events', () => {
  const suspicious = ['accessToken', 'access_token', 'token', 'refreshToken', 'password', 'secret'];

  for (const field of suspicious) {
    const forwarded = sanitizeForRenderer({
      type: 'progress',
      stage: 'assets',
      percent: 1,
      [field]: 'LEAKED',
    });

    expect(JSON.stringify(forwarded)).not.toContain('LEAKED');
    expect(forwarded).toEqual({ type: 'progress', stage: 'assets', percent: 1 });
  }
});
