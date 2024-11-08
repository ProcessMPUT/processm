<template xmlns:v-slot="http://www.w3.org/1999/html">
  <div class="graph">
    <v-toolbar class="toolbar" dense elevation="0" floating>
      <alignments-dialog
        :workspace-id="workspaceId"
        :component-id="data.id"
        v-if="data.data.alignmentKPIReportVersion != null"
        :name="data.name"
      ></alignments-dialog>
      <kpi-dialog v-if="data.data.alignmentKPIReportVersion != null" :workspace-id="workspaceId" :component-id="data.id"></kpi-dialog>
      <v-tooltip bottom v-if="hasNewerVersion()">
        <template v-slot:activator="{ on, attrs }">
          <v-btn v-bind="attrs" v-on="on" icon v-if="componentMode == ComponentMode.Edit" @click="loadNewestModel">
            <v-icon color="warning">priority_high</v-icon>
          </v-btn>
          <v-icon v-bind="attrs" v-on="on" color="warning" v-else>priority_high</v-icon>
        </template>
        <span v-if="componentMode == ComponentMode.Edit">{{ $t("workspace.component.edit.load-new-model") }}</span>
        <span v-else>{{ $t("workspace.component.new-model-available") }}</span>
      </v-tooltip>
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
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import Graph, { AlignmentKPIHolder, CNetGraphData } from "@/components/Graph.vue";
import { EdgeConfig } from "@antv/g6-core/lib/types";
import { CNetComponentData, WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";
import LogTable from "@/components/LogTable.vue";
import AlignmentsDialog from "@/components/AlignmentsDialog.vue";
import WorkspaceService from "@/services/WorkspaceService";
import { ComponentData } from "@/openapi";
import KpiDialog from "@/components/KpiDialog.vue";

@Component({
  computed: {
    ComponentMode() {
      return ComponentMode;
    }
  },
  components: { KpiDialog, AlignmentsDialog, LogTable, Graph }
})
export default class CNetComponent extends Vue {
  @Inject() workspaceService!: WorkspaceService;

  @Prop()
  readonly workspaceId!: string;

  @Prop({ default: {} })
  readonly data!: WorkspaceComponentModel & { data: CNetComponentData };
  graphData: CNetGraphData & AlignmentKPIHolder = {
    nodes: [],
    edges: [],
    alignmentKPIReport: undefined
  };

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  @Prop({ default: false })
  readonly updateData = false;

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
          label: node.name as string,
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
      }),
      alignmentKPIReport: this.data.data.alignmentKPIReport
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

  hasNewerVersion() {
    const newest = this.data.data.newestVersion;
    const current = this.data.data.modelVersion;
    return newest !== undefined && current !== undefined && newest > current;
  }

  async loadNewestModel() {
    const variantId = this.data.data.newestVersion;
    if (variantId === undefined) return;
    this.data.data = new CNetComponentData(
      (await this.workspaceService.getComponentDataVariant(this.workspaceId, this.data.id, variantId)) as Partial<ComponentData>
    );
    this.renderCNet();
  }
}
</script>