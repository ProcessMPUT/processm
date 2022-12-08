<template>
  <div class="workspace-component">
    <div v-if="componentDetails.type != null" class="component-name">
      <v-menu offset-y bottom min-width="0">
        <template #activator="{ on }">
          <v-btn
            class="component-name"
            small
            tile
            depressed
            :ripple="false"
            v-on="on"
            >{{ componentDetails.name }}<v-icon dark>expand_more</v-icon></v-btn
          >
        </template>

        <v-list dense>
          <v-list-item @click="$emit('view', componentDetails.id)">
            <v-list-item-icon><v-icon>visibility</v-icon></v-list-item-icon>
            <v-list-item-title>{{ $t("common.view") }}</v-list-item-title>
          </v-list-item>
          <v-list-item @click="$emit('edit', componentDetails.id)">
            <v-list-item-icon><v-icon>edit</v-icon></v-list-item-icon>
            <v-list-item-title>{{ $t("common.edit") }}</v-list-item-title>
          </v-list-item>
          <v-divider></v-divider>
          <v-list-item @click="$emit('remove', componentDetails.id)">
            <v-list-item-icon><v-icon>delete</v-icon></v-list-item-icon>
            <v-list-item-title>{{ $t("common.remove") }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
    <div v-else>
      <v-btn
        icon
        small
        class="remove-button"
        @click="$emit('remove', componentDetails.id)"
      >
        <v-icon>close</v-icon>
      </v-btn>
    </div>
    <div v-if="isDisplayable" class="workspace-component-content-parent">
      <component
        :is="componentType"
        :data="componentDetails"
        :component-mode="componentMode"
        :update-data="updateData"
        class="workspace-component-content"
      />
      <div class="last-updated">
        {{ $t("common.last-updated") }}:
        {{ lastModified }}
      </div>
    </div>
    <p class="no-data" v-else>{{ $t("workspace.component.no-data") }}</p>
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
  max-width: 100%;
  flex: auto;
}

.component-name {
  overflow: hidden;
  display: flex;
  justify-content: center;
  background-color: var(--v-primary-base);
  flex: 0 0 1.5em;
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
</style>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Prop } from "vue-property-decorator";
import CausalNetComponent from "./causal-net/CausalNetComponent.vue";
import PetriNetComponent from "./petri-net/PetriNetComponent.vue";
import KpiComponent from "./KpiComponent.vue";
import BPMNComponent from "./bpmn/BPMNComponent.vue";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";

export enum ComponentMode {
  Static,
  Interactive,
  Edit
}

@Component({
  components: {
    causalNetComponent: CausalNetComponent,
    kpiComponent: KpiComponent,
    petriNetComponent: PetriNetComponent,
    bpmnComponent: BPMNComponent // https://stackoverflow.com/a/58875919
  }
})
export default class WorkspaceComponent extends Vue {
  @Prop({ default: null })
  readonly componentDetails?: WorkspaceComponentModel;
  @Prop({ default: false })
  readonly interactive!: boolean;
  @Prop({ default: false })
  readonly editable!: boolean;
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;
  /**
   * Set to true to let children update the data model (usually before sending it to the server).
   */
  @Prop({ default: false })
  readonly updateData = false;

  private readonly lastModified =
    this.componentDetails?.dataLastModified ??
    this.componentDetails?.userLastModified;

  get isDisplayable(): boolean {
    return this.componentDetails?.data?.isDisplayable ?? false;
  }

  get componentType() {
    return `${this.componentDetails?.type}Component`;
  }
}
</script>
