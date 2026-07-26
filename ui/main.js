const { app, BrowserWindow, ipcMain, safeStorage, shell } = require('electron');
const path = require('path');
const { startBackend } = require('./src/backendProcess');
const { AuthStore } = require('./src/authStore');
const { sanitizeForRenderer } = require('./src/rendererEvents');

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
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      // The renderer handles profile data from a user-editable file; keep it out of Node.
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  const authStore = new AuthStore(path.join(appDataDir(), 'auth.json'), safeStorage);

  // Every backend event passes through sanitizeForRenderer, which withholds the access token.
  // The renderer must never receive a credential.
  const send = (event) => {
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
    startBackend(JAR_PATH, 'list-profiles', [], send, undefined, javaCommand);
  });

  ipcMain.on('launch-profile', (_event, profileId) => {
    if (typeof profileId !== 'string') return;
    // Decrypted here and handed to the backend over stdin; it is never sent to the renderer.
    const session = authStore.load();
    startBackend(JAR_PATH, 'launch', [profileId], send, undefined, javaCommand, session);
  });

  ipcMain.on('start-login', () => {
    startBackend(JAR_PATH, 'login', [], send, undefined, javaCommand);
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
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
