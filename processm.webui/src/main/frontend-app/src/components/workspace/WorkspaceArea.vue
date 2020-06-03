<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <grid-layout
          :layout.sync="layout"
          :row-height="30"
          :is-draggable="!locked"
          :is-resizable="!locked"
          :is-mirrored="false"
          :vertical-compact="true"
          :margin="[10, 10]"
          :use-css-transforms="true"
        >
          <grid-item
            v-for="item in layout"
            :x="item.x"
            :y="item.y"
            :w="item.w"
            :h="item.h"
            :i="item.i"
            :key="item.i"
            drag-ignore-from="svg"
            class="elevation-1"
          >
            <workspace-component
              :component-details="componentsDetails[item.i]"
              @view="viewComponent"
              @edit="editComponent"
            />
          </grid-item>
        </grid-layout>
      </v-col>
    </v-row>
    <v-switch v-model="locked" :label="$t('workspace.locked')" />
    <single-component-view
      v-if="displayViewModal"
      v-model="displayViewModal"
      :component-details="displayedComponentDetails"
      @close="closeModals"
      @view="viewComponent"
      @edit="editComponent"
    ></single-component-view>
    <edit-component-view
      v-if="displayEditModal"
      v-model="displayEditModal"
      :component-details="displayedComponentDetails"
      @close="closeModals"
      @view="viewComponent"
      @edit="editComponent"
    ></edit-component-view>
  </v-container>
</template>

<style scoped>
.vue-grid-item:hover {
  outline-color: var(--v-primary-lighten1);
  outline-style: solid;
  outline-width: thin;
}
</style>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Prop, Inject } from "vue-property-decorator";
import { GridLayout, GridItem } from "vue-grid-layout";
import SingleComponentView from "./SingleComponentView.vue";
import EditComponentView from "./EditComponentView.vue";
import WorkspaceComponent from "./WorkspaceComponent.vue";
import WorkspaceService from "@/services/WorkspaceService";

@Component({
  components: {
    GridLayout,
    GridItem,
    WorkspaceComponent,
    SingleComponentView,
    EditComponentView
  }
})
export default class WorkspaceArea extends Vue {
  @Prop({ default: "" })
  readonly workspaceId!: string;
  @Inject() workspaceService!: WorkspaceService;

  locked = false;
  displayViewModal = false;
  displayEditModal = false;
  displayedComponentDetails = null;
  componentsDetails = {};
  layout = [];

  async created() {
    const layout = [
      {
        x: 0,
        y: 0,
        width: 6,
        height: 7,
        id: "93bcc033-bbea-490d-93b9-3c8580db5354"
      },
      {
        x: 6,
        y: 0,
        width: 5,
        height: 5,
        id: "c629ba68-aae2-4490-85cd-3875939fc69e"
      },
      {
        x: 6,
        y: 0,
        width: 6,
        height: 5,
        id: "06a29e69-a58e-498c-ad66-6435816d9c7b"
      }
      // { x: 6, y: 0, w: 2, h: 3, i: "3" },
      // { x: 8, y: 0, w: 2, h: 3, i: "4" },
      // { x: 10, y: 0, w: 2, h: 3, i: "5" },
      // { x: 0, y: 5, w: 2, h: 5, i: "6" },
      // { x: 2, y: 5, w: 2, h: 5, i: "7" },
      // { x: 4, y: 5, w: 2, h: 5, i: "8" },
      // { x: 6, y: 3, w: 2, h: 4, i: "9" },
      // { x: 8, y: 4, w: 2, h: 4, i: "10" },
      // { x: 10, y: 4, w: 2, h: 4, i: "11" },
      // { x: 0, y: 10, w: 2, h: 5, i: "12" },
      // { x: 2, y: 10, w: 2, h: 5, i: "13" }
    ];

    for (const layoutItem of layout) {
      const componentId = layoutItem.id;
      this.componentsDetails[
        componentId
      ] = await this.workspaceService.getComponent(
        this.workspaceId,
        componentId
      );
      this.layout.push({
        x: layoutItem.x,
        y: layoutItem.y,
        w: layoutItem.width,
        h: layoutItem.height,
        i: layoutItem.id
      });
    }
  }

  toggleLocked() {
    this.locked = !this.locked;
  }

  viewComponent(id: string) {
    console.log(id);
    this.closeModals();
    this.displayedComponentDetails = this.componentsDetails[id];
    this.displayViewModal = true;
  }

  editComponent(id: string) {
    console.log(id);
    this.closeModals();
    this.displayedComponentDetails = this.componentsDetails[id];
    this.displayEditModal = true;
  }

  removeComponent(id: string) {
    console.log(id);
  }

  closeModals() {
    this.displayViewModal = false;
    this.displayEditModal = false;
  }
}
</script>
