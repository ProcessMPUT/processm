<template>
  <v-dialog fullscreen persistent v-model="value">
    <v-card flat tile>
      <v-card-actions>
        <v-btn color="primary darken-1" icon @click.stop="$emit('discard', componentDetails.id)">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer></v-spacer>
        <v-icon>${{ componentDetails.type }}Component</v-icon>
        {{ $t(`workspace.component.${kebabize(componentDetails.type)}`) }}
        <v-spacer></v-spacer>
        <v-btn color="primary" icon @click.stop="saveChanges">
          <v-icon>save</v-icon>
        </v-btn>
      </v-card-actions>
      <v-card-text>
        <workspace-component
          v-if="isMounted"
          :workspace-id="workspaceId"
          :component-details="component"
          :component-mode="ComponentMode.Edit"
          :update-data="updateData"
          :is-transient="isTransient"
          @view="$emit('view', componentDetails.id)"
          @edit="$emit('edit', componentDetails.id)"
          @remove="$emit('remove', componentDetails.id)"
        />
        <v-alert v-if="componentDetails.lastError !== undefined" type="error">{{ componentDetails.lastError }}</v-alert>
        <v-list dense subheader>
          <v-list-item>
            <v-list-item-content>
              <v-text-field
                v-model="component.name"
                :label="$t('workspace.component.edit.name')"
                :hint="$t('workspace.component.edit.name-hint')"
                :rules="[(v) => !!v || $t('workspace.component.edit.validation.name-empty')]"
                required
              >
              </v-text-field>
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-text-field
                v-model="component.query"
                :hint="$t('workspace.component.edit.query-hint')"
                :rules="[(v) => !!v || $t('workspace.component.edit.validation.query-empty')]"
                required
              >
                <template v-slot:label>
                  {{ $t("workspace.component.edit.query") }}
                  <v-tooltip bottom max-width="600px">
                    <template v-slot:activator="{ on, attrs }">
                      <v-btn :to="{ name: 'pql-docs' }" class="mt-n1" icon small target="_blank" v-bind="attrs" v-on="on">
                        <v-icon>open_in_new</v-icon>
                      </v-btn>
                    </template>
                    <span>{{ $t("workspace.component.edit.query-doc") }}</span>
                  </v-tooltip>
                </template>
              </v-text-field>
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-select
                v-model="component.dataStore"
                :label="$t('workspace.component.edit.datastore')"
                :hint="$t('workspace.component.edit.datastore-hint')"
                :items="dataStores"
                item-text="name"
                item-value="id"
                required
              />
            </v-list-item-content>
          </v-list-item>
          <v-list-item v-for="property in component.customProperties ?? []" :key="property.name">
            <v-list-item-content>
              <v-text-field
                v-if="property.type == 'string'"
                v-model="property.value"
                :hint="$te(`workspace.component.edit.${property.name}-hint`) ? $t(`workspace.component.edit.${property.name}-hint`) : ''"
                :label="$t(`workspace.component.edit.${property.name}`)"
                required
              >
              </v-text-field>
              <v-select
                v-if="property.type == 'enum'"
                v-model="property.value"
                :items="property.enum"
                :label="$t(`workspace.component.edit.${property.name}`)"
                :hint="$te(`workspace.component.edit.${property.name}-hint`) ? $t(`workspace.component.edit.${property.name}-hint`) : ''"
                item-text="name"
                item-value="id"
                required
              />
              <v-text-field
                v-if="property.type == 'non-negative-integer'"
                v-model="property.value"
                type="number"
                :rules="nonNegativeIntegerRules"
                :label="$t(`workspace.component.edit.${property.name}`)"
                :hint="$te(`workspace.component.edit.${property.name}-hint`) ? $t(`workspace.component.edit.${property.name}-hint`) : ''"
                required
              />
              <v-text-field
                  v-if="property.type == 'percent'"
                  v-model="property.value"
                  type="number"
                  :rules="percentRules"
                  :label="$t(`workspace.component.edit.${property.name}`)"
                  :hint="$te(`workspace.component.edit.${property.name}-hint`) ? $t(`workspace.component.edit.${property.name}-hint`) : ''"
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

.v-text-field >>> .v-label {
  overflow: visible;
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
import {isNonNegativeIntegerRule, isPercentRule} from "@/utils/FormValidationRules";

@Component({
  components: { WorkspaceComponent }
})
export default class EditComponentDialog extends Vue {
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
  @Prop({ default: false })
  readonly isTransient!: boolean;

  /**
   * Set to true to let children update the data model (usually before sending it to the server).
   */
  private updateData = false;

  @Inject() workspaceService!: WorkspaceService;
  @Inject() dataStoreService!: DataStoreService;

  isMounted = false;

  nonNegativeIntegerRules = [(v: string) => isNonNegativeIntegerRule(v, this.$t("workspace.component.edit.validation.not-a-non-negative-integer").toString())];
  percentRules = [(v: string) => isPercentRule(v, this.$t("workspace.component.edit.validation.not-a-percent").toString())];

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

      const lastModified = await this.workspaceService.updateComponent(this.workspaceId, this.componentDetails.id, this.component);
      this.component.userLastModified = lastModified.userLastModified;
      this.component.dataLastModified = lastModified.dataLastModified;

      this.$emit("component-updated", this.component);
      this.app.success(`${this.$t("common.saving.success")}`);
    } catch (e) {
      console.error(e);
      this.app.error(e);
    } finally {
      this.updateData = false;
    }
  }
}
</script>