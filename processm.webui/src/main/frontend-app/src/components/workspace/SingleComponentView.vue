<template>
  <v-dialog fullscreen persistent v-model="value">
    <v-card flat tile>
      <v-card-actions>
        <v-btn color="primary darken-1" icon @click.stop="$emit('close')">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer></v-spacer>
        <v-icon>${{ componentDetails.type }}Component</v-icon>
        {{ $t(`workspace.component.${kebabize(componentDetails.type)}`) }}
        <v-spacer></v-spacer>
      </v-card-actions>
      <v-card-text>
        <workspace-component
          v-if="isMounted"
          :component-details="componentDetails"
          :workspace-id="workspaceId"
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
  height: 90%;
  overflow: hidden;
}
</style>

<script lang="ts">
import Vue from "vue";
import WorkspaceComponent, { ComponentMode } from "./WorkspaceComponent.vue";
import { Component, Prop } from "vue-property-decorator";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import { kebabize } from "@/utils/StringCaseConverter";

@Component({
  components: { WorkspaceComponent }
})
export default class SingleComponentView extends Vue {
  ComponentMode = ComponentMode;
  kebabize = kebabize;

  @Prop({ default: {} })
  readonly componentDetails!: WorkspaceComponentModel;
  @Prop()
  readonly workspaceId!: string;
  @Prop({ default: false })
  readonly value!: boolean;

  isMounted = false;

  mounted() {
    this.isMounted = true;
  }
}
</script>
