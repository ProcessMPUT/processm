<template>
  <div style="height: 100%">
    <edit-place-dialog
      v-if="isEditPlaceDialogVisible"
      :place="selectedPlace"
      @close="closeEditPlaceDialog"
    />

    <edit-transition-dialog
      v-if="isEditTransitionDialogVisible"
      :transition="selectedTransition"
      @close="closeEditTransitionDialog"
    />

    <export-pnml-dialog
      v-if="isExportPnmlDialogVisible"
      :state="petriNetManager.state"
      @close="closeExportPnmlDialog"
    />

    <v-container
      v-if="showButtons"
      elevation-6
      fill-width
      fluid
      pa-0
      style="z-index: 1"
    >
      <v-btn
        v-if="!isDebuggerEnabled"
        class="ma-2"
        color="primary"
        light
        @click="runLayouter"
      >
        Run layouter
      </v-btn>
      <v-btn
        v-if="!isDebuggerEnabled"
        class="ma-2"
        color="primary"
        light
        @click="runDebugger"
      >
        Run debugger
      </v-btn>
      <v-btn
        v-if="!isDebuggerEnabled"
        class="ma-2"
        color="primary"
        light
        type="file"
        @click="selectPnmlFile"
      >
        Import PNML
        <input
          ref="importInput"
          accept=".pnml"
          hidden
          type="file"
          @change="importPnml"
        />
      </v-btn>
      <v-btn
        v-if="!isDebuggerEnabled"
        class="ma-2"
        color="primary"
        light
        type="file"
        v-on:click="() => (isExportPnmlDialogVisible = true)"
      >
        Export PNML
      </v-btn>

      <run-experiment-button
        v-if="isRunExperimentButtonVisible && debug && !isDebuggerEnabled"
        :layouter="layouter"
        :petri-net-manager="petriNetManager"
      />

      <v-btn
        v-if="isDebuggerEnabled"
        class="ma-2"
        color="primary"
        light
        v-on:click="() => (isDebuggerEnabled = false)"
      >
        Stop debugger
      </v-btn>
    </v-container>

    <PetriNetDebugger
      v-if="isDebuggerEnabled"
      :state="this.petriNetManager.state"
      class="fill-height"
    />

    <div v-show="!isDebuggerEnabled" class="fill-height">
      <svg ref="editorSvg" height="100%" width="100%">
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
        v-if="showButtons"
        v-show="!isDebuggerEnabled"
        ref="contextMenu"
        :items="this.contextMenuItems"
        @expand="this.onContextMenuExpand"
      />
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
import RunExperimentButton from "@/components/petri-net-editor/run-experiment-button/RunExperimentButton.vue";
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

@Component({
  name: "petri-net-editor",
  components: {
    RunExperimentButton,
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
  private enableDragging!: boolean;
  @Prop()
  private debug!: boolean;
  @Prop()
  private runLayouterOnStart!: boolean;
  private contextMenuItems: ContextMenuItem[] = [];
  private petriNetManager!: PetriNetSvgManager;
  private layouter!: Layouter;

  // noinspection JSUnusedGlobalSymbols
  mounted() {
    const svgId = `editor-${uuidv4()}`;

    this.$refs.editorSvg.setAttribute("id", svgId);

    this.petriNetManager = new PetriNetSvgManager(
      d3.select(`#${svgId}`),
      this.enableDragging
    );

    this.layouter = new BlockLayouter(this.debug);

    this.isRunExperimentButtonVisible = true;

    this.places.forEach((place) =>
      this.petriNetManager.createPlace({
        x: 0,
        y: 0,
        text: place.text,
        tokenCount: 0,
        id: place.id,
        type: place.type
      })
    );
    this.transitions.forEach((transition) =>
      this.petriNetManager.createTransition({
        x: 0,
        y: 0,
        text: transition.text,
        id: transition.id,
        isSilent: transition.isSilent
      })
    );
    this.arcs.forEach((arc) =>
      this.petriNetManager.connect(arc.outElementId, arc.inElementId)
    );

    if (this.runLayouterOnStart) {
      this.runLayouter();
    }
    this.petriNetManager.updateDimensions();
  }

  scale(): void {
    this.$refs.editorSvg.setAttribute(
      "viewBox",
      `0 0 ${this.petriNetManager.width + 10} ${
        this.petriNetManager.height + 10
      }`
    );

    this.$refs.editorSvg.setAttribute("preserveAspectRatio", "xMidYMid meet");

    this.$refs.editorSvg.style.removeProperty("min-width");

    this.$refs.editorSvg.style.removeProperty("min-height");
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
    const contextMenu = this.$refs.contextMenu.$el as HTMLElement;

    this.petriNetManager.createPlace({
      x: contextMenu.offsetLeft,
      y: contextMenu.offsetTop,
      text: "New P"
    });
  }

  private createTransition(): void {
    const contextMenu = this.$refs.contextMenu.$el as HTMLElement;

    this.petriNetManager.createTransition({
      x: contextMenu.offsetLeft,
      y: contextMenu.offsetTop,
      text: "New T"
    });
  }

  private startConnect(): void {
    this.petriNetManager.startConnect(this.contextMenuTargetId);
  }

  private onContextMenuExpand(target: Element | null): void {
    const isPlaceOrTransition =
      target instanceof SVGCircleElement || target instanceof SVGRectElement;

    this.targetIsPlaceOrTransition = isPlaceOrTransition;
    this.targetIsDeletable =
      isPlaceOrTransition || target instanceof SVGLineElement;
    this.isSetTokenVisible = target instanceof SVGCircleElement;
    this.isSetWidthVisible = target instanceof SVGLineElement;
    this.contextMenuTargetId =
      isPlaceOrTransition || target instanceof SVGLineElement ? target!.id : "";

    this.contextMenuItems = this.createContextMenuItems();
  }

  private showEditDialog() {
    const element = this.petriNetManager.getElement(this.contextMenuTargetId);
    if (element instanceof SvgPlace) {
      this.selectedPlace = this.petriNetManager.getPlace(
        this.contextMenuTargetId
      );
      this.isEditPlaceDialogVisible = true;
    } else if (element instanceof SvgTransition) {
      this.selectedTransition = this.petriNetManager.getTransition(
        this.contextMenuTargetId
      );
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
    this.petriNetManager.updateDimensions();
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
        name: "Connect",
        isVisible: this.targetIsPlaceOrTransition,
        action: () => this.startConnect()
      },
      {
        name: "Create place",
        isVisible: this.contextMenuTargetId === "",
        action: () => this.createPlace()
      },
      {
        name: "Create transition",
        isVisible: this.contextMenuTargetId === "",
        action: () => this.createTransition()
      },
      {
        name: "Edit",
        isVisible: this.targetIsPlaceOrTransition,
        action: () => this.showEditDialog()
      },
      {
        name: "Delete",
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
