<template>
  <div>
    <svg
      id="petri-net-debugger-svg"
      height="100%"
      style="background-color: white"
      width="100%"
    >
      <defs>
        <marker
          id="arrow"
          markerHeight="25"
          markerUnits="userSpaceOnUse"
          markerWidth="25"
          orient="auto-start-reverse"
          refX="0"
          refY="5"
          viewBox="0 0 10 10"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" />
        </marker>
      </defs>

      <g class="arcs"></g>
      <g class="transitions"></g>
      <g class="places"></g>
    </svg>

    <context-menu
      :items="this.contextMenuItems"
      @expand="this.onContextMenuExpand"
    />
  </div>
</template>

<script lang="ts">
import Component from "vue-class-component";
import Vue from "vue";
import { PropSync } from "vue-property-decorator";
import ContextMenu from "@/components/petri-net-editor/context-menu/ContextMenu.vue";
import * as d3 from "d3-selection";
import { PetriNetSvgManager } from "@/components/petri-net-editor/svg/PetriNetSvgManager";
import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { ContextMenuItem } from "@/components/petri-net-editor/context-menu/ContextMenuItem";
import { PlaceType } from "@/components/petri-net-editor/model/Place";

@Component({
  components: { ContextMenu }
})
export default class PetriNetDebugger extends Vue {
  @PropSync("state")
  readonly _state!: PetriNetState;

  private petriNetManager!: PetriNetSvgManager;

  private contextMenuItems: ContextMenuItem[] = [];

  private targetIsInvokable = false;
  private targetIsBackwardInvokable = false;
  private contextMenuTargetId = "";

  // noinspection JSUnusedGlobalSymbols
  mounted() {
    this.petriNetManager = new PetriNetSvgManager(
      d3.select("#petri-net-debugger-svg"),
      false
    );
    this.petriNetManager.state = this._state;
    this.petriNetManager.updateDimensions();

    const places = this.petriNetManager.places;

    const totalTokenCount = places.reduce(
      (sum, place) => sum + place.tokenCount,
      0
    );

    for (const place of this.petriNetManager.places) {
      const preTransitions = this.petriNetManager.state.getPreTransitions(
        place.placeModel
      );
      const postTransitions = this.petriNetManager.state.getPostTransitions(
        place.placeModel
      );
      if (preTransitions.length == 0) {
        place.type = PlaceType.INITIAL;
      } else if (postTransitions.length == 0) {
        place.type = PlaceType.FINAL;
      } else {
        place.type = PlaceType.NORMAL;
      }
    }

    if (totalTokenCount == 0) {
      for (const place of places) {
        place.tokenCount = 0;
      }
      places
        .filter((place) => place.placeModel.type == PlaceType.INITIAL)
        .forEach((place) => (place.tokenCount = 1));
    }

    d3.selectAll("#petri-net-debugger-svg *").on("mousedown.drag", null);

    this.highlightInvokableTransitions();
  }

  onContextMenuExpand(target: Element | null): void {
    this.targetIsInvokable = target?.hasAttribute("invokable") ?? false;
    this.targetIsBackwardInvokable =
      target?.hasAttribute("backwardsInvokable") ?? false;
    this.contextMenuTargetId = target?.id ?? "";

    this.contextMenuItems = this.createContextMenuItems();
  }

  private fireTransition(backwards: boolean) {
    if (this.contextMenuTargetId == "") {
      return;
    }

    const transition = this.petriNetManager.getTransition(
      this.contextMenuTargetId
    );
    const prePlaces = this.petriNetManager.state.getPrePlaces(
      transition.transitionModel
    );
    const postPlaces = this.petriNetManager.state.getPostPlaces(
      transition.transitionModel
    );

    for (const place of !backwards ? prePlaces : postPlaces) {
      this.petriNetManager.getPlace(place.id).tokenCount = place.tokenCount - 1;
    }

    for (const place of !backwards ? postPlaces : prePlaces) {
      this.petriNetManager.getPlace(place.id).tokenCount = place.tokenCount + 1;
    }

    this.highlightInvokableTransitions();
  }

  private highlightInvokableTransitions() {
    const transitions = this.petriNetManager.transitions;
    for (const transition of transitions) {
      const isInvokable = this.petriNetManager.state
        .getPrePlaces(transition.transitionModel)
        .every((place) => place.tokenCount > 0);

      const isBackwardsInvokable = this.petriNetManager.state
        .getPostPlaces(transition.transitionModel)
        .every((place) => place.tokenCount > 0);

      transition.highlight = isInvokable || isBackwardsInvokable;
      transition.invokable = isInvokable;
      transition.backwardsInvokable = isBackwardsInvokable;
    }
  }

  private createContextMenuItems(): ContextMenuItem[] {
    return [
      {
        name: "Fire",
        isVisible: this.targetIsInvokable,
        action: () => this.fireTransition(false)
      },
      {
        name: "Fire backwards",
        isVisible: this.targetIsBackwardInvokable,
        action: () => this.fireTransition(true)
      }
    ];
  }
}
</script>
