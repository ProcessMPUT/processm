<template xmlns:v-slot="http://www.w3.org/1999/html">
  <table>
    <tr>
      <td>
        <graph :key="version" :data="data.data" :filter-edge="filterEdge" :refresh="support"></graph>
      </td>
      <td v-show="(componentMode != ComponentMode.Static) && (isFinite(support))">
        <v-card>
          <v-card-title>{{ $t("common.filter") }}</v-card-title>
          <v-card-text>
            <div>
              {{ $t("workspace.component.dfg.support") }}
            </div>
            <v-slider v-model="support" :max="maxSupport" :min="minSupport" step="1" thumb-label="always" vertical></v-slider>
          </v-card-text>
        </v-card>
      </td>
    </tr>
  </table>
</template>

<style scoped>
table {
  width: 100%;
  height: 100%;
}

table td:last-child {
  width: 100px;
  text-align: center;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";
import { DirectlyFollowsGraphComponentData } from "@/openapi";
import Graph from "@/components/Graph.vue";
import { EdgeConfig } from "@antv/g6-core/lib/types";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";

@Component({
  computed: {
    ComponentMode() {
      return ComponentMode;
    }
  },
  components: { Graph }
})
export default class DirectlyFollowsGraphComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: DirectlyFollowsGraphComponentData };

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  minSupport: number = 0;
  maxSupport: number = 1;
  support: number = 1;
  version: number = 0;

  mounted() {
    this.renderDFG();
  }

  renderDFG() {
    if (this.data.data.edges === undefined) return;
    const supports = this.data.data.edges.map((edge) => edge.support as number).sort((a, b) => a - b);
    this.minSupport = Math.min(...supports);
    this.maxSupport = Math.max(...supports);
    this.support = Math.min(supports[Math.round(supports.length * 0.2)], Math.round(this.minSupport + (this.maxSupport - this.minSupport) * 0.2));
    this.support = Math.max(this.support, this.minSupport);
    this.version += 1;
  }

  filterEdge(edge: EdgeConfig): boolean {
    return (edge.support as number) >= this.support;
  }

  @Watch("data")
  update() {
    if (this.data === undefined) return;
    this.renderDFG();
  }
}
</script>
