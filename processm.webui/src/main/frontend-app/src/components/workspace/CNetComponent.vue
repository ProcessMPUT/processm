<template xmlns:v-slot="http://www.w3.org/1999/html">
  <div class="graph">
    <v-toolbar class="toolbar" dense elevation="0" floating>
      <alignments-dialog :alignments="data.data.alignmentKPIReport?.alignments"></alignments-dialog>
    </v-toolbar>
    <table>
      <tr>
        <td>
          <graph :key="version" :data="graphData" :filter-edge="filterEdge" :refresh="support"></graph>
        </td>
        <td v-show="componentMode != ComponentMode.Static">
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
  </div>
</template>

<style scoped>
.toolbar {
  float: left;
  z-index: 1;
}

div.graph {
  height: 100%;
}

table {
  width: 100%;
  height: 100%;
  border-spacing: 0;
  position: absolute;
}

table td:last-child {
  width: 100px;
  text-align: center;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";
import Graph, { CNetGraphData } from "@/components/Graph.vue";
import { EdgeConfig } from "@antv/g6-core/lib/types";
import { CNetComponentData } from "@/models/WorkspaceComponent";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";
import LogTable from "@/components/LogTable.vue";
import AlignmentsDialog from "@/components/AlignmentsDialog.vue";

@Component({
  computed: {
    ComponentMode() {
      return ComponentMode;
    }
  },
  components: { AlignmentsDialog, LogTable, Graph }
})
export default class CNetComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: CNetComponentData };
  graphData: CNetGraphData = {
    nodes: [],
    edges: []
  };

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  minSupport: number = 0;
  maxSupport: number = 1;
  support: number = 1;
  version: number = 0;

  mounted() {
    this.renderCNet();
  }

  renderCNet() {
    //convert cnetdata to graphdata
    this.graphData = {
      nodes: this.data.data.nodes.map((node) => {
        return {
          id: node.id,
          label: node.id,
          joins: node.joins,
          splits: node.splits
        };
      }),
      edges: this.data.data.edges.map((edge) => {
        return {
          id: `${edge.sourceNodeId}->${edge.targetNodeId}`,
          source: edge.sourceNodeId as string,
          target: edge.targetNodeId as string,
          support: edge.support
        };
      })
    };
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
    this.renderCNet();
  }
}
</script>
