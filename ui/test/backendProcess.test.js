const { EventEmitter } = require('events');
const { Readable } = require('stream');
const { startBackend } = require('../src/backendProcess');

function makeFakeProcess(stdoutLines) {
  const proc = new EventEmitter();
  proc.stdout = new Readable({ read() {} });
  process.nextTick(() => {
    for (const line of stdoutLines) {
      proc.stdout.push(line + '\n');
    }
    proc.stdout.push(null);
  });
  return proc;
}

// A JVM that dies before reaching our code says why on stderr and writes nothing at all
// to stdout. Discarding stderr left the UI reporting a bare exit code for a problem whose
// cause was sitting right there — the wrong Java version, most often.
function makeFailingProcess(stderrLines, code) {
  const proc = new EventEmitter();
  proc.stdout = new Readable({ read() {} });
  proc.stderr = new Readable({ read() {} });
  process.nextTick(() => {
    proc.stdout.push(null);
    for (const line of stderrLines) proc.stderr.push(line + '\n');
    proc.stderr.push(null);
    setImmediate(() => proc.emit('close', code));
  });
  return proc;
}

// A dropped line used to vanish completely, which made a swallowed `profiles` event look
// exactly like a backend that ran and did nothing — the hardest possible thing to debug.
test('an unparseable stdout line is reported rather than silently dropped', (done) => {
  const fakeProcess = makeFakeProcess(['not json at all', '{"type":"launched"}']);
  const events = [];

  startBackend('/jar', 'list-profiles', [], (event) => {
    events.push(event);
    if (event.type !== 'launched') return;
    expect(events[0]).toEqual({ type: 'backend_noise', line: 'not json at all' });
    done();
  }, () => fakeProcess);
});

test('a failing backend carries its stderr so the cause is not lost', (done) => {
  const fakeProcess = makeFailingProcess(
    ['Error: LinkageError occurred', 'java.lang.UnsupportedClassVersionError: bad version'],
    1
  );

  startBackend('/jar', 'list-profiles', [], (event) => {
    if (event.type !== 'backend_exit') return;
    expect(event.code).toBe(1);
    expect(event.stderr).toContain('UnsupportedClassVersionError');
    done();
  }, () => fakeProcess);
});

test('a clean exit carries no stderr', (done) => {
  const fakeProcess = makeFailingProcess(['some harmless JVM notice'], 0);

  startBackend('/jar', 'list-profiles', [], (event) => {
    if (event.type !== 'backend_exit') return;
    expect(event.code).toBe(0);
    expect(event.stderr).toBeUndefined();
    done();
  }, () => fakeProcess);
});

// A crashing JVM can emit thousands of stack-trace lines; the renderer only ever shows
// the first, and holding the rest is pure memory growth.
test('stderr capture is bounded', (done) => {
  const many = Array.from({ length: 500 }, (_, i) => `line ${i}`);
  const fakeProcess = makeFailingProcess(many, 1);

  startBackend('/jar', 'list-profiles', [], (event) => {
    if (event.type !== 'backend_exit') return;
    expect(event.stderr.split('\n').length).toBeLessThanOrEqual(20);
    expect(event.stderr).toContain('line 0');
    done();
  }, () => fakeProcess);
});

test('parses JSON lines from stdout and forwards them to onEvent', (done) => {
  const fakeProcess = makeFakeProcess([
    '{"type":"progress","stage":"manifest","percent":0}',
    '{"type":"launched"}',
  ]);
  const spawnFn = jest.fn(() => fakeProcess);
  const events = [];

  startBackend(
    '/path/to/backend.jar',
    'launch',
    ['latest-1.21'],
    (event) => {
      events.push(event);
      if (events.length === 2) {
        expect(events[0]).toEqual({ type: 'progress', stage: 'manifest', percent: 0 });
        expect(events[1]).toEqual({ type: 'launched' });
        expect(spawnFn).toHaveBeenCalledWith('java', [
          '-jar',
          '/path/to/backend.jar',
          'launch',
          'latest-1.21',
        ]);
        done();
      }
    },
    spawnFn
  );
});

// A malformed line must not throw or stop the stream — the JSON lines after it still
// have to arrive. It is reported as backend_noise rather than dropped silently, but it
// is deliberately not a fatal event.
test('a non-JSON line does not stop the events after it', (done) => {
  const fakeProcess = makeFakeProcess(['not json', '{"type":"pong"}']);
  const spawnFn = jest.fn(() => fakeProcess);
  const events = [];

  startBackend('/path/to/backend.jar', 'ping', [], (event) => events.push(event), spawnFn);

  setTimeout(() => {
    expect(events).toEqual([
      { type: 'backend_noise', line: 'not json' },
      { type: 'pong' },
    ]);
    done();
  }, 50);
});

// A JSON line arbitrarily longer than the stream's internal chunk size must still be
// delivered as ONE event. The backend emits profile lists this way, and a naive
// split-on-chunk implementation would silently truncate them.
test('reassembles a JSON line split across stream chunks', (done) => {
  const proc = new EventEmitter();
  proc.stdout = new Readable({ read() {} });
  const spawnFn = jest.fn(() => proc);
  const events = [];

  startBackend('/path/to/backend.jar', 'list-profiles', [], (event) => events.push(event), spawnFn);

  process.nextTick(() => {
    proc.stdout.push('{"type":"profiles","profi');
    proc.stdout.push('les":[{"id":"a"}]}\n');
    proc.stdout.push(null);
  });

  setTimeout(() => {
    expect(events).toEqual([{ type: 'profiles', profiles: [{ id: 'a' }] }]);
    done();
  }, 50);
});

// The renderer must be told when the backend dies, or a failed launch leaves the UI
// showing a spinner forever.
test('reports backend exit through onEvent', (done) => {
  const proc = new EventEmitter();
  proc.stdout = new Readable({ read() {} });
  const spawnFn = jest.fn(() => proc);
  const events = [];

  startBackend('/path/to/backend.jar', 'launch', ['x'], (event) => events.push(event), spawnFn);

  process.nextTick(() => {
    proc.stdout.push(null);
    proc.emit('close', 3);
  });

  setTimeout(() => {
    expect(events).toEqual([{ type: 'backend_exit', code: 3 }]);
    done();
  }, 50);
});

// The backend jar targets Java 17, but many machines have an older `java` first on PATH
// (this one has Java 8), which fails with UnsupportedClassVersionError. The caller must be
// able to point at a specific JRE.
test('uses the supplied java command instead of bare java', (done) => {
  const proc = new EventEmitter();
  proc.stdout = new Readable({ read() {} });
  const spawnFn = jest.fn(() => proc);

  startBackend('/path/to/backend.jar', 'ping', [], () => {}, spawnFn, 'C:/jdk17/bin/java.exe');

  process.nextTick(() => {
    expect(spawnFn).toHaveBeenCalledWith('C:/jdk17/bin/java.exe', [
      '-jar',
      '/path/to/backend.jar',
      'ping',
    ]);
    done();
  });
});

// If the jar is missing or `java` is not on PATH, spawn emits 'error'. Without this the
// failure is invisible to the user.
test('reports spawn failure as an error event', (done) => {
  const proc = new EventEmitter();
  proc.stdout = new Readable({ read() {} });
  const spawnFn = jest.fn(() => proc);
  const events = [];

  startBackend('/missing.jar', 'ping', [], (event) => events.push(event), spawnFn);

  process.nextTick(() => proc.emit('error', new Error('spawn java ENOENT')));

  setTimeout(() => {
    expect(events).toEqual([
      { type: 'error', stage: 'backend', message: 'spawn java ENOENT' },
    ]);
    done();
  }, 50);
});
