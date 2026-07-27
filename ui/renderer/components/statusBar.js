/**
 * The status bar carries facts the launcher actually knows: which runtime it
 * provisioned, how the last run ended, and where the log is. The account lives in
 * the title bar instead, so that account and version never share a region.
 */
function renderStatusBar({ javaVersion, lastRun, offline }, container) {
  container.replaceChildren();

  container.append(chip('런타임', javaVersion ? `Java ${javaVersion}` : '—', false));

  let lastRunText = '—';
  let lastRunWarns = false;
  if (lastRun) {
    lastRunWarns = lastRun.code !== 0;
    lastRunText = lastRun.code === 0 ? '정상 종료' : `비정상 종료 (${lastRun.code})`;
  }
  container.append(chip('마지막 실행', lastRunText, lastRunWarns));

  if (offline) {
    container.append(chip('세션', '오프라인 — 서버 접속 불가', true));
  }

  const logChip = document.createElement('div');
  logChip.className = 'chip';
  const key = document.createElement('span');
  key.className = 'k';
  key.textContent = '로그';
  const openLog = document.createElement('button');
  openLog.dataset.action = 'open-log';
  openLog.textContent = '열기';
  logChip.append(key, openLog);
  container.append(logChip);
}

function chip(label, value, warns) {
  const element = document.createElement('div');
  element.className = 'chip';

  const key = document.createElement('span');
  key.className = 'k';
  key.textContent = label;

  const val = document.createElement('span');
  val.className = warns ? 'v warn' : 'v';
  val.textContent = value;

  element.append(key, val);
  return element;
}

function renderAccount({ username }, container) {
  container.replaceChildren();

  const chipButton = document.createElement('button');
  chipButton.className = 'account';
  chipButton.dataset.action = 'account';
  chipButton.dataset.signedIn = String(Boolean(username));
  chipButton.textContent = username || '로그인';
  container.append(chipButton);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderStatusBar, renderAccount };
}
