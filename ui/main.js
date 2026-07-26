const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const { startBackend } = require('./src/backendProcess');

const JAR_PATH = path.join(
  __dirname,
  '..',
  'backend',
  'build',
  'libs',
  'cubeclient-launcher-backend.jar'
);

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

  const send = (event) => {
    if (!win.isDestroyed()) {
      win.webContents.send('backend-event', event);
    }
  };

  // The backend jar targets Java 17. Bare `java` is whatever is first on PATH, which on many
  // machines is an older JRE that dies with UnsupportedClassVersionError before printing a
  // single event. Until the launcher provisions its own runtime (out of scope for now), let
  // the JRE be pointed at explicitly.
  const javaCommand = process.env.CUBECLIENT_JAVA || 'java';

  // Wait for the page to be ready, or the first events are sent into a renderer that has no
  // listener yet and the profile list silently never appears.
  win.webContents.once('did-finish-load', () => {
    startBackend(JAR_PATH, 'list-profiles', [], send, undefined, javaCommand);
  });

  ipcMain.on('launch-profile', (_event, profileId) => {
    if (typeof profileId !== 'string') return;
    startBackend(JAR_PATH, 'launch', [profileId], send, undefined, javaCommand);
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
