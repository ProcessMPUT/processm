<template>
  <v-dialog fullscreen persistent v-model="value">
    <v-card flat tile>
      <v-card-actions>
        <v-btn color="primary darken-1" icon @click.stop="$emit('close')">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer></v-spacer>
        <v-btn color="primary" icon @click.stop="saveChanges">
          <v-icon>save</v-icon>
        </v-btn>
      </v-card-actions>
      <v-card-text>
        <workspace-component
          v-if="isMounted"
          :component-details="componentDetails"
          :component-mode="ComponentMode.Edit"
          @view="$emit('view', componentDetails.id)"
          @edit="$emit('edit', componentDetails.id)"
          @remove="$emit('remove', componentDetails.id)"
        />
        <v-list subheader>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title>{{
                $t("workspace.component.edit.name")
              }}</v-list-item-title>
              <v-text-field
                v-model="componentName"
                :rules="[
                  (v) =>
                    !!v || $t('workspace.component.edit.validation.name-empty')
                ]"
                required
              />
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title>{{
                $t("workspace.component.edit.type")
              }}</v-list-item-title>
              <v-select
                v-model="componentType"
                :items="[
                  {
                    text: $t('workspace.component.causal-net'),
                    value: 'causalNet'
                  },
                  {
                    text: $t('workspace.component.kpi'),
                    value: 'kpi'
                  }
                ]"
              ></v-select>
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title>{{
                $t("workspace.component.edit.query")
              }}</v-list-item-title>
              <v-text-field
                v-model="componentQuery"
                :rules="[
                  (v) =>
                    !!v || $t('workspace.component.edit.validation.query-empty')
                ]"
                required
              />
            </v-list-item-content>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.v-card,
.v-card > div:last-child {
  height: 50%;
}
</style>

<script lang="ts">
import Vue from "vue";
import WorkspaceComponent, { ComponentMode } from "./WorkspaceComponent.vue";
import { Component, Prop, Inject } from "vue-property-decorator";
import {
  WorkspaceComponent as WorkspaceComponentModel,
  ComponentType
} from "@/models/WorkspaceComponent";
import WorkspaceService from "@/services/WorkspaceService";

@Component({
  components: { WorkspaceComponent }
})
export default class EditComponentView extends Vue {
  ComponentMode = ComponentMode;

  @Prop({ default: {} })
  readonly componentDetails!: WorkspaceComponentModel;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly workspaceId!: string;
  @Inject() workspaceService!: WorkspaceService;

  isMounted = false;
  componentType?: ComponentType;
  componentName?: string;
  componentQuery?: string;

  created() {
    this.componentType = this.componentDetails.type;
    this.componentName = this.componentDetails.name ?? "";
    this.componentQuery = this.componentDetails.query;
  }

  mounted() {
    this.isMounted = true;
  }

  saveChanges() {
    if (this.componentType == null) return;

    const componentModel = new WorkspaceComponentModel({
      id: this.componentDetails.id,
      type: this.componentType,
      name: this.componentName,
      query: this.componentQuery ?? "",
      data: this.componentDetails.data,
      customizationData: this.componentDetails.customizationData
    });

    this.workspaceService.updateComponent(
      this.workspaceId,
      this.componentDetails.id,
      componentModel
    );

    this.$emit("component-updated", componentModel);
  }
}
</script>
