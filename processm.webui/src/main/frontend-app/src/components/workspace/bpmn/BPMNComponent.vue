<template>
  <div></div>
</template>

<script lang="ts">
import { Component } from "vue-property-decorator";
import resize from "vue-resize-directive";
import Vue from "vue";

// eslint-disable-next-line @typescript-eslint/no-var-requires
const BPMNViewer = require("bpmn-js/lib/Viewer");

@Component({
  directives: {
    resize
  }
})
export default class BPMNComponent extends Vue {
  viewer: any;
  mounted() {
    this.viewer = new BPMNViewer({
      container: this.$el
    });
  }
}
</script>

<style scoped></style>
