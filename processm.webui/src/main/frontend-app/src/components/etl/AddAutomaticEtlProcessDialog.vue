<template>
  <v-dialog
    v-model="value"
    @click:outside="cancel"
    max-width="40%"
    max-height="80%"
  >
    <v-card>
      <v-card-title class="headline">
        {{ $t("data-stores.add-automatic-process.title") }}
      </v-card-title>
      <v-card-text>
        <v-form ref="etlProcessConfiguration" lazy-validation>
          <v-text-field
            v-model="processName"
            :label="$t('data-stores.etl.process-name')"
            :rules="notEmptyRule"
            required
          ></v-text-field>

          <v-select
            v-model="selectedDataConnectorId"
            item-text="name"
            item-value="id"
            :items="dataConnectors"
            :label="$t('data-stores.etl.data-connector')"
            :rules="notEmptyRule"
            @change="reloadSuggestedBusinessPerspectives"
            required
          >
            <template v-slot:item="{ item, on, attrs }">
              <v-list-item
                v-if="Object.keys(item.properties).length > 0"
                v-bind="attrs"
                v-on="on"
              >
                <v-list-item-content>
                  <v-list-item-title>{{ item.name }}</v-list-item-title>
                </v-list-item-content>
              </v-list-item>
              <v-tooltip v-else bottom v-bind="attrs" v-on="on">
                <template v-slot:activator="{ on, attrs }">
                  <div v-bind="attrs" v-on="on">
                    <v-list-item :disabled="true" class="px-0">
                      <v-list-item-content>
                        <v-list-item-title>{{ item.name }}</v-list-item-title>
                      </v-list-item-content>
                    </v-list-item>
                  </div>
                </template>
                <span>{{
                  $t(
                    "data-stores.add-automatic-process.connector-not-supported"
                  )
                }}</span>
              </v-tooltip>
            </template>
          </v-select>

          <v-tooltip bottom :disabled="selectedDataConnectorId != null">
            <template v-slot:activator="{ on, attrs }">
              <div v-bind="attrs" v-on="on">
                <v-select
                  v-model="selectedCaseNotion"
                  :items="availableCaseNotions"
                  :rules="notEmptyRule"
                  :label="$t('data-stores.add-automatic-process.case-notion')"
                  :disabled="selectedDataConnectorId == null"
                  :loading="isLoadingCaseNotions"
                  @input="caseNotionSelected"
                  required
                >
                  <template v-slot:item="{ item }">
                    <v-list-item-content>
                      <v-list-item-title>{{
                        Array.from(item.classes.values()).join(", ")
                      }}</v-list-item-title>
                      <v-list-item-subtitle class="case-notion-description">{{
                        getCaseNotionDescription(item)
                      }}</v-list-item-subtitle>
                    </v-list-item-content>
                  </template>
                  <template v-slot:selection="{ item }">
                    <span v-if="!isCaseNotionManuallyModified">
                      {{ Array.from(item.classes.values()).join(", ") }}
                    </span>
                    <span v-else>
                      {{ $t("data-stores.etl.custom-case-notion") }} item
                    </span>
                  </template>
                </v-select>
              </div>
            </template>
            <span>{{
              $t("data-stores.add-automatic-process.select-data-connector")
            }}</span>
          </v-tooltip>
          <div ref="holder"></div>
        </v-form>

        <div class="case-notion-editor-container">
          <case-notion-editor
            v-model="displayCaseNotionEditor"
            :relationship-graph="relationshipGraph"
            :selected-nodes="selectedClasses"
            :selected-links="selectedLinks"
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
          color="primary"
          text
          @click.stop="createEtlProcessConfiguration"
          :loading="isSubmitting"
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
import { Component, Inject, Prop } from "vue-property-decorator";
import DataStoreService from "@/services/DataStoreService";
import App from "@/App.vue";
import CaseNotion, { Relation } from "@/models/CaseNotion";
import { capitalize } from "@/utils/StringCaseConverter";
import { EtlProcessType } from "@/models/EtlProcess";
import { notEmptyRule } from "@/utils/FormValidationRules";
import CaseNotionEditor from "./CaseNotionEditor.vue";

@Component({
  components: {
    CaseNotionEditor
  }
})
export default class AddAutomaticEtlProcessDialog extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly dataStoreId?: string;
  @Prop()
  readonly dataConnectors?: DataConnector[];

  processName = "";
  selectedDataConnectorId: string | null = null;
  selectedCaseNotion: CaseNotion | null = null;
  availableCaseNotions: CaseNotion[] = [];
  isSubmitting = false;
  isLoadingCaseNotions = false;
  isCaseNotionManuallyModified = false;
  selectedClasses: string[] = [];
  selectedLinks: Array<{
    sourceNodeId: string;
    targetNodeId: string;
  }> = [];
  relationshipGraph: CaseNotion | null = null;
  notEmptyRule = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.non-empty-field"
        ).toString()
      )
  ];

  async reloadSuggestedBusinessPerspectives() {
    if (this.dataStoreId == null || this.selectedDataConnectorId == null) {
      return;
    }

    this.relationshipGraph = await this.dataStoreService.getRelationshipGraph(
      this.dataStoreId,
      this.selectedDataConnectorId
    );
    this.selectedCaseNotion = null;
    this.selectedClasses = [];
    this.selectedLinks = [];

    try {
      this.isLoadingCaseNotions = true;
      this.availableCaseNotions = [];
      this.availableCaseNotions = await this.dataStoreService.getCaseNotionSuggestions(
        this.dataStoreId,
        this.selectedDataConnectorId
      );
    } finally {
      this.isLoadingCaseNotions = false;
    }
  }

  async createEtlProcessConfiguration() {
    // consider adding extra validation to check if case notion:
    // * is a single component,
    // * can be sorted topologically.
    if (!this.validateForm()) throw new Error("The provided data is invalid");
    if (
      this.dataStoreId == null ||
      this.selectedDataConnectorId == null ||
      this.selectedClasses.length == 0
    ) {
      return;
    }

    try {
      const caseNotion =
        this.selectedCaseNotion != null && !this.isCaseNotionManuallyModified
          ? this.selectedCaseNotion
          : {
              classes: new Map<string, string>(
                this.selectedClasses.map((classId) => [
                  classId,
                  this.relationshipGraph?.classes.get(classId) || ""
                ])
              ),
              edges: this.selectedLinks.map(
                ({ sourceNodeId, targetNodeId }) => ({
                  sourceClassId: sourceNodeId,
                  targetClassId: targetNodeId
                })
              )
            };
      this.isSubmitting = true;
      const etlProcess = await this.dataStoreService.createEtlProcess(
        this.dataStoreId,
        this.processName,
        EtlProcessType.Automatic,
        this.selectedDataConnectorId,
        caseNotion
      );
      this.app.success(`${this.$t("common.saving.success")}`);
      this.$emit("submitted", etlProcess);
      this.resetForm();
    } catch (error) {
      this.app.error(`${this.$t("common.saving.failure")}`);
    } finally {
      this.isSubmitting = false;
    }
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  getCaseNotionDescription(caseNotion: CaseNotion): string {
    return capitalize(
      caseNotion.edges
        .map((relation) =>
          this.$t("data-stores.add-automatic-process.case-notion-description", {
            source: caseNotion.classes.get(relation.sourceClassId),
            target: caseNotion.classes.get(relation.targetClassId)
          })
        )
        .join(", ") + "."
    );
  }

  caseNotionSelected() {
    this.selectedClasses = Array.from(
      this.selectedCaseNotion?.classes?.keys() ?? []
    );
    this.selectedLinks = this.getSelectedLinks(this.selectedClasses);
    this.isCaseNotionManuallyModified = false;
  }

  nodeSelected(nodeId: string) {
    if (this.relationshipGraph == null) return;

    const nodeIndex = this.selectedClasses.findIndex((id) => id == nodeId);

    if (nodeIndex < 0) {
      this.selectedClasses.push(nodeId);
    } else {
      this.selectedClasses.splice(nodeIndex, 1);
    }

    this.selectedLinks = this.getSelectedLinks(this.selectedClasses);
    this.isCaseNotionManuallyModified = true;
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

  private getSelectedLinks(selectedClasses: string[]) {
    if (this.relationshipGraph == null) return [];

    const selectedClassesSet = new Set(selectedClasses);

    return this.relationshipGraph.edges.reduce(
      (
        selectedRelations: Array<{
          sourceNodeId: string;
          targetNodeId: string;
        }>,
        relation: Relation
      ) => {
        if (
          selectedClassesSet.has(relation.sourceClassId) &&
          selectedClassesSet.has(relation.targetClassId)
        ) {
          selectedRelations.push({
            sourceNodeId: relation.sourceClassId,
            targetNodeId: relation.targetClassId
          });
        }
        return selectedRelations;
      },
      []
    );
  }
}
</script>
