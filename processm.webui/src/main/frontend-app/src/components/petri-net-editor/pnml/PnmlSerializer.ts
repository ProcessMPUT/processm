import {
  ArcDto,
  PlaceDto,
  TransitionDto
} from "@/components/petri-net-editor/Dto";
import { Transition } from "@/components/petri-net-editor/model/Transition";
import {
  Place,
  PlaceType
} from "@/components/petri-net-editor/model/Place";
import { Arc } from "@/components/petri-net-editor/model/Arc";
import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";

export class PnmlSerializer {
  static serialize(state: PetriNetState, name: string): string {
    const parser = new DOMParser();
    const pnmlDoc: Document = parser.parseFromString(
      `<?xml version="1.0" encoding="UTF-8"?>
            <pnml>
                <net id="" type="http://www.pnml.org/version-2009/grammar/ptnet">
                    <name>
                        <text>${name}</text>
                    </name>
                    <page id="page1"></page>
                </net>
            </pnml>
        `,
      "text/xml"
    );
    const page: Element = pnmlDoc.getElementsByTagName("page")[0];

    PnmlSerializer.appendTransitions(page, state.transitions);
    PnmlSerializer.appendPlaces(page, state.places);
    PnmlSerializer.appendArcs(page, state.arcs);

    return new XMLSerializer().serializeToString(pnmlDoc);
  }

  static deserialize(data: string): PetriNetState {
    const parser = new DOMParser();
    const pnmlDoc: Document = parser.parseFromString(data, "text/xml");

    const places = PnmlSerializer.deserializePlaces(pnmlDoc);
    const transitions = PnmlSerializer.deserializeTransitions(pnmlDoc);
    const arcs = PnmlSerializer.deserializeArcs(pnmlDoc);

    const state = new PetriNetState();

    places.forEach((place) =>
      state.createPlace({
        x: place.x ?? 0,
        y: place.y ?? 0,
        text: place.text,
        tokenCount: 0,
        id: place.id,
        type: place.type
      })
    );
    transitions.forEach((transition) =>
      state.createTransition({
        x: transition.x ?? 0,
        y: transition.y ?? 0,
        text: transition.text,
        id: transition.id
      })
    );
    arcs.forEach((arc) =>
      state.createArc(arc.outElementId, arc.inElementId, arc.id)
    );

    return state;
  }

  private static deserializeTransitions(pnmlDoc: Document): TransitionDto[] {
    const transitions = [];
    for (const pnmlTransition of pnmlDoc.getElementsByTagName("transition")) {
      const position = pnmlTransition.getElementsByTagName("position")[0];
      transitions.push({
        id: pnmlTransition.getAttribute("id"),
        text: pnmlTransition.getElementsByTagName("text")[0].textContent,
        x: parseInt(position.getAttribute("x")!),
        y: parseInt(position.getAttribute("y")!)
      } as TransitionDto);
    }
    return transitions;
  }

  private static deserializePlaces(pnmlDoc: Document): PlaceDto[] {
    const places = [];
    for (const pnmlPlace of pnmlDoc.getElementsByTagName("place")) {
      const position = pnmlPlace.getElementsByTagName("position")[0];
      const initialMarking = pnmlPlace
        .getElementsByTagName("initialMarking")[0]
        .getElementsByTagName("text")[0].textContent!;

      let typeElement = pnmlPlace.getElementsByTagName("type")[0];
      let placeType: PlaceType | null = null;
      if (typeElement) {
        placeType = parseInt(typeElement.textContent!);
      }

      places.push({
        id: pnmlPlace.getAttribute("id"),
        text: pnmlPlace.getElementsByTagName("text")[0].textContent,
        x: parseInt(position.getAttribute("x")!),
        y: parseInt(position.getAttribute("y")!),
        type: placeType,
        tokenCount: parseInt(initialMarking)
      } as PlaceDto);
    }
    return places;
  }

  private static deserializeArcs(pnmlDoc: Document): ArcDto[] {
    const arcs = [];
    for (const pnmlArc of pnmlDoc.getElementsByTagName("arc")) {
      arcs.push({
        id: pnmlArc.getAttribute("id"),
        outElementId: pnmlArc.getAttribute("source"),
        inElementId: pnmlArc.getAttribute("target")
      } as ArcDto);
    }
    return arcs;
  }

  private static appendTransitions(
    page: Element,
    transitions: Transition[]
  ): void {
    for (const transition of transitions) {
      const transitionElement = `
                <transition id="${transition.id}">
                    <name>
                        <text>${transition.text}</text>
                        <graphics>
                          <offset x="0" y="0"/>
                        </graphics>
                    </name>
                    <graphics>
                        <position x="${transition.x}" y="${transition.y}"/>
                    </graphics>
                </transition>
            `;

      page.innerHTML += transitionElement;
    }
  }

  private static appendPlaces(page: Element, places: Place[]): void {
    for (const place of places) {
      const placeElement = `
                 <place id="${place.id}">
                    <name>
                         <text>${place.text}</text>
                         <graphics>
                              <offset x="0" y="0"/>
                         </graphics>
                    </name>
                    <graphics>
                         <position x="${place.cx}" y="${place.cy}"/>
                    </graphics>
                    <initialMarking>
                         <text>${place.tokenCount}</text>
                    </initialMarking>
                    <toolspecific tool="processm" version="1.0">
                        <type>${place.type}</type>
                    </toolspecific>
                 </place>
            `;

      page.innerHTML += placeElement;
    }
  }

  private static appendArcs(page: Element, arcs: Arc[]): void {
    for (const arc of arcs) {
      const arcElement = `
                <arc id="${arc.id}"
                    source="${arc.outElementId}" target="${arc.inElementId}">
                    <inscription>
                         <text></text>
                    </inscription>
                    <graphics/>
                </arc>
            `;

      page.innerHTML += arcElement;
    }
  }
}
