<template>
  <div
      v-resize:debounce.10="onResize"
      class="svg-container"
      v-if="data != null"
  >
    <svg
        :viewBox="`0 0 ${contentWidth} ${contentHeight}`"
        x="0"
        y="0"
        width="100%"
        height="100%"
        ref="svg"
        class="svg-content"
        :style="{ cursor: editMode == null ? 'auto' : 'crosshair' }"
        preserveAspectRatio="xMidYMid meet"
    >

    </svg>
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
          :outlined="editMode != EditMode.Addition"
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
          :outlined="editMode != EditMode.Deletion"
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

@Component
export default class PetriNetComponent extends Vue {
  @Prop({default: {}})
  readonly data!: { data: PetriNetComponentData };

  mounted() {
    console.log(this.data);
  }
}
</script>

<style scoped>

</style>