/**
 * The version switcher. Deliberately not called a "profile" switcher anywhere the
 * user can see: that word reads as "account", and account switching lives in the
 * title bar instead. The two concepts never share a screen region.
 */
const LOADER_LABELS = { vanilla: '바닐라', fabric: 'Fabric' };

function formatVersionLabel(profile) {
  const loader = LOADER_LABELS[profile.loader] || profile.loader;
  return `${profile.mcVersion} · ${loader}`;
}

function renderVersionMenu({ profiles, selectedId, open }, container) {
  container.replaceChildren();

  const wrapper = document.createElement('div');
  wrapper.className = 'version-menu';

  const selected = profiles.find((p) => p.id === selectedId);
  const trigger = document.createElement('button');
  trigger.className = 'version-trigger';
  trigger.dataset.action = 'toggle-versions';
  trigger.setAttribute('aria-expanded', String(open));
  trigger.append(selected ? formatVersionLabel(selected) : '버전 선택', ' ▾');
  wrapper.append(trigger);

  if (open) {
    const list = document.createElement('div');
    list.className = 'version-list';

    for (const profile of profiles) {
      const option = document.createElement('button');
      option.dataset.action = 'select-version';
      option.dataset.profileId = profile.id;
      option.textContent = formatVersionLabel(profile);
      if (profile.id === selectedId) {
        option.setAttribute('aria-current', 'true');
      }
      list.append(option);
    }

    // Kept visible so the absence of the feature is obvious, disabled so it cannot
    // be pressed for nothing.
    const add = document.createElement('button');
    add.disabled = true;
    add.textContent = '+ 버전 추가 (준비 중)';
    list.append(add);

    wrapper.append(list);
  }

  container.append(wrapper);
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { renderVersionMenu, formatVersionLabel };
}
