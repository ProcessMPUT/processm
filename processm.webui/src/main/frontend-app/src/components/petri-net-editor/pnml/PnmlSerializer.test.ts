import {PetriNetState} from "@/components/petri-net-editor/model/PetriNetState";
import {PnmlSerializer} from "@/components/petri-net-editor/pnml/PnmlSerializer";
import {expect, test} from '@jest/globals';
import {PlaceType} from "@/components/petri-net-editor/model/Place";

test('XML injection in name', () => {
    const state = new PetriNetState();
    const text = PnmlSerializer.serialize(state, "</pnml>");
    const dom = new DOMParser().parseFromString(text, "text/xml");
    const root = dom.getRootNode().firstChild;
    expect(root).not.toBeNull();
    expect(root?.nodeName).toBe("pnml");
    const node = dom.querySelector("pnml > net > name > text")
    expect(node).not.toBeNull();
    expect(node?.textContent).toBe("</pnml>");
});

test('XML injection in places', () => {
    const state = new PetriNetState()
    const place = state.createPlace({
        "id": '"; </drop\' table>',
        'x': 100,
        'y': 200,
        'text': '</some text>',
        'tokenCount': 5,
        'type': PlaceType.FINAL
    })
    const text = PnmlSerializer.serialize(state, "name")
    const dom = new DOMParser().parseFromString(text, "text/xml")
    const ePlace = dom.querySelector("pnml > net > page > place")
    expect(ePlace?.getAttribute('id')).toBe(place.id)
    expect(ePlace?.querySelector("name > text")?.textContent).toBe(place.text)
    expect(ePlace?.querySelector("graphics > position")?.getAttribute("x")).toBe(place.cx.toString())
    expect(ePlace?.querySelector("graphics > position")?.getAttribute("y")).toBe(place.cy.toString())
    expect(ePlace?.querySelector("initialMarking")?.querySelector("text")?.textContent).toBe(place.tokenCount.toString())
    expect(ePlace?.querySelector("toolspecific > type")?.textContent).toBe(place.type.toString())
});

test('XML injection in transitions', () => {
    const state = new PetriNetState()
    const transition = state.createTransition({
        "id": '"; </drop\' table>',
        'x': 100,
        'y': 200,
        'text': '</some text>',
        'isSilent': false
    })
    const text = PnmlSerializer.serialize(state, "name")
    console.log(text)
    const dom = new DOMParser().parseFromString(text, "text/xml")
    const e = dom.querySelector("pnml > net > page > transition")
    expect(e?.getAttribute('id')).toBe(transition.id)
    expect(e?.querySelector("name > text")?.textContent).toBe(transition.text)
    expect(e?.querySelector("graphics > position")?.getAttribute("x")).toBe(transition.x.toString())
    expect(e?.querySelector("graphics > position")?.getAttribute("y")).toBe(transition.y.toString())
});

test('XML injection in arcs', () => {
    const state = new PetriNetState();
    state.createPlace({"id": ";' drop table \"outId", "x": 0, "y": 0, "text": "some text"})
    state.createTransition({"id": ";' drop table \"inId", "x": 0, "y": 0, "text": "some text"})
    const arc = state.createArc(";' drop table \"outId", ";' drop table \"inId", ";' drop table \"id")
    expect(arc).not.toBeNull()
    const text = PnmlSerializer.serialize(state, "name")
    const dom = new DOMParser().parseFromString(text, "text/xml")
    const e = dom.querySelector("pnml > net > page > arc")
    expect(e?.getAttribute('id')).toBe(arc!.id)
    expect(e?.getAttribute('source')).toBe(arc!.outElementId)
    expect(e?.getAttribute('target')).toBe(arc!.inElementId)
});