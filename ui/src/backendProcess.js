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
 * @param {string} [javaCommand]  which JRE to run. The jar targets Java 17, and a machine
 *   whose PATH `java` is older fails with UnsupportedClassVersionError before emitting
 *   anything, so this must be overridable.
 * @param {object} [session]  {username, uuid, accessToken} written to the backend's stdin as
 *   one JSON line. Sent over stdin rather than argv because command-line arguments are visible
 *   to any process that can list the process table, and this carries an access token.
 * @returns {import('child_process').ChildProcess}
 */
function startBackend(
  jarPath,
  subcommand,
  args,
  onEvent,
  spawnFn = defaultSpawn,
  javaCommand = 'java',
  session = null
) {
  const proc = spawnFn(javaCommand, ['-jar', jarPath, subcommand, ...args]);

  if (proc.stdin) {
    if (session) {
      proc.stdin.write(JSON.stringify(session) + '\n');
    }
    // Always close: the backend blocks reading a line, so leaving stdin open would hang a
    // launch that has no session.
    proc.stdin.end();
  }
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
