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
  cancelLogin: () => ipcRenderer.send('cancel-login'),
  openLog: (profileId) => ipcRenderer.send('open-log', profileId),
  copyToClipboard: (text) => ipcRenderer.send('copy-to-clipboard', text),
  // The main process validates the host before handing anything to the OS.
  openExternal: (url) => ipcRenderer.send('open-external', url),
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close: () => ipcRenderer.send('window-close'),
});
