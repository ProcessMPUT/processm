<template>
  <div v-if="data != null" v-resize:debounce.10="onResize">
    <petri-net-editor
      ref="editor"
      :debug="false"
      :run-layouter-on-start="!hasWorkingLayout"
      :show-buttons="showButtons"
      :enable-dragging="enableDragging"
      :has-newer-version="hasNewerVersion()"
      :places="getPlacesAsDto()"
      :transitions="getTransitionsAsDto()"
      :arcs="getArcsAsDto()"
      @loadNewestModel="loadNewestModel"
    ></petri-net-editor>
    <div class="node-details" />
  </div>
  <p v-else>{{ $t("workspace.component.no-data") }}</p>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import { PetriNetComponentData, ProcessModelCustomizationData, WorkspaceComponent } from "@/models/WorkspaceComponent";
import resize from "vue-resize-directive";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";
import PetriNetEditor from "@/components/petri-net-editor/PetriNetEditor.vue";
import { ArcDto, PlaceDto, TransitionDto } from "@/components/petri-net-editor/Dto";
import { PlaceType } from "@/components/petri-net-editor/model/Place";
import AlignmentsDialog from "@/components/AlignmentsDialog.vue";
import { ComponentData } from "@/openapi";
import WorkspaceService from "@/services/WorkspaceService";
import { waitForRepaint } from "@/utils/waitForRepaint";

@Component({
  computed: {
    ComponentMode() {
      return ComponentMode;
    }
  },
  components: { AlignmentsDialog, PetriNetEditor },
  directives: {
    resize
  }
})
export default class PetriNetComponent extends Vue {
  @Inject() workspaceService!: WorkspaceService;

  @Prop()
  readonly workspaceId!: string;

  @Prop({ default: {} })
  readonly data!: WorkspaceComponent & { data: PetriNetComponentData };

  @Prop({ default: null })
  readonly componentMode!: ComponentMode | null;

  @Prop({ default: false })
  readonly updateData = false;

  $refs!: {
    editor: PetriNetEditor;
  };

  private readonly showButtons: boolean = this.componentMode === ComponentMode.Edit;

  private readonly enableDragging: boolean = this.componentMode === ComponentMode.Edit || this.componentMode === ComponentMode.Interactive;

  private get petriNet() {
    return this.data.data as PetriNetComponentData;
  }

  private get hasWorkingLayout() {
    const layout = (this.data.customizationData as ProcessModelCustomizationData)?.layout;
    if (layout == undefined || layout.length == 0) return false;
    const knownIds: Set<String> = new Set(
      layout.map((item) => {
        return item.id;
      })
    );
    for (const place of this.petriNet.places ?? []) {
      if (!knownIds.has(place.id)) return false;
    }
    for (const transition of this.petriNet.transitions ?? []) {
      if (!knownIds.has(transition.id)) return false;
    }
    return true;
  }

  private get layout() {
    const result: Record<string, { id: string; x: number; y: number }> = {};
    const items = (this.data.customizationData as ProcessModelCustomizationData)?.layout;
    if (items !== undefined) {
      for (const item of items) result[item.id] = item;
    }
    return result;
  }

  private getPlacesAsDto(): PlaceDto[] {
    const layout = this.layout;
    const places = this.petriNet.places?.map((place) => {
      return {
        id: place.id,
        // TODO: Missing `text` attribute in place data from server
        text: "",
        type: this.getPlaceType(place.id),
        tokenCount: this.petriNet.initialMarking ? this.petriNet.initialMarking[place.id] : 0,
        x: layout[place.id]?.x,
        y: layout[place.id]?.y
      } as PlaceDto;
    });

    return places ?? [];
  }

  private getTransitionsAsDto(): TransitionDto[] {
    const layout = this.layout;
    const transitions = this.petriNet.transitions?.map((transition) => {
      return {
        id: transition.id,
        text: transition.name,
        isSilent: transition.isSilent,
        x: layout[transition.id]?.x,
        y: layout[transition.id]?.y
      } as TransitionDto;
    });

    return transitions ?? [];
  }

  private getArcsAsDto(): ArcDto[] {
    const arcs = this.petriNet.transitions?.flatMap((transition) => {
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
    if (this.petriNet.initialMarking !== undefined && place in this.petriNet.initialMarking) {
      return PlaceType.INITIAL;
    }

    if (this.petriNet.finalMarking !== undefined && place in this.petriNet.finalMarking) {
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
      (this.data.customizationData as ProcessModelCustomizationData).layout = rawData.places
        .map((place) => {
          return { id: place.id, x: place.cx, y: place.cy };
        })
        .concat(
          rawData.transitions.map((transition) => {
            return { id: transition.id, x: transition.x, y: transition.y };
          })
        );

      const inPlaces: Record<string, Array<string>> = {};
      const outPlaces: Record<string, Array<string>> = {};
      for (const arc of rawData.arcs) {
        const inp = arc.inElementId;
        const out = arc.outElementId;
        if (!(out in inPlaces)) inPlaces[out] = [];
        inPlaces[out].push(inp);
        if (!(inp in outPlaces)) outPlaces[inp] = [];
        outPlaces[inp].push(out);
      }

      this.petriNet.transitions = rawData.transitions.map((transition) => {
        return {
          id: transition.id,
          name: transition.text,
          isSilent: transition.isSilent,
          inPlaces: outPlaces[transition.id],
          outPlaces: inPlaces[transition.id]
        };
      });
      this.petriNet.places = rawData.places.map((place) => {
        return { id: place.id };
      });
    }
  }

  hasNewerVersion() {
    const newest = this.data.data.newestVersion;
    const current = this.data.data.modelVersion;
    return newest !== undefined && current !== undefined && newest > current;
  }

  async loadNewestModel() {
    const variantId = this.data.data.newestVersion;
    if (variantId === undefined) return;
    this.data.data = new PetriNetComponentData(
      (await this.workspaceService.getComponentDataVariant(this.workspaceId, this.data.id, variantId)) as Partial<ComponentData>
    );

    await waitForRepaint(() => {
      this.$refs.editor.redraw(true);
    });
  }

  @Watch("data")
  async update() {
    await waitForRepaint(() => {
      this.$refs.editor.redraw(true);
    });
  }
}
</script>

<style scoped></style>