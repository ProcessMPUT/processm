<template>
  <v-dialog v-model="value" max-height="80%" max-width="40%" @click:outside="cancel" @keydown.esc="cancel">
    <v-card>
      <v-card-title class="headline">
        {{ $t("automatic-etl-process-dialog.title") }}
      </v-card-title>
      <v-card-text>
        <v-form ref="etlProcessConfiguration" lazy-validation>
          <v-text-field v-model="processName" :label="$t('data-stores.etl.process-name')" :rules="notEmptyRule" name="process-name" required></v-text-field>

          <v-select
            v-model="selectedDataConnectorId"
            :items="availableDataConnectors"
            :label="$t('data-stores.etl.data-connector')"
            :rules="notEmptyRule"
            item-disabled="disabled"
            item-text="name"
            item-value="id"
            name="selected-data-connector-id"
            required
            @change="reloadSuggestedBusinessPerspectives"
          >
            <template v-slot:item="{ item, on, attrs }">
              <v-list-item v-if="!item.disabled" v-bind="attrs" v-on="on">
                <v-list-item-content>
                  <v-list-item-title>{{ item.name }}</v-list-item-title>
                </v-list-item-content>
              </v-list-item>
              <v-tooltip v-else bottom v-bind="attrs" v-on="on">
                <template v-slot:activator="{ on, attrs }">
                  <div :name="'automatic-etl-process-dialog-connector-' + item.id" v-bind="attrs" v-on="on">
                    <v-list-item :disabled="true" class="px-0">
                      <v-list-item-content>
                        <v-list-item-title>{{ item.name }}</v-list-item-title>
                      </v-list-item-content>
                    </v-list-item>
                  </div>
                </template>
                <span>{{ $t("data-stores.automatic-etl-process.connector-not-supported") }}</span>
              </v-tooltip>
            </template>
          </v-select>

          <v-tooltip bottom :disabled="selectedDataConnectorId != null">
            <template v-slot:activator="{ on, attrs }">
              <div v-bind="attrs" v-on="on">
                <v-combobox
                  v-model="selectedCaseNotion"
                  :disabled="selectedDataConnectorId == null"
                  :items="availableCaseNotions"
                  :label="$t('data-stores.automatic-etl-process.case-notion')"
                  :loading="isLoadingCaseNotions"
                  required
                  @input="caseNotionSelected"
                  :filter="filter"
                >
                  <template v-slot:item="{ item }">
                    <v-list-item-content>
                      <v-list-item-title>{{ getCaseNotionName(item) }}</v-list-item-title>
                      <v-list-item-subtitle class="case-notion-description">{{ getCaseNotionDescription(item) }} </v-list-item-subtitle>
                    </v-list-item-content>
                  </template>
                  <template v-slot:selection="{ item }">
                    <span v-if="!isCaseNotionManuallyModified">
                      {{ getCaseNotionName(item) }}
                    </span>
                    <span v-else> {{ $t("data-stores.etl.custom-case-notion") }} </span>
                  </template>
                </v-combobox>
              </div>
            </template>
            <span>{{ $t("data-stores.automatic-etl-process.select-data-connector") }}</span>
          </v-tooltip>
          <div ref="holder"></div>
        </v-form>

        <div class="case-notion-editor-container">
          <case-notion-editor
            v-model="displayCaseNotionEditor"
            :relationship-graph="relationshipGraph"
            :selected-links="selectedLinks"
            :selected-nodes="selectedClasses"
            @node-selected="nodeSelected"
          />
        </div>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn color="secondary" text @click.stop="cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn
          :disabled="!isValid"
          :loading="isSubmitting"
          color="primary"
          text
          @click.stop="createEtlProcessConfiguration"
          name="btn-create-etl-process-configuration"
        >
          {{ $t("common.save") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.v-expansion-panel-content {
  margin: 5px;
}

.case-notion-description {
  white-space: normal;
}

.case-notion-editor-container {
  display: flex;
  overflow-x: scroll;
  overflow-y: hidden;
}
</style>

<script lang="ts">
import { DataConnector } from "@/models/DataStore";
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import DataStoreService from "@/services/DataStoreService";
import App from "@/App.vue";
import { capitalize } from "@/utils/StringCaseConverter";
import { notEmptyRule } from "@/utils/FormValidationRules";
import { AutomaticEtlProcess, CaseNotion, EtlProcessType, RelationshipGraph, RelationshipGraphEdgesInner } from "@/openapi";

const CaseNotionEditor = () => import("./CaseNotionEditor.vue");

@Component({
  components: {
    CaseNotionEditor
  }
})
export default class AutomaticEtlProcessDialog extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly dataStoreId?: string;
  @Prop()
  readonly dataConnectors?: DataConnector[];

  /**
   * An optional configuration used to populate form feeds on dialog open.
   */
  @Prop()
  readonly initialConfig: AutomaticEtlProcess | null = null;

  processName = "";
  availableDataConnectors: Array<{ name: string; id: string; disabled: boolean }> = [];
  selectedDataConnectorId: string | null = null;
  selectedCaseNotion: CaseNotion | string | null = null;
  availableCaseNotions: CaseNotion[] = [];
  isSubmitting = false;
  isLoadingCaseNotions = false;
  isCaseNotionManuallyModified = false;
  selectedClasses: number[] = [];
  selectedLinks: Array<number> = [];
  relationshipGraph: RelationshipGraph | null = null;
  classIdToName: Record<number, string> = {};
  nameToClassId: Record<string, number> = {};
  relationIdToDefinition: Record<number, RelationshipGraphEdgesInner> = {};
  notEmptyRule = [(v: string) => notEmptyRule(v, this.$t("data-connector-dialog.validation.non-empty-field").toString())];
  isValid = false;

  @Watch("dataConnectors")
  updateAvailableDataConnectors() {
    this.availableDataConnectors =
      this.dataConnectors?.map((item: DataConnector) => {
        return { name: item.name, id: item.id, disabled: Object.keys(item.properties).length == 0 };
      }) ?? [];
  }

  @Watch("initialConfig")
  async setInitialConfig() {
    const etl = this.initialConfig;

    this.processName = etl?.name || "";
    this.selectedDataConnectorId = etl?.dataConnectorId || "";
    if (this.selectedDataConnectorId != "") {
      await this.reloadSuggestedBusinessPerspectives();
    }
    this.selectedCaseNotion = etl?.caseNotion || null;
    if (this.selectedCaseNotion !== null) {
      this.isCaseNotionManuallyModified = true;
      this.caseNotionSelected();
    } else {
      this.isCaseNotionManuallyModified = false;
      this.selectedCaseNotion = null;
      this.selectedClasses = [];
      this.selectedLinks = [];
    }
  }

  async reloadSuggestedBusinessPerspectives() {
    if (this.dataStoreId == null || this.selectedDataConnectorId == null) {
      return;
    }

    try {
      this.isLoadingCaseNotions = true;
      this.relationshipGraph = await this.dataStoreService.getRelationshipGraph(this.dataStoreId, this.selectedDataConnectorId);
      this.classIdToName = {};
      this.relationshipGraph.classes.forEach((item) => {
        this.classIdToName[item.id] = item.name;
        this.nameToClassId[item.name] = item.id;
      });
      this.relationIdToDefinition = {};
      this.relationshipGraph.edges.forEach((item) => {
        this.relationIdToDefinition[item.id] = item;
      });

      this.availableCaseNotions = [];
      this.availableCaseNotions = await this.dataStoreService.getCaseNotionSuggestions(this.dataStoreId, this.selectedDataConnectorId);
    } finally {
      this.isLoadingCaseNotions = false;
    }
  }

  async createEtlProcessConfiguration() {
    // consider adding extra validation to check if case notion:
    // * is a single component,
    // * can be sorted topologically.
    if (!this.validateForm()) throw new Error("The provided data is invalid");
    if (this.dataStoreId == null || this.selectedDataConnectorId == null || this.selectedClasses.length == 0) {
      return;
    }

    try {
      const caseNotion = {
        classes: this.selectedClasses,
        edges: this.selectedLinks
      };
      this.isSubmitting = true;
      const etlProcess = await this.dataStoreService.saveEtlProcess(
        this.dataStoreId,
        this.processName,
        EtlProcessType.Automatic,
        this.selectedDataConnectorId,
        caseNotion,
        this.initialConfig?.id
      );
      this.app.success(`${this.$t("common.saving.success")}`);
      this.$emit("submitted", etlProcess);
      this.resetForm();
    } catch (error) {
      this.app.error(error);
    } finally {
      this.isSubmitting = false;
    }
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  private isCaseNotion(item: any): item is CaseNotion {
    return (item as CaseNotion).classes !== undefined && (item as CaseNotion).edges !== undefined;
  }

  getCaseNotionName(caseNotion: CaseNotion | string): string {
    if (this.isCaseNotion(caseNotion)) {
      return caseNotion.classes.map((classId) => this.classIdToName[classId]).join(", ");
    } else {
      return caseNotion;
    }
  }

  getCaseNotionDescription(caseNotion: CaseNotion | string): string {
    if (this.isCaseNotion(caseNotion)) {
      return capitalize(
        caseNotion.edges
          .map((relationId) => {
            const relation = this.relationIdToDefinition[relationId];
            return this.$t("data-stores.automatic-etl-process.case-notion-description", {
              source: this.classIdToName[relation.sourceClassId],
              target: this.classIdToName[relation.targetClassId]
            });
          })
          .join(", ") + "."
      );
    } else {
      return caseNotion;
    }
  }

  private checkValidity(): boolean {
    return this.selectedClasses.length > 0;
  }

  caseNotionSelected() {
    if (this.selectedCaseNotion == null) return;
    if (this.isCaseNotion(this.selectedCaseNotion)) {
      this.selectedClasses = this.selectedCaseNotion?.classes ?? [];
      this.selectedLinks = this.getSelectedLinks(this.selectedClasses);
      this.isCaseNotionManuallyModified = false;
      this.isValid = true;
    } else {
      this.selectedClasses = [];
      for (const item of this.selectedCaseNotion.split(",")) {
        const cls = this.nameToClassId[item.trim()];
        if (cls !== undefined && cls !== null) this.selectedClasses.push(cls);
      }
      this.selectedLinks = this.getSelectedLinks(this.selectedClasses);
      this.isCaseNotionManuallyModified = true;
      this.isValid = this.checkValidity();
    }
  }

  nodeSelected(nodeId: number) {
    if (this.relationshipGraph == null) return;

    const nodeIndex = this.selectedClasses.findIndex((id) => id == nodeId);

    if (nodeIndex < 0) {
      this.selectedClasses.push(nodeId);
    } else {
      this.selectedClasses.splice(nodeIndex, 1);
    }

    this.selectedLinks = this.getSelectedLinks(this.selectedClasses);
    this.isCaseNotionManuallyModified = true;
    this.isValid = this.checkValidity();
  }

  get displayCaseNotionEditor() {
    return this.selectedDataConnectorId != null;
  }

  private validateForm() {
    return (this.$refs.etlProcessConfiguration as HTMLFormElement).validate();
  }

  private resetForm() {
    this.processName = "";
    this.selectedDataConnectorId = null;
    this.selectedCaseNotion = null;
    (this.$refs.etlProcessConfiguration as HTMLFormElement)?.reset();
  }

  private getSelectedLinks(selectedClasses: number[]) {
    if (this.relationshipGraph == null) return [];

    const selectedClassesSet = new Set(selectedClasses);

    return this.relationshipGraph.edges.reduce((selectedRelations: Array<number>, relation: RelationshipGraphEdgesInner) => {
      const sourceClassId = relation.sourceClassId;
      const targetClassId = relation.targetClassId;
      if (sourceClassId !== undefined && targetClassId !== undefined && selectedClassesSet.has(sourceClassId) && selectedClassesSet.has(targetClassId)) {
        selectedRelations.push(relation.id);
      }
      return selectedRelations;
    }, []);
  }

  private filter(item: CaseNotion, queryText: string, itemText: any) {
    for (const clsId of item.classes) {
      if (this.classIdToName[clsId].includes(queryText)) {
        return true;
      }
    }
    return false;
  }
}
</script>