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
    <component
      :is="componentType"
      :data="componentDetails"
      :component-mode="componentMode"
      v-if="isDisplayable"
      class="workspace-component-content"
    />
    <p class="no-data" v-else>{{ $t("workspace.component.no-data") }}</p>
  </div>
</template>

<style scoped>
.workspace-component {
  display: flex;
  flex-flow: column;
  height: 100%;
}

button.v-btn.v-btn.component-name[type="button"] {
  overflow: hidden;
  background-color: inherit;
  max-width: 100%;
  justify-content: left;
}

.component-name {
  overflow: hidden;
  display: flex;
  justify-content: center;
  background-color: var(--v-primary-base);
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
</style>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Prop } from "vue-property-decorator";
import CausalNetComponent from "./causal-net/CausalNetComponent.vue";
import KpiComponent from "./KpiComponent.vue";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";

export enum ComponentMode {
  Static,
  Interactive,
  Edit
}

@Component({
  components: { CausalNetComponent, KpiComponent }
})
export default class WorkspaceComponent extends Vue {
  ComponentMode = ComponentMode;

  @Prop({ default: null })
  readonly componentDetails?: WorkspaceComponentModel;
  @Prop({ default: false })
  readonly interactive!: boolean;
  @Prop({ default: false })
  readonly editable!: boolean;
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  get isDisplayable(): boolean {
    return this.componentDetails?.data?.isDisplayable ?? false;
  }

  get componentType() {
    return `${this.componentDetails?.type}Component`;
  }
}
</script>
