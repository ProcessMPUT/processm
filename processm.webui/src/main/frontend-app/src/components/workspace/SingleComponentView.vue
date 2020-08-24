<template>
  <v-dialog fullscreen persistent v-model="value">
    <v-card flat tile>
      <v-card-actions>
        <v-btn color="primary darken-1" icon @click.stop="$emit('close')">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer></v-spacer>
      </v-card-actions>
      <v-card-text>
        <workspace-component
          v-if="isMounted"
          :component-details="componentDetails"
          :component-mode="ComponentMode.Interactive"
          @view="$emit('view', componentDetails.id)"
          @edit="$emit('edit', componentDetails.id)"
          @remove="$emit('remove', componentDetails.id)"
        />
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.v-card,
.v-card > div:last-child {
  height: 100%;
}
</style>

<script lang="ts">
import Vue from "vue";
import WorkspaceComponent, { ComponentMode } from "./WorkspaceComponent.vue";
import { Component, Prop } from "vue-property-decorator";
import WorkspaceComponentModel from "@/models/WorkspaceComponent";

@Component({
  components: { WorkspaceComponent }
})
export default class SingleComponentView extends Vue {
  ComponentMode = ComponentMode;

  @Prop({ default: {} })
  readonly componentDetails!: WorkspaceComponentModel;
  @Prop({ default: false })
  readonly value!: boolean;

  isMounted = false;

  mounted() {
    this.isMounted = true;
  }
}
</script>
