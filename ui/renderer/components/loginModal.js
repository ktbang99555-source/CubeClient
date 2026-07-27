/**
 * The device-code sign-in modal.
 *
 * It renders only what the sanitizer lets through — user code, verification URI,
 * and failure text. The access token travels on the `auth_result` event, which
 * `sanitizeForRenderer` withholds from the renderer entirely, so it never arrives
 * here. This component reads named fields rather than spreading state, so an
 * unexpected field cannot be drawn by accident.
 */
function renderLoginModal(state, container) {
  // Captured before clearing: this is how we tell "the modal just opened" (move
  // focus in) apart from "the modal is re-rendering for a phase change" (leave
  // focus wherever the user had tabbed to).
  const wasClosed = container.children.length === 0;

  container.replaceChildren();
  if (!state) return;

  const backdrop = document.createElement('div');
  backdrop.className = 'backdrop';

  const modal = document.createElement('div');
  modal.className = 'modal';
  modal.setAttribute('role', 'dialog');
  modal.setAttribute('aria-modal', 'true');
  // Programmatically focusable so it can receive focus on open, without adding it
  // to the normal tab order.
  modal.tabIndex = -1;

  if (state.phase === 'starting') {
    modal.append(
      heading('로그인 준비 중…'),
      paragraph('잠시만 기다려 주세요.'),
      row([button('취소', 'cancel-login')])
    );
  } else if (state.phase === 'code') {
    const code = document.createElement('div');
    code.className = 'code';
    code.textContent = state.userCode || '';

    const openButton = button('브라우저에서 열기', 'open-verification', true);
    openButton.dataset.uri = state.verificationUri || '';

    modal.append(
      heading('Microsoft 계정으로 로그인'),
      paragraph('브라우저에서 아래 코드를 입력하세요. 완료될 때까지 기다립니다.'),
      code,
      row([openButton, button('코드 복사', 'copy-code')]),
      row([button('취소', 'cancel-login')])
    );
  } else if (state.phase === 'failed') {
    const failure = document.createElement('p');
    failure.className = 'failure';
    failure.textContent = state.message || '알 수 없는 이유로 로그인하지 못했습니다.';

    modal.append(
      heading('로그인하지 못했습니다'),
      failure,
      row([button('다시 시도', 'retry-login', true), button('닫기', 'close-login')])
    );
  } else {
    // An unrecognised phase (a typo'd string, a renamed phase after a backend change)
    // must not fall through to the failure screen — that would tell the user their
    // sign-in failed when nothing of the kind happened. Leave the container empty
    // instead; it was already cleared at the top of this function.
    return;
  }

  backdrop.append(modal);
  container.append(backdrop);

  // Only steal focus when the modal is opening, not on every re-render — otherwise
  // a phase change (e.g. starting -> code) would yank focus back from wherever the
  // user had already tabbed to.
  if (wasClosed) modal.focus();
}

function heading(text) {
  const element = document.createElement('h2');
  element.textContent = text;
  return element;
}

function paragraph(text) {
  const element = document.createElement('p');
  element.textContent = text;
  return element;
}

function row(children) {
  const element = document.createElement('div');
  element.className = 'row';
  element.append(...children);
  return element;
}

function button(label, action, primary = false) {
  const element = document.createElement('button');
  if (primary) element.className = 'primary';
  element.dataset.action = action;
  element.textContent = label;
  return element;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderLoginModal };
}
