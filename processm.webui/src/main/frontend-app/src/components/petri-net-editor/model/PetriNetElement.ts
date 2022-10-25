export abstract class PetriNetElement {
  readonly id: string;
  text: string;

  protected constructor(id: string, text: string) {
    this.id = id;
    this.text = text;
  }
}
