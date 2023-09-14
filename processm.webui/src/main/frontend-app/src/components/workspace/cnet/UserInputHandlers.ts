import CNet, { Node, Link, ElementType, Point } from "./CNet";
import { Selection, BaseType } from "d3";

function validateBoundaries(
  location: Point,
  maxWidth: number,
  maxHeight: number
) {
  return {
    x: Math.min(Math.max(location.x, 0), maxWidth),
    y: Math.min(Math.max(location.y, 0), maxHeight)
  } as Point;
}

export interface UserInputSource {
  readonly linkToBeCreated: Selection<BaseType, unknown, null, undefined>;
  readonly nodeToBeCreated: Selection<BaseType, unknown, null, undefined>;
  readonly nodeDetails: Selection<BaseType, unknown, null, undefined>;
  readonly componentHeight: number;
  readonly componentWidth: number;
  readonly contentHeight: number;
  readonly contentWidth: number;
  //readonly causalNet: CausalNet;
  readonly CNet: CNet;
  readonly currentScalingFactor: number;
  readonly displayPreferences: {
    nodeSize: number;
    bindingNodeSize: number;
  };
  nodes(
    filterExpression?: ((link: Node) => boolean) | null
  ): Selection<SVGGElement, Node, SVGGElement, unknown>;
  links(
    filterExpression?: ((link: Link) => boolean) | null
  ): Selection<
    Element | d3.EnterElement | Document | Window | SVGPathElement | null,
    Link,
    SVGGElement,
    unknown
  >;
  nodeColor(nodeType: ElementType): string;
  refreshNodes(): void;
  refreshLinks(): void;
  updateNodeLayoutPosition(nodeId: string, position: Point): void;
  scaleElements(scalingFactor?: number): void;
  runSimulation(): void;
}

export interface UserInputHandler {
  backgroundClick(point: Point): void;
  backgroundMousemove(point: Point): void;
  nodeClick(eventNode: Node): void;
  linkClick(eventLink: Link, point: Point): void;
  nodeMouseover(eventNode: Node): void;
  nodeMouseout(eventNode: Node): void;
  linkMouseover(eventLink: Link, point: Point): void;
  linkMouseout(eventLink: Link): void;
  nodeDragstarted(eventNode: Node): void;
  nodeDragged(eventNode: Node, point: Point): void;
  nodeDragended(eventNode: Node, point: Point): void;
}

export class AdditionModeInputHandler implements UserInputHandler {
  constructor(private component: UserInputSource) {
    this.nodeToBeCreated = this.component.nodeToBeCreated;
    this.linkToBeCreated = this.component.linkToBeCreated;
  }

  private newLinkTargetNode: Node | null = null;
  private readonly nodeToBeCreated: Selection<
    BaseType,
    unknown,
    null,
    undefined
  >;
  private readonly linkToBeCreated: Selection<
    BaseType,
    unknown,
    null,
    undefined
  >;

  backgroundClick(point: Point) {
    const normalizedPoint = validateBoundaries(
      point,
      this.component.contentWidth,
      this.component.contentHeight
    );
    this.component.causalNet.addRegularNode(normalizedPoint);
    this.component.refreshNodes();
    this.component.scaleElements();
    this.component.runSimulation();
  }

  backgroundMousemove(point: Point) {
    const { x, y } = validateBoundaries(
      point,
      this.component.contentWidth,
      this.component.contentHeight
    );
    this.nodeToBeCreated.attr("transform", `translate(${x}, ${y})`);
  }

  nodeClick() {
    return;
  }

  linkClick(eventLink: Link, point: Point) {
    if (eventLink.hasType(ElementType.Binding)) return;

    const nodeType = this.selectTypeOfBindingNodeToCreate(eventLink, point);

    this.component.causalNet.addBindingNode(nodeType, eventLink, point);
    this.component.refreshNodes();
    this.component.refreshLinks();
    this.component.scaleElements();
    this.component.runSimulation();
  }

  nodeMouseover(eventNode: Node) {
    this.hideNodeToBeCreated();
    this.newLinkTargetNode = eventNode;
    this.component
      .nodes((node) => node.id == eventNode.id)
      .attr("stroke", (d) => this.component.nodeColor(d.nodeType));
  }

  nodeMouseout(eventNode: Node) {
    this.showNodeToBeCreated();
    this.newLinkTargetNode = null;
    this.component
      .nodes((node) => node.id == eventNode.id)
      .attr("stroke", "#fff");
  }

  linkMouseover(eventLink: Link, point: Point) {
    if (eventLink.hasType(ElementType.Binding)) return;

    const nodeType = this.selectTypeOfBindingNodeToCreate(eventLink, point);
    this.applyElementTypeToNodeToBeCreated(nodeType);
  }

  linkMouseout() {
    this.applyElementTypeToNodeToBeCreated(ElementType.Regular);
  }

  nodeDragstarted(eventNode: Node) {
    this.linkToBeCreated
      .style("display", "inline")
      .attr("x1", eventNode.x as number)
      .attr("y1", eventNode.y as number)
      .attr("x2", eventNode.x as number)
      .attr("y2", eventNode.y as number);
  }

  nodeDragged(eventNode: Node, point: Point) {
    this.linkToBeCreated.attr("x2", point.x).attr("y2", point.y);
    this.hideNodeToBeCreated();
  }

  nodeDragended(eventNode: Node, point: Point) {
    this.showNodeToBeCreated();
    this.nodeToBeCreated.attr("transform", `translate(${point.x}, ${point.y})`);
    this.linkToBeCreated.style("display", "none");
    if (
      this.newLinkTargetNode != null &&
      eventNode.nodeType == this.newLinkTargetNode.nodeType
    ) {
      if (eventNode.nodeType == ElementType.Regular) {
        this.component.causalNet.addRegularLink(
          eventNode,
          this.newLinkTargetNode
        );
        this.component.refreshNodes();
      } else {
        this.component.causalNet.addBindingLink(
          eventNode,
          this.newLinkTargetNode
        );
      }
      this.component.refreshLinks();
      this.component.scaleElements();
      this.component.runSimulation();
    }
  }

  private showNodeToBeCreated() {
    this.nodeToBeCreated.style("display", "inline");
  }

  private hideNodeToBeCreated() {
    this.nodeToBeCreated.style("display", "none");
  }

  private applyElementTypeToNodeToBeCreated(nodeType: ElementType) {
    const nodeColor = this.component.nodeColor(nodeType);
    this.nodeToBeCreated
      .attr("fill", nodeColor)
      .attr(
        "r",
        nodeType == ElementType.Regular
          ? this.component.displayPreferences.nodeSize
          : this.component.displayPreferences.bindingNodeSize
      );
  }

  private selectTypeOfBindingNodeToCreate(link: Link, point: Point) {
    const sourceNode = link.source as Node,
      targetNode = link.target as Node;

    if (sourceNode.nodeType == targetNode.nodeType) {
      return sourceNode.nodeType;
    } else if (sourceNode.hasType(ElementType.Regular)) {
      return targetNode.nodeType;
    } else if (targetNode.hasType(ElementType.Regular)) {
      return sourceNode.nodeType;
    } else {
      return Math.hypot(
        (sourceNode.x || 0) - point.x,
        (sourceNode.y || 0) - point.y
      ) <
        Math.hypot((targetNode.x || 0) - point.x, (targetNode.y || 0) - point.y)
        ? sourceNode.nodeType
        : targetNode.nodeType;
    }
  }
}

export class DeletionModeInputHandler implements UserInputHandler {
  constructor(private component: UserInputSource) {}

  backgroundClick() {
    return;
  }

  backgroundMousemove() {
    return;
  }

  nodeClick(eventNode: Node): void {
    const isBindingNode = eventNode.hasType(ElementType.Binding);
    const intermediateLinksToBeRemoved = isBindingNode
      ? []
      : this.component.causalNet.links.filter(
          (link) =>
            link.indirectSourceNodeId == eventNode.id ||
            link.indirectTargetNodeId == eventNode.id
        );
    const bindingNodesToBeRemoved = isBindingNode
      ? new Set([eventNode.id])
      : this.selectBindingNodesToBeRemoved(...intermediateLinksToBeRemoved);
    const bindingLinksToBeRemoved = this.selectBindingLinksToBeRemoved(
      bindingNodesToBeRemoved
    );

    const nodesToBeRemoved = new Set([
      ...bindingNodesToBeRemoved,
      eventNode.id
    ]);
    const linksToBeRemoved = new Set([
      ...intermediateLinksToBeRemoved.map((link) => link.id),
      ...bindingLinksToBeRemoved
    ]);

    this.component.causalNet.removeNodes((node) =>
      nodesToBeRemoved.has(node.id)
    );
    this.component.causalNet.removeLinks((link) =>
      linksToBeRemoved.has(link.id)
    );
    this.component.refreshNodes();
    this.component.refreshLinks();
  }

  linkClick(eventLink: Link): void {
    const isBindingLink = eventLink.hasType(ElementType.Binding);
    const intermediateLinksToBeRemoved = isBindingLink
      ? []
      : this.component.causalNet.links.filter(
          (link) => link.groupId == eventLink.groupId
        );
    const bindingNodesToBeRemoved = isBindingLink
      ? new Set<string>()
      : this.selectBindingNodesToBeRemoved(...intermediateLinksToBeRemoved);
    const bindingLinksToBeRemoved = isBindingLink
      ? new Set([eventLink.id])
      : this.selectBindingLinksToBeRemoved(bindingNodesToBeRemoved);

    this.markElementsToBeRemoved(
      bindingNodesToBeRemoved,
      new Set([
        ...intermediateLinksToBeRemoved.map((link) => link.id),
        ...bindingLinksToBeRemoved
      ])
    );

    const linksToBeRemoved = new Set([
      ...intermediateLinksToBeRemoved.map((link) => link.id),
      ...bindingLinksToBeRemoved
    ]);

    this.component.causalNet.removeNodes((node) =>
      bindingNodesToBeRemoved.has(node.id)
    );
    this.component.causalNet.removeLinks((link) =>
      linksToBeRemoved.has(link.id)
    );
    this.component.refreshNodes();
    this.component.refreshLinks();
  }

  nodeMouseover(eventNode: Node): void {
    const isBindingNode = eventNode.hasType(ElementType.Binding);
    const intermediateLinksToBeRemoved = isBindingNode
      ? []
      : this.component.causalNet.links.filter(
          (link) =>
            link.indirectSourceNodeId == eventNode.id ||
            link.indirectTargetNodeId == eventNode.id
        );
    const bindingNodesToBeRemoved = isBindingNode
      ? new Set([eventNode.id])
      : this.selectBindingNodesToBeRemoved(...intermediateLinksToBeRemoved);
    const bindingLinksToBeRemoved = this.selectBindingLinksToBeRemoved(
      bindingNodesToBeRemoved
    );

    this.markElementsToBeRemoved(
      new Set([...bindingNodesToBeRemoved, eventNode.id]),
      new Set([
        ...intermediateLinksToBeRemoved.map((link) => link.id),
        ...bindingLinksToBeRemoved
      ])
    );
  }

  nodeMouseout() {
    this.unmarkElementsToBeRemoved();
  }

  linkMouseover(eventLink: Link): void {
    const isBindingLink = eventLink.hasType(ElementType.Binding);
    const intermediateLinksToBeRemoved = isBindingLink
      ? []
      : this.component.causalNet.links.filter(
          (link) => link.groupId == eventLink.groupId
        );
    const bindingNodesToBeRemoved = isBindingLink
      ? new Set<string>()
      : this.selectBindingNodesToBeRemoved(...intermediateLinksToBeRemoved);
    const bindingLinksToBeRemoved = isBindingLink
      ? new Set([eventLink.id])
      : this.selectBindingLinksToBeRemoved(bindingNodesToBeRemoved);

    this.markElementsToBeRemoved(
      bindingNodesToBeRemoved,
      new Set([
        ...intermediateLinksToBeRemoved.map((link) => link.id),
        ...bindingLinksToBeRemoved
      ])
    );
  }
  linkMouseout() {
    this.unmarkElementsToBeRemoved();
  }

  nodeDragstarted() {
    return;
  }

  nodeDragged() {
    return;
  }

  nodeDragended() {
    return;
  }

  private markElementsToBeRemoved(nodes: Set<string>, links: Set<string>) {
    this.component
      .nodes()
      .filter((node) => nodes.has(node.id))
      .transition()
      .duration(200)
      .style("opacity", 0.3);
    this.component
      .links()
      .filter((link) => links.has(link.id))
      .transition()
      .duration(200)
      .style("opacity", 0.3);
  }

  private unmarkElementsToBeRemoved() {
    this.component.nodes().transition().duration(500).style("opacity", 1);
    this.component.links().transition().duration(500).style("opacity", 1);
  }

  private selectBindingNodesToBeRemoved(...links: Link[]) {
    return links.reduce(
      (bindingNodes: Set<string>, link: Link) =>
        (link.target as Node | null)?.hasType(ElementType.Binding)
          ? bindingNodes.add((link.target as Node).id)
          : bindingNodes,
      new Set()
    );
  }

  private selectBindingLinksToBeRemoved(bindingNodes: Set<string>) {
    return Array.from(
      this.component.causalNet.links.reduce(
        (groupedBindingLinks: Map<string, Array<string>>, link: Link) => {
          if (!link.hasType(ElementType.Binding)) return groupedBindingLinks;

          const sourceNodeId = (link.source as Node).id,
            targetNodeId = (link.target as Node).id;

          if (bindingNodes.has((link.source as Node).id)) {
            if (!groupedBindingLinks.has(sourceNodeId)) {
              groupedBindingLinks.set(sourceNodeId, []);
            }
            groupedBindingLinks.get(sourceNodeId)?.push(link.id);
          }

          if (bindingNodes.has((link.target as Node).id)) {
            if (!groupedBindingLinks.has(targetNodeId)) {
              groupedBindingLinks.set(targetNodeId, []);
            }
            groupedBindingLinks.get(targetNodeId)?.push(link.id);
          }

          return groupedBindingLinks;
        },
        new Map()
      )
    ).reduce(
      (linksToBeRemoved: Set<string>, [, bindingLinks]: [string, string[]]) =>
        bindingLinks.length == 1
          ? linksToBeRemoved.add(bindingLinks[0])
          : linksToBeRemoved,
      new Set()
    );
  }
}

export class InteractiveModeInputHandler implements UserInputHandler {
  constructor(private component: UserInputSource) {
    this.nodeDetails = this.component.nodeDetails;
  }

  private readonly nodeDetails: Selection<BaseType, unknown, null, undefined>;

  backgroundClick() {
    return;
  }

  backgroundMousemove() {
    return;
  }

  nodeClick() {
    return;
  }

  linkClick() {
    return;
  }

  nodeMouseover(eventNode: Node): void {
    const selectedNodes = eventNode.hasType(ElementType.Binding)
      ? this.component.nodes(
          (node) => node.bindingLinkId == eventNode.bindingLinkId
        )
      : this.component.nodes((node) => node.id == eventNode.id);
    selectedNodes.attr("stroke", (d) => this.component.nodeColor(d.nodeType));

    if (selectedNodes.size() > 1) {
      this.component
        .links((link) => link.groupId == eventNode.bindingLinkId)
        .attr("stroke-opacity", "1");
    }
    this.nodeDetails
      .html(`ID: ${eventNode.id}\nX: ${eventNode.x}\nY: ${eventNode.y}`)
      .style(
        "left",
        `${
          this.convertToAbsolutePercentage(
            (eventNode.x as number) / this.component.contentWidth,
            this.component.componentWidth
          ) * 100
        }%`
      )
      .style(
        "top",
        `${
          this.convertToAbsolutePercentage(
            (eventNode.y as number) / this.component.contentHeight,
            this.component.componentHeight
          ) * 100
        }%`
      )
      .transition()
      .duration(200)
      .style("opacity", 0.8);
  }

  nodeMouseout(eventNode: Node): void {
    const selectedNodes = eventNode.hasType(ElementType.Binding)
      ? this.component.nodes(
          (node) => node.bindingLinkId == eventNode.bindingLinkId
        )
      : this.component.nodes((node) => node.id == eventNode.id);
    selectedNodes.attr("stroke", "#fff");

    if (selectedNodes.size() > 1) {
      this.component
        .links((link) => link.groupId == eventNode.bindingLinkId)
        .attr("stroke-opacity", (d) =>
          d.hasType(ElementType.Binding) ? 0.5 : 0.7
        );
    }

    this.nodeDetails.transition().duration(500).style("opacity", 0);
  }

  linkMouseover(eventLink: Link): void {
    this.component
      .links((link) => link.groupId == eventLink.groupId)
      .attr("stroke-opacity", "1");
    (eventLink.hasType(ElementType.Binding)
      ? this.component.nodes((node) => node.bindingLinkId == eventLink.groupId)
      : this.component.nodes(
          (node) => node.intermediateLinkId == eventLink.groupId
        )
    ).attr("stroke", (d) => this.component.nodeColor(d.nodeType));
  }

  linkMouseout(eventLink: Link): void {
    this.component
      .links((link) => link.groupId == eventLink.groupId)
      .transition()
      .duration(500)
      .attr("stroke-opacity", (d) =>
        d.hasType(ElementType.Binding) ? 0.5 : 0.7
      );
    (eventLink.hasType(ElementType.Binding)
      ? this.component.nodes((node) => node.bindingLinkId == eventLink.groupId)
      : this.component.nodes(
          (node) => node.intermediateLinkId == eventLink.groupId
        )
    )
      .transition()
      .duration(500)
      .attr("stroke", "#fff");
  }

  nodeDragstarted(eventNode: Node): void {
    eventNode.fx = null;
    eventNode.fy = null;
  }

  nodeDragged(eventNode: Node, point: Point): void {
    ({ x: eventNode.fx, y: eventNode.fy } = validateBoundaries(
      point,
      this.component.contentWidth,
      this.component.contentHeight
    ));
  }

  nodeDragended(eventNode: Node): void {
    eventNode.fx = eventNode.x;
    eventNode.fy = eventNode.y;

    if (eventNode.x == null || eventNode.y == null) return;

    this.component.updateNodeLayoutPosition(eventNode.id, {
      x: eventNode.x,
      y: eventNode.y
    });
  }

  private convertToAbsolutePercentage(
    relativePercentage: number,
    elementSize: number
  ) {
    const minDimension = Math.min(
      this.component.componentHeight,
      this.component.componentWidth
    );
    const percentageScaling = minDimension / elementSize;
    const sizeOffset = 0.5 - percentageScaling / 2;

    return percentageScaling * relativePercentage + sizeOffset;
  }
}
