// Exercises yellowtrack-sqljs.worker.js in Node, with stand-ins for importScripts,
// indexedDB and postMessage.
//
// Not wired into Gradle: there is no JS test harness in this build and adding one for a
// single file is more machinery than it earns. It is here because the worker cannot be
// checked any other way short of a signed-in browser — the web app does not touch its
// database until somebody signs in, so loading the page headlessly proves nothing.
//
// Run it with the Node that Gradle already downloaded:
//
//     $(ls -d ~/.gradle/nodejs/node-*/bin)/node \
//       webApp/tools/worker-persistence-check.js \
//       webApp/src/wasmJsMain/resources/yellowtrack-sqljs.worker.js
//
// Expected:
//     after writes, stored bytes: 8192
//     restored row: [["kept"]]
//     unchanged after a read: true

// postMessage, so the persistence logic can be exercised without a browser.
const fs = require('fs');
const path = require('path');
const repo = path.resolve(__dirname, '..', '..');
const initSqlJsReal = require(path.join(repo, 'build/wasm/node_modules/sql.js'));

const store = new Map();               // stands in for the IndexedDB object store
let posted = [];

global.importScripts = () => {};
global.initSqlJs = (opts) => initSqlJsReal({
  locateFile: () => path.join(repo, 'build/wasm/node_modules/sql.js/dist/sql-wasm.wasm'),
});
global.postMessage = (m) => posted.push(m);

function fakeRequest(result) {
  const r = { result, onsuccess: null, onerror: null, onupgradeneeded: null };
  setTimeout(() => r.onsuccess && r.onsuccess(), 0);
  return r;
}

global.indexedDB = {
  open() {
    const db = {
      objectStoreNames: { contains: () => true },
      createObjectStore: () => {},
      transaction() {
        const tx = { oncomplete: null, onerror: null, onabort: null,
          objectStore: () => ({
            get: (k) => fakeRequest(store.get(k) || undefined),
            put: (v, k) => { store.set(k, v); setTimeout(() => tx.oncomplete && tx.oncomplete(), 0); return fakeRequest(true); },
          }) };
        return tx;
      },
    };
    return fakeRequest(db);
  },
};

const src = fs.readFileSync(process.argv[2], 'utf8');

// Each "load" gets its own `self`, the way a fresh worker would. The previous harness
// declared one `self` in this file's scope, so the second load's onmessage was written to
// an object nothing read back — a bug in the harness, not the worker.
function load() {
  const container = {};
  new Function('self', src)(container);
  return (data) => new Promise((resolve) => {
    posted = [];
    Promise.resolve(container.onmessage({ data })).then(() => resolve(posted[0]));
  });
}

const send = load();

(async () => {
  // 1. First load: empty store, create a table and insert a row.
  await send({ id: 1, action: 'exec', sql: 'CREATE TABLE t (a TEXT);' });
  await send({ id: 2, action: 'exec', sql: "INSERT INTO t VALUES ('kept');" });
  await new Promise((r) => setTimeout(r, 900));            // let the debounce fire
  console.log('after writes, stored bytes:', store.get('sqlite') ? store.get('sqlite').length : 0);

  // 2. Second load: a fresh worker over the same store must see the row.
  const send2 = load();
  await new Promise((r) => setTimeout(r, 300));
  const read = await send2({ id: 3, action: 'exec', sql: 'SELECT a FROM t;' });
  console.log('restored row:', JSON.stringify(read.results.values));

  // 3. A read must not schedule a save.
  const before = store.get('sqlite').length;
  await send2({ id: 4, action: 'exec', sql: 'SELECT a FROM t;' });
  await new Promise((r) => setTimeout(r, 900));
  console.log('unchanged after a read:', store.get('sqlite').length === before);
})();
