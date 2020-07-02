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
              :component-mode="ComponentMode.Static"
              @view="viewComponent"
              @edit="editComponent"
              @remove="removeComponent"
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
      @remove="removeComponent"
    ></single-component-view>
    <edit-component-view
      v-if="displayEditModal"
      v-model="displayEditModal"
      :component-details="displayedComponentDetails"
      @close="closeModals"
      @view="viewComponent"
      @edit="editComponent"
      @remove="removeComponent"
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
import WorkspaceComponent, { ComponentMode } from "./WorkspaceComponent.vue";
import WorkspaceService from "@/services/WorkspaceService";
import WorkspaceComponentModel from "@/models/WorkspaceComponent";
import WorkspaceLayoutItem from "@/models/WorkspaceLayoutItem";

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
  ComponentMode = ComponentMode;

  @Prop({ default: "" })
  readonly workspaceId!: string;
  @Inject() workspaceService!: WorkspaceService;

  locked = false;
  displayViewModal = false;
  displayEditModal = false;
  displayedComponentDetails?: WorkspaceComponentModel;
  componentsDetails: Record<string, WorkspaceComponentModel> = {};
  layout: Array<{ i: string; x: number; y: number; w: number; h: number }> = [];

  async created() {
    const layout: WorkspaceLayoutItem[] = [
      {
        componentId: "93bcc033-bbea-490d-93b9-3c8580db5354",
        x: 0,
        y: 0,
        width: 3,
        height: 10
      },
      {
        componentId: "c629ba68-aae2-4490-85cd-3875939fc69e",
        x: 3,
        y: 0,
        width: 5,
        height: 8
      },
      {
        componentId: "06a29e69-a58e-498c-ad66-6435816d9c7b",
        x: 0,
        y: 0,
        width: 3,
        height: 4
      },
      {
        componentId: "f7bad885-6add-4516-b536-d6f88c154b6e",
        x: 3,
        y: 0,
        width: 5,
        height: 10
      },
      {
        componentId: "cf607cb0-0b88-4ccd-9795-5cd0201b3c39",
        x: 8,
        y: 0,
        width: 4,
        height: 7
      }
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
      const componentId = layoutItem.componentId;
      this.componentsDetails[
        componentId
      ] = await this.workspaceService.getComponent(
        this.workspaceId,
        componentId
      );
      this.layout.push({
        i: layoutItem.componentId,
        x: layoutItem.x,
        y: layoutItem.y,
        w: layoutItem.width,
        h: layoutItem.height
      });
    }
  }

  toggleLocked() {
    this.locked = !this.locked;
  }

  viewComponent(id: string) {
    this.closeModals();
    this.displayedComponentDetails = this.componentsDetails[id];
    this.displayViewModal = true;
  }

  editComponent(id: string) {
    this.closeModals();
    this.displayedComponentDetails = this.componentsDetails[id];
    this.displayEditModal = true;
  }

  removeComponent(id: string) {
    const componentIndex = this.layout.findIndex(
      component => component.i == id
    );

    if (componentIndex >= 0) {
      this.layout.splice(componentIndex, 1);
      this.closeModals();
    }
  }

  closeModals() {
    this.displayViewModal = false;
    this.displayEditModal = false;
  }
}
</script>
