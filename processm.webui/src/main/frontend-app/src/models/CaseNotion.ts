export default interface CaseNotion {
  classes: Map<string, string>;
  edges: Relation[];
}

export interface Relation {
  sourceClassId: string;
  targetClassId: string;
}
