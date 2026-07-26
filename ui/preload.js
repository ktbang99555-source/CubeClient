const { contextBridge, ipcRenderer } = require('electron');

// The only surface the renderer gets. It can receive backend events and ask to launch a
// profile by id — it cannot spawn processes or touch the filesystem itself.
contextBridge.exposeInMainWorld('cubeclient', {
  onBackendEvent: (callback) => {
    ipcRenderer.on('backend-event', (_event, data) => callback(data));
  },
  launchProfile: (profileId) => ipcRenderer.send('launch-profile', profileId),
});
