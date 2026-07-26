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

test('ignores non-JSON lines instead of throwing', (done) => {
  const fakeProcess = makeFakeProcess(['not json', '{"type":"pong"}']);
  const spawnFn = jest.fn(() => fakeProcess);
  const events = [];

  startBackend('/path/to/backend.jar', 'ping', [], (event) => events.push(event), spawnFn);

  setTimeout(() => {
    expect(events).toEqual([{ type: 'pong' }]);
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
