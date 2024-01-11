<template>
  <v-container>
    <v-row justify="end" class="pa-1">
      <v-tooltip bottom :open-delay="tooltipOpenDelay">
        <template v-slot:activator="{ on, attrs }">
          <v-btn class="ma-1" color="primary" depressed fab small v-bind="attrs" @click="createComponent" v-on="on">
            <v-icon>add_chart</v-icon>
          </v-btn>
        </template>
        <span>{{ $t("workspace.tooltip.add") }}</span>
      </v-tooltip>
      <v-tooltip bottom :open-delay="tooltipOpenDelay">
        <template v-slot:activator="{ on, attrs }">
          <v-btn :outlined="unlocked" class="ma-1" color="primary" depressed fab small v-bind="attrs" @click="toggleLocked" v-on="on">
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
            <empty-component v-else @type-selected="initializeEmptyComponent(item.i, $event)" />
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
import { LayoutElement, WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import { ComponentType, CustomProperty } from "@/openapi";
import { WorkspaceObserver } from "@/utils/WorkspaceObserver";

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
  notifications: WorkspaceObserver | undefined = undefined;

  async fullRefresh() {
    const components = await this.workspaceService.getWorkspaceComponents(this.workspaceId);
    this.componentsDetails.clear();
    this.layout.length = 0;
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

  async created() {
    await this.fullRefresh();
    if (this.notifications === undefined) this.notifications = this.workspaceService.observeWorkspace(this.workspaceId, this.refreshComponent);
    this.notifications.start();
  }

  // noinspection JSUnusedGlobalSymbols
  beforeDestroy() {
    this.notifications?.close();
  }

  async removeComponent(componentId: string) {
    const componentIndex = this.layout.findIndex((component) => component.i == componentId);

    if (componentIndex >= 0) {
      await this.workspaceService.removeComponent(this.workspaceId, componentId);
      this.layout.splice(componentIndex, 1);
      this.closeModals();
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

  updateComponent(componentData: WorkspaceComponentModel) {
    this.componentsDetails.set(componentData.id, componentData);
    this.closeModals();
    this.$children.find((v, _) => v.$data?.component?.id == componentData.id)?.$forceUpdate();
  }

  async saveLayout() {
    const updatedLayoutElements = {} as Record<string, LayoutElement>;
    this.componentsWithLayoutsToBeUpdated.forEach((componentId) => {
      const component = this.componentsDetails.get(componentId);

      if (component?.layout !== undefined) updatedLayoutElements[componentId] = component.layout;
    });

    if (await this.workspaceService.updateLayout(this.workspaceId, updatedLayoutElements)) this.componentsWithLayoutsToBeUpdated.clear();
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
    let customProperties: CustomProperty[];
    switch (componentType) {
      case ComponentType.CausalNet:
        // FIXME: preconfigured WorkspaceComponentModel should be downloaded from server
        // This code repeats processm.services.api.AbstractComponentHelpersKt.getCustomProperties()
        customProperties = [
          {
            id: 0,
            name: "algorithm",
            type: "enum",
            enum: [
              {
                id: "urn:processm:miners/OnlineHeuristicMiner",
                name: "Online Heuristic Miner"
              },
              {
                id: "urn:processm:miners/OnlineInductiveMiner",
                name: "Online Inductive "
              }
            ],
            value: "urn:processm:miners/OnlineHeuristicMiner"
          }
        ];
        break;
      default:
        customProperties = [];
        break;
    }
    this.componentsDetails.set(
      componentId,
      new WorkspaceComponentModel({
        id: componentId,
        query: "",
        dataStore: "",
        type: componentType,
        customProperties: customProperties
      })
    );
    this.editComponent(componentId);
  }

  private async refreshComponent(componentId: string) {
    const component = await this.workspaceService.getComponent(this.workspaceId, componentId);
    if (this.componentsDetails.has(component.id)) {
      this.componentsDetails.set(component.id, component);
      const idx = this.layout.findIndex((value) => value.i == component.id);
      console.assert(idx >= 0);
      this.layout.splice(idx, 1);
      this.layout.push({
        i: component.id,
        x: component.layout?.x ?? 0,
        y: component.layout?.y ?? 0,
        w: component.layout?.width ?? this.defaultComponentWidth,
        h: component.layout?.height ?? this.defaultComponentHeight
      });
    } else {
      await this.fullRefresh();
    }
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
