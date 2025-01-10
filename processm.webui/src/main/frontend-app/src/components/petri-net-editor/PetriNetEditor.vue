<template>
  <div style="height: 100%">
    <edit-place-dialog v-if="isEditPlaceDialogVisible" :place="selectedPlace" @close="closeEditPlaceDialog" />

    <edit-transition-dialog v-if="isEditTransitionDialogVisible" :transition="selectedTransition" @close="closeEditTransitionDialog" />

    <export-pnml-dialog v-if="isExportPnmlDialogVisible" :state="petriNetManager.state" @close="closeExportPnmlDialog" />

    <v-container v-if="showButtons" elevation-6 fill-width fluid pa-0 style="z-index: 1">
      <slot name="toolbar"> </slot>
      <v-tooltip bottom v-if="hasNewerVersion">
        <template v-slot:activator="{ on, attrs }">
          <v-btn v-bind="attrs" v-on="on" icon v-if="showButtons" @click="$emit('loadNewestModel')">
            <v-icon color="warning">priority_high</v-icon>
          </v-btn>
          <v-icon v-bind="attrs" v-on="on" color="warning" v-else>priority_high</v-icon>
        </template>
        <span v-if="showButtons">{{ $t("workspace.component.edit.load-new-model") }}</span>
        <span v-else>{{ $t("workspace.component.new-model-available") }}</span>
      </v-tooltip>
      <v-btn v-if="showButtons && !isDebuggerEnabled" class="ma-2" color="primary" light @click="runLayouter">
        {{ $t("common.calculate-layout") }}
      </v-btn>
      <v-btn v-if="showButtons && !isDebuggerEnabled" class="ma-2" color="primary" light @click="runDebugger">
        {{ $t("common.debug") }}
      </v-btn>
      <v-btn v-if="showButtons && !isDebuggerEnabled" class="ma-2" color="primary" light type="file" @click="selectPnmlFile">
        {{ $t("petri-net.pnml.import") }}
        <input ref="importInput" accept=".pnml" hidden type="file" @change="importPnml" />
      </v-btn>
      <v-btn v-if="showButtons && !isDebuggerEnabled" class="ma-2" color="primary" light type="file" v-on:click="() => (isExportPnmlDialogVisible = true)">
        {{ $t("petri-net.pnml.export") }}
      </v-btn>

      <v-btn v-if="isDebuggerEnabled" class="ma-2" color="primary" light v-on:click="() => (isDebuggerEnabled = false)"> Stop debugger </v-btn>
    </v-container>
    <v-toolbar v-if="!showButtons && (hasNewerVersion || $slots.toolbar)" class="toolbar" dense elevation="0" floating>
      <slot name="toolbar"> </slot>
      <v-tooltip bottom v-if="hasNewerVersion">
        <template v-slot:activator="{ on, attrs }">
          <v-btn v-bind="attrs" v-on="on" icon v-if="showButtons" @click="$emit('loadNewestModel')">
            <v-icon color="warning">priority_high</v-icon>
          </v-btn>
          <v-icon v-bind="attrs" v-on="on" color="warning" v-else>priority_high</v-icon>
        </template>
        <span v-if="showButtons">{{ $t("workspace.component.edit.load-new-model") }}</span>
        <span v-else>{{ $t("workspace.component.new-model-available") }}</span>
      </v-tooltip>
    </v-toolbar>

    <PetriNetDebugger v-if="isDebuggerEnabled" :state="this.petriNetManager.state" class="fill-height" />

    <div v-show="!isDebuggerEnabled" class="fill-height">
      <svg ref="editorSvg" height="100%" width="100%">
        <defs>
          <marker
            :id="'arrow-' + svgId"
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

      <context-menu v-if="showButtons" v-show="!isDebuggerEnabled" ref="contextMenu" :items="this.contextMenuItems" @expand="this.onContextMenuExpand" />
    </div>
  </div>
</template>

<script lang="ts">
import * as d3 from "d3-selection";
import Component from "vue-class-component";
import Vue from "vue";
import ContextMenu from "@/components/petri-net-editor/context-menu/ContextMenu.vue";
import { Prop } from "vue-property-decorator";
import PetriNetDebugger from "@/components/petri-net-editor/petri-net-debugger/PetriNetDebugger.vue";
import EditPlaceDialog from "@/components/petri-net-editor/edit-place-dialog/EditPlaceDialog.vue";
import EditTransitionDialog from "@/components/petri-net-editor/edit-transition-dialog/EditTransitionDialog.vue";
import ExportPnmlDialog from "@/components/petri-net-editor/export-pnml-dialog/ExportPnmlDialog.vue";
import { SvgPlace } from "./svg/SvgPlace";
import { ArcDto, PlaceDto, TransitionDto } from "./Dto";
import { ContextMenuItem } from "@/components/petri-net-editor/context-menu/ContextMenuItem";
import { PetriNetSvgManager } from "@/components/petri-net-editor/svg/PetriNetSvgManager";
import { Layouter } from "@/components/petri-net-editor/layouter/interfaces/Layouter";
import { BlockLayouter } from "@/components/petri-net-editor/layouter/BlockLayouter";
import { SvgTransition } from "@/components/petri-net-editor/svg/SvgTransition";
import { PnmlSerializer } from "@/components/petri-net-editor/pnml/PnmlSerializer";
import { v4 as uuidv4 } from "uuid";
import { Place } from "@/components/petri-net-editor/model/Place";
import { Transition } from "@/components/petri-net-editor/model/Transition";
import { Arc } from "@/components/petri-net-editor/model/Arc";
import svgPanZoom from "svg-pan-zoom";
import { waitForRepaint } from "@/utils/waitForRepaint";

@Component({
  name: "petri-net-editor",
  components: {
    ExportPnmlDialog,
    EditTransitionDialog,
    PetriNetDebugger,
    EditPlaceDialog,
    ContextMenu
  }
})
export default class PetriNetEditor extends Vue {
  $refs!: {
    importInput: HTMLInputElement;
    contextMenu: ContextMenu;
    editorSvg: HTMLElement;
  };
  private svgId = uuidv4();
  private isEditPlaceDialogVisible = false;
  private isEditTransitionDialogVisible = false;
  private isExportPnmlDialogVisible = false;
  private isDebuggerEnabled = false;
  private isRunExperimentButtonVisible = false;
  private selectedPlace!: SvgPlace;
  private selectedTransition!: SvgTransition;
  private targetIsPlaceOrTransition = false;
  private targetIsDeletable = false;
  private isSetTokenVisible = false;
  private isSetWidthVisible = false;
  private contextMenuTargetId = "";
  @Prop()
  private places!: PlaceDto[];
  @Prop()
  private transitions!: TransitionDto[];
  @Prop()
  private arcs!: ArcDto[];
  @Prop()
  private showButtons!: boolean;
  @Prop()
  private hasNewerVersion!: boolean;
  @Prop()
  private enableDragging!: boolean;
  @Prop()
  private debug!: boolean;
  @Prop()
  private runLayouterOnStart!: boolean;
  private contextMenuItems: ContextMenuItem[] = [];
  private petriNetManager!: PetriNetSvgManager;
  private layouter!: Layouter;
  private zoomer?: SvgPanZoom.Instance;

  // noinspection JSUnusedGlobalSymbols
  mounted() {
    const editorId = `editor-${this.svgId}`;

    this.$refs.editorSvg.setAttribute("id", editorId);

    this.petriNetManager = new PetriNetSvgManager(this.svgId, d3.select(`#${editorId}`), this.enableDragging);

    this.layouter = new BlockLayouter(this.debug);

    waitForRepaint(this.redraw.bind(this));
  }

  redraw(forceLayouter: boolean = false) {
    this.petriNetManager.reset();

    this.places.forEach((place) =>
      this.petriNetManager.createPlace({
        x: place.x ?? 0,
        y: place.y ?? 0,
        text: place.text,
        tokenCount: 0,
        id: place.id,
        type: place.type
      })
    );
    this.transitions.forEach((transition) =>
      this.petriNetManager.createTransition({
        x: transition.x ?? 0,
        y: transition.y ?? 0,
        text: transition.text,
        id: transition.id,
        isSilent: transition.isSilent
      })
    );
    this.arcs.forEach((arc) => this.petriNetManager.connect(arc.outElementId, arc.inElementId));

    if (this.runLayouterOnStart) {
      this.runLayouter();
    }
    if (this.zoomer === undefined) this.zoomer = svgPanZoom(this.$refs.editorSvg);
    this.zoomer.updateBBox().fit();
  }

  getPetriNetJson(): {
    places: Place[];
    transitions: Transition[];
    arcs: Arc[];
  } {
    return {
      places: this.petriNetManager.state.places,
      transitions: this.petriNetManager.state.transitions,
      arcs: this.petriNetManager.state.arcs
    };
  }

  private createPlace(): void {
    const scale = this.petriNetManager.scale;
    const rect = this.$refs.editorSvg.getBoundingClientRect();
    this.petriNetManager.createPlace({
      x: (this.$refs.contextMenu.x - rect.x) / scale,
      y: (this.$refs.contextMenu.y - rect.y) / scale,
      text: this.$t("petri-net.new.place").toString()
    });
  }

  private createTransition(): void {
    const scale = this.petriNetManager.scale;
    const rect = this.$refs.editorSvg.getBoundingClientRect();
    this.petriNetManager.createTransition({
      x: (this.$refs.contextMenu.x - rect.x) / scale,
      y: (this.$refs.contextMenu.y - rect.y) / scale,
      text: this.$t("petri-net.new.transition").toString()
    });
  }

  private startConnect(): void {
    this.petriNetManager.startConnect(this.contextMenuTargetId);
  }

  private onContextMenuExpand(target: Element | null): void {
    const isPlaceOrTransition = target instanceof SVGCircleElement || target instanceof SVGRectElement;

    this.targetIsPlaceOrTransition = isPlaceOrTransition;
    this.targetIsDeletable = isPlaceOrTransition || target instanceof SVGLineElement;
    this.isSetTokenVisible = target instanceof SVGCircleElement;
    this.isSetWidthVisible = target instanceof SVGLineElement;
    this.contextMenuTargetId = isPlaceOrTransition || target instanceof SVGLineElement ? target!.id : "";

    this.contextMenuItems = this.createContextMenuItems();
  }

  private showEditDialog() {
    const element = this.petriNetManager.getElement(this.contextMenuTargetId);
    if (element instanceof SvgPlace) {
      this.selectedPlace = this.petriNetManager.getPlace(this.contextMenuTargetId);
      this.isEditPlaceDialogVisible = true;
    } else if (element instanceof SvgTransition) {
      this.selectedTransition = this.petriNetManager.getTransition(this.contextMenuTargetId);
      this.isEditTransitionDialogVisible = true;
    }
  }

  private closeEditPlaceDialog() {
    this.closeDialog(() => (this.isEditPlaceDialogVisible = false));
  }

  private closeEditTransitionDialog() {
    this.closeDialog(() => (this.isEditTransitionDialogVisible = false));
  }

  private closeExportPnmlDialog() {
    this.closeDialog(() => (this.isExportPnmlDialogVisible = false));
  }

  private closeDialog(callback: () => void) {
    setTimeout(callback, 200);
  }

  private runLayouter(): void {
    const [isCorrect, message] = this.petriNetManager.state.isCorrectNet();

    if (!isCorrect) {
      alert(`Can't run layouter!\n${message}`);
      return;
    }

    this.petriNetManager.state = this.layouter.run(this.petriNetManager.state);
  }

  private runDebugger(): void {
    const [isCorrect, message] = this.petriNetManager.state.isCorrectNet();

    if (!isCorrect) {
      alert(message);
      return;
    }

    this.isDebuggerEnabled = true;
  }

  private selectPnmlFile(): void {
    this.$refs.importInput.click();
  }

  private importPnml(event: any): void {
    const eventTarget = event.currentTarget! as HTMLInputElement;

    const reader = new FileReader();
    reader.onload = (loadEvent) => {
      const data: string = loadEvent.target!.result as string;
      this.petriNetManager.state = PnmlSerializer.deserialize(data);
      this.layouter.clearOverlay();
    };

    const file = event.target.files[0];
    reader.readAsText(file);

    eventTarget.value = "";
  }

  private createContextMenuItems(): ContextMenuItem[] {
    return [
      {
        name: this.$t("petri-net.connect").toString(),
        isVisible: this.targetIsPlaceOrTransition,
        action: () => this.startConnect()
      },
      {
        name: this.$t("petri-net.create.place").toString(),
        isVisible: this.contextMenuTargetId === "",
        action: () => this.createPlace()
      },
      {
        name: this.$t("petri-net.create.transition").toString(),
        isVisible: this.contextMenuTargetId === "",
        action: () => this.createTransition()
      },
      {
        name: this.$t("common.edit").toString(),
        isVisible: this.targetIsPlaceOrTransition,
        action: () => this.showEditDialog()
      },
      {
        name: this.$t("common.remove").toString(),
        isVisible: this.targetIsDeletable,
        action: () => this.removeElementOrArc()
      }
    ];
  }

  private removeElementOrArc() {
    if (this.petriNetManager.hasElement(this.contextMenuTargetId)) {
      this.petriNetManager.removeElement(this.contextMenuTargetId);
    } else {
      this.petriNetManager.removeArc(this.contextMenuTargetId);
    }
  }
}
</script>