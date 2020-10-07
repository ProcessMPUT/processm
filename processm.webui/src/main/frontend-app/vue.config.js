/* eslint-disable @typescript-eslint/no-var-requires */
const path = require("path");
const apiMocker = require("mocker-api");

module.exports = {
  transpileDependencies: ["vuetify"],
  lintOnSave: process.env.NODE_ENV !== "production",
  devServer: {
    port: 8081,
    before(app) {
      apiMocker(app, path.resolve("./api-mocker/api.js"));
    },
  },

  pluginOptions: {
    i18n: {
      locale: "en",
      fallbackLocale: "en",
      localeDir: "locales",
      enableInSFC: false,
    },
  },
};
