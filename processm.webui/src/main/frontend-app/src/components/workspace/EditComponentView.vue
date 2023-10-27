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
          :update-data="updateData"
          @view="$emit('view', componentDetails.id)"
          @edit="$emit('edit', componentDetails.id)"
          @remove="$emit('remove', componentDetails.id)"
        />
        <v-alert v-if="componentDetails.lastError !== undefined" type="error">{{ componentDetails.lastError }}</v-alert>
        <v-list subheader>
          <v-list-item>
            <v-list-item-content>
              <v-text-field
                v-model="component.name"
                :label="$t('workspace.component.edit.name')"
                :rules="[(v) => !!v || $t('workspace.component.edit.validation.name-empty')]"
                required
              />
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-select v-model="component.type" :label="$t('workspace.component.edit.type')" :items="availableComponents">
                <template slot="item" slot-scope="componentType">
                  <v-icon>${{ componentType.item }}Component</v-icon>
                  {{ $t(`workspace.component.${kebabize(componentType.item)}`) }}
                </template>
                <template slot="selection" slot-scope="componentType">
                  <v-icon>${{ componentType.item }}Component</v-icon>
                  {{ $t(`workspace.component.${kebabize(componentType.item)}`) }}
                </template>
              </v-select>
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-text-field
                v-model="component.query"
                :label="$t('workspace.component.edit.query')"
                :rules="[(v) => !!v || $t('workspace.component.edit.validation.query-empty')]"
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
import { ComponentType } from "@/openapi";
import { kebabize } from "@/utils/StringCaseConverter";
import { waitForRepaint } from "@/utils/waitForRepaint";

@Component({
  components: { WorkspaceComponent }
})
export default class EditComponentView extends Vue {
  @Inject() app!: App;
  readonly availableComponents = Object.values(ComponentType);
  kebabize = kebabize;

  ComponentMode = ComponentMode;

  @Prop({ default: {} })
  readonly componentDetails!: WorkspaceComponentModel;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly workspaceId!: string;

  /**
   * Set to true to let children update the data model (usually before sending it to the server).
   */
  private updateData = false;

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
    if ((this.component.dataStore ?? "") == "" && this.dataStores.length > 0) this.component.dataStore = this.dataStores[0].id;
    this.isMounted = true;
  }

  async saveChanges() {
    try {
      // We cannot use this.$emit(), as $emit() sends events to parent only
      // So, we are forced to create extra meaningless property that a child is supposed to implement and
      // change the value of this property in some way. Sweet.
      this.updateData = true;
      await waitForRepaint(() => 0);

      await this.workspaceService.updateComponent(this.workspaceId, this.componentDetails.id, this.component);

      this.$emit("component-updated", this.component);
      this.app.success(`${this.$t("common.saving.success")}`);
    } catch (e) {
      console.error(e);
      this.app.error(`${this.$t("common.saving.failure")}: ${e.message}`);
    } finally {
      this.updateData = false;
    }
  }
}
</script>
