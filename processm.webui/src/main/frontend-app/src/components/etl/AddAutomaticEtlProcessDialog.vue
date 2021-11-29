<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("data-stores.add-automatic-process.title") }}
      </v-card-title>
      <v-card-text>
        <v-form ref="etlProcessConfiguration" lazy-validation>
          <v-text-field
            v-model="processName"
            :label="$t('data-stores.etl.process-name')"
            required
            :rules="connectionNameRules"
          ></v-text-field>

          <v-select
            v-model="selectedDataConnectorId"
            item-text="name"
            item-value="id"
            :items="dataConnectors"
            :label="$t('data-stores.etl.data-connector')"
            @change="reloadSuggestedBusinessPerspectives"
            required
          ></v-select>

          <v-tooltip bottom :disabled="selectedDataConnectorId != null">
            <template v-slot:activator="{ on, attrs }">
              <div v-bind="attrs" v-on="on">
                <v-select
                  v-model="selectedCaseNotion"
                  :items="availableCaseNotions"
                  :label="$t('data-stores.add-automatic-process.case-notion')"
                  @change="reloadSuggestedBusinessPerspectives"
                  required
                  :disabled="selectedDataConnectorId == null"
                  :loading="isLoadingCaseNotions"
                >
                  <template v-slot:item="{ item }">
                    <v-list-item-content>
                      <v-list-item-title>{{
                        Object.values(item.classes).join(", ")
                      }}</v-list-item-title>
                      <v-list-item-subtitle class="case-notion-description">{{
                        getCaseNotionDescription(item)
                      }}</v-list-item-subtitle>
                    </v-list-item-content>
                  </template>
                  <template v-slot:selection="{ item }">
                    {{ Object.values(item.classes).join(", ") }}
                  </template>
                </v-select>
              </div>
            </template>
            <span>{{
              $t("data-stores.add-automatic-process.select-data-connector")
            }}</span>
          </v-tooltip>
        </v-form>
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
</style>

<script lang="ts">
import { DataConnector } from "@/models/DataStore";
import Vue from "vue";
import { Component, Inject, Prop } from "vue-property-decorator";
import DataStoreService from "@/services/DataStoreService";
import {
  connectionStringFormatRule,
  notEmptyRule
} from "@/utils/FormValidationRules";
import App from "@/App.vue";
import CaseNotion from "@/models/CaseNotion";
import { capitalize } from "@/utils/StringCaseConverter";
import { EtlProcessType } from "@/models/EtlProcess";

@Component
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
  connectionNameRules = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.non-empty-field"
        ).toString()
      )
  ];
  connectionStringRules = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.non-empty-field"
        ).toString()
      ),
    (v: string) =>
      connectionStringFormatRule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.connection-string-format"
        ).toString()
      )
  ];

  async reloadSuggestedBusinessPerspectives() {
    if (this.dataStoreId == null || this.selectedDataConnectorId == null) {
      return;
    }

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
    if (
      this.dataStoreId == null ||
      this.selectedDataConnectorId == null ||
      this.selectedCaseNotion == null
    ) {
      return;
    }

    try {
      this.isSubmitting = true;
      const etlProcess = await this.dataStoreService.createEtlProcess(
        this.dataStoreId,
        this.processName,
        EtlProcessType.Automatic,
        this.selectedDataConnectorId,
        this.selectedCaseNotion
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
            source: caseNotion.classes[relation.sourceClassId],
            target: caseNotion.classes[relation.targetClassId]
          })
        )
        .join(", ") + "."
    );
  }

  private validateForm() {
    return (this.$refs.etlProcessConfiguration as HTMLFormElement).validate();
  }

  private resetForm() {
    this.processName = "";
    this.selectedDataConnectorId = null;
    (this.$refs.etlProcessConfiguration as HTMLFormElement)?.reset();
  }
}
</script>
