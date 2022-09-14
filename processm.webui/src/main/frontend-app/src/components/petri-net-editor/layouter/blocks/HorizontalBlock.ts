import { Block } from "@/components/petri-net-editor/layouter/blocks/Block";
import { SVGRectSelection, SVGSelection } from "@/utils/Types";
import { Position } from "@/components/petri-net-editor/layouter/interfaces/Position";

export class HorizontalBlock extends Block {
  private _svgBlock: SVGRectSelection | null = null;
  readonly _isReversed: boolean;

  constructor(
    block1: Block,
    block2: Block,
    block3: Block,
    isReversed: boolean
  ) {
    super(!isReversed ? [block1, block2, block3] : [block3, block2, block1]);

    this._isReversed = isReversed;

    this.calculatePositions();
  }

  render(svg: SVGSelection): void {
    this._svgBlock = svg
      .append("rect")
      .attr("x", this.absoluteX)
      .attr("y", this.absoluteY)
      .attr("width", this.width)
      .attr("height", this.height)
      .attr("opacity", 0.5)
      .attr("stroke", !this._isReversed ? "green" : "aquamarine")
      .attr("stroke-width", 3)
      .attr("fill", "none")
      .style("filter", "drop-shadow(1px 3px 4px rgb(0 0 0 / 0.3))");

    this.blocks.forEach((block) => block.render(svg));
  }

  delete(): void {
    this._svgBlock?.remove();
  }

  applyLayout(position: Position): void {
    this.absoluteX = position.x;
    this.absoluteY = position.y;

    const xOffset = !this._isReversed
      ? 0
      : Math.abs(Math.min(...this.blocks.map((block) => block.x)));

    this.blocks.forEach((block) => {
      block.applyLayout({
        x: position.x + block.x + xOffset,
        y: position.y + block.y
      });
    });
  }

  get numberOfLayers(): number {
    return this.blocks.reduce((sum, block) => sum + block.numberOfLayers, 0);
  }

  private updateDimensions() {
    const blocksLeftToRight = this.blocks.slice().sort((a, b) => a.x - b.x);

    this.width = !this._isReversed
      ? blocksLeftToRight[2].x + blocksLeftToRight[2].width
      : Math.abs(blocksLeftToRight[0].x);

    this.height = Math.max(...this.blocks.map((block) => block.height));
  }

  protected calculatePositions(): void {
    const maxHeight = Math.max(...this.blocks.map((block) => block.height));

    this.blocks.forEach((block, i) => {
      const previousBlock = i == 0 ? null : this.blocks[i - 1];
      const prevX = previousBlock?.x ?? 0;
      const spacing = previousBlock == null ? 0 : 100;

      if (!this._isReversed) {
        block.x = prevX + (previousBlock?.width ?? 0) + spacing;
      } else {
        block.x = prevX - block.width - spacing;
      }

      block.y = (maxHeight - block.height) / 2;
    });

    this.updateDimensions();
  }
}
