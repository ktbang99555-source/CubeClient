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

// The device code is the one thing the user must read and retype accurately.
test('the code is selectable text, not an image or a background', () => {
  renderLoginModal(
    { phase: 'code', userCode: 'RHF7XEH4', verificationUri: 'https://www.microsoft.com/link' },
    container
  );

  expect(container.querySelector('.code').textContent.trim()).toBe('RHF7XEH4');
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

// The token travels on auth_result, which sanitizeForRenderer withholds. Even if a
// token somehow reached this component, it must not be drawn.
test('never renders an access token even if one is present on the state', () => {
  renderLoginModal(
    { phase: 'code', userCode: 'ABCD1234', verificationUri: 'https://example.com', accessToken: 'SECRET' },
    container
  );

  expect(container.textContent).not.toContain('SECRET');
});

test('does not interpret the failure message as HTML', () => {
  renderLoginModal({ phase: 'failed', message: '<img src=x onerror=1>' }, container);

  expect(container.querySelector('img')).toBeNull();
});
