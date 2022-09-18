import { Place, PlaceOptions } from "@/components/petri-net-editor/model/Place";
import {
  Transition,
  TransitionOptions
} from "@/components/petri-net-editor/model/Transition";
import type { EventBus, SVGLineSelection, SVGSelection } from "@/utils/Types";
import type { PetriNetElement } from "@/components/petri-net-editor/model/PetriNetElement";
import { EventNames } from "@/components/petri-net-editor/PetriNetEditorConstants";
import * as d3 from "d3-selection";
import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { SvgArc } from "@/components/petri-net-editor/svg/SvgArc";
import { SvgPlace } from "@/components/petri-net-editor/svg/SvgPlace";
import { PetriNetSvgElement } from "@/components/petri-net-editor/svg/PetriNetSvgElement";
import { SvgTransition } from "@/components/petri-net-editor/svg/SvgTransition";
import mitt from "mitt";
import { Block } from "@/components/petri-net-editor/layouter/blocks/Block";
import { NodeBlock } from "@/components/petri-net-editor/layouter/blocks/NodeBlock";

export class PetriNetSvgManager {
  private readonly eventBus: EventBus = mitt();

  private svgPlaces: Map<string, SvgPlace> = new Map<string, SvgPlace>();
  private svgTransitions: Map<string, SvgTransition> = new Map<
    string,
    SvgTransition
  >();
  private svgArcs: Map<string, SvgArc> = new Map<string, SvgArc>();

  private _state: PetriNetState;
  private readonly svg: SVGSelection;
  private connectSvgLine: SVGLineSelection | null = null;

  constructor(svg: SVGSelection) {
    this.svg = svg;
    this._state = new PetriNetState();
    this.enableDragging();
  }

  get state(): PetriNetState {
    return this._state;
  }

  set state(newState: PetriNetState) {
    this._state.arcs.forEach((arc) => this.removeArc(arc.id));

    [...this._state.places, ...this._state.transitions].forEach((element) =>
      this.removeElement(element.id)
    );

    this._state = new PetriNetState();
    newState.places.forEach((place) => this.createPlace(place.getOptions()));
    newState.transitions.forEach((transition) =>
      this.createTransition(transition.getOptions())
    );
    newState.arcs.forEach((arc) =>
      this.connect(arc.outElementId, arc.inElementId)
    );
    this._state.layoutMap = newState.layoutMap;
  }

  get places(): SvgPlace[] {
    return [...this.svgPlaces.values()];
  }

  get transitions(): SvgTransition[] {
    return [...this.svgTransitions.values()];
  }

  createPlace(options: PlaceOptions): string {
    const place = this._state.createPlace(options);
    this.svgPlaces.set(place.id, new SvgPlace(this.svg, this.eventBus, place));

    return place.id;
  }

  createTransition(options: TransitionOptions): string {
    const transition = this._state.createTransition(options);
    this.svgTransitions.set(
      transition.id,
      new SvgTransition(this.svg, this.eventBus, transition)
    );

    return transition.id;
  }

  connect(outId: string, inId: string): void {
    const arc = this._state.createArc(outId, inId);
    if (arc != null) {
      const svgArc = new SvgArc(this.svg, arc);
      this.svgArcs.set(arc.id, svgArc);
      this.updateArcPosition(svgArc);
    }
  }

  startConnect(outId: string): void {
    this.connectSvgLine?.remove();
    const outElement: PetriNetElement = this._state.getElement(outId);

    let x1 = 0;
    let y1 = 0;
    if (outElement instanceof Place) {
      x1 = outElement.cx;
      y1 = outElement.cy;
    } else if (outElement instanceof Transition) {
      x1 = outElement.x + Transition.WIDTH / 2;
      y1 = outElement.y + Transition.HEIGHT / 2;
    }

    // TODO: fix false inspection error
    // @ts-ignore
    this.svg.on("mousemove", (event: MouseEvent) => {
      if (this.connectSvgLine == null) {
        this.connectSvgLine = SvgArc.createLine(this.svg.select(".arcs"))
          .attr("x1", x1)
          .attr("y1", y1);
      }

      this.connectSvgLine.attr("x2", event.offsetX);
      this.connectSvgLine.attr("y2", event.offsetY);
    });

    // TODO: fix false inspection error
    // @ts-ignore
    this.svg.on("click", (event: MouseEvent) => {
      const target = event.target as Element;
      const isPlaceOrTransition =
        target instanceof SVGCircleElement || target instanceof SVGRectElement;

      this.connectSvgLine?.remove();
      this.connectSvgLine = null;
      this.svg.on("mousemove", null);

      if (isPlaceOrTransition) {
        this.connect(outId, d3.select(target).attr("id"));
      }
    });
  }

  getPlace(placeId: string): SvgPlace {
    return this.svgPlaces.get(placeId)!;
  }

  getTransition(transitionId: string): SvgTransition {
    return this.svgTransitions.get(transitionId)!;
  }

  hasElement(elementId: string): boolean {
    const element: PetriNetSvgElement | undefined =
      this.svgPlaces.get(elementId) ?? this.svgTransitions.get(elementId);

    return element != undefined;
  }

  getElement(elementId: string): PetriNetSvgElement {
    const element: PetriNetSvgElement | undefined =
      this.svgPlaces.get(elementId) ?? this.svgTransitions.get(elementId);

    if (element == undefined) {
      throw new Error(`Element (id="${elementId}") not found`);
    }

    return element;
  }

  removeElement(id: string): void {
    const svgElement: PetriNetSvgElement = this.getElement(id);
    svgElement.delete();
    this._state
      .getElementArcs(svgElement.model.id)
      .forEach((arc) => this.removeArc(arc.id));
    this._state.removeElement(svgElement.model.id);
  }

  removeArc(id: string): void {
    this.svgArcs.get(id)!.delete();
    this.svgArcs.delete(id);

    this._state.removeArc(id);
  }

  updateDimensions(): void {
    const width = Math.max(
      ...this._state.transitions.map(
        (transition) => transition.x + Transition.WIDTH
      ),
      ...this._state.places.map((place) => place.cx + Place.RADIUS)
    );
    const height = Math.max(
      ...this._state.transitions.map(
        (transition) => transition.y + Transition.HEIGHT
      ),
      ...this._state.places.map((place) => place.cy + Place.RADIUS)
    );

    this.svg
      .style("min-width", `${width + 50}px`)
      .style("min-height", `${height + 50}px`);
  }

  enableDragging(): void {
    this.eventBus.on(EventNames.ON_DRAG, (elementId) => {
      this._state.getElementArcs(elementId as string).forEach((arc) => {
        const svgArc = this.svgArcs.get(arc.id)!;
        this.updateArcPosition(svgArc);
      });
    });
  }

  disableDragging(): void {
    this.eventBus.off(EventNames.ON_DRAG);
  }

  getNumberOfIntersectingArcs(): number {
    let result = 0;

    const arcs = [...this.svgArcs.values()];
    for (const arc1 of arcs) {
      for (const arc2 of arcs) {
        if (arc2 == arc1) {
          break;
        }

        if (this.areArcsIntersecting(arc1, arc2)) {
          result += 1;
        }
      }
    }

    return result;
  }

  getNumberOfLayers(): number {
    return Math.max(
      0,
      ...this._state.getBlocks().map((block) => block.numberOfLayers)
    );
  }

  getHierarchyDepth(): number {
    return Math.max(
      0,
      ...this._state
        .getBlocks()
        .map((block) => this.getHierarchyDepthRec(block))
    );
  }

  getBranchingFactor(): number {
    const blocks = this.getBlocks().filter(
      (block) => !(block instanceof NodeBlock)
    );

    return (
      blocks
        .map((block) => block.blocks.length)
        .reduce((sum, numberOfBlocks) => sum + numberOfBlocks, 0) /
      blocks.length
    );
  }

  private getHierarchyDepthRec(block: Block): number {
    return (
      1 +
      Math.max(
        0,
        ...block.blocks.map((block1) => this.getHierarchyDepthRec(block1))
      )
    );
  }

  // Source: https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection
  private areArcsIntersecting(arc1: SvgArc, arc2: SvgArc): boolean {
    const [x1, y1] = arc1.getOutPosition();
    const [x2, y2] = arc1.getInPosition();

    const [x3, y3] = arc2.getOutPosition();
    const [x4, y4] = arc2.getInPosition();

    const determinant = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
    if (determinant === 0) {
      return false;
    } else {
      const t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / determinant;
      const u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / determinant;
      return 0 < t && t < 1 && 0 < u && u < 1;
    }
  }

  private getBlocks(): Block[] {
    const blocks = this._state.getBlocks();
    return [...blocks, ...blocks.flatMap((block) => this.getBlocksRec(block))];
  }

  private getBlocksRec(block: Block): Block[] {
    return [
      ...block.blocks,
      ...block.blocks.flatMap((block1) => this.getBlocksRec(block1))
    ];
  }

  private updateArcPosition(arc: SvgArc) {
    const outElement: PetriNetSvgElement = this.getElement(
      arc.model.outElementId
    );
    PetriNetSvgManager.updateOutPosition(arc, outElement.model);

    const inElement: PetriNetSvgElement = this.getElement(
      arc.model.inElementId
    );
    PetriNetSvgManager.updateInPosition(arc, inElement.model);
  }

  private static updateInPosition(
    arc: SvgArc,
    inElement: PetriNetElement
  ): void {
    const [arcX1, arcY1] = arc.getOutPosition();

    let newX2 = arcX1;
    let newY2 = arcY1;
    if (inElement instanceof Transition) {
      [newX2, newY2] = PetriNetSvgManager.calculateTransitionInPosition(
        arc,
        inElement
      );
    } else if (inElement instanceof Place) {
      const a = arcX1 - inElement.cx;
      const b = arcY1 - inElement.cy;
      const c = Math.hypot(a, b);

      const angleSine = a / c;
      const angleCosine = b / c;

      newX2 = inElement.cx + (Place.RADIUS + 30) * angleSine;
      newY2 = inElement.cy + (Place.RADIUS + 30) * angleCosine;
    }

    arc.setInPosition(newX2, newY2);
  }

  private static calculateTransitionInPosition(
    arc: SvgArc,
    inElement: Transition
  ): [number, number] {
    const [arcX1, arcY1] = arc.getOutPosition();

    let newX2 = arcX1;
    let newY2 = arcY1;
    let cornerX: number | null = null;
    let cornerY: number | null = null;

    if (arcX1 <= inElement.x) {
      cornerX = inElement.x;
    } else if (arcX1 >= inElement.x + Transition.WIDTH) {
      cornerX = inElement.x + Transition.WIDTH;
    }

    if (arcY1 <= inElement.y) {
      cornerY = inElement.y;
    } else if (arcY1 >= inElement.y + Transition.HEIGHT) {
      cornerY = inElement.y + Transition.HEIGHT;
    }

    if (cornerX != null && cornerY != null) {
      [newX2, newY2] = PetriNetSvgManager.calculateOffsetPosition(
        arcX1,
        arcY1,
        cornerX,
        cornerY,
        30
      );
    } else if (cornerX != null) {
      newX2 = arcX1 <= cornerX ? cornerX - 30 : cornerX + 30;
    } else if (cornerY != null) {
      newY2 = arcY1 <= cornerY ? cornerY - 30 : cornerY + 30;
    }

    return [newX2, newY2];
  }

  private static updateOutPosition(
    arc: SvgArc,
    outElement: PetriNetElement
  ): void {
    let newX1 = 0;
    let newY1 = 0;

    if (outElement instanceof Transition) {
      newX1 = outElement.x + Transition.WIDTH / 2;
      newY1 = outElement.y + Transition.HEIGHT / 2;
    } else if (outElement instanceof Place) {
      newX1 = outElement.cx;
      newY1 = outElement.cy;
    }

    arc.setOutPosition(newX1, newY1);
  }

  private static calculateOffsetPosition(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    offset: number
  ): [number, number] {
    const a = x1 - x2;
    const b = y1 - y2;
    const c = Math.hypot(a, b);

    const angleSine = a / c;
    const angleCosine = b / c;

    return [x2 + 30 * angleSine, y2 + offset * angleCosine];
  }
}
