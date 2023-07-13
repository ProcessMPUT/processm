<template>
  <div ref="graph" class="dfg"></div>
</template>

<style scoped>
.dfg {
  width: 100%;
  height: 100%;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";
import G6, { GraphData } from "@antv/g6";
import { DirectlyFollowsGraphComponentData } from "@/openapi";

@Component
export default class DirectlyFollowsGraphComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: DirectlyFollowsGraphComponentData };

  mounted() {
    const graphData = (this.data.data as unknown) as GraphData;
    this.markSelfLoops(graphData);
    this.calcSize(graphData);
    this.calcLayers(graphData);

    console.log(graphData);

    setTimeout(() => {
      // wait until height of the component is calculated
      const container = this.$refs.graph as HTMLElement;
      const graph = new G6.Graph({
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
            stroke: "#424242",
            endArrow: {
              path: G6.Arrow.vee(10, 10, 0),
              //d: 0,
              fill: "#424242",
              stroke: "#424242"
            }
          },
          labelCfg: {
            refY: 5
          }
        }
      });
      graph.data(graphData); // Load the data
      graph.render(); // Render the graph
    }, 0);
  }

  markSelfLoops(data: GraphData) {
    const selfLoops = data.edges!.filter((edge) => edge.source === edge.target);
    for (const edge of selfLoops) {
      edge.type = "loop";
    }
  }

  calcSize(data: GraphData) {
    for (const node of data.nodes!) {
      const chars = node.label!.toString().length;
      if (chars > 20) {
        node.size = [5 * chars + Math.log2(chars), 25];
      }
    }
  }

  calcLayers(data: GraphData) {
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
}
</script>
