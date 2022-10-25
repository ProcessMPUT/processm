import { v4 as uuidv4 } from "uuid";
import { PetriNetElement } from "@/components/petri-net-editor/model/PetriNetElement";

export interface TransitionOptions {
  id?: string;
  x: number;
  y: number;
  text: string;
  isSilent?: boolean;
}

export class Transition extends PetriNetElement {
  static readonly HEIGHT: number = 50;
  static readonly WIDTH: number = 10;
  x: number;
  y: number;
  readonly isSilent: boolean = false;

  constructor(
    x: number,
    y: number,
    text: string,
    id: string,
    isSilent: boolean = false
  ) {
    super(id, text);

    this.x = x;
    this.y = y;
    this.isSilent = isSilent;
    this.text = this.isSilent ? "τ" : text;
  }

  static fromOptions(options: TransitionOptions): Transition {
    if (!options.id) {
      options.id = uuidv4();
    }
    if (options.isSilent == undefined) {
      options.isSilent = false;
    }

    return new Transition(
      options.x,
      options.y,
      options.text,
      options.id,
      options.isSilent
    );
  }

  getOptions(): TransitionOptions {
    return {
      id: this.id,
      x: this.x,
      y: this.y,
      text: this.text,
      isSilent: this.isSilent
    };
  }
}
