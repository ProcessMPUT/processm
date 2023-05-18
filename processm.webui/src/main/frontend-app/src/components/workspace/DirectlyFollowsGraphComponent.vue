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
import G6 from "@antv/g6";

@Component
export default class DirectlyFollowsGraphComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: { value: string } };

  mounted() {
    const data = {
      // TODO: replace with actual data
      // The array of nodes
      nodes: [
        {
          id: "node1", // String, unique and required
          x: 100, // Number, the x coordinate
          y: 200 // Number, the y coordinate
        },
        {
          id: "node2", // String, unique and required
          x: 300, // Number, the x coordinate
          y: 200 // Number, the y coordinate
        }
      ],
      // The array of edges
      edges: [
        {
          source: "node1", // String, required, the id of the source node
          target: "node2" // String, required, the id of the target node
        }
      ]
    };
    setTimeout(() => {
      // wait until height of the component is calculated
      const container = this.$refs.graph as HTMLElement;
      const graph = new G6.Graph({
        container: container, // String | HTMLElement, required, the id of DOM element or an HTML node
        width: container.offsetWidth, // Number, required, the width of the graph
        height: container.offsetHeight, // Number, required, the height of the graph
        fitView: true,
        layout: {
          type: "dagre"
        },
        defaultNode: {
          type: "rect"
        }
      });
      graph.data(data); // Load the data defined in Step 2
      graph.render(); // Render the graph
    }, 0);
  }
}
</script>
