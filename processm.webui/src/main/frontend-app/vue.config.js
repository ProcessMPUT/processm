/* eslint-disable @typescript-eslint/no-var-requires */
const path = require("path");
const apiMocker = require("mocker-api");

module.exports = {
  transpileDependencies: ["vuetify"],
  lintOnSave: process.env.NODE_ENV !== "production",
  devServer: {
    port: 8081,
    // Use either the proxy to the actual backend or apiMocker below
    proxy: "http://localhost:2080/",
    // before(app) {
    //   apiMocker(app, path.resolve("./api-mocker/api.js"));
    // },
  },

  pluginOptions: {
    i18n: {
      locale: "en",
      fallbackLocale: "en",
      localeDir: "locales",
      enableInSFC: false,
    },
  },

  configureWebpack: {
    devtool: "source-map",
  },
};
