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
      <g class="links" :stroke="displayPreferences.edgeColor" fill="none" />
      <g class="nodes" stroke="#fff" />
      <circle
        class="nodeToBeCreated"
        opacity="0.5"
        :r="displayPreferences.nodeSize"
        :style="{ display: editMode == EditMode.Addition ? 'inline' : 'none' }"
      />
      <line
        class="linkToBeCreated"
        display="none"
        :stroke-width="displayPreferences.edgeThickness * currentScalingFactor"
        :stroke="displayPreferences.edgeColor"
        opacity="0.3"
      />
      <defs>
        <marker
          :id="`arrow${arrowMarkerId}`"
          markerUnits="userSpaceOnUse"
          :viewBox="
            `0 0 ${displayPreferences.edgeArrowMaximumSize} ${displayPreferences.edgeArrowMaximumSize}`
          "
          orient="auto"
          :refX="
            `${displayPreferences.edgeArrowHeight +
              displayPreferences.nodeSize}`
          "
          :refY="`${displayPreferences.edgeArrowWidth / 2}`"
          :markerWidth="`${displayPreferences.edgeArrowMaximumSize}`"
          :markerHeight="`${displayPreferences.edgeArrowMaximumSize}`"
        >
          <path
            :d="
              `M 0 0
              ${
                displayPreferences.edgeArrowHeight
              } ${displayPreferences.edgeArrowWidth / 2}
              0 ${displayPreferences.edgeArrowWidth} 
              ${displayPreferences.edgeArrowWidth /
                2} ${displayPreferences.edgeArrowWidth / 2}`
            "
            :fill="`${this.displayPreferences.edgeColor}`"
          />
        </marker>
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
      <template #activator>
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

<style>
.node-name {
  alignment-baseline: middle;
  font-family: Arial, Helvetica, sans-serif;
  paint-order: stroke;
  font-size: 60%;
}

.node-name-background {
  fill: white;
  opacity: 0.6;
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
import { v4 as uuidv4 } from "uuid";
import { ComponentMode } from "../WorkspaceComponent.vue";
import CausalNet, { Point, Node, Link, ElementType } from "./CausalNet";
import {
  UserInputSource,
  UserInputHandler,
  AdditionModeInputHandler,
  DeletionModeInputHandler,
  InteractiveModeInputHandler
} from "./UserInputHandlers";
import WorkspaceComponentModel, {
  CausalNetComponentData,
  CausalNetComponentCustomizationData
} from "../../../models/WorkspaceComponent";

enum EditMode {
  Addition,
  Deletion
}

@Component({
  directives: {
    resize
  }
})
export default class CausalNetComponent extends Vue implements UserInputSource {
  ComponentMode = ComponentMode;
  EditMode = EditMode;

  @Prop({ default: {} })
  readonly data!: WorkspaceComponentModel<
    CausalNetComponentData,
    CausalNetComponentCustomizationData
  >;
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  private readonly arrowMarkerId = uuidv4();
  private editMode: EditMode | null = null;
  private userInputHandler: UserInputHandler | null = null;
  private simulation: Simulation<Node, Link> | undefined;

  private rootSvg(): Selection<BaseType, unknown, null, undefined> {
    return d3.select(this.$el).select("svg");
  }

  private nodesLayer() {
    return this.rootSvg().select("g.nodes");
  }

  private nodeShapes() {
    return this.nodes().select("g") as Selection<
      SVGPathElement,
      Node,
      SVGGElement,
      unknown
    >;
  }

  private bindingNodeShapes() {
    return this.nodes().select("path") as Selection<
      SVGPathElement,
      Node,
      SVGGElement,
      unknown
    >;
  }

  private nodeLabels() {
    return this.nodes().select("text") as Selection<
      SVGTextElement,
      Node,
      SVGGElement,
      unknown
    >;
  }

  private linksLayer() {
    return this.rootSvg().select("g.links");
  }

  private arrowheads(): Selection<BaseType, unknown, null, undefined> {
    return this.rootSvg()
      .select("defs")
      .select("marker");
  }

  public causalNet!: CausalNet;
  public currentScalingFactor = 1;
  public readonly contentHeight: number = 250;
  public readonly contentWidth: number = 250;
  public displayPreferences = {
    nodeSize: 4,
    bindingNodeSize: 2,
    edgeThickness: 2,
    bindingEdgeThickness: 1,
    edgeArrowHeight: 7,
    edgeArrowWidth: 5,
    nodeLabelSize: 16,
    regularNodeColor: "black",
    splitNodeColor: "blue",
    joinNodeColor: "green",
    edgeColor: "grey",
    get edgeArrowMaximumSize() {
      return Math.max(this.edgeArrowHeight, this.edgeArrowWidth);
    }
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
    return this.rootSvg().select("line.linkToBeCreated");
  }

  public get nodeToBeCreated() {
    return this.rootSvg().select("circle.nodeToBeCreated");
  }

  public nodeColor(nodeType: ElementType) {
    return (nodeType & ElementType.Regular) == ElementType.Regular
      ? this.displayPreferences.regularNodeColor
      : (nodeType & ElementType.Split) == ElementType.Split
      ? this.displayPreferences.splitNodeColor
      : this.displayPreferences.joinNodeColor;
  }

  public runSimulation() {
    this.simulation?.nodes(this.causalNet.nodes).force(
      "charge",
      d3
        .forceManyBody()
        .strength(d => ((d as Node).hasType(ElementType.Regular) ? -1 : -5))
    );
    this.simulation
      ?.force<ForceLink<Node, Link>>("link")
      ?.links(this.causalNet.links);
    this.simulation?.alphaTarget(0.3).restart();
  }

  public nodes(filterExpression: ((link: Node) => boolean) | null = null) {
    const selection = this.nodesLayer().selectAll("g") as Selection<
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
    const selection = this.linksLayer().selectAll("path") as Selection<
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
    if (this.data.data == null) {
      throw new Error("Component data field must not be null");
    }

    this.causalNet = new CausalNet(
      this.data.data.nodes,
      this.data.data.edges,
      this.data.customizationData?.layout,
      this.nodeTransition
    );

    if (this.data.customizationData?.layout == null) {
      this.data.customizationData = {
        layout: Array.from(this.causalNet.nodesLayout).map(([id, point]) => ({
          id,
          x: point.x,
          y: point.y
        }))
      };
    }

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

    this.rootSvg()
      .on("click", () =>
        this.userInputHandler?.backgroundClick(this.getMousePosition())
      )
      .on("mousemove", () =>
        this.userInputHandler?.backgroundMousemove(this.getMousePosition())
      );

    this.refreshLinks();
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
    this.setEditMode(null);
  }

  public refreshNodes() {
    this.nodes()
      .data(this.causalNet.nodes, d => (d as Node).id)
      .join(
        enter => {
          const enteredItems = enter
            .append("g")
            .on("mouseover", d => this.userInputHandler?.nodeMouseover(d))
            .on("mouseout", d => this.userInputHandler?.nodeMouseout(d))
            .on("click", d => {
              this.userInputHandler?.nodeClick(d);
              d3.event.stopPropagation();
            })
            .call(
              drag<SVGGElement, Node, SVGGElement>()
                .on("start", d => this.userInputHandler?.nodeDragstarted(d))
                .on("drag", d =>
                  this.userInputHandler?.nodeDragged(d, d3.event)
                )
                .on("end", d =>
                  this.userInputHandler?.nodeDragended(d, d3.event)
                )
            );

          enteredItems
            .filter(d => d.hasType(ElementType.Binding))
            .append("path")
            .attr("opacity", 1)
            .attr(
              "d",
              d3
                .symbol()
                .type(d3.symbolCircle)
                .size((this.displayPreferences.bindingNodeSize * 2) ** 2)
            )
            .style("cursor", "pointer")
            .style("fill", d => this.nodeColor(d.nodeType));
          enteredItems
            .filter(d => d.hasType(ElementType.Regular))
            .append("rect")
            .attr("class", "node-name-background")
            .attr(
              "width",
              d =>
                d.id.length * 3 +
                this.displayPreferences.nodeSize * 3.6 +
                this.displayPreferences.nodeSize * 0.2
            )
            .attr("height", this.displayPreferences.nodeSize * 2 * 1.2)
            .attr("stroke", this.displayPreferences.regularNodeColor)
            .attr("rx", this.displayPreferences.nodeSize)
            .attr("x", -this.displayPreferences.nodeSize * 1.2)
            .attr("y", -this.displayPreferences.nodeSize * 1.2);
          enteredItems
            .filter(d => d.hasType(ElementType.Regular))
            .append("circle")
            .attr("r", this.displayPreferences.nodeSize);
          enteredItems
            .append("text")
            .attr("class", "node-name")
            .attr("x", this.displayPreferences.nodeSize * 1.2)
            .attr(
              "textLength",
              d => d.id.length * 3 + this.displayPreferences.nodeSize * 1.2
            )
            .text(d => (d.hasType(ElementType.Regular) ? d.id : ""));

          return enteredItems;
        },
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
      .data(this.causalNet.links, d => (d as Link).id)
      .join(
        enter =>
          enter
            .append("path")
            .attr("stroke-opacity", d =>
              d.hasType(ElementType.Binding) ? 0.5 : 0.7
            )
            .attr("marker-end", d =>
              d.isTargetNodeRegular() ? `url(#arrow${this.arrowMarkerId})` : ""
            )
            .on("mouseover", d =>
              this.userInputHandler?.linkMouseover(d, this.getMousePosition())
            )
            .on("mouseout", d => this.userInputHandler?.linkMouseout(d))
            .on("click", d => {
              this.userInputHandler?.linkClick(d, this.getMousePosition());
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

    this.links().attr(
      "stroke-width",
      d =>
        (d.hasType(ElementType.Binding)
          ? this.displayPreferences.bindingEdgeThickness
          : this.displayPreferences.edgeThickness) * scalingFactor
    );
    this.nodes().attr("stroke-width", scalingFactor);
    this.nodeLabels().attr(
      "font-size",
      this.displayPreferences.nodeLabelSize * scalingFactor
    );

    this.currentScalingFactor = scalingFactor;
  }

  public updateNodeLayoutPosition(nodeId: string, position: Point): void {
    if (this.data.customizationData?.layout == null) return;

    const nodeIndex = this.data.customizationData.layout.findIndex(
      node => node.id == nodeId
    );

    if (nodeIndex < 0) {
      this.data.customizationData.layout.push({
        id: nodeId,
        x: position.x,
        y: position.y
      });
    } else {
      const node = this.data.customizationData?.layout[nodeIndex];

      node.x = position.x;
      node.y = position.y;
    }
  }

  private setEditMode(newMode: EditMode | null) {
    if (this.componentMode == ComponentMode.Edit) {
      this.editMode = this.editMode != newMode ? newMode : null;

      if (this.editMode == EditMode.Addition) {
        this.userInputHandler = new AdditionModeInputHandler(this);
      } else if (this.editMode == EditMode.Deletion) {
        this.userInputHandler = new DeletionModeInputHandler(this);
      } else {
        this.userInputHandler = new InteractiveModeInputHandler(this);
      }
    } else {
      this.editMode = null;
      this.userInputHandler =
        this.componentMode == ComponentMode.Interactive
          ? new InteractiveModeInputHandler(this)
          : null;
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
    this.causalNet.recalculateLayout();
    this.simulation?.alphaTarget(0.3).restart();
  }

  private nodeTransition(
    nodePosition: Point,
    layoutWidth?: number,
    layoutHeight?: number
  ) {
    const scaleX = this.contentWidth / (layoutWidth || this.contentWidth),
      scaleY = this.contentHeight / (layoutHeight || this.contentHeight),
      offsetX =
        Math.max(
          this.contentWidth - (layoutWidth || this.contentWidth) * scaleX,
          0
        ) / 2,
      offsetY =
        Math.max(
          this.contentHeight - (layoutHeight || this.contentHeight) * scaleY,
          0
        ) / 2;

    return {
      x: nodePosition.x * scaleX + offsetX,
      y: nodePosition.y * scaleY + offsetY
    };
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
