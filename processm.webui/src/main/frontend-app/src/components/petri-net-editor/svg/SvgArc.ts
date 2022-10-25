import type { SVGLineSelection, SVGSelection } from "@/utils/Types";
import { Arc } from "@/components/petri-net-editor/model/Arc";

export class SvgArc {
  static readonly WIDTH = 4;

  readonly model: Arc;

  private readonly _svgGroup: SVGSelection;
  private readonly _svgLine: SVGLineSelection;

  private x1 = 0;
  private y1 = 0;
  private x2 = 0;
  private y2 = 0;

  constructor(svg: SVGSelection, arc: Arc) {
    this.model = arc;

    this._svgGroup = svg.select(".arcs");

    this._svgLine = SvgArc.createLine(this._svgGroup, SvgArc.WIDTH).attr(
      "id",
      this.model.id
    );
  }

  static createLine(scope: SVGSelection, width: number): SVGLineSelection {
    return scope
      .append("line")
      .attr("fill", "none")
      .attr("stroke", "black")
      .attr("stroke-width", width)
      .attr("marker-end", "url(#arrow)");
  }

  private static makeNanSafe(value: number): number {
    if(isFinite(value))
      return value
    else
      return 0;
  }

  delete() {
    this._svgLine.remove();
  }

  setOutPosition(x: number, y: number) {
    x = SvgArc.makeNanSafe(x);
    y = SvgArc.makeNanSafe(y);

    this.x1 = x;
    this.y1 = y;
    this._svgLine.attr("x1", x).attr("y1", y);
  }

  getOutPosition(): [number, number] {
    return [this.x1, this.y1];
  }

  setInPosition(x: number, y: number) {
    x = SvgArc.makeNanSafe(x);
    y = SvgArc.makeNanSafe(y);

    this.x2 = x;
    this.y2 = y;

    this._svgLine.attr("x2", this.x2).attr("y2", this.y2);
  }

  getInPosition(): [number, number] {
    return [this.x2, this.y2];
  }
}
