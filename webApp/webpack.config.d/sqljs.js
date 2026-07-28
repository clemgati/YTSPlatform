// SQLDelight's web-worker driver runs sql.js. The sql.js Emscripten glue references a
// few Node core modules that don't exist in the browser, so stub them out. It also
// fetches its wasm binary from the absolute path "/sql-wasm.wasm" at runtime (see the
// worker's locateFile call), so copy that binary from node_modules to the served root.
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    fs: false,
    path: false,
    crypto: false,
});

const CopyWebpackPlugin = require('copy-webpack-plugin');
config.plugins.push(
    new CopyWebpackPlugin({
        patterns: [
            { from: '../../node_modules/sql.js/dist/sql-wasm.wasm', to: 'sql-wasm.wasm' },
        ],
    })
);
