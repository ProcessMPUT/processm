import {
  EventBus,
  SVGCircleSelection,
  SVGDragEvent,
  SVGSelection,
  SVGTextSelection
} from "@/utils/Types";
import { Place, PlaceType } from "@/components/petri-net-editor/model/Place";
import { drag } from "d3-drag";
import { EventNames } from "@/components/petri-net-editor/PetriNetEditorConstants";
import { PetriNetSvgElement } from "@/components/petri-net-editor/svg/PetriNetSvgElement";

export class SvgPlace extends PetriNetSvgElement {
  static readonly tokenDisplayLimit = 7;

  readonly placeModel: Place;

  private readonly eventBus: EventBus;

  private readonly svg: SVGSelection;
  private readonly svgGroup: SVGSelection;
  private readonly svgCircle: SVGCircleSelection;
  private readonly svgText: SVGTextSelection;
  private readonly svgTokens: SVGCircleSelection[] = [];
  private svgTokenText: SVGTextSelection | null = null;

  private svgTextWidth: number = 0;

  constructor(svg: SVGSelection, eventBus: EventBus, place: Place) {
    super(place);
    this.eventBus = eventBus;
    this.placeModel = place;

    this.svg = svg;
    this.svgGroup = svg.select(".places");

    this.svgCircle = this.svgGroup
      .append("circle")
      .attr("id", this.placeModel.id)
      .attr("r", Place.RADIUS)
      .attr("stroke", "black")
      .attr("stroke-width", 2)
      .attr("fill", "white")
      .style("filter", "drop-shadow(1px 3px 4px rgb(0 0 0 / 0.3))");

    this.type = this.placeModel.type;
    this.spawnTokens();

    this.svgText = this.svgGroup
      .insert("text", this.svgCircle.node()?.querySelector)
      .style("pointer-events", "none")
      .style("user-select", "none");
    this.text = this.placeModel.text;

    this.initDragAndDrop();
    this.updatePosition();
  }

  set text(text: string) {
    this.placeModel.text = text;
    this.svgText.text(text);
    // TODO: Remove later
    // this.svgText.text(this.placeModel.id);

    const textBoundingBox = this.svgText.node()?.getBoundingClientRect();
    this.svgTextWidth = textBoundingBox?.width ?? 0;

    this.updateTextPosition();
  }

  get text(): string {
    return this.svgText.text();
  }

  set tokenCount(tokenCount: number) {
    if (tokenCount < 0) {
      tokenCount = 0;
    }

    this.placeModel.tokenCount = tokenCount;
    this.spawnTokens();
    this.updateTokenPosition();
  }

  get tokenCount(): number {
    return this.placeModel.tokenCount;
  }

  set type(placeType: PlaceType) {
    let fillColor;
    this.placeModel.type = placeType;
    switch (this.placeModel.type) {
      case PlaceType.INITIAL:
        fillColor = "lightgreen";
        break;
      case PlaceType.FINAL:
        fillColor = "crimson";
        break;
      case PlaceType.NORMAL:
        fillColor = "white";
        break;
    }

    this.svgCircle.attr("fill", fillColor);
  }

  get type(): PlaceType {
    return this.placeModel.type;
  }

  delete(): void {
    this.svgCircle.remove();
    this.svgText.remove();
    this.svgTokens.forEach((svgToken) => svgToken.remove());
    this.svgTokenText?.remove();
  }

  private updatePosition() {
    this.svgCircle
      .attr("cx", this.placeModel.cx)
      .attr("cy", this.placeModel.cy);
    this.updateTokenPosition();
    this.updateTextPosition();
  }

  private updateTextPosition() {
    this.svgText
      .attr("x", this.placeModel.cx - this.svgTextWidth / 2)
      .attr("y", this.placeModel.cy - Place.RADIUS - 5);
  }

  private updateTokenPosition() {
    if (this.svgTokenText != null) {
      this.svgTokenText
        .attr("x", this.placeModel.cx - Place.RADIUS / 2)
        .attr("y", this.placeModel.cy + 5);
    } else if (this.svgTokens.length === 1) {
      this.svgTokens[0]
        .attr("cx", this.placeModel.cx)
        .attr("cy", this.placeModel.cy);
    } else if (this.svgTokens.length >= 2) {
      const angleIncrement = Math.PI / this.svgTokens.length;
      this.svgTokens.forEach((token, i) => {
        const angle = i * angleIncrement * 2;
        token
          .attr("cx", this.placeModel.cx + (Math.sin(angle) * Place.RADIUS) / 2)
          .attr(
            "cy",
            this.placeModel.cy + (Math.cos(angle) * Place.RADIUS) / 2
          );
      });
    }
  }

  private spawnTokens() {
    const tokenRadius =
      this.placeModel.tokenCount >= 3
        ? Place.RADIUS / this.placeModel.tokenCount
        : Place.RADIUS / 3;

    this.svgTokenText?.remove();
    this.svgTokenText = null;
    this.svgTokens.forEach((token) => {
      token.remove();
    });
    this.svgTokens.length = 0;

    if (
      this.placeModel.tokenCount > 0 &&
      this.placeModel.tokenCount <= SvgPlace.tokenDisplayLimit
    ) {
      for (let i = 0; i < this.placeModel.tokenCount; i++) {
        const token = this.svgGroup
          .insert("circle", this.svgCircle.node()?.querySelector)
          .attr("r", tokenRadius)
          .style("pointer-events", "none");
        this.svgTokens.push(token);
      }
    } else if (this.placeModel.tokenCount > SvgPlace.tokenDisplayLimit) {
      this.svgTokenText = this.svgGroup
        .insert("text", this.svgCircle.node()?.querySelector)
        .text(this.placeModel.tokenCount)
        .attr("font-weight", "bold")
        .style("pointer-events", "none")
        .style("user-select", "none");
    }
  }

  private initDragAndDrop() {
    this.svgCircle.call(
      drag<SVGCircleElement, unknown>()
        // TODO: fix false inspection error
        // @ts-ignore
        .on("start", (event: SVGDragEvent) => this.onDrag(event))
        .on("end", () => this.ended())
    );
  }

  private onDrag(event: SVGDragEvent) {
    this.svgCircle.classed("dragging", true);

    const dx = event.x - this.placeModel.cx;
    const dy = event.y - this.placeModel.cy;

    // TODO: fix false inspection error
    // @ts-ignore
    event.on("drag", (event: SVGDragEvent) => this.dragged(event, dx, dy));
  }

  private dragged(event: SVGDragEvent, dx: number, dy: number) {
    this.eventBus.emit(EventNames.ON_DRAG, this.placeModel.id);
    const boundingBox = this.svg.node()?.getBoundingClientRect();
    // TODO: Log some error
    if (boundingBox == null) {
      return;
    }

    this.placeModel.cx = event.x - dx;
    if (this.placeModel.cx < Place.RADIUS) {
      this.placeModel.cx = Place.RADIUS;
    } else if (this.placeModel.cx > boundingBox.width - Place.RADIUS) {
      this.placeModel.cx = boundingBox.width - Place.RADIUS;
    }

    this.placeModel.cy = event.y - dy;
    if (this.placeModel.cy < Place.RADIUS) {
      this.placeModel.cy = Place.RADIUS;
    } else if (this.placeModel.cy > boundingBox.height - Place.RADIUS) {
      this.placeModel.cy = boundingBox.height - Place.RADIUS;
    }

    this.updatePosition();
  }

  private ended() {
    this.svgCircle.classed("dragging", false);
  }
}
