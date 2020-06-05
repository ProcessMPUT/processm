<template>
  <div v-resize:debounce.10="onResize" class="svg-container">
    <svg
      :viewBox="`0 0 ${width} ${height}`"
      x="0"
      y="0"
      width="100%"
      height="100%"
      ref="svg"
      class="svg-content"
      preserveAspectRatio="xMidYMid meet"
    />
    <div class="node-details" />
  </div>
</template>

<style scoped>
.svg-container {
  display: block;
  position: relative;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.svg-content {
  display: inline-block;
  position: absolute;
}

.node-details {
  position: absolute;
  text-align: left;
  padding: 4px;
  font: 12px sans-serif;
  background: lightsteelblue;
  border: 0px;
  border-radius: 4px;
  pointer-events: none;
  white-space: pre-line;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";
import * as d3 from "d3";
import resize from "vue-resize-directive";
import {
  ForceLink,
  Simulation,
  SimulationNodeDatum,
  SimulationLinkDatum,
  BaseType,
  Selection,
  EnterElement
} from "d3";
import dagre from "dagre";
import { v4 as uuidv4 } from "uuid";

interface Node extends SimulationNodeDatum {
  id: string;
}

interface DataNode extends Node {
  depth: number;
  inputBindings: string[][];
  outputBindings: string[][];
}

interface VisualNode extends Node {
  isBindingNode: boolean;
  bindingId?: string;
  isStartNode?: boolean;
  isEndNode?: boolean;
}

interface VisualLink extends SimulationLinkDatum<VisualNode> {
  isBindingLink: boolean;
  bindingId?: string;
  isBetweenBindings: boolean;
  displayArrow?: boolean;
}

@Component({
  directives: {
    resize
  }
})
export default class CasualNet extends Vue {
  @Prop({ default: {} })
  readonly data!: {
    nodes: Array<DataNode>;
    layout?: Array<{ id: string; x: number; y: number }>;
  };
  @Prop({ default: false })
  readonly draggable!: boolean;
  @Prop({ default: false })
  readonly editable!: boolean;

  private readonly height: number = 250;
  private readonly width: number = 250;
  private bindingNodes: Array<VisualNode> = [];
  private bindingLinks: Array<VisualLink> = [];
  private simulation: Simulation<VisualNode, VisualLink> | undefined;
  private nodeDetails!: Selection<BaseType, unknown, null, undefined>;
  private svg!: Selection<d3.BaseType, unknown, null, undefined>;
  private node!: Selection<SVGGElement, VisualNode, SVGGElement, unknown>;
  private link!: Selection<
    Element | EnterElement | Document | Window | SVGPathElement | null,
    VisualLink,
    SVGGElement,
    unknown
  >;
  private arrowhead!: Selection<BaseType, unknown, null, undefined>;
  private nodeShape!: Selection<
    SVGPathElement,
    VisualNode,
    SVGGElement,
    unknown
  >;
  private nodeLabel!: Selection<
    SVGTextElement,
    VisualNode,
    SVGGElement,
    unknown
  >;
  private displayPreferences = {
    nodeSize: 10,
    bindingNodeSize: 5,
    edgeThickness: 1,
    bindingEdgeThickness: 1,
    edgeArrowSize: 12,
    nodeLabelSize: 16
  };

  mounted() {
    const g = new dagre.graphlib.Graph()
      .setGraph({
        marginx: this.displayPreferences.nodeSize,
        marginy: this.displayPreferences.nodeSize,
        acyclicer: "greedy"
      })
      .setDefaultEdgeLabel(function() {
        return {};
      });

    this.data.nodes.forEach((dataNode: DataNode) => {
      const successors = new Set(dataNode.outputBindings.flat());
      successors.forEach(successor => {
        const targetNode = this.data.nodes.find(
          (node: Node) => node.id == successor
        );
        if (targetNode != null) {
          g.setEdge(dataNode.id, targetNode.id);
          this.createIntermediateLinks(dataNode, targetNode);
        }
      });
    });

    this.data.nodes.forEach((dataNode: DataNode) => {
      g.setNode(dataNode.id, { label: dataNode.id });
    });

    const isLayoutPredefined =
      this.data.nodes.length == this.data.layout?.length &&
      this.data.nodes.every(node =>
        this.data.layout?.some(nodeLayout => nodeLayout.id == node.id)
      );

    if (!isLayoutPredefined) {
      dagre.layout(g);
      const layoutWidth = g.graph().width || this.width,
        layoutHeight = g.graph().height || this.height,
        scaleX = this.width / layoutWidth,
        scaleY = this.height / layoutHeight,
        offsetX = Math.max(this.width - layoutWidth * scaleX, 0) / 2,
        offsetY = Math.max(this.height - layoutHeight * scaleY, 0) / 2;
      g.nodes().forEach(nodeId => {
        const node = g.node(nodeId);
        node.x = node.x * scaleX + offsetX;
        node.y = node.y * scaleY + offsetY;
      });
    } else {
      g.nodes().forEach(nodeId => {
        Object.assign(g.node(nodeId), {
          x: this.data.layout?.find(n => n.id == nodeId)?.x || 0,
          y: this.data.layout?.find(n => n.id == nodeId)?.y || 0
        });
      });
    }

    this.data.nodes.forEach(
      (dataNode: DataNode, index: number, allDataNodes: DataNode[]) => {
        const layoutNode = g.node(dataNode.id),
          x = layoutNode.x,
          y = layoutNode.y,
          node = {
            id: dataNode.id,
            isBindingNode: false,
            fx: x,
            fy: y,
            isStartNode: index == 0,
            isEndNode: index == allDataNodes.length - 1
          };
        this.bindingNodes.push(node);
        this.createBindingElements(
          node.id,
          dataNode.outputBindings,
          "output",
          x,
          y
        );
        this.createBindingElements(
          node.id,
          dataNode.inputBindings,
          "input",
          x,
          y
        );
      }
    );

    this.simulation = d3.forceSimulation<VisualNode, VisualLink>().force(
      "link",
      d3
        .forceLink()
        .id(d => (d as VisualNode).id)
        .distance(0.1)
        .strength(d =>
          (d as VisualLink).isBindingLink
            ? 0
            : (d as VisualLink).isBetweenBindings
            ? 2
            : 1
        )
    );

    this.svg = d3.select(this.$el).select("svg");

    this.nodeDetails = d3
      .select(this.$el)
      .select("div.node-details")
      .style("opacity", 0);

    this.arrowhead = this.svg
      .append("svg:defs")
      .append("svg:marker")
      .attr("id", `arrow+${uuidv4()}`)
      .attr("refX", 8 + this.displayPreferences.nodeSize / 2)
      .attr("refY", 4)
      .attr("markerUnits", "userSpaceOnUse")
      .attr("viewBox", `0 0 10 10`)
      .attr("orient", "auto");

    this.arrowhead
      .append("path")
      .attr("d", "M 0 0 10 4 0 8 2 4")
      .style("fill", "#999");

    this.link = this.svg
      .append("g")
      .attr("stroke", "#999")
      .attr("fill", "none")
      .selectAll("line")
      .data(this.bindingLinks)
      .join("path")
      .attr("stroke-opacity", d => (d.isBindingLink ? 0.5 : 1));

    this.link
      .filter(d => d.displayArrow || false)
      .attr("marker-end", `url(#${this.arrowhead.attr("id")})`);

    this.node = this.svg
      .append("g")
      .attr("stroke", "#fff")
      .selectAll("g")
      .data(this.bindingNodes, d => (d as Node).id)
      .enter()
      .append("g")
      .on("mouseover", (d, i, nodes) => {
        const selectedNodes = !d.isBindingNode
          ? d3.select(nodes[i])
          : this.node.filter(node => node.bindingId == d.bindingId);
        selectedNodes.attr("stroke", this.color());
        if (selectedNodes.size() > 1) {
          const color = d.bindingId?.includes("output") ? "blue" : "red";
          this.link
            .filter(link => link.bindingId == d.bindingId)
            .attr("stroke", color);
        }
        this.nodeDetails
          .html(`ID: ${d.id}\nX: ${d.x}\nY: ${d.y}`)
          .style(
            "left",
            `${this.convertToAbsolutePercentage(
              (d.x as number) / this.width,
              (this.$refs.svg as Element).clientWidth
            ) * 100}%`
          )
          .style(
            "top",
            `${this.convertToAbsolutePercentage(
              (d.y as number) / this.height,
              (this.$refs.svg as Element).clientHeight
            ) * 100}%`
          )
          .transition()
          .duration(200)
          .style("opacity", 0.8);
      })
      .on("mouseout", (d, i, nodes) => {
        const selectedNodes = !d.isBindingNode
          ? d3.select(nodes[i])
          : this.node.filter(node => node.bindingId == d.bindingId);
        if (selectedNodes.size() > 1) {
          this.link
            .filter(link => link.bindingId == d.bindingId)
            .attr("stroke", "#999");
        }
        selectedNodes.attr("stroke", "#fff");
        this.nodeDetails
          .transition()
          .duration(500)
          .style("opacity", 0);
      });

    this.node.call(
      d3
        .drag<SVGGElement, VisualNode, SVGGElement>()
        .on("start", this.dragstarted)
        .on("drag", this.dragged)
        .on("end", this.dragended)
    );

    this.nodeShape = this.node
      .append("path")
      .attr("opacity", 1)
      .style("fill", this.color());

    this.nodeLabel = this.node
      .filter(d => !d.isBindingNode)
      .append("text")
      .attr("class", "node-name")
      .style("font-family", "Arial, Helvetica, sans-serif") // move it to <style>
      .style("font-variant", "small-caps")
      .style("paint-order", "stroke")
      .text(d => d.id);

    const scalingFactor = this.calculateScalingFactor(
      (this.$refs.svg as Element).clientWidth,
      (this.$refs.svg as Element).clientHeight
    );

    this.scaleElements(scalingFactor);

    this.simulation.on("tick", () => {
      this.link.attr("d", this.linkArc);
      this.node.attr(
        "transform",
        d => `translate(${d.x as number},${d.y as number})`
      );
    });
    this.simulation.nodes(this.bindingNodes);
    this.simulation
      ?.force<ForceLink<VisualNode, VisualLink>>("link")
      ?.links(this.bindingLinks);
  }

  convertToAbsolutePercentage(relativePercentage: number, elementSize: number) {
    const minDimension = Math.min(
      (this.$refs.svg as Element).clientHeight,
      (this.$refs.svg as Element).clientWidth
    );
    const percentageScaling = minDimension / elementSize;
    const sizeOffset = 0.5 - percentageScaling / 2;

    return percentageScaling * relativePercentage + sizeOffset;
  }

  dragstarted(d: VisualNode) {
    if (!this.draggable) return;
    d.fx = null;
    d.fy = null;
  }

  dragged(d: VisualNode) {
    if (!this.draggable) return;
    const validateBoundaries = (x: number, minX: number, maxX: number) =>
      Math.min(Math.max(x, minX), maxX);

    d.fx = validateBoundaries(d3.event.x, 0, this.width);
    d.fy = validateBoundaries(d3.event.y, 0, this.height);
  }

  dragended(d: VisualNode) {
    if (!this.draggable) return;
    // if (!d3.event.active) this.simulation?.alphaTarget(0.3).restart();
    d.fx = d.x;
    d.fy = d.y;
  }

  onResize(element: Element) {
    const scalingFactor = this.calculateScalingFactor(
      element.clientWidth,
      element.clientHeight
    );

    this.scaleElements(scalingFactor);
  }

  scaleElements(scalingFactor: number) {
    if (scalingFactor == Number.POSITIVE_INFINITY) return;

    this.arrowhead
      .attr(
        "markerWidth",
        this.displayPreferences.edgeArrowSize * scalingFactor
      )
      .attr(
        "markerHeight",
        this.displayPreferences.edgeArrowSize * scalingFactor
      );

    this.link.attr(
      "stroke-width",
      d =>
        (d.isBindingLink
          ? this.displayPreferences.bindingEdgeThickness
          : this.displayPreferences.edgeThickness) * scalingFactor
    );
    this.node.attr("stroke-width", scalingFactor);
    this.nodeShape.attr("d", d =>
      d3
        .symbol()
        .type(d.isStartNode || d.isEndNode ? d3.symbolDiamond : d3.symbolCircle)
        .size(
          ((d.isBindingNode
            ? this.displayPreferences.bindingNodeSize
            : this.displayPreferences.nodeSize) *
            scalingFactor) **
            2
        )()
    );
    this.nodeLabel.attr(
      "font-size",
      this.displayPreferences.nodeLabelSize * scalingFactor
    );
  }

  mousedownCanvas() {
    console.log("click");
  }

  createBindingElements(
    nodeId: string,
    bindings: string[][],
    bindingType: string,
    x: number,
    y: number
  ) {
    bindings.forEach(binding => {
      const bindingId = `${nodeId}-${bindingType}-${binding.join("-")}`;

      binding.forEach(bindingElement =>
        this.bindingNodes.push({
          id: `${bindingElement}_${bindingId}`,
          isBindingNode: true,
          bindingId: bindingId,
          x: x,
          y: y
        })
      );

      for (let i = 0; i < binding.length - 1; i++) {
        this.bindingLinks.push({
          source: `${binding[i]}_${bindingId}`,
          target: `${binding[i + 1]}_${bindingId}`,
          isBindingLink: true,
          bindingId: bindingId,
          isBetweenBindings: true
        });
      }
    });
  }

  createIntermediateLinks(sourceNode: DataNode, targetNode: DataNode) {
    let lastBoundNodeId = sourceNode.id;

    for (let i = 0; i < sourceNode.outputBindings.length; i++) {
      if (!sourceNode.outputBindings[i].includes(targetNode.id)) {
        continue;
      }

      const currentNodeId = `${targetNode.id}_${
        sourceNode.id
      }-output-${sourceNode.outputBindings[i].join("-")}`;
      this.bindingLinks.push({
        source: lastBoundNodeId,
        target: currentNodeId,
        isBindingLink: false,
        isBetweenBindings: true
      });
      lastBoundNodeId = currentNodeId;
    }

    let isBetweenBindings = false;
    for (let i = targetNode.inputBindings.length; i > 0; i--) {
      if (!targetNode.inputBindings[i - 1].includes(sourceNode.id)) {
        continue;
      }

      const currentNodeId = `${sourceNode.id}_${
        targetNode.id
      }-input-${targetNode.inputBindings[i - 1].join("-")}`;
      this.bindingLinks.push({
        source: lastBoundNodeId,
        target: currentNodeId,
        isBindingLink: false,
        isBetweenBindings: isBetweenBindings
      });
      isBetweenBindings = true;
      lastBoundNodeId = currentNodeId;
    }

    this.bindingLinks.push({
      source: lastBoundNodeId,
      target: targetNode.id,
      isBindingLink: false,
      isBetweenBindings: true,
      displayArrow: true
    } as VisualLink);
  }

  color() {
    return (d: VisualNode) =>
      !d.isBindingNode ? "black" : d.id.includes("input") ? "red" : "blue";
  }

  linkArc(d: d3.SimulationLinkDatum<VisualNode>) {
    return `
    M${(d.source as VisualNode).x},${(d.source as VisualNode).y}
    L${(d.target as VisualNode).x},${(d.target as VisualNode).y}
`;

    // const r = Math.hypot(d.target.x - d.source.x, d.target.y - d.source.y);
    // return `
    //     M${d.source.x},${d.source.y}
    //     A${r},${r} 0 0,1 ${d.target.x},${d.target.y}
    // `;
  }

  calculateScalingFactor(width: number, height: number) {
    return Math.max(this.width / width, this.height / height);
  }
}
</script>
