<template>
  <div ref="container" class="vue-bpmn-diagram-container"></div>
</template>

<style scoped>
.vue-bpmn-diagram-container {
  position: relative;
  height: 100%;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop } from "vue-property-decorator";
import App from "@/App.vue";
import BpmnJS from "bpmn-js";

@Component
export default class BPMNComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: { xml: string } };
  options = {
    diagramXML: this.data.data.xml,
    propertiesPanel: {},
    additionalModules: [],
    moddleExtensions: []
  };
  @Inject()
  private app!: App;
  private bpmnViewer?: BpmnJS;

  mounted() {
    const container = this.$refs.container;

    const _options = Object.assign({ container: container }, this.options);
    this.bpmnViewer = new BpmnJS(_options);
    this.bpmnViewer.importXML(_options.diagramXML);

    this.bpmnViewer.on("import.done", (event: any) => {
      console.log("import.done");

      const error = event.error;
      const warnings = event.warnings;

      if (error) {
        this.app.error(error);
      } else {
        this.app.warn(warnings);
      }

      this.bpmnViewer?.get("canvas").zoom("fit-viewport");
    });
  }

  beforeDestroy() {
    this.bpmnViewer?.destroy();
  }
}
</script>
