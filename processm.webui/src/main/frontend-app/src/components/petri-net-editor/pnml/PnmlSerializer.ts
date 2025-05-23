import {
  ArcDto,
  PlaceDto,
  TransitionDto
} from "@/components/petri-net-editor/Dto";
import {Transition} from "@/components/petri-net-editor/model/Transition";
import {Place, PlaceType} from "@/components/petri-net-editor/model/Place";
import {Arc} from "@/components/petri-net-editor/model/Arc";
import {PetriNetState} from "@/components/petri-net-editor/model/PetriNetState";


function getFirstElementByTagName(element: Element | null, qualifiedName: string): Element | null {
  if (element == null)
    return null
  const list = element.getElementsByTagName(qualifiedName)
  if (list.length >= 1)
    return list[0]
  else
    return null
}

function safeParseInt(text: string | null | undefined): number | null {
  if (text == null)
    return null
  else
    return parseInt(text)
}

export class PnmlSerializer {
  static serialize(state: PetriNetState, name: string): string {
    const parser = new DOMParser();
    const pnmlDoc: Document = parser.parseFromString(
      `<?xml version="1.0" encoding="UTF-8"?>
            <pnml>
                <net id="" type="http://www.pnml.org/version-2009/grammar/ptnet">
                    <name>
                        <text></text>
                    </name>
                    <page id="page1"></page>
                </net>
            </pnml>
        `,
      "text/xml"
    );
    pnmlDoc.querySelector("pnml > net > name > text")!.textContent = name;
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
        tokenCount: place.tokenCount ?? 0,
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
      const position = getFirstElementByTagName(pnmlTransition,"position");
      transitions.push({
        id: pnmlTransition.getAttribute("id"),
        text: getFirstElementByTagName(pnmlTransition,"text")?.textContent ?? "",
        x: safeParseInt(position?.getAttribute("x")),
        y: safeParseInt(position?.getAttribute("y"))
      } as TransitionDto);
    }
    return transitions;
  }


  private static deserializePlaces(pnmlDoc: Document): PlaceDto[] {
    const places = [];
    for (const pnmlPlace of pnmlDoc.getElementsByTagName("place")) {
      const position = getFirstElementByTagName(pnmlPlace, "position");
      const initialMarking = getFirstElementByTagName(getFirstElementByTagName(pnmlPlace, "initialMarking"), "text")?.textContent

      const placeType: PlaceType | null = safeParseInt(getFirstElementByTagName(pnmlPlace, "type")?.textContent)

      places.push({
        id: pnmlPlace.getAttribute("id"),
        text: getFirstElementByTagName(pnmlPlace,"text")?.textContent ?? "",
        x: safeParseInt(position?.getAttribute("x")),
        y: safeParseInt(position?.getAttribute("y")),
        type: placeType,
        tokenCount: safeParseInt(initialMarking)
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
      const transitionElement = page.ownerDocument.createElement("transition")
      transitionElement.innerHTML += `
                    <name>
                        <text></text>
                        <graphics>
                          <offset x="0" y="0"/>
                        </graphics>
                    </name>
                    <graphics>
                        <position/>
                    </graphics>
            `;
      transitionElement.setAttribute("id", transition.id)
      transitionElement.querySelector("name > text")!.textContent = transition.text
      transitionElement.querySelector("graphics > position")!.setAttribute("x", transition.x.toString())
      transitionElement.querySelector("graphics > position")!.setAttribute("y", transition.y.toString())

      page.appendChild(transitionElement)
    }
  }

  private static appendPlaces(page: Element, places: Place[]): void {
    for (const place of places) {
      const placeElement = page.ownerDocument.createElement("place")
      placeElement.innerHTML += `
                    <name>
                         <text></text>
                         <graphics>
                              <offset x="0" y="0"/>
                         </graphics>
                    </name>
                    <graphics>
                         <position/>
                    </graphics>
                    <initialMarking>
                         <text></text>
                    </initialMarking>
                    <toolspecific tool="processm" version="1.0">
                        <type>${place.type}</type>
                    </toolspecific>
            `;
      placeElement.setAttribute("id", place.id)
      placeElement.querySelector("name > text")!.textContent = place.text
      placeElement.querySelector("graphics > position")!.setAttribute("x", place.cx.toString())
      placeElement.querySelector("graphics > position")!.setAttribute("y", place.cy.toString())
      placeElement.querySelector("initialMarking")!.querySelector("text")!.textContent = place.tokenCount.toString()
      placeElement.querySelector("toolspecific > type")!.textContent = place.type.toString()

      page.appendChild(placeElement)
    }
  }

  private static appendArcs(page: Element, arcs: Arc[]): void {
    for (const arc of arcs) {
      const arcElement = page.ownerDocument.createElement("arc")
      arcElement.innerHTML = `
                    <inscription>
                         <text></text>
                    </inscription>
                    <graphics/>
            `;
      arcElement.setAttribute("id", arc.id)
      arcElement.setAttribute("source", arc.outElementId)
      arcElement.setAttribute("target", arc.inElementId)

      page.appendChild(arcElement)
    }
  }
}
