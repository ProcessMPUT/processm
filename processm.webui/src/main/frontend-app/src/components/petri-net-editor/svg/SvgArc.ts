import type { SVGLineSelection, SVGSelection } from '@/utils/Types';
import { Arc } from '@/components/petri-net-editor/model/Arc';


export class SvgArc {
    readonly model: Arc;

    private readonly svgGroup: SVGSelection;
    private readonly svgLine: SVGLineSelection;

    private x1 = 0;
    private y1 = 0;
    private x2 = 0;
    private y2 = 0;

    public width = 4;

    constructor(svg: SVGSelection, arc: Arc) {
        this.model = arc;

        this.svgGroup = svg.select('.arcs');

        this.svgLine = SvgArc.createLine(this.svgGroup, 4)
            .attr('id', this.model.id);
    }

    delete() {
        this.svgLine.remove();
    }

    setOutPosition(x: number, y: number) {
        x = SvgArc.makeNanSafe(x);
        y = SvgArc.makeNanSafe(y);

        this.x1 = x;
        this.y1 = y;
        this.svgLine.attr('x1', x).attr('y1', y);
    }

    getOutPosition(): [number, number] {
        return [this.x1, this.y1];
    }

    setInPosition(x: number, y: number) {
        x = SvgArc.makeNanSafe(x);
        y = SvgArc.makeNanSafe(y);

        this.x2 = x;
        this.y2 = y;

        this.svgLine.attr('x2', this.x2).attr('y2', this.y2);
    }

    getInPosition(): [number, number] {
        return [this.x2, this.y2];
    }

    static createLine(scope: SVGSelection, width: number = 4): SVGLineSelection {
        return scope.append('line')
            .attr('fill', 'none')
            .attr('stroke', 'black')
            .attr('stroke-width', width)
            .attr('marker-end', 'url(#arrow)');
    }

    private static makeNanSafe(value: number): number {
        if (isNaN(value)) {
            return 0;
        }

        return value;
    }
}
