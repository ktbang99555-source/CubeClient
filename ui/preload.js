const { contextBridge, ipcRenderer } = require('electron');

// The only surface the renderer gets. It can receive (already sanitized) backend events and
// ask the main process to act — it cannot spawn processes, read auth.json, or touch the
// filesystem itself, and it never receives an access token.
contextBridge.exposeInMainWorld('cubeclient', {
  onBackendEvent: (callback) => {
    ipcRenderer.on('backend-event', (_event, data) => callback(data));
  },
  launchProfile: (profileId) => ipcRenderer.send('launch-profile', profileId),
  startLogin: () => ipcRenderer.send('start-login'),
  openLog: (profileId) => ipcRenderer.send('open-log', profileId),
});
