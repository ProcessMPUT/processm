<template>
  <div v-resize:debounce.10="onResize" :class="`svg-container ${componentId}`">
    <svg x="0" y="0" width="100%" height="100%" ref="svg" class="svg-content" />
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
import { Component, Prop, Watch } from "vue-property-decorator";
import * as d3 from "d3";
import resize from "vue-resize-directive";

interface Node extends d3.SimulationNodeDatum {
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

interface VisualLink extends d3.SimulationLinkDatum<VisualNode> {
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
  readonly data!: any;
  @Prop({ default: false })
  readonly draggable!: boolean;
  @Prop({ default: false })
  readonly editable!: boolean;

  private bindingNodes: Array<VisualNode> = [];
  private bindingLinks: Array<VisualLink> = [];
  private simulation: d3.Simulation<VisualNode, VisualLink> | undefined;
  private readonly height: number = 250;
  private readonly width: number = 250;
  private nodeDetails!: d3.Selection<d3.BaseType, unknown, HTMLElement, any>;
  private svg!: d3.Selection<d3.BaseType, unknown, HTMLElement, any>;
  private node!: d3.Selection<SVGGElement, VisualNode, SVGGElement, unknown>;
  private link!: d3.Selection<
    Element | d3.EnterElement | Document | Window | SVGPathElement | null,
    VisualLink,
    SVGGElement,
    unknown
  >;
  private arrowhead!: d3.Selection<d3.BaseType, unknown, HTMLElement, any>;
  private arrowheadShape!: d3.Selection<
    SVGPathElement,
    unknown,
    HTMLElement,
    any
  >;
  private nodeShape!: d3.Selection<SVGPathElement, VisualNode, SVGGElement, unknown>;
  private nodeLabel!: d3.Selection<SVGTextElement, VisualNode, SVGGElement, unknown>;
  private displayPreferences = {
      nodeSize: 10,
      bindingNodeSize: 5,
      edgeThickness: 2,
      bindingEdgeThickness: 1,
      edgeArrowSize: 10,
      nodeLabelSize: 16
  }

  get componentId(): string {
        return `c${this._uid}`
    }


  mounted() {
    const depthStats = this.data.nodes.reduce((accumulator: any, node: DataNode) => {
      accumulator[node.depth] = (accumulator[node.depth] || 0) + 1;
      return accumulator;
    }, {});
    const heightUnit = this.height / Object.keys(depthStats).length;
    const usedWidth: Array<number> = [];

    this.data.nodes.forEach((dataNode: DataNode, index: number, allDataNodes: []) => {
      const widthUnit = this.width / depthStats[dataNode.depth];
      usedWidth[dataNode.depth] = (usedWidth[dataNode.depth] || 0) + 1;
      const node = {
        id: dataNode.id,
        isBindingNode: false,
        fx: widthUnit * usedWidth[dataNode.depth] - widthUnit / 2,
        fy: dataNode.depth * heightUnit + heightUnit / 2,
        isStartNode: index == 0,
        isEndNode: index == allDataNodes.length - 1
      };
      this.bindingNodes.push(node);
      this.createBindingElements(
        node.id,
        dataNode.outputBindings,
        "output",
        node.fx,
        node.fy
      );
      this.createBindingElements(
        node.id,
        dataNode.inputBindings,
        "input",
        node.fx,
        node.fy
      );
    });

    this.data.nodes.forEach((dataNode: DataNode) => {
      const successors = new Set(dataNode.outputBindings.flat());
      successors.forEach(successor => {
        const targetNode = this.data.nodes.find(
          (node: Node) => node.id == successor
        );
        if (targetNode != null) {
          this.createIntermediateLinks(dataNode, targetNode);
        }
      });
    });

    this.simulation = d3.forceSimulation<VisualNode, VisualLink>().force(
      "link",
      d3
        .forceLink()
        .id(d => (d as VisualNode).id)
        .distance(d => 0.1)
        .strength(d =>
          (d as VisualLink).isBindingLink
            ? 0
            : (d as VisualLink).isBetweenBindings
            ? 2
            : 1
        )
    );

    this.svg = d3
      .select(`.${this.componentId}`)
      .select("svg")
      .attr("preserveAspectRatio", "xMidYMid meet")
      // .attr("transform", "rotate(0)");
      .attr("viewBox", `0 0 ${this.width} ${this.height}`)
      .on("mousedown", this.mousedownCanvas);

    this.nodeDetails = d3
      .select(`.${this.componentId}`)
      .select("div.node-details")
      .style("opacity", 0);

    this.arrowhead = this.svg
      .append("svg:defs")
      .append("svg:marker")
      .attr("id", `triangle-${this.componentId}`)
      .attr("refX", 8 + this.displayPreferences.nodeSize / 2)
      .attr("refY", 4)
      .attr("markerUnits", "userSpaceOnUse")
      .attr("viewBox", `0 0 10 10`)
      .attr("orient", "auto");

    this.arrowheadShape = this.arrowhead
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
          .transition()
          .duration(200)
          .style("opacity", 0.8);
        this.nodeDetails
          .html(`ID: ${d.id}\nX: ${d.x}\nY: ${d.y}`)
          .style("left", `${(<number>d.x / this.width) * 100}%`)
          .style("top", `${(<number>d.y / this.height) * 100}%`);
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
      })

    if (this.draggable) {
      this.node
        .call(d3.drag<SVGElement, VisualNode>()
          .on("start", this.dragstarted)
          .on("drag", this.dragged)
          .on("end", this.dragended));
    }

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
      (this.$refs.svg as Element).clientHeight);

    this.scaleElements(scalingFactor);

    this.simulation.on("tick", () => {
      this.link.attr("d", this.linkArc);
      this.node.attr("transform", d => `translate(${<number>d.x},${<number>d.y})`);
    });
    this.simulation.nodes(this.bindingNodes);
    (this.simulation.force("link") as any)?.links(this.bindingLinks);
  }

  dragstarted(d: any) {
    d.fx = null;
    d.fy = null;
  }

  dragged(d: any) {
    const validateBoundaries = (x: number, minX: number, maxX: number) =>
      Math.min(Math.max(x, minX), maxX);

    d.fx = validateBoundaries(d3.event.x, 0, this.width);
    d.fy = validateBoundaries(d3.event.y, 0, this.height);
  }

  dragended(d: any) {
    // if (!d3.event.active) this.simulation?.alphaTarget(0.3).restart();
    d.fx = d.x;
    d.fy = d.y;
  }

  onResize(element: Element) {
    const scalingFactor = this.calculateScalingFactor(element.clientWidth, element.clientHeight);

    this.scaleElements(scalingFactor);
  }

  scaleElements(scalingFactor: number) {
    if (scalingFactor == Number.POSITIVE_INFINITY) return;

    this.arrowhead
      .attr("markerWidth", this.displayPreferences.edgeArrowSize * scalingFactor)
      .attr("markerHeight", this.displayPreferences.edgeArrowSize * scalingFactor);

    this.link.attr(
      "stroke-width",
      d => (d.isBindingLink ? this.displayPreferences.bindingEdgeThickness : this.displayPreferences.edgeThickness) * scalingFactor
    );
    this.node.attr("stroke-width", scalingFactor)
    this.nodeShape.attr("d", d =>
        d3.symbol()
          .type(d.isStartNode || d.isEndNode ? d3.symbolDiamond : d3.symbolCircle)
          .size(((d.isBindingNode ? this.displayPreferences.bindingNodeSize : this.displayPreferences.nodeSize) * scalingFactor) ** 2)());
    this.nodeLabel.attr("font-size", this.displayPreferences.nodeLabelSize * scalingFactor);
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

  linkArc(d: any) {
    return `
    M${d.source.x},${d.source.y}
    L${d.target.x},${d.target.y}
`;

    // const r = Math.hypot(d.target.x - d.source.x, d.target.y - d.source.y);
    // return `
    //     M${d.source.x},${d.source.y}
    //     A${r},${r} 0 0,1 ${d.target.x},${d.target.y}
    // `;
  }

  calculateScalingFactor(width: number, height: number) {
    return Math.max(
      this.width / width,
      this.height / height
    );
  }
}
</script>
