<template>
  <v-container>
    <v-row justify="end" class="pa-1">
      <v-tooltip bottom :open-delay="tooltipOpenDelay">
        <template v-slot:activator="{ on, attrs }">
          <v-btn
            fab
            small
            depressed
            class="ma-1"
            color="primary"
            @click="createComponent"
            v-bind="attrs"
            v-on="on"
          >
            <v-icon>add_chart</v-icon>
          </v-btn>
        </template>
        <span>{{ $t("workspace.tooltip.add") }}</span>
      </v-tooltip>
      <v-tooltip bottom :open-delay="tooltipOpenDelay">
        <template v-slot:activator="{ on, attrs }">
          <v-btn
            fab
            small
            depressed
            :outlined="unlocked"
            color="primary"
            class="ma-1"
            @click="toggleLocked"
            v-bind="attrs"
            v-on="on"
          >
            <v-icon v-if="unlocked">lock</v-icon>
            <v-icon v-else>lock_open</v-icon>
          </v-btn>
        </template>
        <span v-if="unlocked">{{ $t("workspace.tooltip.lock") }}</span>
        <span v-else>{{ $t("workspace.tooltip.unlock") }}</span>
      </v-tooltip>
      <v-tooltip bottom :open-delay="tooltipOpenDelay">
        <template v-slot:activator="{ on, attrs }">
          <v-btn
            fab
            small
            depressed
            :disabled="componentsWithLayoutsToBeUpdated.size == 0"
            color="primary"
            class="ma-1"
            @click="saveLayout"
            v-bind="attrs"
            v-on="on"
          >
            <v-icon>save</v-icon>
          </v-btn>
        </template>
        <span>{{ $t("workspace.tooltip.save") }}</span>
      </v-tooltip>
    </v-row>

    <v-row>
      <v-col cols="12">
        <grid-layout
          :layout.sync="layout"
          :row-height="30"
          :is-draggable="unlocked"
          :is-resizable="unlocked"
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
            drag-ignore-from="div.workspace-component-content"
            class="elevation-1"
            @moved="updateComponentPosition"
            @resized="updateComponentSize"
          >
            <workspace-component
              v-if="componentsDetails.has(item.i)"
              :component-details="componentsDetails.get(item.i)"
              :component-mode="ComponentMode.Static"
              @view="viewComponent"
              @edit="editComponent"
              @remove="removeComponent"
            />
            <empty-component
              v-else
              @type-selected="initializeEmptyComponent(item.i, $event)"
            />
          </grid-item>
        </grid-layout>
      </v-col>
    </v-row>
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
      :workspace-id="workspaceId"
      @close="closeModals"
      @view="viewComponent"
      @edit="editComponent"
      @remove="removeComponent"
      @component-updated="updateComponent"
    ></edit-component-view>
  </v-container>
</template>

<style scoped>
.vue-grid-item:hover {
  outline-color: var(--v-primary-lighten1);
  outline-style: solid;
  outline-width: thin;
}

.vue-grid-layout {
  max-height: 0px;
}

.container >>> .vue-grid-item.vue-grid-placeholder {
  background: var(--v-primary-lighten1);
}
</style>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Inject, Prop } from "vue-property-decorator";
import { GridItem, GridLayout } from "vue-grid-layout";
import { v4 as uuidv4 } from "uuid";
import SingleComponentView from "./SingleComponentView.vue";
import EditComponentView from "./EditComponentView.vue";
import EmptyComponent from "./EmptyComponent.vue";
import WorkspaceComponent, { ComponentMode } from "./WorkspaceComponent.vue";
import WorkspaceService from "@/services/WorkspaceService";
import {
  ComponentType,
  LayoutElement,
  WorkspaceComponent as WorkspaceComponentModel
} from "@/models/WorkspaceComponent";

@Component({
  components: {
    GridLayout,
    GridItem,
    WorkspaceComponent,
    SingleComponentView,
    EditComponentView,
    EmptyComponent
  }
})
export default class WorkspaceArea extends Vue {
  ComponentMode = ComponentMode;

  @Prop({ default: "" })
  readonly workspaceId!: string;
  @Inject() workspaceService!: WorkspaceService;

  readonly defaultComponentWidth = 4;
  readonly defaultComponentHeight = 4;
  readonly tooltipOpenDelay = 200;
  readonly componentsWithLayoutsToBeUpdated = new Set<string>();
  unlocked = false;
  displayViewModal = false;
  displayEditModal = false;
  displayedComponentDetails?: WorkspaceComponentModel;
  componentsDetails: Map<string, WorkspaceComponentModel> = new Map();
  layout: Array<{
    i: string;
    x: number;
    y: number;
    w: number;
    h: number;
  }> = [];

  async created() {
    const components = await this.workspaceService.getWorkspaceComponents(
      this.workspaceId
    );
    for (const component of components) {
      this.componentsDetails.set(component.id, component);
      this.layout.push({
        i: component.id,
        x: component.layout?.x ?? 0,
        y: component.layout?.y ?? 0,
        w: component.layout?.width ?? this.defaultComponentWidth,
        h: component.layout?.height ?? this.defaultComponentHeight
      });
    }
  }

  toggleLocked() {
    this.unlocked = !this.unlocked;
  }

  createComponent() {
    this.layout.unshift({
      i: uuidv4(),
      x: 0,
      y: 0,
      w: this.defaultComponentWidth,
      h: this.defaultComponentHeight
    });
  }

  viewComponent(id: string) {
    this.closeModals();
    this.displayedComponentDetails = this.componentsDetails.get(id);
    this.displayViewModal = true;
  }

  editComponent(id: string) {
    this.closeModals();
    this.displayedComponentDetails = this.componentsDetails.get(id);
    this.displayEditModal = true;
  }

  async removeComponent(componentId: string) {
    const componentIndex = this.layout.findIndex(
      (component) => component.i == componentId
    );

    if (componentIndex >= 0) {
      await this.workspaceService.removeComponent(
        this.workspaceId,
        componentId
      );
      this.layout.splice(componentIndex, 1);
      this.closeModals();
    }
  }

  updateComponent(componentData: WorkspaceComponentModel) {
    this.componentsDetails.set(componentData.id, componentData);
    this.closeModals();
    this.$children
      .find((v, _) => v.$data?.component?.id == componentData.id)
      ?.$forceUpdate();
  }

  closeModals() {
    this.displayViewModal = false;
    this.displayEditModal = false;
  }

  updateComponentPosition(id: string, x: number, y: number) {
    this.updateComponentLayout(id, { x, y });
  }

  updateComponentSize(id: string, height: number, width: number) {
    this.updateComponentLayout(id, { height, width });
  }

  initializeEmptyComponent(componentId: string, componentType: ComponentType) {
    this.componentsDetails.set(
      componentId,
      new WorkspaceComponentModel({
        id: componentId,
        query: "",
        dataStore: "",
        type: componentType
      })
    );
    this.editComponent(componentId);
  }

  async saveLayout() {
    const updatedLayoutElements = {} as Record<string, LayoutElement>;
    this.componentsWithLayoutsToBeUpdated.forEach((componentId) => {
      const component = this.componentsDetails.get(componentId);

      if (component?.layout !== undefined)
        updatedLayoutElements[componentId] = component.layout;
    });

    if (
      await this.workspaceService.updateLayout(
        this.workspaceId,
        updatedLayoutElements
      )
    )
      this.componentsWithLayoutsToBeUpdated.clear();
  }

  private updateComponentLayout(id: string, layout: Partial<LayoutElement>) {
    const component = this.componentsDetails.get(id);

    if (component === undefined) return;

    const componentLayout = component.layout ?? new LayoutElement({});

    if (layout.x != null) componentLayout.x = layout.x;
    if (layout.y != null) componentLayout.y = layout.y;
    if (layout.width != null) componentLayout.width = layout.width;
    if (layout.height != null) componentLayout.height = layout.height;

    component.layout = componentLayout;
    this.componentsWithLayoutsToBeUpdated.add(id);
  }
}
</script>
