<template>
  <div v-resize:debounce.10="onResize" class="svg-container">
    <svg
      :viewBox="`0 0 ${contentWidth} ${contentHeight}`"
      x="0"
      y="0"
      width="100%"
      height="100%"
      ref="svg"
      class="svg-content"
      :style="{ cursor: editMode == null ? 'auto' : 'crosshair' }"
      preserveAspectRatio="xMidYMid meet"
    >
      <g class="links" stroke="#999" fill="none" />
      <g class="nodes" stroke="#fff" />
      <circle
        class="nodeToBeCreated"
        opacity="0.5"
        :r="(displayPreferences.nodeSize * currentScalingFactor) / 2"
        :style="{ display: editMode == EditMode.Addition ? 'inline' : 'none' }"
      />
      <line
        class="linkToBeCreated"
        display="none"
        :stroke-width="displayPreferences.edgeThickness * currentScalingFactor"
        stroke="#999"
        opacity="0.3"
      />
      <defs>
        <marker
          :id="`arrow${uuidv4()}`"
          markerUnits="userSpaceOnUse"
          viewBox="0 0 10 10"
          orient="auto"
          :refX="8 + displayPreferences.nodeSize / 2"
          refY="4"
        />
      </defs>
    </svg>
    <div class="node-details" />
    <v-speed-dial
      v-if="componentMode == ComponentMode.Edit"
      top
      right
      absolute
      direction="left"
    >
      <template v-slot:activator>
        <v-btn
          color="blue darken-2"
          dark
          fab
          elevation="0"
          @click="setEditMode(null)"
        >
          <v-icon v-if="editMode == null">edit</v-icon>
          <v-icon v-else>close</v-icon>
        </v-btn>
      </template>
      <v-btn
        fab
        dark
        small
        color="green"
        elevation="0"
        :outlined="editMode != EditMode.Addition"
        @click.stop="setEditMode(EditMode.Addition)"
      >
        <v-icon>add</v-icon>
      </v-btn>
      <v-btn
        fab
        dark
        small
        color="red"
        elevation="0"
        :outlined="editMode != EditMode.Deletion"
        @click.stop="setEditMode(EditMode.Deletion)"
      >
        <v-icon>delete</v-icon>
      </v-btn>
      <v-btn
        fab
        dark
        small
        color="purple"
        elevation="0"
        @click.stop="rearrangeNodes"
      >
        <v-icon>grain</v-icon>
      </v-btn>
    </v-speed-dial>
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
  background: var(--v-primary-lighten3);
  border: 0px;
  border-radius: 4px;
  pointer-events: none;
  white-space: pre-line;
  opacity: 0;
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
  BaseType,
  Selection,
  EnterElement,
  drag,
  ContainerElement
} from "d3";
import dagre from "dagre";
import { v4 as uuidv4 } from "uuid";
import { ComponentMode } from "../WorkspaceComponent.vue";
import CasualNet, {
  DataNode,
  DataLink,
  Point,
  Node,
  Link,
  ElementType
} from "./CasualNet";
import {
  UserInputSource,
  UserInputHandler,
  AdditionModeInputHandler,
  DeletionModeInputHandler,
  InteractiveModeInputHandler
} from "./UserInputHandlers";

enum EditMode {
  Addition,
  Deletion
}

@Component({
  directives: {
    resize
  }
})
export default class CasualNetComponent extends Vue implements UserInputSource {
  ComponentMode = ComponentMode;
  EditMode = EditMode;
  uuidv4 = uuidv4;

  @Prop({ default: {} })
  readonly data!: {
    nodes: Array<DataNode>;
    edges: Array<DataLink>;
    layout?: Array<{ id: string; x: number; y: number }>;
  };
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  private editMode: EditMode | null = null;
  private userInputHandler!: UserInputHandler;
  private simulation: Simulation<Node, Link> | undefined;

  private get rootSvg(): Selection<BaseType, unknown, null, undefined> {
    return d3.select(this.$el).select("svg");
  }

  private get nodesLayer() {
    return this.rootSvg.select("g.nodes");
  }

  private get nodeShapes() {
    return this.nodes().select("path") as Selection<
      SVGPathElement,
      Node,
      SVGGElement,
      unknown
    >;
  }

  private get nodeLabels() {
    return this.nodes().select("text") as Selection<
      SVGTextElement,
      Node,
      SVGGElement,
      unknown
    >;
  }

  private get linksLayer() {
    return this.rootSvg.select("g.links");
  }

  private get arrowheads(): Selection<BaseType, unknown, null, undefined> {
    return this.rootSvg.select("defs").select("marker");
  }

  public casualNet!: CasualNet;
  public currentScalingFactor = 1;
  public readonly contentHeight: number = 250;
  public readonly contentWidth: number = 250;
  public displayPreferences = {
    nodeSize: 20,
    bindingNodeSize: 10,
    edgeThickness: 3,
    bindingEdgeThickness: 2,
    edgeArrowSize: 12,
    nodeLabelSize: 16
  };

  public get nodeDetails() {
    return d3.select(this.$el).select("div.node-details");
  }

  public get componentHeight() {
    return (this.$refs.svg as Element).clientHeight;
  }

  public get componentWidth() {
    return (this.$refs.svg as Element).clientWidth;
  }
  public get linkToBeCreated() {
    return this.rootSvg.select("line.linkToBeCreated");
  }

  public get nodeToBeCreated() {
    return this.rootSvg.select("circle.nodeToBeCreated");
  }

  public nodeColor(nodeType: ElementType) {
    return (nodeType & ElementType.Regular) == ElementType.Regular
      ? "black"
      : (nodeType & ElementType.Join) == ElementType.Join
      ? "red"
      : "blue";
  }

  public runSimulation() {
    this.simulation?.nodes(this.casualNet.nodes);
    this.simulation
      ?.force<ForceLink<Node, Link>>("link")
      ?.links(this.casualNet.links);
    this.simulation?.alphaTarget(0.3).restart();
  }

  public nodes(filterExpression: ((link: Node) => boolean) | null = null) {
    const selection = this.nodesLayer.selectAll("g") as Selection<
      SVGGElement,
      Node,
      SVGGElement,
      unknown
    >;

    return filterExpression != null
      ? selection.filter(filterExpression)
      : selection;
  }

  public links(filterExpression: ((link: Link) => boolean) | null = null) {
    const selection = this.linksLayer.selectAll("path") as Selection<
      Element | EnterElement | Document | Window | SVGPathElement | null,
      Link,
      SVGGElement,
      unknown
    >;

    return filterExpression != null
      ? selection.filter(filterExpression)
      : selection;
  }

  mounted() {
    const isLayoutPredefined =
      this.data.nodes.length == this.data.layout?.length &&
      this.data.nodes.every(node =>
        this.data.layout?.some(nodeLayout => nodeLayout.id == node.id)
      );

    const nodesLayout =
      isLayoutPredefined && this.data.layout != null
        ? this.data.layout?.reduce(
            (
              layout: Map<string, Point>,
              node: {
                id: string;
                x: number;
                y: number;
              }
            ) => {
              layout.set(node.id, { x: node.x, y: node.y });
              return layout;
            },
            new Map()
          )
        : this.calculateLayout();

    this.casualNet = new CasualNet(
      this.data.nodes,
      this.data.edges,
      nodesLayout
    );

    this.userInputHandler = new InteractiveModeInputHandler(this);

    this.simulation = d3.forceSimulation<Node, Link>().force(
      "link",
      d3
        .forceLink()
        .id(d => (d as Link).id)
        .distance(d => ((d as Link).isLoopLink ? 20 : 0.1))
        .strength(d =>
          (d as Link).hasType(ElementType.Binding)
            ? 0
            : (d as Link).isBetweenSplitAndJoin
            ? 0.3
            : 1
        )
    );

    this.rootSvg
      .on("click", () =>
        this.userInputHandler.backgroundClick(this.getMousePosition())
      )
      .on("mousemove", () =>
        this.userInputHandler.backgroundMousemove(this.getMousePosition())
      );

    this.arrowheads
      .append("path")
      .attr("d", "M 0 0 10 4 0 8 2 4")
      .style("fill", "#999");

    this.refreshLinks();

    this.links()
      .filter(d => d.displayArrow || false)
      .attr("marker-end", `url(#${this.arrowheads.attr("id")})`);

    this.refreshNodes();

    this.nodeToBeCreated
      .attr("transform", "translate(-100, -100)")
      .style("pointer-events", "none");

    const scalingFactor = this.calculateScalingFactor(
      (this.$refs.svg as Element).clientWidth,
      (this.$refs.svg as Element).clientHeight
    );

    this.scaleElements(scalingFactor);

    this.simulation.on("tick", () => {
      this.links().attr("d", this.linkArc);
      this.nodes().attr(
        "transform",
        d => `translate(${d.x as number},${d.y as number})`
      );
    });
    this.runSimulation();
  }

  public refreshNodes() {
    this.nodes()
      .data(this.casualNet.nodes, d => (d as Node).id)
      .join(
        enter =>
          enter
            .append("g")
            .on("mouseover", d => this.userInputHandler.nodeMouseover(d))
            .on("mouseout", d => this.userInputHandler.nodeMouseout(d))
            .on("click", d => {
              this.userInputHandler.nodeClick(d);
              d3.event.stopPropagation();
            })
            .call(
              drag<SVGGElement, Node, SVGGElement>()
                .on("start", d => this.userInputHandler.nodeDragstarted(d))
                .on("drag", d => this.userInputHandler.nodeDragged(d, d3.event))
                .on("end", d =>
                  this.userInputHandler.nodeDragended(d, d3.event)
                )
            )
            .append("path")
            .attr("opacity", 1)
            .style("cursor", "pointer")
            .style("fill", d => this.nodeColor(d.nodeType)),
        update => update,
        exit =>
          exit.call(exit =>
            exit
              .transition()
              .duration(300)
              .style("opacity", 0)
              .remove()
          )
      );
  }

  public refreshLinks() {
    this.links()
      .data(this.casualNet.links, d => (d as Link).id)
      .join(
        enter =>
          enter
            .append("path")
            .attr("stroke-opacity", d =>
              d.hasType(ElementType.Binding) ? 0.5 : 1
            )
            .on("mouseover", d =>
              this.userInputHandler.linkMouseover(d, this.getMousePosition())
            )
            .on("mouseout", d => this.userInputHandler.linkMouseout(d))
            .on("click", d => {
              this.userInputHandler.linkClick(d, this.getMousePosition());
              d3.event.stopPropagation();
            }),
        update => update,
        exit =>
          exit.call(exit =>
            exit
              .transition()
              .duration(300)
              .style("opacity", 0)
              .remove()
          )
      );
  }

  public scaleElements(scalingFactor = this.currentScalingFactor) {
    if (scalingFactor == Number.POSITIVE_INFINITY) return;

    this.arrowheads
      .attr(
        "markerWidth",
        this.displayPreferences.edgeArrowSize * scalingFactor
      )
      .attr(
        "markerHeight",
        this.displayPreferences.edgeArrowSize * scalingFactor
      );

    this.links().attr(
      "stroke-width",
      d =>
        (d.hasType(ElementType.Binding)
          ? this.displayPreferences.bindingEdgeThickness
          : this.displayPreferences.edgeThickness) * scalingFactor
    );
    this.nodes().attr("stroke-width", scalingFactor);

    this.nodeShapes.attr("d", d =>
      d3
        .symbol()
        .type(d.isStartNode || d.isEndNode ? d3.symbolDiamond : d3.symbolCircle)
        .size(
          ((d.hasType(ElementType.Binding)
            ? this.displayPreferences.bindingNodeSize
            : this.displayPreferences.nodeSize) *
            scalingFactor) **
            2
        )()
    );
    this.nodeLabels.attr(
      "font-size",
      this.displayPreferences.nodeLabelSize * scalingFactor
    );

    this.currentScalingFactor = scalingFactor;
  }

  private setEditMode(newMode: EditMode | null) {
    if (this.editMode == null && newMode == null) return;

    this.editMode = this.editMode != newMode ? newMode : null;

    if (this.editMode == EditMode.Addition) {
      this.userInputHandler = new AdditionModeInputHandler(this);
    } else if (this.editMode == EditMode.Deletion) {
      this.userInputHandler = new DeletionModeInputHandler(this);
    } else {
      this.userInputHandler = new InteractiveModeInputHandler(this);
    }
  }

  private onResize(element: Element) {
    const scalingFactor = this.calculateScalingFactor(
      element.clientWidth,
      element.clientHeight
    );

    this.scaleElements(scalingFactor);
  }

  private rearrangeNodes() {
    const nodesLayout = this.calculateLayout();

    this.casualNet.nodes.forEach(node => {
      const nodePosition = nodesLayout.get(node.id);
      if (nodePosition == null) return;

      node.fx = nodePosition.x;
      node.fy = nodePosition.y;
    });

    this.simulation?.alphaTarget(0.3).restart();
  }

  private calculateLayout() {
    const networkGraph = new dagre.graphlib.Graph()
      .setGraph({
        marginx: this.displayPreferences.nodeSize,
        marginy: this.displayPreferences.nodeSize,
        acyclicer: "greedy"
      })
      .setDefaultEdgeLabel(() => {
        return new Map();
      });

    this.data.nodes.forEach((dataNode: DataNode) => {
      const successors = new Set(dataNode.splits.flat());
      successors.forEach(successor => {
        const targetNode = this.data.nodes.find(
          (node: DataNode) => node.id == successor
        );
        if (targetNode != null) {
          networkGraph.setEdge(dataNode.id, targetNode.id);
        }
      });
    });

    this.data.nodes.forEach((dataNode: DataNode) => {
      networkGraph.setNode(dataNode.id, { label: dataNode.id });
    });

    dagre.layout(networkGraph);
    const layoutWidth = networkGraph.graph().width || this.contentWidth,
      layoutHeight = networkGraph.graph().height || this.contentHeight,
      scaleX = this.contentWidth / layoutWidth,
      scaleY = this.contentHeight / layoutHeight,
      offsetX = Math.max(this.contentWidth - layoutWidth * scaleX, 0) / 2,
      offsetY = Math.max(this.contentHeight - layoutHeight * scaleY, 0) / 2;

    return networkGraph
      .nodes()
      .reduce((layout: Map<string, Point>, nodeId: string) => {
        const node = networkGraph.node(nodeId);
        layout.set(nodeId, {
          x: node.x * scaleX + offsetX,
          y: node.y * scaleY + offsetY
        });
        return layout;
      }, new Map());
  }

  private linkArc(link: Link) {
    const sourceNode = link.source as Node,
      targetNode = link.target as Node;

    if (sourceNode == null || targetNode == null) return "";

    if (link.isLoopLink || false) {
      const r = Math.hypot(
        targetNode.x || 0 - (sourceNode.x || 0),
        targetNode.y || 0 - (sourceNode.y || 0)
      );
      return `
        M${sourceNode.x},${sourceNode.y}
        A${r},${r} 0 0,1 ${targetNode.x},${targetNode.y}
    `;
    } else {
      return `
        M${sourceNode.x},${sourceNode.y}
        L${targetNode.x},${targetNode.y}
    `;
    }
  }

  private calculateScalingFactor(width: number, height: number) {
    return Math.max(this.contentWidth / width, this.contentHeight / height);
  }

  private getMousePosition() {
    const [x, y] = d3.mouse(this.$refs.svg as ContainerElement);
    return { x, y } as Point;
  }
}
</script>
