import { SVGSelection } from '@/utils/Types';
import { Position } from '@/components/petri-net-editor/layouter/interfaces/Position';

export abstract class Block {
    x: number = 0;
    y: number = 0;
    width: number = 0.0;
    height: number = 0.0;
    absoluteX: number = 0.0;
    absoluteY: number = 0.0;

    readonly blocks: Block[];

    protected constructor(blocks: Block[]) {
        this.blocks = blocks;
    }

    abstract get numberOfLayers(): number;

    protected abstract calculatePositions(): void

    abstract applyLayout(position: Position): void;

    abstract render(svg: SVGSelection): void;

    abstract delete(): void;
}
