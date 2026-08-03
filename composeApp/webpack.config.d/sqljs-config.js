config.resolve = config.resolve || {};
config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    crypto: false,
    fs: false,
    path: false,
};

config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;

const CopyWebpackPlugin = require("copy-webpack-plugin");
config.plugins = config.plugins || [];
config.plugins.push(
    new CopyWebpackPlugin({
        patterns: [
            { from: "../../node_modules/sql.js/dist/sql-wasm.js", to: "sql-wasm.js" },
            { from: "../../node_modules/sql.js/dist/sql-wasm.wasm", to: "sql-wasm.wasm" },
        ],
    }),
);
