import type { Selection } from "d3-selection";
import type { D3DragEvent, SubjectPosition } from "d3-drag";
import { Emitter, EventType } from "mitt";

export type SVGSelection = Selection<SVGElement, unknown, Element, unknown>;

export type SVGLineSelection = Selection<
  SVGLineElement,
  unknown,
  Element,
  unknown
>;

export type SVGRectSelection = Selection<
  SVGRectElement,
  unknown,
  Element,
  unknown
>;

export type SVGCircleSelection = Selection<
  SVGCircleElement,
  unknown,
  Element,
  unknown
>;

export type SVGTextSelection = Selection<
  SVGTextElement,
  unknown,
  Element,
  unknown
>;

export type SVGDragEvent = D3DragEvent<
  SVGGraphicsElement,
  unknown,
  SubjectPosition
>;

export type EventBus = Emitter<Record<EventType, unknown>>;
