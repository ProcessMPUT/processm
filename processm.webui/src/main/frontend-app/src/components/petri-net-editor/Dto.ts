import { PlaceType } from "@/components/petri-net-editor/model/Place";

export interface PlaceDto {
  id: string;
  text: string;
  x?: number;
  y?: number;
  type?: PlaceType;
  tokenCount?: number;
}

export interface TransitionDto {
  id: string;
  text: string;
  x?: number;
  y?: number;
  isSilent: boolean;
}

export interface ArcDto {
  id?: string;
  inElementId: string;
  outElementId: string;
}
