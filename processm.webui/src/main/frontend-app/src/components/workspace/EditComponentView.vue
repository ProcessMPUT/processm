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
                v-model="componentDetails.name"
                :rules="[
                  v =>
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
                v-model="componentDetails.type"
                :items="[
                  {
                    text: $t('workspace.component.causal-net'),
                    value: 'causalNet'
                  },
                  { text: $t('workspace.component.kpi'), value: 'kpi' }
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
                v-model="componentDetails.data.query"
                :rules="[
                  v =>
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
import WorkspaceComponentModel, {
  ComponentType,
  CausalNetComponentData
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

  mounted() {
    this.isMounted = true;
  }

  saveChanges() {
    if (this.componentDetails.data == undefined) {
      console.error(
        `The component ${this.componentDetails.id} has undifined 'data' property`
      );
      return;
    }

    if (this.componentDetails.type == ComponentType.CausalNet) {
      const causalNetComponentData = this.componentDetails
        .data as CausalNetComponentData;

      this.workspaceService.updateComponentData(
        this.workspaceId,
        this.componentDetails.id,
        {
          type: causalNetComponentData.type,
          query: causalNetComponentData.query,
          nodes: causalNetComponentData.nodes,
          edges: causalNetComponentData.edges,
          layout: causalNetComponentData.layout
        }
      );
    }
  }
}
</script>
