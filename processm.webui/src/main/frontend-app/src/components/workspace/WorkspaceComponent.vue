<template>
  <div class="workspace-component">
    <div class="component-name">
      <v-menu offset-y bottom min-width="0">
        <template v-slot:activator="{ on }">
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
            <v-list-item-title>View</v-list-item-title>
          </v-list-item>
          <v-list-item @click="$emit('edit', componentDetails.id)">
            <v-list-item-icon><v-icon>edit</v-icon></v-list-item-icon>
            <v-list-item-title>Edit</v-list-item-title>
          </v-list-item>
          <v-divider></v-divider>
          <v-list-item @click="$emit('remove', componentDetails.id)">
            <v-list-item-icon><v-icon>delete</v-icon></v-list-item-icon>
            <v-list-item-title>Remove</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
    <component
      :is="`${componentDetails.type}Component`"
      :data="componentDetails.data"
      :component-mode="componentMode"
    />
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
</style>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Prop, Inject } from "vue-property-decorator";
import CasualNetComponent from "./casual-net/CasualNetComponent.vue";
import KpiComponent from "./KpiComponent.vue";
import WorkspaceService from "@/services/WorkspaceService";

export enum ComponentMode {
  Static,
  Interactive,
  Edit
}

@Component({
  components: { CasualNetComponent, KpiComponent }
})
export default class WorkspaceComponent extends Vue {
  ComponentMode = ComponentMode;

  @Prop({ default: "" })
  readonly workspaceId!: string;
  @Prop({ default: {} })
  readonly componentDetails!: object;
  @Prop({ default: false })
  readonly interactive!: boolean;
  @Prop({ default: false })
  readonly editable!: boolean;
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;
  @Inject() workspaceService!: WorkspaceService;
}
</script>
