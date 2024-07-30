<template>
  <div class="workspace-component">
    <div v-if="componentDetails.type != null" class="component-name">
      {{ componentDetails.name }}
      <v-menu offset-y bottom min-width="0">
        <template #activator="{ on }">
          <v-btn :ripple="false" class="component-name ignore-drag" depressed small tile v-on="on">
            <v-icon dark>expand_more</v-icon>
          </v-btn>
        </template>

        <v-list dense>
          <v-list-item @click="$emit('view', componentDetails.id)" v-if="canSwitchToView">
            <v-list-item-icon>
              <v-icon>visibility</v-icon>
            </v-list-item-icon>
            <v-list-item-title>{{ $t("common.view") }}</v-list-item-title>
          </v-list-item>
          <v-list-item @click="$emit('edit', componentDetails.id)" v-if="canSwitchToEdit">
            <v-list-item-icon>
              <v-icon>edit</v-icon>
            </v-list-item-icon>
            <v-list-item-title>{{ $t("common.edit") }}</v-list-item-title>
          </v-list-item>
          <v-divider v-if="canSwitchToEdit || canSwitchToView"></v-divider>
          <v-list-item @click="$emit('remove', componentDetails.id)">
            <v-list-item-icon>
              <v-icon>delete</v-icon>
            </v-list-item-icon>
            <v-list-item-title>{{ $t("common.remove") }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
    <div v-else>
      <v-btn class="remove-button" icon small @click="$emit('remove', componentDetails.id)">
        <v-icon>close</v-icon>
      </v-btn>
    </div>
    <div class="workspace-component-content-parent ignore-drag">
      <v-progress-linear :active="loading" :indeterminate="true" absolute class="progressbar" color="secondary accent-4" top></v-progress-linear>
      <component
        :is="componentType"
        v-if="isDisplayable"
        :workspace-id="workspaceId"
        :component-mode="componentMode"
        :data="componentDetails"
        :update-data="updateData"
        class="workspace-component-content"
      />
      <p v-else class="no-data">{{ $t("workspace.component.no-data") }}</p>
      <div class="last-updated">
        {{ $t("common.last-updated") }}:
        {{ new Date(lastModified + "Z").toLocaleString() }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.workspace-component {
  display: flex;
  flex-flow: column;
  height: 100%;
}

.workspace-component-content-parent {
  position: relative;
  flex: 1 1 100%;
  overflow: auto;
}

button.v-btn.v-btn.component-name[type="button"] {
  overflow: hidden;
  background-color: inherit;
  align-self: center;
  min-width: 0;
}

.component-name {
  overflow: hidden;
  display: flex;
  justify-content: center;
  background-color: var(--v-primary-base);
  flex: 0 0 1.5em;
  text-transform: uppercase;
  font-weight: 500;
  font-size: small;
}

.component-name:hover {
  background-color: var(--v-primary-lighten1);
}

.remove-button {
  position: absolute;
  top: 2px;
  right: 2px;
}

.no-data {
  text-align: center;
}

.last-updated {
  font-size: 0.8em;
  color: var(--v-secondary-base);
  position: absolute;
  left: 0;
  bottom: 0;
  overflow: hidden;
}

.progressbar {
  z-index: 2;
}
</style>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Prop, Watch } from "vue-property-decorator";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import { ComponentType } from "@/openapi/api";

const PetriNetComponent = () => import("./petri-net/PetriNetComponent.vue");
const KpiComponent = () => import("./KpiComponent.vue");
const BPMNComponent = () => import("./bpmn/BPMNComponent.vue");
const TreeLogViewComponent = () => import("@/components/workspace/TreeLogViewComponent.vue");
const FlatLogViewComponent = () => import("@/components/workspace/FlatLogViewComponent.vue");
const DirectlyFollowsGraphComponent = () => import("@/components/workspace/DirectlyFollowsGraphComponent.vue");
const CNetComponent = () => import("@/components/workspace/CNetComponent.vue");

export enum ComponentMode {
  Static,
  Interactive,
  Edit
}

@Component({
  components: {
    causalNetComponent: CNetComponent,
    kpiComponent: KpiComponent,
    petriNetComponent: PetriNetComponent,
    bpmnComponent: BPMNComponent,
    treeLogViewComponent: TreeLogViewComponent,
    flatLogViewComponent: FlatLogViewComponent,
    directlyFollowsGraphComponent: DirectlyFollowsGraphComponent // https://stackoverflow.com/a/58875919
  }
})
export default class WorkspaceComponent extends Vue {
  @Prop()
  readonly workspaceId!: string;
  @Prop({ default: null })
  readonly componentDetails!: WorkspaceComponentModel | null;
  @Prop({ default: false })
  readonly interactive!: boolean;
  @Prop({ default: false })
  readonly editable!: boolean;
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;
  @Prop({ default: false })
  readonly isTransient!: boolean;
  /**
   * Set to true to let children update the data model (usually before sending it to the server).
   */
  @Prop({ default: false })
  readonly updateData = false;

  lastModified: string | null = null;

  private refreshLastModified() {
    this.lastModified = this.componentDetails?.dataLastModified ?? this.componentDetails?.userLastModified ?? null;
  }

  mounted() {
    this.refreshLastModified();
  }

  private get loading(): boolean {
    switch (this.componentDetails?.type) {
      case ComponentType.FlatLogView:
      case ComponentType.TreeLogView:
      case null:
        return false;
      default:
        return Date.parse(this.componentDetails?.userLastModified ?? "1970-01-01") > Date.parse(this.componentDetails?.dataLastModified ?? "1970-01-01");
    }
  }

  get isDisplayable(): boolean {
    return this.componentDetails?.data?.isDisplayable ?? false;
  }

  get componentType() {
    return `${this.componentDetails?.type}Component`;
  }

  get canSwitchToView() {
    return this.componentMode != ComponentMode.Interactive && !this.isTransient;
  }

  get canSwitchToEdit() {
    return this.componentMode != ComponentMode.Edit;
  }

  @Watch("componentDetails")
  componentDetailsChanged() {
    this.refreshLastModified();
  }
}
</script>
