// SQLDelight's web-worker driver, with the database kept in IndexedDB instead of thrown
// away on every reload.
//
// The stock worker from @cashapp/sqldelight-sqljs-worker does `new SQL.Database()` and
// nothing else, so a browser tab started empty, re-pulled the whole studio, and started
// empty again on the next reload. ADR 0012 downgraded that from data loss to a cold start —
// nothing is only in the browser any more — but the cold start is real: every reload cost a
// full download, the outbox for the shoot-day surfaces could not survive a refresh, and the
// device manufactured a version-1 profile every time its database came up empty.
//
// Written as a *classic* worker rather than a module. The stock one does
// `import initSqlJs from "sql.js"`, which only resolves because webpack bundles it through
// the `new URL(..., import.meta.url)` marker. Depending on that from our own file means
// depending on where webpack decides to put things; `importScripts` of a copied file does
// not. Both sql-wasm.js and sql-wasm.wasm are copied to the served root by
// webApp/webpack.config.d/sqljs.js.

importScripts('/sql-wasm.js');

const IDB_NAME = 'yellowtrack';
const IDB_STORE = 'database';
const IDB_KEY = 'sqlite';

// Long enough that a burst of writes — saving a form touches several tables — exports once
// rather than once per statement, and short enough that closing the tab straight after a
// save rarely loses it. Losing it costs a re-download rather than the work: the server was
// told first, which is the whole of ADR 0012 decision 1.
const SAVE_DELAY_MS = 400;

let db = null;
let saveTimer = null;
let dirty = false;

function openIdb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(IDB_NAME, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(IDB_STORE)) {
        request.result.createObjectStore(IDB_STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function readSaved(idb) {
  return new Promise((resolve) => {
    const request = idb.transaction(IDB_STORE, 'readonly').objectStore(IDB_STORE).get(IDB_KEY);
    request.onsuccess = () => resolve(request.result || null);
    // A read that fails is treated as "nothing stored". Refusing to start because the cache
    // is unreadable would be the wrong trade for a cache.
    request.onerror = () => resolve(null);
  });
}

function writeSaved(idb, bytes) {
  return new Promise((resolve) => {
    const transaction = idb.transaction(IDB_STORE, 'readwrite');
    transaction.objectStore(IDB_STORE).put(bytes, IDB_KEY);
    transaction.oncomplete = () => resolve(true);
    transaction.onerror = () => resolve(false);
    transaction.onabort = () => resolve(false);
  });
}

let idbHandle = null;

async function persistNow() {
  if (!dirty || db === null || idbHandle === null) return;
  dirty = false;
  try {
    await writeSaved(idbHandle, db.export());
  } catch (_) {
    // Storage can be full, or blocked in a private window. The application keeps working
    // from memory exactly as it did before this file existed.
  }
}

function schedulePersist() {
  dirty = true;
  if (saveTimer !== null) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    saveTimer = null;
    persistNow();
  }, SAVE_DELAY_MS);
}

// Reads do not dirty the cache, and exporting the whole database after every SELECT would
// make scrolling a list cost a serialisation. Anything that is not plainly a read is treated
// as a write, including PRAGMA — `user_version` is set that way.
function mutates(sql) {
  return !/^\s*(select|pragma\s+foreign_keys|explain)\b/i.test(sql);
}

async function createDatabase() {
  const SQL = await initSqlJs({ locateFile: () => '/sql-wasm.wasm' });

  try {
    idbHandle = await openIdb();
    const saved = await readSaved(idbHandle);
    db = saved ? new SQL.Database(new Uint8Array(saved)) : new SQL.Database();
  } catch (_) {
    // Private browsing, a blocked origin, or a corrupt store. Fall back to memory, which is
    // what every previous build did unconditionally.
    idbHandle = null;
    db = new SQL.Database();
  }
}

function onModuleReady() {
  const data = this.data;

  switch (data && data.action) {
    case 'exec': {
      if (!data['sql']) {
        throw new Error('exec: Missing query string');
      }
      const results = db.exec(data.sql, data.params)[0] ?? { values: [] };
      if (mutates(data.sql)) schedulePersist();
      return postMessage({ id: data.id, results });
    }
    case 'begin_transaction':
      return postMessage({ id: data.id, results: db.exec('BEGIN TRANSACTION;') });
    case 'end_transaction': {
      const results = db.exec('END TRANSACTION;');
      // A committed transaction is the one moment worth being sure about, so this does not
      // wait for the debounce to decide it is idle.
      schedulePersist();
      return postMessage({ id: data.id, results });
    }
    case 'rollback_transaction':
      return postMessage({ id: data.id, results: db.exec('ROLLBACK TRANSACTION;') });
    default:
      throw new Error(`Unsupported action: ${data && data.action}`);
  }
}

function onError(err) {
  return postMessage({ id: this.data.id, error: err });
}

const sqlModuleReady = createDatabase();

self.onmessage = (event) =>
  sqlModuleReady.then(onModuleReady.bind(event)).catch(onError.bind(event));
