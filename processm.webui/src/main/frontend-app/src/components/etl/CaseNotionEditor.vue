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
import {Component, Prop, Watch} from "vue-property-decorator";
import {dia, layout, shapes, util} from "jointjs";
import dagre from "dagre";
import graphlib from "graphlib";
import {RelationshipGraph} from "@/openapi";

@Component
export default class CaseNotionEditor extends Vue {
  @Prop({default: false})
  readonly value!: boolean;
  @Prop({default: null})
  readonly selectedNodes?: number[] | null;
  @Prop({default: null})
  readonly selectedLinks?: Array<number> | null;
  @Prop({default: null})
  readonly relationshipGraph?: RelationshipGraph | null;
  @Prop({default: "network-simplex"})
  readonly layoutAlgorithm?: "network-simplex" | "tight-tree" | "longest-path";

  private graph = new dia.Graph();
  private paper = new dia.Paper({});
  private nodes = new Map<number, shapes.standard.Rectangle>();
  private links = new Array<shapes.standard.Link>();

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
  relationshipGraphChanged(relationshipGraph: RelationshipGraph | null) {
    this.graph = new dia.Graph();
    this.nodes = new Map<number, shapes.standard.Rectangle>();
    this.links = new Array<shapes.standard.Link>();

    if (relationshipGraph != null) {
      for (let classDescriptor of relationshipGraph.classes) {
        const classId = classDescriptor.id;
        const className = classDescriptor.name;
        const nodeWidth = 100 + className.length;
        const nodeHeight = 50;
        const rect = new shapes.standard.Rectangle();
        rect.resize(nodeWidth, nodeHeight);
        // FIXME Documentation for JointJS is poor and I am not sure whether annotations has some special meaning or not.
        // FIXME By the name and the fact it accepts any object, I infer it is intended to store user-defined data, but I may be misguided.
        rect.attr({
          annotations: { classId: classId },
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

      relationshipGraph.edges.forEach((edge) => {
        const sourceClassId = edge.sourceClassId;
        const targetClassId = edge.targetClassId;
        const sourceNode = this.nodes.get(sourceClassId);
        const targetNode = this.nodes.get(targetClassId);

        if (sourceNode == undefined || targetNode == undefined) return;

        const link = new shapes.standard.Link();
        link.source(sourceNode);
        link.target(targetNode);
        link.connector("rounded");
        link.attr({
          annotations: { relationship: edge }
        });
        this.links.push(link);
      });

      this.links.forEach((link: shapes.standard.Link) => {
        link.addTo(this.graph);
      });
    }

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
    this.selectedNodesChanged();
    this.selectedLinksChanged();
  }

  nodeSelected(node: dia.ElementView) {
    const classId = node.model.attr('annotations')?.classId;
    if (classId !== undefined) {
      this.$emit("node-selected", classId);
    }
  }

  updateNodesSelection() {
    const selectedNodes = new Set(this.selectedNodes);
    this.nodes.forEach((node, nodeId) => {
      node.attr("body/selected", selectedNodes.has(nodeId));
    });
  }

  updateLinksSelection() {
    if (this.selectedLinks == null) return;

    const selectedLinks = new Set(this.selectedLinks);

    this.links.forEach(link => {
      const relationshipId = link.attr('annotations')?.relationship?.id;
      const isSelected = selectedLinks.has(relationshipId);
      link.attr({
        line: {
          selected: isSelected,
          targetMarker: {
            selected: isSelected
          }
        }
      });
    });
  }
}
</script>
