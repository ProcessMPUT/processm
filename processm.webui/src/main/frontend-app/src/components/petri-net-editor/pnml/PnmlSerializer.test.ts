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

test("deserialize", () => {
  const document = `<?xml version="1.0" encoding="UTF-8"?>
            <pnml>
                <net id="" type="http://www.pnml.org/version-2009/grammar/ptnet">
                    <name>
                        <text>blah</text>
                    </name>
                    <page id="page1">
                    <transition id="t1">
                    <name>
                        <text>transition1</text>
                        <graphics>
                          <offset x="0" y="0"/>
                        </graphics>
                    </name>
                    <graphics>
                        <position x="100" y="200"/>
                    </graphics>
                    </transition>
                    <transition id="t2">
                    <name>
                        <text>transition2</text>
                        <graphics>
                          <offset x="0" y="0"/>
                        </graphics>
                    </name>
                    <graphics>
                        <position x="150" y="250"/>
                    </graphics>
                    </transition>
                    <place id="p1">
                     <name>
                         <text>place1</text>
                         <graphics>
                              <offset x="0" y="0"/>
                         </graphics>
                    </name>
                    <graphics>
                         <position x="50" y="100"/>
                    </graphics>
                    <initialMarking>
                         <text>17</text>
                    </initialMarking>
                    <toolspecific tool="processm" version="1.0">
                        <type>2</type>
                    </toolspecific>
                    </place>
                     <place id="p2">
                     <name>
                         <text>place2</text>
                         <graphics>
                              <offset x="0" y="0"/>
                         </graphics>
                    </name>
                    <graphics>
                         <position x="150" y="200"/>
                    </graphics>
                    <initialMarking>
                         <text>7</text>
                    </initialMarking>
                    <toolspecific tool="processm" version="1.0">
                        <type>1</type>
                    </toolspecific>
                    </place>
                    <arc id="a1" source="p1" target="t1"/>
                    <arc id="a2" source="t1" target="p2"/>
                    </page>
                </net>
            </pnml>
    `
  const pnet = PnmlSerializer.deserialize(document)
  expect(pnet.transitions.length).toBe(2)
  expect(pnet.transitions[0].id).toBe("t1")
  expect(pnet.transitions[0].text).toBe("transition1")
  expect(pnet.transitions[0].x).toBe(100)
  expect(pnet.transitions[0].y).toBe(200)
  expect(pnet.transitions[1].id).toBe("t2")
  expect(pnet.transitions[1].text).toBe("transition2")
  expect(pnet.transitions[1].x).toBe(150)
  expect(pnet.transitions[1].y).toBe(250)
  expect(pnet.places.length).toBe(2)
  expect(pnet.places[0].id).toBe("p1")
  expect(pnet.places[0].text).toBe("place1")
  expect(pnet.places[0].cx).toBe(50)
  expect(pnet.places[0].cy).toBe(100)
  expect(pnet.places[0].type).toBe(PlaceType.NORMAL)
  expect(pnet.places[0].tokenCount).toBe(17)
  expect(pnet.places[1].id).toBe("p2")
  expect(pnet.places[1].text).toBe("place2")
  expect(pnet.places[1].cx).toBe(150)
  expect(pnet.places[1].cy).toBe(200)
  expect(pnet.places[1].type).toBe(PlaceType.FINAL)
  expect(pnet.places[1].tokenCount).toBe(7)
  expect(pnet.arcs.length).toBe(2)
  expect(pnet.arcs[0].id).toBe("a1")
  expect(pnet.arcs[0].outElementId).toBe("p1")
  expect(pnet.arcs[0].inElementId).toBe("t1")
  expect(pnet.arcs[1].id).toBe("a2")
  expect(pnet.arcs[1].outElementId).toBe("t1")
  expect(pnet.arcs[1].inElementId).toBe("p2")
});


test("deserialize no graphics", () => {
  const document = `<?xml version="1.0" encoding="UTF-8"?>
            <pnml>
                <net id="" type="http://www.pnml.org/version-2009/grammar/ptnet">
                    <name>
                        <text>blah</text>
                    </name>
                    <page id="page1">
                    <transition id="t1">
                    <name>
                        <text>transition1</text>
                    </name>
                    </transition>
                    <transition id="t2">
                    <name>
                        <text>transition2</text>
                    </name>
                    </transition>
                    <place id="p1">
                     <name>
                         <text>place1</text>
                    </name>
                    <initialMarking>
                         <text>17</text>
                    </initialMarking>
                    <toolspecific tool="processm" version="1.0">
                        <type>2</type>
                    </toolspecific>
                    </place>
                     <place id="p2">
                     <name>
                         <text>place2</text>
                    </name>
                    <initialMarking>
                         <text>7</text>
                    </initialMarking>
                    <toolspecific tool="processm" version="1.0">
                        <type>1</type>
                    </toolspecific>
                    </place>
                    <arc id="a1" source="p1" target="t1"/>
                    <arc id="a2" source="t1" target="p2"/>
                    </page>
                </net>
            </pnml>
    `
  const pnet = PnmlSerializer.deserialize(document)
  expect(pnet.transitions.length).toBe(2)
  expect(pnet.transitions[0].id).toBe("t1")
  expect(pnet.transitions[0].text).toBe("transition1")
  expect(pnet.transitions[0].x).not.toBeNull()
  expect(pnet.transitions[0].y).not.toBeNull()
  expect(pnet.transitions[1].id).toBe("t2")
  expect(pnet.transitions[1].text).toBe("transition2")
  expect(pnet.transitions[1].x).not.toBeNull()
  expect(pnet.transitions[1].y).not.toBeNull()
  expect(pnet.places.length).toBe(2)
  expect(pnet.places[0].id).toBe("p1")
  expect(pnet.places[0].text).toBe("place1")
  expect(pnet.places[0].cx).not.toBeNull()
  expect(pnet.places[0].cy).not.toBeNull()
  expect(pnet.places[0].type).toBe(PlaceType.NORMAL)
  expect(pnet.places[0].tokenCount).toBe(17)
  expect(pnet.places[1].id).toBe("p2")
  expect(pnet.places[1].text).toBe("place2")
  expect(pnet.places[1].cx).not.toBeNull()
  expect(pnet.places[1].cy).not.toBeNull()
  expect(pnet.places[1].type).toBe(PlaceType.FINAL)
  expect(pnet.places[1].tokenCount).toBe(7)
  expect(pnet.arcs.length).toBe(2)
  expect(pnet.arcs[0].id).not.toBeNull()
  expect(pnet.arcs[0].outElementId).toBe("p1")
  expect(pnet.arcs[0].inElementId).toBe("t1")
  expect(pnet.arcs[1].id).toBe("a2")
  expect(pnet.arcs[1].outElementId).toBe("t1")
  expect(pnet.arcs[1].inElementId).toBe("p2")
});

test("deserialize - underspecified transition", () => {
  const document = `<?xml version="1.0" encoding="UTF-8"?>
            <pnml>
                <net id="" type="http://www.pnml.org/version-2009/grammar/ptnet">
                    <name>
                        <text>blah</text>
                    </name>
                    <page id="page1">
                    <transition id="t1">
                    <name>
                        <text>transition1</text>
                    </name>
                    </transition>
                    <transition/>
                    </page>
                </net>
            </pnml>
    `
  const pnet = PnmlSerializer.deserialize(document)
  expect(pnet.transitions.length).toBe(2)
  expect(pnet.transitions[0].id).toBe("t1")
  expect(pnet.transitions[0].text).toBe("transition1")
  expect(pnet.transitions[1].id).not.toBeNull()
  expect(pnet.transitions[1].text).toBe("")
  expect(pnet.transitions[1].x).not.toBeNull()
  expect(pnet.transitions[1].y).not.toBeNull()
  expect(pnet.places.length).toBe(0)
  expect(pnet.arcs.length).toBe(0)
});

test("deserialize - underspecified place", () => {
  const document = `<?xml version="1.0" encoding="UTF-8"?>
            <pnml>
                <net id="" type="http://www.pnml.org/version-2009/grammar/ptnet">
                    <name>
                        <text>blah</text>
                    </name>
                    <page id="page1">
                    <place/>
                    </page>
                </net>
            </pnml>
    `
  const pnet = PnmlSerializer.deserialize(document)
  expect(pnet.places.length).toBe(1)
  expect(pnet.places[0].id).not.toBeNull()
  expect(pnet.places[0].text).toBe("")
  expect(pnet.places[0].cx).not.toBeNull()
  expect(pnet.places[0].cy).not.toBeNull()
  expect(pnet.places[0].tokenCount).toBe(0)
  expect(pnet.places[0].type).toBe(PlaceType.NORMAL)
  expect(pnet.transitions.length).toBe(0)
  expect(pnet.arcs.length).toBe(0)
});