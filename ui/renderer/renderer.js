/**
 * Pure DOM rendering for the launcher window.
 *
 * These functions take data and a container and nothing else — no Electron, no IPC — so they
 * can be tested under jsdom. The wiring that feeds them lives at the bottom of this file and
 * only activates inside a real Electron renderer.
 *
 * Everything is built with createElement/textContent rather than innerHTML: profile fields come
 * from a user-editable profiles.json, and string-built markup would let a crafted value inject
 * nodes into the window.
 */

function renderProfiles(profiles, container) {
  container.replaceChildren();

  for (const profile of profiles) {
    const card = document.createElement('div');
    card.className = 'profile-card';
    card.dataset.profileId = profile.id;

    const title = document.createElement('div');
    title.textContent = `${profile.mcVersion} (${profile.loader})`;
    card.appendChild(title);

    const playButton = document.createElement('button');
    playButton.textContent = 'Play';
    card.appendChild(playButton);

    container.appendChild(card);
  }
}

function renderProgress(stage, percent, container) {
  container.replaceChildren();

  const label = document.createElement('div');
  label.textContent = stage;
  container.appendChild(label);

  const progressEl = document.createElement('progress');
  progressEl.max = 100;
  progressEl.value = percent;
  container.appendChild(progressEl);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderProfiles, renderProgress };
}

// Electron-only wiring. `window.cubeclient` is exposed by preload.js; it is absent under jsdom,
// so this block never runs during tests.
if (typeof window !== 'undefined' && window.cubeclient) {
  const app = document.getElementById('app');
  const profilesContainer = document.createElement('div');
  const progressContainer = document.createElement('div');
  app.appendChild(profilesContainer);
  app.appendChild(progressContainer);

  const showMessage = (text) => {
    progressContainer.replaceChildren();
    const message = document.createElement('div');
    message.textContent = text;
    progressContainer.appendChild(message);
  };

  profilesContainer.addEventListener('click', (domEvent) => {
    if (domEvent.target.tagName !== 'BUTTON') return;
    const card = domEvent.target.closest('.profile-card');
    if (card) {
      window.cubeclient.launchProfile(card.dataset.profileId);
    }
  });

  window.cubeclient.onBackendEvent((event) => {
    if (event.type === 'profiles') {
      renderProfiles(event.profiles, profilesContainer);
    } else if (event.type === 'progress') {
      renderProgress(event.stage, event.percent, progressContainer);
    } else if (event.type === 'error') {
      showMessage(`Error (${event.stage}): ${event.message}`);
    } else if (event.type === 'backend_exit' && event.code !== 0) {
      showMessage(`Backend exited with code ${event.code}`);
    }
  });
}
