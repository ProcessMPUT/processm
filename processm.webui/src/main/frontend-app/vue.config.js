/* eslint-disable @typescript-eslint/no-var-requires */
const path = require("path");

module.exports = {
  transpileDependencies: ["vuetify"],
  lintOnSave: process.env.NODE_ENV !== "production",
  devServer: {
    port: 8081,
    proxy: {
      "/api": {
        target: "http://localhost:2080/",
        ws: true,
        changeOrigin: true,
      },
    },
    compress: false,
  },

  pluginOptions: {
    i18n: {
      locale: "en-US",
      fallbackLocale: "en-US",
      localeDir: "locales",
      enableInSFC: false,
    },
  },

  configureWebpack: {
    cache: { type: "filesystem" },
    // https://webpack.js.org/configuration/devtool/
    devtool: process.env.NODE_ENV === "development" ? "source-map" : undefined,
    parallelism: 128,
    performance: {
      hints: "warning",
    },
    resolve: {
      fallback: {
        stream: require.resolve("stream-browserify"),
      },
    },
  },
};
