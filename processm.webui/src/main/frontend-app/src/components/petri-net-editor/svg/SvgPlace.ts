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
  static readonly TOKEN_DISPLAY_LIMIT = 7;

  readonly placeModel: Place;

  private readonly _eventBus: EventBus | null;

  private readonly _svg: SVGSelection;
  private readonly _svgGroup: SVGSelection;
  private readonly _svgCircle: SVGCircleSelection;
  private readonly _svgText: SVGTextSelection;
  private readonly _svgTokens: SVGCircleSelection[] = [];
  private _svgTokenText: SVGTextSelection | null = null;

  private _svgTextWidth = 0;

  constructor(svg: SVGSelection, eventBus: EventBus | null, place: Place) {
    super(place);
    this._eventBus = eventBus;
    this.placeModel = place;

    this._svg = svg;
    this._svgGroup = svg.select(".places");

    this._svgCircle = this._svgGroup
      .append("circle")
      .attr("id", this.placeModel.id)
      .attr("r", Place.RADIUS)
      .attr("stroke", "black")
      .attr("stroke-width", 2)
      .attr("fill", "white")
      .style("filter", "drop-shadow(1px 3px 4px rgb(0 0 0 / 0.3))");

    this.type = this.placeModel.type;
    this.spawnTokens();

    this._svgText = this._svgGroup
      .insert("text", this._svgCircle.node()?.querySelector)
      .style("pointer-events", "none")
      .style("user-select", "none");
    this.text = this.placeModel.text;

    if (this._eventBus != null) {
      this.initDragAndDrop();
    }
    this.updatePosition();
  }

  get text(): string {
    return this._svgText.text();
  }

  set text(text: string) {
    this.placeModel.text = text;
    this._svgText.text(text);
    // TODO: Remove later
    // this.svgText.text(this.placeModel.id);

    const textBoundingBox = this._svgText.node()?.getBoundingClientRect();
    this._svgTextWidth = textBoundingBox?.width ?? 0;

    this.updateTextPosition();
  }

  get tokenCount(): number {
    return this.placeModel.tokenCount;
  }

  set tokenCount(tokenCount: number) {
    if (tokenCount < 0) {
      tokenCount = 0;
    }

    this.placeModel.tokenCount = tokenCount;
    this.spawnTokens();
    this.updateTokenPosition();
  }

  get type(): PlaceType {
    return this.placeModel.type;
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

    this._svgCircle.attr("fill", fillColor);
  }

  delete(): void {
    this._svgCircle.remove();
    this._svgText.remove();
    this._svgTokens.forEach((svgToken) => svgToken.remove());
    this._svgTokenText?.remove();
  }

  private updatePosition() {
    this._svgCircle
      .attr("cx", this.placeModel.cx)
      .attr("cy", this.placeModel.cy);
    this.updateTokenPosition();
    this.updateTextPosition();
  }

  private updateTextPosition() {
    const textX = this.placeModel.cx - this._svgTextWidth / 2;
    const textY = this.placeModel.cy - Place.RADIUS - 5;

    this._svgText.attr("x", textX).attr("y", textY);
  }

  private updateTokenPosition() {
    if (this._svgTokenText != null) {
      const tokenTextX = this.placeModel.cx - Place.RADIUS / 2;
      const tokenTextY = this.placeModel.cy + 5;

      this._svgTokenText.attr("x", tokenTextX).attr("y", tokenTextY);
    } else if (this._svgTokens.length === 1) {
      this._svgTokens[0]
        .attr("cx", this.placeModel.cx)
        .attr("cy", this.placeModel.cy);
    } else if (this._svgTokens.length >= 2) {
      const angleIncrement = Math.PI / this._svgTokens.length;
      this._svgTokens.forEach((token, i) => {
        const angle = i * angleIncrement * 2;

        const tokenX =
          this.placeModel.cx + (Math.sin(angle) * Place.RADIUS) / 2;
        const tokenY =
          this.placeModel.cy + (Math.cos(angle) * Place.RADIUS) / 2;

        token.attr("cx", tokenX).attr("cy", tokenY);
      });
    }
  }

  private spawnTokens() {
    const tokenRadius =
      this.placeModel.tokenCount >= 3
        ? Place.RADIUS / this.placeModel.tokenCount
        : Place.RADIUS / 3;

    this._svgTokenText?.remove();
    this._svgTokenText = null;
    this._svgTokens.forEach((token) => token.remove());
    this._svgTokens.length = 0;

    if (
      this.placeModel.tokenCount > 0 &&
      this.placeModel.tokenCount <= SvgPlace.TOKEN_DISPLAY_LIMIT
    ) {
      for (let i = 0; i < this.placeModel.tokenCount; i++) {
        const token = this._svgGroup
          .insert("circle", this._svgCircle.node()?.querySelector)
          .attr("r", tokenRadius)
          .style("pointer-events", "none");
        this._svgTokens.push(token);
      }
    } else if (this.placeModel.tokenCount > SvgPlace.TOKEN_DISPLAY_LIMIT) {
      this._svgTokenText = this._svgGroup
        .insert("text", this._svgCircle.node()?.querySelector)
        .text(this.placeModel.tokenCount)
        .attr("font-weight", "bold")
        .style("pointer-events", "none")
        .style("user-select", "none");

      console.log(this._svgTokenText.style("font-size"));
    }
  }

  private initDragAndDrop() {
    this._svgCircle.call(
      drag<SVGCircleElement, unknown>()
        // TODO: fix false inspection error
        /* eslint-disable @typescript-eslint/ban-ts-comment */
        // @ts-ignore
        .on("start", (event: SVGDragEvent) => this.onDrag(event))
        .on("end", () => this.ended())
    );
  }

  private onDrag(event: SVGDragEvent) {
    this._svgCircle.classed("dragging", true);

    const dx = event.x - this.placeModel.cx;
    const dy = event.y - this.placeModel.cy;

    // TODO: fix false inspection error
    /* eslint-disable @typescript-eslint/ban-ts-comment */
    // @ts-ignore
    event.on("drag", (event: SVGDragEvent) => this.dragged(event, dx, dy));
  }

  private dragged(event: SVGDragEvent, dx: number, dy: number) {
    this._eventBus!.emit(EventNames.ON_DRAG, this.placeModel.id);
    const boundingBox = this._svg.node()?.getBoundingClientRect();
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
    this._svgCircle.classed("dragging", false);
  }
}
