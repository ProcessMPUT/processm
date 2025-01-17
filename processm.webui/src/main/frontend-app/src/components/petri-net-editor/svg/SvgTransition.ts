import { drag } from "d3-drag";
import type {
  EventBus,
  SVGDragEvent,
  SVGRectSelection,
  SVGSelection,
  SVGTextSelection
} from "@/utils/Types";
import { EventNames } from "@/components/petri-net-editor/PetriNetEditorConstants";
import { PetriNetSvgElement } from "@/components/petri-net-editor/svg/PetriNetSvgElement";
import { Transition } from "@/components/petri-net-editor/model/Transition";

export class SvgTransition extends PetriNetSvgElement {
  readonly transitionModel: Transition;
  readonly height: number = 50;
  readonly width: number = 10;
  readonly isSilent: boolean = false;
  private readonly eventBus: EventBus | null;
  private readonly svg: SVGSelection;
  private readonly svgGroup: SVGSelection;
  private svgTextWidth = 0;
  private readonly svgRectangle: SVGRectSelection;
  private readonly svgText: SVGTextSelection;

  constructor(
    svg: SVGSelection,
    eventBus: EventBus | null,
    transition: Transition
  ) {
    super(transition);
    this.eventBus = eventBus;
    this.transitionModel = transition;

    this.svg = svg;
    this.svgGroup = svg.select(".transitions");

    this.svgRectangle = this.svgGroup
      .append("rect")
      .attr("id", this.transitionModel.id)
      .attr("width", this.width)
      .attr("height", this.height)
      .attr("stroke", "black")
      .attr("stroke-width", 2)
      .attr("fill", !this.isSilent ? "white" : "black")
      .style("filter", "drop-shadow(1px 3px 4px rgb(0 0 0 / 0.3))");

    this.svgText = this.svgGroup
      .insert("text", this.svgRectangle.node()?.querySelector)
      .style("pointer-events", "none")
      .style("user-select", "none");

    this.text = !this.isSilent ? this.transitionModel.text : "τ";

    if (this.eventBus != null) {
      this.initDragAndDrop();
    }
    this.updatePosition();
  }

  get text(): string {
    return this.svgText.text();
  }

  set text(text: string) {
    this.transitionModel.text = text;
    this.svgText.text(text);

    const textBoundingBox = this.svgText.node()?.getBoundingClientRect();
    this.svgTextWidth = textBoundingBox?.width ?? 0;

    this.updateTextPosition();
  }

  set highlight(isHighlighted: boolean) {
    if (isHighlighted) {
      this.svgRectangle
        .style("filter", "drop-shadow(1px 3px 4px yellow)")
        .attr("fill", "yellow");
    } else {
      this.svgRectangle
        .style("filter", "drop-shadow(1px 3px 4px rgb(0 0 0 / 0.3))")
        .attr("fill", "white");
    }
  }

  set invokable(isInvokable: boolean) {
    this.svgRectangle.attr("invokable", isInvokable ? 1 : 0);
  }

  set backwardsInvokable(isBackwardsInvokable: boolean) {
    this.svgRectangle.attr("backwardsInvokable", isBackwardsInvokable ? 1 : 0);
  }

  delete(): void {
    this.svgRectangle.remove();
    this.svgText.remove();
  }

  private updatePosition() {
    this.svgRectangle
      .attr("x", this.transitionModel.x)
      .attr("y", this.transitionModel.y);
    this.updateTextPosition();
  }

  private updateTextPosition() {
    const textX =
      this.transitionModel.x + this.width / 2 - this.svgTextWidth / 2;
    const textY = this.transitionModel.y - 5;

    this.svgText.attr("x", textX).attr("y", textY);
  }

  private initDragAndDrop() {
    this.svgRectangle.call(
      drag<SVGRectElement, unknown>()
        // TODO: fix false inspection error
        /* eslint-disable @typescript-eslint/ban-ts-comment */
        // @ts-ignore
        .on("start", (event: SVGDragEvent) => this.onDrag(event))
        .on("end", () => this.ended())
    );
  }

  private onDrag(event: SVGDragEvent) {
    this.svgRectangle.classed("dragging", true);

    const dx = event.x - this.transitionModel.x;
    const dy = event.y - this.transitionModel.y;

    // TODO: fix false inspection error
    // @ts-ignore
    event.on("drag", (event: SVGDragEvent) => this.dragged(event, dx, dy));
  }

  private dragged(event: SVGDragEvent, dx: number, dy: number): void {
    this.eventBus!.emit(EventNames.ON_DRAG, this.transitionModel.id);
    const boundingBox = this.svg.node()?.getBoundingClientRect();
    // TODO: Log some error
    if (boundingBox == null) {
      return;
    }

    this.transitionModel.x = event.x - dx;
    if (this.transitionModel.x < 0) {
      this.transitionModel.x = 0;
    } else if (this.transitionModel.x > boundingBox.width - this.width) {
      this.transitionModel.x = boundingBox.width - this.width;
    }

    this.transitionModel.y = event.y - dy;
    if (this.transitionModel.y < 0) {
      this.transitionModel.y = 0;
    } else if (this.transitionModel.y > boundingBox.height - this.height) {
      this.transitionModel.y = boundingBox.height - this.height;
    }

    this.updatePosition();
  }

  private ended() {
    this.svgRectangle.classed("dragging", false);
  }
}
