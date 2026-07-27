const { app, BrowserWindow, ipcMain, safeStorage, shell, clipboard } = require('electron');
const path = require('path');
const fs = require('fs');
const { startBackend } = require('./src/backendProcess');
const { AuthStore } = require('./src/authStore');
const { sanitizeForRenderer } = require('./src/rendererEvents');
const { isAllowedExternalUrl } = require('./src/externalUrl');

const JAR_PATH = path.join(
  __dirname,
  '..',
  'backend',
  'build',
  'libs',
  'cubeclient-launcher-backend.jar'
);

function appDataDir() {
  const base = process.env.APPDATA || app.getPath('userData');
  return path.join(base, 'CubeClient');
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1000,
    height: 650,
    minWidth: 860,
    minHeight: 560,
    // The title bar is drawn in the renderer so it can carry the account chip and match
    // the rest of the shell.
    frame: false,
    backgroundColor: '#0f1216',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      // The renderer handles profile data from a user-editable file; keep it out of Node.
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  // Set CUBECLIENT_DEBUG=1 to mirror the renderer's console and the backend's event
  // stream into this terminal. The renderer is a separate process, so without this its
  // errors are invisible unless DevTools happens to be open — which made a "nothing
  // happens" report impossible to diagnose remotely.
  const debug = Boolean(process.env.CUBECLIENT_DEBUG);
  if (debug) {
    win.webContents.on('console-message', (_e, _level, message) => {
      console.error('[renderer]', message);
    });
    win.webContents.on('preload-error', (_e, preloadPath, error) => {
      console.error('[preload-error]', preloadPath, error && error.message);
    });
  }

  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  const authStore = new AuthStore(path.join(appDataDir(), 'auth.json'), safeStorage);

  // Every backend event passes through sanitizeForRenderer, which withholds the access token.
  // The renderer must never receive a credential.
  const send = (event) => {
    // Only the type: the auth_result event carries a token, and this goes to a terminal.
    if (debug) console.error('[backend event]', event.type);
    // The exception: an unparseable line is only useful if you can see it.
    if (debug && event.type === 'backend_noise') console.error('[backend noise]', event.line);

    if (event.type === 'auth_result') {
      try {
        authStore.save(event);
      } catch (err) {
        if (!win.isDestroyed()) {
          win.webContents.send('backend-event', {
            type: 'error',
            stage: 'login',
            message: err.message,
          });
        }
        return;
      }
    }

    const forwarded = sanitizeForRenderer(event);
    if (forwarded && !win.isDestroyed()) {
      win.webContents.send('backend-event', forwarded);
    }
  };

  // The backend provisions the game's JRE itself, but running the backend jar needs a Java 17
  // before it can do that — a bootstrap the launcher cannot solve until it ships its own
  // runtime. CUBECLIENT_JAVA points at one in the meantime.
  const javaCommand = process.env.CUBECLIENT_JAVA || 'java';

  // Wait for the page to be ready, or the first events are sent into a renderer that has no
  // listener yet and the profile list silently never appears.
  win.webContents.once('did-finish-load', () => {
    if (debug) {
      console.error('[main] page loaded; java =', javaCommand);
      console.error('[main] jar =', JAR_PATH, fs.existsSync(JAR_PATH) ? '(found)' : '(MISSING)');
    }
    startBackend(JAR_PATH, 'list-profiles', [], send, undefined, javaCommand);
  });

  ipcMain.on('launch-profile', (_event, profileId) => {
    if (typeof profileId !== 'string') return;
    // Decrypted here and handed to the backend over stdin; it is never sent to the renderer.
    const session = authStore.load();
    startBackend(JAR_PATH, 'launch', [profileId], send, undefined, javaCommand, session);
  });

  // Tracked so the modal's 취소 button (and Escape) can actually stop the sign-in rather
  // than leaving a backend polling Microsoft for fifteen minutes with nobody watching.
  let loginProcess = null;

  ipcMain.on('start-login', () => {
    if (loginProcess) return;
    loginProcess = startBackend(JAR_PATH, 'login', [], (event) => {
      if (event.type === 'backend_exit') loginProcess = null;
      send(event);
    }, undefined, javaCommand);
  });

  ipcMain.on('cancel-login', () => {
    if (!loginProcess) return;
    loginProcess.kill();
    loginProcess = null;
  });

  ipcMain.on('open-log', (_event, profileId) => {
    if (typeof profileId !== 'string') return;
    // Confine the path to the instances tree: profileId comes from the renderer, and a value
    // like "../.." would otherwise open an arbitrary file.
    const instancesDir = path.join(appDataDir(), 'instances');
    const logPath = path.join(instancesDir, profileId, 'logs', 'latest.log');
    if (!path.resolve(logPath).startsWith(path.resolve(instancesDir) + path.sep)) return;
    shell.openPath(logPath);
  });

  ipcMain.on('window-minimize', () => win.minimize());
  ipcMain.on('window-maximize', () => {
    if (win.isMaximized()) win.unmaximize();
    else win.maximize();
  });
  ipcMain.on('window-close', () => win.close());

  ipcMain.on('copy-to-clipboard', (_event, text) => {
    if (typeof text === 'string') clipboard.writeText(text);
  });

  ipcMain.on('open-external', (_event, url) => {
    // Only Microsoft's device-login page is ever opened from here. Without this check the
    // renderer could hand the OS any URL — including a file: URL, which on Windows runs a
    // program. The allowlist lives in its own module so it can be unit-tested.
    if (!isAllowedExternalUrl(url)) return;
    shell.openExternal(url);
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
