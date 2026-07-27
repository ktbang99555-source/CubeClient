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
  container.replaceChildren();
  if (!state) return;

  const backdrop = document.createElement('div');
  backdrop.className = 'backdrop';

  const modal = document.createElement('div');
  modal.className = 'modal';
  modal.setAttribute('role', 'dialog');
  modal.setAttribute('aria-modal', 'true');

  if (state.phase === 'starting') {
    modal.append(
      heading('로그인 준비 중…'),
      paragraph('잠시만 기다려 주세요.'),
      row([button('취소', 'cancel-login')])
    );
  } else if (state.phase === 'code') {
    const code = document.createElement('div');
    code.className = 'code';
    code.textContent = state.userCode;

    const openButton = button('브라우저에서 열기', 'open-verification', true);
    openButton.dataset.uri = state.verificationUri;

    modal.append(
      heading('Microsoft 계정으로 로그인'),
      paragraph('브라우저에서 아래 코드를 입력하세요. 완료될 때까지 기다립니다.'),
      code,
      row([openButton, button('코드 복사', 'copy-code')]),
      row([button('취소', 'cancel-login')])
    );
  } else {
    const failure = document.createElement('p');
    failure.className = 'failure';
    failure.textContent = state.message;

    modal.append(
      heading('로그인하지 못했습니다'),
      failure,
      row([button('다시 시도', 'retry-login', true), button('닫기', 'close-login')])
    );
  }

  backdrop.append(modal);
  container.append(backdrop);
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
