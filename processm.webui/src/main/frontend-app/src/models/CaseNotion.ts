export default interface CaseNotion {
  classes: { [key: string]: string };
  edges: Relation[];
}

export interface Relation {
  sourceClassId: string;
  targetClassId: string;
}
