export interface XesAttribute {
  "@key": string;
  "@value": string | number | boolean | null;
}

export interface XesComponent {
  date?: XesAttribute[];
  boolean?: XesAttribute[];
  float?: XesAttribute[];
  int?: XesAttribute[];
  string?: XesAttribute[];
  id?: XesAttribute[];
}

export type Event = XesComponent;

export interface Trace extends XesComponent {
  event: Event[];
}

export interface Log extends XesComponent {
  global: XesComponent[] | null;
  trace: Trace[];
}

export function isXesAttribute(
  object: XesAttribute | XesComponent
): object is XesAttribute {
  return "@key" in object && "@value" in object;
}

export function isXesLog(object: XesAttribute | XesComponent): object is Log {
  return "trace" in object && "global" in object;
}

export function isXesTrace(
  object: XesAttribute | XesComponent
): object is Trace {
  return "event" in object;
}
