<template>
  <div class="editor-container">
    <div v-show="value" ref="paper"></div>
  </div>
</template>

<style scoped>
.editor-container {
  justify-content: center;
  display: flex;
}
</style>

<style>
g.joint-cell > rect {
  rx: 5;
  cursor: pointer;
}

g.joint-cell > text {
  cursor: pointer;
}

g.joint-cell > rect[selected="true"] {
  stroke: var(--v-primary-lighten1);
}

g.joint-cell > rect[selected="true"] + text {
  font-weight: bold;
}

g.joint-cell > path[selected="true"] {
  stroke: var(--v-primary-lighten1);
}

svg > defs > marker > path[selected="true"] {
  stroke: var(--v-primary-lighten1);
  fill: var(--v-primary-lighten1);
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";
import { dia, layout, shapes, util } from "jointjs";
import dagre from "dagre";
import graphlib from "graphlib";
import { CaseNotion } from "@/openapi";

@Component
export default class CaseNotionEditor extends Vue {
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop({ default: null })
  readonly selectedNodes?: string[] | null;
  @Prop({ default: null })
  readonly selectedLinks?: Array<{
    sourceNodeId: string;
    targetNodeId: string;
  }> | null;
  @Prop({ default: null })
  readonly relationshipGraph?: CaseNotion | null;
  @Prop({ default: "network-simplex" })
  readonly layoutAlgorithm?: "network-simplex" | "tight-tree" | "longest-path";

  private graph = new dia.Graph();
  private paper = new dia.Paper({});
  private nodes = new Map<string, shapes.standard.Rectangle>();
  private links = new Map<[shapes.standard.Rectangle, shapes.standard.Rectangle], shapes.standard.Link>();

  @Watch("selectedNodes")
  selectedNodesChanged() {
    if (this.selectedNodes == null || this.relationshipGraph == null) return;

    this.updateNodesSelection();
  }

  @Watch("selectedLinks")
  selectedLinksChanged() {
    if (this.selectedLinks == null || this.relationshipGraph == null) return;
    this.updateLinksSelection();
  }

  @Watch("relationshipGraph")
  relationshipGraphChanged(relationshipGraph: CaseNotion | null) {
    if (relationshipGraph == null) return;

    this.graph = new dia.Graph();
    this.nodes = new Map<string, shapes.standard.Rectangle>();
    this.links = new Map<[shapes.standard.Rectangle, shapes.standard.Rectangle], shapes.standard.Link>();

    for (let className in relationshipGraph.classes) {
      let classId = relationshipGraph.classes[className];
      const nodeWidth = 100 + className.length;
      const nodeHeight = 50;
      const rect = new shapes.standard.Rectangle();
      rect.resize(nodeWidth, nodeHeight);
      rect.idAttribute = classId;
      rect.attr({
        label: {
          text: util.breakText(className, {
            width: nodeWidth,
            height: nodeHeight
          })
        }
      });
      this.nodes.set(classId, rect);
    }

    this.nodes.forEach((node: shapes.standard.Rectangle) => {
      node.addTo(this.graph);
    });

    relationshipGraph.edges.forEach(({ sourceClassId, targetClassId }) => {
      const sourceNode = this.nodes.get(sourceClassId);
      const targetNode = this.nodes.get(targetClassId);

      if (sourceNode == null || targetNode == null) return;

      const link = new shapes.standard.Link();
      link.source(sourceNode);
      link.target(targetNode);
      link.router("orthogonal");
      link.connector("rounded");
      this.links.set([sourceNode, targetNode], link);
    });

    this.links.forEach((link: shapes.standard.Link) => {
      link.addTo(this.graph);
    });

    const graphBox = layout.DirectedGraph.layout(this.graph, {
      dagre,
      graphlib,
      nodeSep: 30,
      edgeSep: 50,
      rankDir: "LR",
      align: "UL",
      ranker: this.layoutAlgorithm
    });

    this.paper = new dia.Paper({
      el: this.$refs.paper as HTMLElement,
      model: this.graph,
      width: graphBox.width,
      height: graphBox.height
    });

    this.paper.on("element:pointerdblclick", this.nodeSelected);
    this.paper.on("link:pointerdblclick", this.linkSelected);
    this.selectedNodesChanged();
    this.selectedLinksChanged();
  }

  nodeSelected(node: dia.ElementView) {
    this.$emit("node-selected", node.model.idAttribute);
  }

  linkSelected(link: dia.LinkView) {
    this.$emit("link-selected", link.model.idAttribute);
  }

  updateNodesSelection() {
    const selectedNodes = new Set(this.selectedNodes);
    this.nodes.forEach((node, nodeId) => {
      node.attr("body/selected", selectedNodes.has(nodeId));
    });
  }

  updateLinksSelection() {
    if (this.selectedLinks == null) return;

    const selectedLinks = new Set(this.selectedLinks.map((link) => `${link.sourceNodeId}_${link.targetNodeId}`));

    this.links.forEach((link, [sourceNode, targetNode]) => {
      const areBothNodesSelected = selectedLinks.has(`${sourceNode.idAttribute}_${targetNode.idAttribute}`);
      link.attr({
        line: {
          selected: areBothNodesSelected,
          targetMarker: {
            selected: areBothNodesSelected
          }
        }
      });
    });
  }
}
</script>
