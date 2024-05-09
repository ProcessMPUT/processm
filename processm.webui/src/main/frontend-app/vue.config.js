/* eslint-disable @typescript-eslint/no-var-requires */
const path = require("path");
const apiMocker = require("mocker-api");

module.exports = {
  transpileDependencies: ["vuetify"],
  lintOnSave: process.env.NODE_ENV !== "production",
  devServer: {
    port: 8081,
    // Use either the proxy to the actual backend or apiMocker below
    proxy: {
      "/api": {
        target: "http://localhost:2080/",
        ws: true,
        changeOrigin: true,
      },
    },
    compress: false,
    // before(app) {
    //   apiMocker(app, path.resolve("./api-mocker/api.js"));
    // },
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
