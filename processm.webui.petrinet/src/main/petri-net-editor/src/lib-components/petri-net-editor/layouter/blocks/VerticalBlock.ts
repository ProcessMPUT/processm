import {Block} from '@/lib-components/petri-net-editor/layouter/blocks/Block';
import {SVGRectSelection, SVGSelection} from '@/utils/Types';
import {Position} from '@/lib-components/petri-net-editor/layouter/interfaces/Position';

export class VerticalBlock extends Block {
    private _svgBlock: SVGRectSelection | null = null;
    private _isReversed: boolean;

    constructor(block1: Block, block2: Block, isReversed: boolean) {
        super([block1, block2]);

        this._isReversed = isReversed;
        this.calculatePositions();
    }

    render(svg: SVGSelection): void {
        this._svgBlock = svg
            .append('rect')
            .attr('x', this.absoluteX)
            .attr('y', this.absoluteY)
            .attr('width', this.width)
            .attr('height', this.height)
            .attr('opacity', 0.5)
            .attr('stroke', 'red')
            .attr('stroke-width', 3)
            .attr('fill', 'none')
            .style('filter', 'drop-shadow(1px 3px 4px rgb(0 0 0 / 0.3))');

        this.blocks.forEach(block => block.render(svg));
    }

    delete(): void {
        this._svgBlock?.remove();
    }

    applyLayout(position: Position): void {
        this.absoluteX = position.x;
        this.absoluteY = position.y;

        this.blocks.forEach(block => {
            block.applyLayout({
                x: position.x + block.x,
                y: position.y + block.y
            });
        });
    }

    getNumberOfLayers(): number {
        return Math.max(...this.blocks.map(block => block.getNumberOfLayers()));
    }

    private updateDimensions() {
        const topBlock = this.blocks[0];
        const bottomBlock = this.blocks[1];

        this.width = Math.max(topBlock.width, bottomBlock.width);
        this.height = bottomBlock.y + bottomBlock.height;
    }

    protected override calculatePositions(): void {
        const topBlock = this.blocks[0];
        const bottomBlock = this.blocks[1];

        const maxWidth = Math.max(...this.blocks.map(block => block.width));

        topBlock.x = (maxWidth - topBlock.width) / 2;
        topBlock.y = 0;

        bottomBlock.x = (maxWidth - bottomBlock.width) / 2;
        bottomBlock.y = topBlock.height + 100;

        this.updateDimensions();
    }
}
