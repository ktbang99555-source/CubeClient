const readline = require('readline');
const { spawn: defaultSpawn } = require('child_process');

/**
 * Spawns the Java backend for one subcommand and streams its stdout JSON lines to `onEvent`.
 *
 * The backend's only channel to this process is newline-delimited JSON on stdout, so this
 * function is the single place that contract is decoded. `readline` is used rather than
 * splitting raw chunks because a chunk boundary can fall in the middle of a line.
 *
 * @param {string} jarPath   path to the backend jar
 * @param {string} subcommand  e.g. 'list-profiles', 'launch', 'login'
 * @param {string[]} args    subcommand arguments
 * @param {(event: object) => void} onEvent  receives each decoded event
 * @param {Function} [spawnFn]  injected for tests
 * @returns {import('child_process').ChildProcess}
 */
function startBackend(jarPath, subcommand, args, onEvent, spawnFn = defaultSpawn) {
  const proc = spawnFn('java', ['-jar', jarPath, subcommand, ...args]);
  const rl = readline.createInterface({ input: proc.stdout });

  rl.on('line', (line) => {
    let event;
    try {
      event = JSON.parse(line);
    } catch (err) {
      // The backend also writes non-JSON noise (JVM warnings, stack traces) to this stream.
      // Dropping it is deliberate: one malformed line must not take down the launcher.
      return;
    }
    onEvent(event);
  });

  // Without these the UI has no way to distinguish "still working" from "died", and a
  // failed launch would leave the renderer showing progress forever.
  proc.on('error', (err) => {
    onEvent({ type: 'error', stage: 'backend', message: err.message });
  });

  proc.on('close', (code) => {
    onEvent({ type: 'backend_exit', code });
  });

  return proc;
}

module.exports = { startBackend };
