<template>
  <div v-if="data != null" v-resize:debounce.10="onResize">
    <petri-net-editor
      ref="editor"
      :debug="false"
      :run-layouter-on-start="true"
      :show-buttons="showButtons"
      :enable-dragging="enableDragging"
      :places="getPlacesAsDto()"
      :transitions="getTransitionsAsDto()"
      :arcs="getArcsAsDto()"
    ></petri-net-editor>
    <div class="node-details" />
  </div>
  <p v-else>{{ $t("workspace.component.no-data") }}</p>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";
import { PetriNetComponentData } from "@/models/WorkspaceComponent";
import resize from "vue-resize-directive";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";
import PetriNetEditor from "@/components/petri-net-editor/PetriNetEditor.vue";
import { ArcDto, PlaceDto, TransitionDto } from "@/components/petri-net-editor/Dto";
import { PlaceType } from "@/components/petri-net-editor/model/Place";

@Component({
  components: { PetriNetEditor },
  directives: {
    resize
  }
})
export default class PetriNetComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: PetriNetComponentData };

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  @Prop({ default: false })
  readonly updateData = false;

  $refs!: {
    editor: PetriNetEditor;
  };

  private readonly showButtons: boolean =
    this.componentMode === ComponentMode.Edit;

  private readonly enableDragging: boolean =
    this.componentMode === ComponentMode.Edit ||
    this.componentMode === ComponentMode.Interactive;

  mounted() {
    // TODO: How to read initial and final marking?
    console.log(this.data.data);
  }

  private getPlacesAsDto(): PlaceDto[] {
    const places = this.data.data.places?.map((place) => {
      return {
        id: place.id,
        // TODO: Missing attribute
        text: "",
        type: this.getPlaceType(place),
        tokenCount: this.getPlaceType(place) == PlaceType.INITIAL ? 1 : 0
      } as PlaceDto;
    });

    return places ?? [];
  }

  private getTransitionsAsDto(): TransitionDto[] {
    const transitions = this.data.data.transitions?.map((transition) => {
      return {
        // TODO: Missing attribute
        id: transition.name,
        text: transition.name,
        isSilent: transition.isSilent
      } as TransitionDto;
    });

    return transitions ?? [];
  }

  private getArcsAsDto(): ArcDto[] {
    const arcs = this.data.data.transitions?.flatMap((transition) => {
      const inArcs = transition.inPlaces.map((inPlace: any) => {
        return {
          outElementId: inPlace.id,
          // TODO: Replace with id
          inElementId: transition.name
        } as ArcDto;
      });

      const outArcs = transition.outPlaces.map((outPlace: any) => {
        return {
          // TODO: Replace with id
          outElementId: transition.name,
          inElementId: outPlace.id
        } as ArcDto;
      });

      return [...inArcs, ...outArcs];
    });

    console.log(arcs);
    return arcs ?? [];
  }

  private getPlaceType(place: any): PlaceType {
    if (place in this.data.data.initialMarking) {
      return PlaceType.INITIAL;
    }

    if (place in this.data.data.finalMarking) {
      return PlaceType.FINAL;
    }

    return PlaceType.NORMAL;
  }

  private onResize(element: Element) {
    this.$refs.editor.scale(element.clientWidth, element.clientHeight);

    // TODO: Implement
  }

  @Watch("updateData")
  async saveJson() {
    if (this.updateData) {
      // eslint-disable-next-line
      const rawData = this.$refs.editor.getPetriNetJson();

      // TODO: Remap rawData to ProcessM data format
      // const data = {};
      // this.data.data.xml = data.xml;
    }
  }
}
</script>

<style scoped></style>
