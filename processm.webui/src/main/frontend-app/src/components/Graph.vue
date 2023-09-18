<template>
  <div ref="graph" class="graph"></div>
</template>

<style scoped>
.graph {
  width: 100%;
  height: 100%;
}
</style>

<script lang="ts">
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import G6, {GraphData, NodeConfigMap} from "@antv/g6";
import {EdgeConfig, NodeConfig} from "@antv/g6-core/lib/types";

@Component({})
export default class Graph extends Vue {
  @Prop()
  readonly data!: GraphData | CNetGraphData;

  @Prop({ default: (edge: EdgeConfig) => true })
  readonly filterEdge!: (edge: EdgeConfig) => boolean;

  /**
   * Change the value of this property to refresh the component.
   */
  @Prop({ default: 0 })
  refresh!: number;

  graph: any;

  readonly edgeColor = "#424242";

  mounted() {
    setTimeout(() => {
      const container = this.$refs.graph as HTMLElement;
      this.graph = new G6.Graph({
        container: container, // String | HTMLElement, required, the id of DOM element or an HTML node
        width: container.offsetWidth, // Number, required, the width of the graph
        height: container.offsetHeight, // Number, required, the height of the graph
        fitView: true,
        fitCenter: true,
        layout: {
          type: "comboCombined",
          preventOverlap: true,
          outerLayout: new G6.Layout["dagre"]({
            rankdir: "LR",
            nodesep: 20,
            ranksep: 50,
            controlPoints: true
          })
        },
        defaultNode: {
          type: "ellipse",
          size: [100, 25],
          logoIcon: {
            show: false
          },
          stateIcon: {
            show: false
          }
        },
        defaultEdge: {
          type: "quadratic",
          style: {
            stroke: this.edgeColor,
            endArrow: {
              path: G6.Arrow.vee(10, 10, 0),
              //d: 0,
              fill: this.edgeColor,
              stroke: this.edgeColor
            }
          },
          labelCfg: {
            refY: 5
          }
        }
      });
      // wait until height of the component is calculated
      const graphData = this.data;
      this.markSelfLoops(graphData);
      this.calcSize(graphData);
      this.calcLayers(graphData);

      console.log(graphData);
      this.graph.data(graphData); // Load the data
      this.graph.render(); // Render the graph

      this.updateEdges();
    }, 0);
  }

  markSelfLoops(data: GraphData) {
    const selfLoops = data.edges!.filter((edge) => edge.source === edge.target);
    for (const edge of selfLoops) {
      edge.type = "loop";
    }
  }

  calcSize(data: GraphData) {
    if (data.nodes!.some((node) => node.size !== undefined)) return;

    for (const node of data.nodes!) {
      const chars = node.label!.toString().length;
      if (chars > 20) {
        node.size = [5 * chars + Math.log2(chars), 25];
      }
    }
  }

  calcLayers(data: GraphData) {
    if (data.nodes!.some((node) => node.layer !== undefined)) return;
    // begin with start nodes
    const queue = data.nodes!.filter((node) => !data.edges!.some((edge) => edge.source !== node.id && edge.target === node.id));
    for (let node of queue) {
      node.layer = 0;
    }

    while (queue.length > 0) {
      const node = queue.shift()!;
      const followers = data
        .edges!.filter((edge) => edge.source === node.id)
        .map((edge) => data.nodes!.find((node) => node.id === edge.target)!)
        .filter((node) => node.layer === undefined);
      for (const follower of followers) {
        follower.layer = (node.layer as number) + 1;
      }
      queue.push(...followers);
    }

    // move final nodes to the last layer
    const maxLayer = Math.max(...data.nodes!.map((node) => node.layer as number)) + 1;
    const final = data.nodes!.filter((node) => !data.edges!.some((edge) => edge.source === node.id && edge.target !== node.id));
    for (const node of final) {
      node.layer = maxLayer;
    }

    for (const node of data.nodes!) {
      node.comboId = "" + node.layer;
    }
  }

  updateEdges() {
    for (const edge of this.data.edges!) {
      if (!edge.id) throw new Error("Missing edge id!");
      const edgeObj = this.graph.findById(edge.id);
      const visible = this.filterEdge(edge);
      if (visible) edgeObj.show();
      else edgeObj.hide();
    }
  }

  @Watch("refresh")
  update() {
    if (this.graph === undefined) return;
    this.updateEdges();
    this.graph.refresh();
  }
}

export interface CNetGraphData extends GraphData {
  nodes?: CNetNodeConfig[];
}

export interface CNetNodeConfig extends NodeConfig {
  joins: string[][];
  splits: string[][];
}
</script>
