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
        <v-alert v-if="componentDetails.lastError !== undefined" type="error"
          >{{ componentDetails.lastError }}
        </v-alert>
        <v-list subheader>
          <v-list-item>
            <v-list-item-content>
              <v-text-field
                v-model="component.name"
                :label="$t('workspace.component.edit.name')"
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
              <v-select
                v-model="component.type"
                :label="$t('workspace.component.edit.type')"
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
              <v-text-field
                v-model="component.query"
                :label="$t('workspace.component.edit.query')"
                :rules="[
                  (v) =>
                    !!v || $t('workspace.component.edit.validation.query-empty')
                ]"
                required
              />
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-select
                v-model="component.dataStore"
                :label="$t('workspace.component.edit.datastore')"
                :items="dataStores"
                item-text="name"
                item-value="id"
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
import { Component, Inject, Prop } from "vue-property-decorator";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import WorkspaceService from "@/services/WorkspaceService";
import App from "@/App.vue";
import DataStoreService from "@/services/DataStoreService";
import DataStore from "@/models/DataStore";

@Component({
  components: { WorkspaceComponent }
})
export default class EditComponentView extends Vue {
  @Inject() app!: App;

  ComponentMode = ComponentMode;

  @Prop({ default: {} })
  readonly componentDetails!: WorkspaceComponentModel;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly workspaceId!: string;
  @Inject() workspaceService!: WorkspaceService;
  @Inject() dataStoreService!: DataStoreService;

  isMounted = false;

  /**
   * A shallow copy of [componentDetails]. This object replaces the [componentDetails] when saved or is discarded.
   */
  component: WorkspaceComponentModel = Object.assign({}, this.componentDetails);
  dataStores: Array<DataStore> = [];

  async mounted() {
    this.dataStores = await this.dataStoreService.getAll();
    if ((this.component.dataStore ?? "") == "" && this.dataStores.length > 0)
      this.component.dataStore = this.dataStores[0].id;
    this.isMounted = true;
  }

  async saveChanges() {
    try {
      await this.workspaceService.updateComponent(
        this.workspaceId,
        this.componentDetails.id,
        this.component
      );

      this.$emit("component-updated", this.component);
      this.app.success(`${this.$t("common.saving.success")}`);
    } catch (e) {
      console.error(e);
      this.app.error(`${this.$t("common.saving.failure")}: ${e.message}`);
    }
  }
}
</script>
