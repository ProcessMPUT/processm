import { v4 as uuidv4 } from "uuid";

export class Arc {
  readonly id: string;
  readonly outElementId: string;
  readonly inElementId: string;

  constructor(outElementId: string, inElementId: string, id?: string) {
    this.id = id ?? uuidv4();
    this.outElementId = outElementId;
    this.inElementId = inElementId;
  }
}
