import { v4 as uuidv4 } from "uuid";
import { SimulationLinkDatum, SimulationNodeDatum } from "d3";
import dagre from "dagre";
import { Graph } from "graphlib";

export interface DataNode {
  id: string;
  joins: string[][];
  splits: string[][];
}

export interface DataLink {
  sourceNodeId: string;
  targetNodeId: string;
}

export enum ElementType {
  Regular = 0b001,
  Split = 0b0110,
  Join = 0b1010,
  Binding = Split & Join
}

export interface Point {
  x: number;
  y: number;
}

export class Node implements SimulationNodeDatum {
  constructor(
    public id: string,
    public nodeType: ElementType,
    public readonly isStartNode: boolean,
    public readonly isEndNode: boolean,
    init?: Partial<Node>
  ) {
    Object.assign(this, init);
  }

  public readonly relatedNodeId?: string;
  public readonly bindingLinkId?: string;
  public readonly intermediateLinkId?: string;
  public bindingSlot = 0;
  public index?: number;
  public x?: number;
  public y?: number;
  public vx?: number;
  public vy?: number;
  public fx?: number | null;
  public fy?: number | null;

  public hasType(type: ElementType) {
    return type === (this.nodeType & type);
  }
}

export class Link implements SimulationLinkDatum<Node> {
  constructor(
    public id: string,
    public source: Node | string,
    public target: Node | string,
    public readonly linkType: ElementType,
    public readonly groupId: string,
    init?: Partial<Link>
  ) {
    Object.assign(this, init);
  }

  public index?: number;
  public readonly indirectSourceNodeId?: string;
  public readonly indirectTargetNodeId?: string;

  public get isBetweenSplitAndJoin() {
    if (this.hasType(ElementType.Binding)) {
      return false;
    }

    const sourceNode = this.source as Node | null,
      targetNode = this.target as Node | null;

    if (
      !sourceNode?.hasType(ElementType.Binding) ||
      !targetNode?.hasType(ElementType.Binding)
    ) {
      return false;
    }

    return sourceNode?.nodeType != targetNode?.nodeType;
  }

  public get isLoopLink() {
    return (this.source as Node)?.id == (this.target as Node)?.id;
  }

  public isTargetNodeRegular() {
    const targetNodeId = (this.target as Node)?.id || (this.target as string);
    return targetNodeId == this.indirectTargetNodeId;
  }

  public hasType(type: ElementType) {
    return type === (this.linkType & type);
  }
}

export default class CNet {
  constructor(
    dataNodes: Array<DataNode>,
    private dataLinks: Array<DataLink>,
    layout?: Array<{ id: string; x: number; y: number }>,
    private nodeTransition:
      | ((
          nodePosition: Point,
          layoutWidth?: number,
          layoutHeight?: number
        ) => Point)
      | null = null
  ) {
    const isLayoutPredefined =
      layout != null &&
      dataNodes.length <= layout.length &&
      dataNodes.every((node) =>
        layout.some((nodeLayout) => nodeLayout.id == node.id)
      );

    this.nodesLayout =
      isLayoutPredefined && layout != null
        ? layout?.reduce(
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
        : this.calculateLayout(dataNodes, dataLinks);

    this.nodes = dataNodes.map((node, index) => {
      const x = this.nodesLayout.get(node.id)?.x,
        y = this.nodesLayout.get(node.id)?.y;
      return new Node(
        node.id,
        ElementType.Regular,
        index == 0,
        index == dataNodes.length - 1,
        { fx: x, fy: y, x, y }
      );
    });

    dataNodes.forEach((node) => {
      const nodePosition = this.nodesLayout.get(node.id);

      if (nodePosition == null) return;

      const groupedSplitNodes = this.createBindingNodes(
        node.id,
        node.splits,
        nodePosition,
        ElementType.Split
      );
      const groupedJoinNodes = this.createBindingNodes(
        node.id,
        node.joins,
        nodePosition,
        ElementType.Join
      );
      const splitNodes = Array.from(groupedSplitNodes.values()).flat();
      const joinNodes = Array.from(groupedJoinNodes.values()).flat();
      const splitLinks = this.createBindingLinks(
        node.id,
        ElementType.Split,
        splitNodes,
        this.nodesLayout
      );
      const joinLinks = this.createBindingLinks(
        node.id,
        ElementType.Join,
        joinNodes,
        this.nodesLayout
      );
      const intermediateSplitLinks = this.createIntermediateLinks(
        node.id,
        groupedSplitNodes
      );
      const intermediateJoinLinks = this.createIntermediateLinks(
        node.id,
        groupedJoinNodes
      );

      this.nodes.push(...splitNodes);
      this.nodes.push(...joinNodes);
      this.links.push(
        ...splitLinks.concat(
          joinLinks,
          intermediateSplitLinks,
          intermediateJoinLinks
        )
      );

      this.updateBindingNodesPositions(node.id, splitNodes, this.nodesLayout);
      this.updateBindingNodesPositions(node.id, joinNodes, this.nodesLayout);
    });
    this.links.push(...this.createLinksBetweenSplitsAndJoins(this.nodes));
  }

  private updateBindingNodesPositions(
    sourceNodeId: string,
    bindingNodes: Node[],
    nodesLayout: Map<string, Point>
  ) {
    const maximumBindingSlot = Math.max(
      ...bindingNodes.map((node) => node.bindingSlot)
    );

    if (!nodesLayout.has(sourceNodeId)) return;

    bindingNodes.forEach((bindingNode) => {
      if (
        bindingNode.relatedNodeId == null ||
        !nodesLayout.has(bindingNode.relatedNodeId)
      )
        return;

      const sourceNodePosition = nodesLayout.get(sourceNodeId);
      const targetNodePosition = nodesLayout.get(bindingNode.relatedNodeId);

      if (sourceNodePosition == null || targetNodePosition == null) return;

      const position = this.calculateBindingNodePosition(
        sourceNodePosition,
        targetNodePosition,
        bindingNode.bindingSlot,
        maximumBindingSlot
      );
      bindingNode.x = position.x;
      bindingNode.y = position.y;
    });
  }

  private calculateBindingNodePosition(
    sourceNodePosition: Point,
    targetNodePosition: Point,
    bindingSlot: number,
    maximumBindingSlot: number
  ) {
    const edgeSpaceUsageRatio = 4 / 9;
    const unit = edgeSpaceUsageRatio / (maximumBindingSlot + 1);
    const lambda = unit * bindingSlot + unit / 2;

    return {
      x: (1 - lambda) * sourceNodePosition.x + lambda * targetNodePosition.x,
      y: (1 - lambda) * sourceNodePosition.y + lambda * targetNodePosition.y
    };
  }

  public nodes: Array<Node>;
  public links: Array<Link> = [];
  public nodesLayout: Map<string, Point>;

  public removeNodes(filterExpression: (node: Node) => boolean) {
    this.nodes = this.nodes.filter((node) => !filterExpression(node));
  }

  public removeLinks(filterExpression: (link: Link) => boolean) {
    this.links = this.links.filter((link) => !filterExpression(link));
  }

  public addRegularLink(sourceNode: Node, targetNode: Node) {
    if (sourceNode.nodeType != targetNode.nodeType) return;

    const intermediateLinkId = this.createIntermediateLinkId(
      sourceNode.id,
      targetNode.id
    );
    const splitNode = new Node(uuidv4(), ElementType.Split, false, false, {
      relatedNodeId: targetNode.id,
      bindingLinkId: uuidv4(),
      intermediateLinkId
    });
    const joinNode = new Node(uuidv4(), ElementType.Join, false, false, {
      relatedNodeId: sourceNode.id,
      bindingLinkId: uuidv4(),
      intermediateLinkId
    });
    const indirectNodes = {
      indirectSourceNodeId: sourceNode.id,
      indirectTargetNodeId: targetNode.id
    };

    this.nodes.push(splitNode, joinNode);
    this.links.push(
      new Link(
        uuidv4(),
        sourceNode,
        splitNode,
        ElementType.Regular,
        intermediateLinkId,
        indirectNodes
      )
    );
    this.links.push(
      new Link(
        uuidv4(),
        splitNode,
        joinNode,
        ElementType.Regular,
        intermediateLinkId,
        indirectNodes
      )
    );
    this.links.push(
      new Link(
        uuidv4(),
        joinNode,
        targetNode,
        ElementType.Regular,
        intermediateLinkId,
        indirectNodes
      )
    );
  }

  public addBindingLink(sourceNode: Node, targetNode: Node) {
    const bindingId = sourceNode.bindingLinkId;

    if (bindingId == null || sourceNode.nodeType != targetNode.nodeType) return;

    this.links.push(
      new Link(uuidv4(), sourceNode, targetNode, sourceNode.nodeType, bindingId)
    );
  }

  public addRegularNode(point?: Point) {
    const id = uuidv4();
    this.nodes.push(
      new Node(id, ElementType.Regular, false, false, {
        fx: point?.x,
        fy: point?.y
      })
    );
  }

  public addBindingNode(
    nodeType: ElementType,
    intermediateLink: Link,
    point?: Point
  ) {
    const bindingSlot = 0;
    const sourceNode = intermediateLink.source;
    const newBindingNode = new Node(uuidv4(), nodeType, false, false, {
      relatedNodeId:
        (nodeType & ElementType.Split) == ElementType.Split
          ? intermediateLink.indirectTargetNodeId
          : intermediateLink.indirectSourceNodeId,
      bindingLinkId: uuidv4(),
      intermediateLinkId: intermediateLink.groupId,
      bindingSlot,
      x: point?.x,
      y: point?.y
    });
    intermediateLink.source = newBindingNode;
    this.nodes.push(newBindingNode);
    this.links.push(
      new Link(
        uuidv4(),
        sourceNode,
        newBindingNode,
        ElementType.Regular,
        intermediateLink.groupId,
        {
          indirectSourceNodeId: intermediateLink.indirectSourceNodeId,
          indirectTargetNodeId: intermediateLink.indirectTargetNodeId
        }
      )
    );
  }

  public recalculateLayout() {
    const newLayout = this.calculateLayout(
      this.nodes.filter((node) => node.hasType(ElementType.Regular)),
      this.dataLinks
    );

    this.nodes.forEach((node) => {
      const nodePosition = newLayout.get(node.id);
      if (nodePosition != null) {
        (node.fx = node.x = nodePosition.x),
          (node.fy = node.y = nodePosition.y);
      }
    });
  }

  private createBindingNodes(
    nodeId: string,
    bindings: string[][],
    nodePosition: Point,
    bindingType: ElementType
  ) {
    return bindings.reduce(
      (bindings: Map<string, Node[]>, binding: string[]) => {
        const bindingLinkId = uuidv4();

        bindings.set(
          bindingLinkId,
          binding.map((bindingNodeId) => {
            const intermediateLinkId =
              bindingType == ElementType.Split
                ? this.createIntermediateLinkId(nodeId, bindingNodeId)
                : this.createIntermediateLinkId(bindingNodeId, nodeId);

            return new Node(uuidv4(), bindingType, false, false, {
              bindingLinkId,
              intermediateLinkId,
              relatedNodeId: bindingNodeId,
              x: nodePosition.x,
              y: nodePosition.y
            });
          })
        );

        return bindings;
      },
      new Map()
    );
  }

  private createBindingLinks(
    nodeId: string,
    bindingType: ElementType,
    bindingNodes: Array<Node>,
    nodesLayout: Map<string, Point>
  ) {
    const nodePosition = nodesLayout.get(nodeId);

    if (nodePosition == null) return [];

    const angleToBindingNodes = bindingNodes.reduce(
      (angles: Map<string, number>, node: Node) => {
        if (!angles.has(node.id)) {
          const bindingNodePosition =
            nodesLayout.get(node.id) ||
            nodesLayout.get(node.relatedNodeId || "");
          if (bindingNodePosition == null) return angles;

          const angleToBindingNode =
            bindingType == ElementType.Split
              ? this.calculateAngleBetweenPoints(
                  nodePosition,
                  bindingNodePosition
                )
              : this.calculateAngleBetweenPoints(
                  bindingNodePosition,
                  nodePosition
                );
          angles.set(node.id, angleToBindingNode);
        }
        return angles;
      },
      new Map()
    );

    return Array.from(
      this.groupObjectsByProperty(
        bindingNodes,
        (node) => node.bindingLinkId || ""
      ).entries()
    ).reduce(
      (
        bindingLinks: Array<Link>,
        [bindingLinkId, bindingNodes]: [string, Node[]]
      ) => {
        bindingNodes.sort(
          (a: Node, b: Node) =>
            (angleToBindingNodes.get(a.id) || 0) -
            (angleToBindingNodes.get(b.id) || 0)
        );

        for (let i = 0; i < bindingNodes.length - 1; i++) {
          bindingLinks.push(
            new Link(
              uuidv4(),
              bindingNodes[i],
              bindingNodes[i + 1],
              bindingType,
              bindingLinkId
            )
          );
        }

        return bindingLinks;
      },
      []
    );
  }

  private createIntermediateLinks(
    nodeId: string,
    bindings: Map<string, Node[]>
  ) {
    const frontierNodes = new Set<string | undefined>();
    const lastBoundNodes = new Map<string, string>();
    const orderedBindings = Array.from(bindings.entries())
      .sort(
        ([, bindingNodesA], [, bindingNodesB]) =>
          bindingNodesA.length - bindingNodesB.length
      )
      .map(([binding]) => binding);
    let bindingSlot = 0;

    return orderedBindings.reduce(
      (intermediateLinks: Array<Link>, bindingId: string) => {
        const bindingNodes = bindings.get(bindingId);

        if (bindingNodes == null) return intermediateLinks;

        if (
          bindingNodes.some((node) => frontierNodes.has(node.relatedNodeId))
        ) {
          bindingSlot++;
          frontierNodes.clear();
        }

        bindingNodes.forEach((node) => {
          if (node.relatedNodeId == null || node.intermediateLinkId == null)
            return;
          const isSplit = node.hasType(ElementType.Split);
          node.bindingSlot = bindingSlot;
          frontierNodes.add(node.relatedNodeId);
          const sourceNodeId = isSplit
            ? lastBoundNodes.get(node.relatedNodeId) || nodeId
            : node.id;
          const targetNodeId = isSplit
            ? node.id
            : lastBoundNodes.get(node.relatedNodeId) || nodeId;
          const indirectSourceNodeId = isSplit ? nodeId : node.relatedNodeId;
          const indirectTargetNodeId = isSplit ? node.relatedNodeId : nodeId;
          intermediateLinks.push(
            new Link(
              uuidv4(),
              sourceNodeId,
              targetNodeId,
              ElementType.Regular,
              node.intermediateLinkId,
              {
                indirectSourceNodeId,
                indirectTargetNodeId
              }
            )
          );
          lastBoundNodes.set(node.relatedNodeId, node.id);
        });
        return intermediateLinks;
      },
      []
    );
  }

  private createLinksBetweenSplitsAndJoins(nodes: Array<Node>) {
    return Array.from(
      this.groupObjectsByProperty(
        nodes,
        (node) => node.intermediateLinkId
      ).entries()
    ).reduce(
      (
        links: Array<Link>,
        [intermediateLinkId, nodes]: [string | undefined, Array<Node>]
      ) => {
        if (intermediateLinkId == null) return links;

        const newLinksNodes = nodes.reduce(
          (nodes: Map<ElementType, Node>, node: Node) => {
            if (
              !nodes.has(node.nodeType) ||
              (nodes.get(node.nodeType)?.bindingSlot || 0) <= node.bindingSlot
            ) {
              nodes.set(node.nodeType, node);
            }

            return nodes;
          },
          new Map()
        );
        const sourceNode = newLinksNodes.get(ElementType.Split),
          targetNode = newLinksNodes.get(ElementType.Join);

        if (sourceNode != null && targetNode != null) {
          links.push(
            new Link(
              uuidv4(),
              sourceNode,
              targetNode,
              ElementType.Regular,
              intermediateLinkId,
              {
                indirectSourceNodeId: targetNode?.relatedNodeId,
                indirectTargetNodeId: sourceNode?.relatedNodeId
              }
            )
          );
        }
        return links;
      },
      []
    );
  }

  private calculateLayout(
    nodes: Array<{ id: string }>,
    links: Array<{ sourceNodeId: string; targetNodeId: string }>
  ) {
    const networkGraph = new Graph()
      .setGraph({
        marginx: 10,
        marginy: 10,
        acyclicer: "greedy"
      })
      .setDefaultEdgeLabel(() => {
        return new Map();
      });

    links.forEach((link) => networkGraph.setEdge(link.sourceNodeId, link.targetNodeId));
    nodes.forEach((node: { id: string }) => {
      networkGraph.setNode(node.id, { label: node.id });
    });

    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    dagre.layout(networkGraph);
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    const layoutWidth = networkGraph.graph().width;
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    const layoutHeight = networkGraph.graph().height;

    return networkGraph.nodes().reduce((layout: Map<string, Point>, nodeId: string) => {
      const node = networkGraph.node(nodeId);
      const nodePosition = this.nodeTransition?.(node as Point, layoutWidth, layoutHeight) || (node as Point);
      return layout.set(nodeId, nodePosition);
    }, new Map());
  }

  private groupObjectsByProperty<TObject, TProperty>(
    objects: Array<TObject>,
    propertySelector: (object: TObject) => TProperty
  ) {
    return objects.reduce(
      (objectsGroup: Map<TProperty, Array<TObject>>, object: TObject) => {
        const propertyValue = propertySelector(object);

        if (propertyValue == null) return objectsGroup;

        if (!objectsGroup.has(propertyValue)) {
          objectsGroup.set(propertyValue, []);
        }

        objectsGroup.get(propertyValue)?.push(object);

        return objectsGroup;
      },
      new Map()
    );
  }

  private calculateAngleBetweenPoints(from: Point, to: Point) {
    return Math.atan2(to.y - from.y, to.x - from.x) - Math.atan2(1, 0);
  }

  private createIntermediateLinkId(sourceNodeId: string, targetNodeId: string) {
    return `${sourceNodeId}${targetNodeId}`;
  }
}
