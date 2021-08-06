import Vue from "vue";

// Inspired by https://github.com/vuejs/vue/issues/9200
export async function waitForRepaint(fn: Function) {
  return new Promise((resolve, reject) => {
    Vue.nextTick(() => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          resolve(fn());
        });
      });
    });
  });
}
