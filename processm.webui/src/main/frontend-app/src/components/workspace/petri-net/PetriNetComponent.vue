<template>
  <div
    v-resize:debounce.10="onResize"
    class="svg-container"
    v-if="data != null"
  >
    <petri-net-editor
      :debug="false"
      :run-layouter-on-start="true"
      :show-buttons="showButtons"
    ></petri-net-editor>
    <div class="node-details" />
  </div>
  <p v-else>{{ $t("workspace.component.no-data") }}</p>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";
import { PetriNetComponentData } from "@/models/WorkspaceComponent";
import resize from "vue-resize-directive";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";
import PetriNetEditor from "@/components/petri-net-editor/PetriNetEditor.vue";

@Component({
  components: { PetriNetEditor },
  directives: {
    resize
  }
})
export default class PetriNetComponent extends Vue {
  private showButtons = false;

  @Prop({ default: {} })
  readonly data!: { data: PetriNetComponentData };

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  mounted() {
    console.log(this.data);
  }

  private onResize(element: Element) {
    // const scalingFactor = this.calculateScalingFactor(
    //     element.clientWidth,
    //     element.clientHeight
    // );
    //
    // this.scaleElements(scalingFactor);
  }
}
</script>

<style scoped>

</style>