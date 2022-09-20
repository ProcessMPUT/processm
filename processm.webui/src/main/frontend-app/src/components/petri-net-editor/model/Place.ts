import { PetriNetElement } from "@/components/petri-net-editor/model/PetriNetElement";
import { v4 as uuidv4 } from "uuid";

export enum PlaceType {
  INITIAL = 0,
  FINAL = 1,
  NORMAL = 2
}

export interface PlaceOptions {
  id?: string;
  x: number;
  y: number;
  text: string;
  tokenCount?: number;
  type?: PlaceType;
}

export class Place extends PetriNetElement {
  static readonly RADIUS: number = 20;
  cx: number = Place.RADIUS;
  cy: number = Place.RADIUS;
  type: PlaceType = PlaceType.NORMAL;

  protected _tokenCount = 0;

  static fromOptions(options: PlaceOptions): Place {
    const id = options.id ?? uuidv4();
    const tokenCount = options.tokenCount ?? 0;
    const type = options.type ?? PlaceType.NORMAL;

    return new Place(id, options.x, options.y, options.text, tokenCount, type);
  }

  private constructor(
    id: string,
    x: number,
    y: number,
    text: string,
    tokenCount: number,
    type: PlaceType
  ) {
    super(id, text);

    this.type = type;
    this.text = text;

    if (tokenCount < 0) {
      tokenCount = 0;
    }
    this._tokenCount = tokenCount;

    if (x > Place.RADIUS) {
      this.cx = x;
    }
    if (y > Place.RADIUS) {
      this.cy = y;
    }
  }

  get tokenCount(): number {
    return this._tokenCount;
  }

  set tokenCount(tokenCount: number) {
    if (tokenCount < 0) {
      tokenCount = 0;
    }

    this._tokenCount = tokenCount;
  }

  getOptions(): PlaceOptions {
    return {
      id: this.id,
      x: this.cx,
      y: this.cy,
      text: this.text,
      tokenCount: this._tokenCount,
      type: this.type
    };
  }
}
