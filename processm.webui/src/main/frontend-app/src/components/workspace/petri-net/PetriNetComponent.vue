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
  }

  private getPlacesAsDto(): PlaceDto[] {
    const places = this.data.data.places?.map((place) => {
      return {
        id: place.id,
        // TODO: Missing `text` attribute in place data from server
        text: "",
        type: this.getPlaceType(place.id),
        tokenCount: this.data.data.initialMarking ? this.data.data.initialMarking[place.id] : 0
      } as PlaceDto;
    });

    return places ?? [];
  }

  private getTransitionsAsDto(): TransitionDto[] {
    const transitions = this.data.data.transitions?.map((transition) => {
      return {
        id: transition.id,
        text: transition.name,
        isSilent: transition.isSilent
      } as TransitionDto;
    });

    return transitions ?? [];
  }

  private getArcsAsDto(): ArcDto[] {
    const arcs = this.data.data.transitions?.flatMap((transition) => {
      const inArcs = transition.inPlaces.map((inPlace: string) => {
        return {
          outElementId: inPlace,
          inElementId: transition.id
        } as ArcDto;
      });

      const outArcs = transition.outPlaces.map((outPlace: string) => {
        return {
          outElementId: transition.id,
          inElementId: outPlace
        } as ArcDto;
      });

      return [...inArcs, ...outArcs];
    });

    return arcs ?? [];
  }

  private getPlaceType(place: string): PlaceType {
    if (this.data.data.initialMarking !== undefined && place in this.data.data.initialMarking) {
      return PlaceType.INITIAL;
    }

    if (this.data.data.finalMarking !== undefined && place in this.data.data.finalMarking) {
      return PlaceType.FINAL;
    }

    return PlaceType.NORMAL;
  }

  private onResize(element: Element) {
    this.$refs.editor.scale();
  }

  @Watch("updateData")
  async saveJson() {
    if (this.updateData) {
      // eslint-disable-next-line
      const rawData = this.$refs.editor.getPetriNetJson();

      // TODO: #160 Remap rawData to ProcessM data format
      // const data = {};
      // this.data.data.xml = data.xml;
    }
  }
}
</script>

<style scoped></style>
