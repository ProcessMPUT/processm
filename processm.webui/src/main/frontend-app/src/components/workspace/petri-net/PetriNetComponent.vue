<template>
  <div
      v-resize:debounce.10="onResize"
      class="svg-container"
      v-if="data != null"
  >
    <petri-net-editor
        :debug="false"
        :run-layouter-on-start="true"
    />
    <div class="node-details"/>
    <v-speed-dial
        v-if="componentMode == ComponentMode.Edit"
        top
        right
        absolute
        direction="left"
    >
      <template #activator>
        <v-btn
            color="blue darken-2"
            dark
            fab
            elevation="0"
            @click="setEditMode(null)"
        >
          <v-icon v-if="editMode == null">edit</v-icon>
          <v-icon v-else>close</v-icon>
        </v-btn>
      </template>
      <v-btn
          fab
          dark
          small
          color="green"
          elevation="0"
          :outlined="editMode !== EditMode.Addition"
          @click.stop="setEditMode(EditMode.Addition)"
      >
        <v-icon>add</v-icon>
      </v-btn>
      <v-btn
          fab
          dark
          small
          color="red"
          elevation="0"
          :outlined="editMode !== EditMode.Deletion"
          @click.stop="setEditMode(EditMode.Deletion)"
      >
        <v-icon>delete</v-icon>
      </v-btn>
      <v-btn
          fab
          dark
          small
          color="purple"
          elevation="0"
          @click.stop="rearrangeNodes"
      >
        <v-icon>grain</v-icon>
      </v-btn>
    </v-speed-dial>
  </div>
  <p v-else>{{ $t("workspace.component.no-data") }}</p>
</template>

<script lang="ts">
import Vue from "vue";
import {Component, Prop} from "vue-property-decorator";
import {PetriNetComponentData} from "@/models/WorkspaceComponent";
import {PetriNetEditor} from "petri-net-editor"
import resize from "vue-resize-directive";
import {ComponentMode} from "@/components/workspace/WorkspaceComponent.vue";
import {
  AdditionModeInputHandler,
  DeletionModeInputHandler,
  InteractiveModeInputHandler
} from "@/components/workspace/causal-net/UserInputHandlers";

enum EditMode {
  Addition,
  Deletion
}

@Component({
  components: {PetriNetEditor},
  directives: {
    resize
  }
})
export default class PetriNetComponent extends Vue {
  ComponentMode = ComponentMode;
  EditMode = EditMode;

  @Prop({default: {}})
  readonly data!: { data: PetriNetComponentData };

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  mounted() {
    console.log(this.data);
  }

  private setEditMode(newMode: EditMode | null) {
    // if (this.componentMode == ComponentMode.Edit) {
    //   this.editMode = this.editMode != newMode ? newMode : null;
    //
    //   if (this.editMode == EditMode.Addition) {
    //     this.userInputHandler = new AdditionModeInputHandler(this);
    //   } else if (this.editMode == EditMode.Deletion) {
    //     this.userInputHandler = new DeletionModeInputHandler(this);
    //   } else {
    //     this.userInputHandler = new InteractiveModeInputHandler(this);
    //   }
    // } else {
    //   this.editMode = null;
    //   this.userInputHandler =
    //       this.componentMode == ComponentMode.Interactive
    //           ? new InteractiveModeInputHandler(this)
    //           : null;
    // }
  }

  private onResize(element: Element) {
    // const scalingFactor = this.calculateScalingFactor(
    //     element.clientWidth,
    //     element.clientHeight
    // );
    //
    // this.scaleElements(scalingFactor);
  }

  private rearrangeNodes() {
    // this.causalNet.recalculateLayout();
    // this.simulation?.alphaTarget(0.3).restart();
  }
}
</script>

<style scoped>

</style>