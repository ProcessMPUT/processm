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
          <v-btn fab small depressed :disabled="!dirtyLayout" color="primary" class="ma-1" @click="saveLayout" v-bind="attrs" v-on="on">
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
              :workspace-id="workspaceId"
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
    <edit-component-dialog
      v-if="displayEditModal"
      v-model="displayEditModal"
      :component-details="displayedComponentDetails"
      :workspace-id="workspaceId"
      @close="closeModals"
      @view="viewComponent"
      @edit="editComponent"
      @remove="removeComponent"
      @component-updated="updateComponent"
    ></edit-component-dialog>
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
import Vue, { watch } from "vue";
import Component from "vue-class-component";
import { Inject, Prop } from "vue-property-decorator";
import { GridItem, GridLayout } from "vue-grid-layout";
import { v4 as uuidv4 } from "uuid";
import SingleComponentView from "./SingleComponentView.vue";
import EditComponentDialog from "./EditComponentDialog.vue";
import EmptyComponent from "./EmptyComponent.vue";
import WorkspaceComponent, { ComponentMode } from "./WorkspaceComponent.vue";
import WorkspaceService from "@/services/WorkspaceService";
import { LayoutElement, WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import { ComponentType } from "@/openapi";
import App from "@/App.vue";

@Component({
  components: {
    GridLayout,
    GridItem,
    WorkspaceComponent,
    SingleComponentView,
    EditComponentDialog,
    EmptyComponent
  }
})
export default class WorkspaceArea extends Vue {
  ComponentMode = ComponentMode;

  @Prop({ default: "" })
  readonly workspaceId!: string;
  @Inject() workspaceService!: WorkspaceService;
  @Inject() app!: App;

  readonly defaultComponentWidth = 4;
  readonly defaultComponentHeight = 4;
  readonly tooltipOpenDelay = 200;
  dirtyLayout = false;
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
    watch(this.app.lastEvent, (lastEvent) => {
      const componentId = lastEvent.lastEvent?.componentId;
      if (componentId == null) return;
      if (!this.componentsDetails.has(componentId)) return;
      this.refreshComponent(componentId);
    });
  }

  // noinspection JSUnusedGlobalSymbols

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
    // saveLayout in cast that was a new component and thus it modified the layout
    this.saveLayout();
  }

  async saveLayout() {
    const updatedLayoutElements = {} as Record<string, LayoutElement>;
    this.layout.forEach((layoutElement) => {
      const component = this.componentsDetails.get(layoutElement.i);
      if (component === undefined) return;
      // skip components whose layout did not change
      if (
        component.layout?.x === layoutElement.x &&
        component.layout?.y === layoutElement.y &&
        component.layout?.width === layoutElement.w &&
        component.layout?.height === layoutElement.h
      )
        return;
      const layout = {
        x: layoutElement.x,
        y: layoutElement.y,
        width: layoutElement.w,
        height: layoutElement.h
      };
      updatedLayoutElements[layoutElement.i] = layout;
      component.layout = layout;
    });
    if (Object.keys(updatedLayoutElements).length > 0) {
      await this.workspaceService.updateLayout(this.workspaceId, updatedLayoutElements);
    }
    this.dirtyLayout = false;
  }

  closeModals() {
    this.displayViewModal = false;
    this.displayEditModal = false;
  }

  updateComponentPosition(id: string, x: number, y: number) {
    // the update itself is already reflected in this.layout
    // this function is only called for the components moved by the user
    // and not for the components displaced by the movement of other components
    this.dirtyLayout = true;
  }

  updateComponentSize(id: string, height: number, width: number) {
    // comments in updateComponentPosition apply
    this.dirtyLayout = true;
  }

  async initializeEmptyComponent(componentId: string, componentType: ComponentType) {
    const emptyComponent = await this.workspaceService.getEmptyComponent(componentType);
    emptyComponent.id = componentId;
    emptyComponent.dataStore = "";
    this.componentsDetails.set(componentId, new WorkspaceComponentModel(emptyComponent));
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
}
</script>