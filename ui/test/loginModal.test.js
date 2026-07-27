/**
 * @jest-environment jsdom
 */
const { renderLoginModal } = require('../renderer/components/loginModal');

let container;
beforeEach(() => {
  document.body.innerHTML = '<div id="modal-root"></div>';
  container = document.getElementById('modal-root');
});

test('null closes the modal', () => {
  renderLoginModal({ phase: 'starting' }, container);
  renderLoginModal(null, container);

  expect(container.children.length).toBe(0);
});

test('starting shows a waiting message and lets the user back out', () => {
  renderLoginModal({ phase: 'starting' }, container);

  expect(container.textContent).toContain('준비');
  expect(container.querySelector('[data-action="cancel-login"]')).not.toBeNull();
});

test('code phase shows the code and both helper actions', () => {
  renderLoginModal(
    { phase: 'code', userCode: 'RHF7XEH4', verificationUri: 'https://www.microsoft.com/link' },
    container
  );

  expect(container.querySelector('.code').textContent).toBe('RHF7XEH4');
  expect(container.querySelector('[data-action="copy-code"]')).not.toBeNull();

  const open = container.querySelector('[data-action="open-verification"]');
  expect(open.dataset.uri).toBe('https://www.microsoft.com/link');
});

test('failure shows the backend message verbatim and offers retry and close', () => {
  const message = 'This Microsoft account has no Xbox profile. Sign in once at https://www.xbox.com';
  renderLoginModal({ phase: 'failed', message }, container);

  // The backend already explains what to fix; rewording it here would lose the fix.
  expect(container.textContent).toContain(message);
  expect(container.querySelector('[data-action="retry-login"]')).not.toBeNull();
  expect(container.querySelector('[data-action="close-login"]')).not.toBeNull();
});

test('each render replaces the previous phase rather than stacking modals', () => {
  renderLoginModal({ phase: 'starting' }, container);
  renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com' },
    container
  );

  expect(container.querySelectorAll('.modal').length).toBe(1);
});

// 다시 시도 re-runs sign-in from the failure screen. The previous failure text must go
// with it — leaving it on screen would read as a second, fresh rejection.
test('retrying after a failure replaces the failure screen', () => {
  renderLoginModal({ phase: 'failed', message: '로그인이 거부됐습니다' }, container);
  renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com' },
    container
  );

  expect(container.querySelectorAll('.modal').length).toBe(1);
  expect(container.textContent).not.toContain('거부');
});

// The token travels on auth_result, which sanitizeForRenderer withholds entirely, so it
// cannot arrive here. This pins the second line of defence: every phase reads named
// fields, so an unexpected field on the state is never drawn — in any phase. Checking
// only one phase would miss a future branch that leaked in another.
test('never renders an access token in any phase', () => {
  const states = [
    { phase: 'starting', accessToken: 'SECRET' },
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com', accessToken: 'SECRET' },
    { phase: 'failed', message: '로그인이 거부됐습니다', accessToken: 'SECRET' },
  ];

  for (const state of states) {
    renderLoginModal(state, container);

    expect(container.textContent).not.toContain('SECRET');
    expect(container.innerHTML).not.toContain('SECRET');
  }
});

test('does not interpret the failure message as HTML', () => {
  renderLoginModal({ phase: 'failed', message: '<img src=x onerror=1>' }, container);

  expect(container.querySelector('img')).toBeNull();
});

// A truncated backend event must not put the word "undefined" on screen where the
// sign-in code belongs. versionMenu and statusBar were both amended for this.
test('a code phase with no code does not print undefined', () => {
  renderLoginModal({ phase: 'code', verificationUri: 'https://example.com' }, container);

  expect(container.textContent).not.toContain('undefined');
});

test('a failure with no message says something rather than printing undefined', () => {
  renderLoginModal({ phase: 'failed' }, container);

  expect(container.textContent).not.toContain('undefined');
  expect(container.querySelector('.failure').textContent.trim()).not.toBe('');
});

// An implicit else meant a typo'd phase told the user their sign-in had failed.
test('an unrecognised phase closes the modal rather than claiming a failure', () => {
  renderLoginModal({ phase: 'starting' }, container);
  renderLoginModal({ phase: 'whoops' }, container);

  expect(container.children.length).toBe(0);
});

test('opening the modal moves focus into the dialog', () => {
  renderLoginModal({ phase: 'starting' }, container);

  expect(document.activeElement).toBe(container.querySelector('.modal'));
});

// Phase changes re-render the whole modal. Focusing unconditionally would drag the
// user back to the dialog container every time the phase advanced.
test('a phase change does not drag focus back to the dialog', () => {
  renderLoginModal({ phase: 'starting' }, container);
  renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com' },
    container
  );

  expect(document.activeElement).not.toBe(container.querySelector('.modal'));
});
