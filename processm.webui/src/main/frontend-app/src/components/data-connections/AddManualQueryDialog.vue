<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("add-manual-query-dialog.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form ref="queryForm" lazy-validation>
            <v-row>
              <v-col>
                <v-text-field
                  v-model="etlConfigurationName"
                  :label="$t('add-manual-query-dialog.etlConfigurationName')"
                  :rules="etlConfigurationNameRules"
                  data-testid="etlConfigurationName"
                ></v-text-field>
                <v-select
                  v-model="dataConnector"
                  :items="dataConnectors"
                  item-text="name"
                  :rules="dataConnectorRules"
                  return-object
                  data-testid="dataConnector"
                ></v-select>
                <v-textarea
                  v-model="query"
                  :label="$t('add-manual-query-dialog.query')"
                  :rules="queryRules"
                  data-testid="query"
                ></v-textarea>
                <v-switch
                  v-model="batch"
                  :label="$t('add-manual-query-dialog.batch')"
                  data-testid="batch"
                ></v-switch>
                <v-text-field
                  v-model="refresh"
                  :label="$t('add-manual-query-dialog.refresh')"
                  :disabled="batch"
                  :rules="refreshRules"
                  suffix="seconds"
                  data-testid="refresh"
                ></v-text-field>
                <v-switch
                  v-model="enabled"
                  :label="$t('add-manual-query-dialog.enabled')"
                  data-testid="enabled"
                ></v-switch>
                <v-text-field
                  v-model="traceIdColumn"
                  :label="$t('add-manual-query-dialog.traceIdColumn')"
                  :rules="traceIdColumnRules"
                  data-testid="traceIdColumn"
                ></v-text-field>
                <v-text-field
                  v-model="eventIdColumn"
                  :label="$t('add-manual-query-dialog.eventIdColumn')"
                  :rules="eventIdColumnRules"
                  data-testid="eventIdColumn"
                ></v-text-field>
              </v-col>
            </v-row>
          </v-form>
        </v-container>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn color="secondary" text @click.stop="cancel" data-testid="cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn
          color="primary"
          text
          @click.stop="createEtlProcess"
          :loading="isSubmitting"
          data-testid="submit"
        >
          {{ $t("common.save") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import { ConnectionType, DataConnector } from "@/models/DataStore";
import Vue from "vue";
import { Component, Inject, Prop } from "vue-property-decorator";
import App from "@/App.vue";
import DataStoreService from "../../services/DataStoreService";
import {
  isDefinedRule,
  isPositiveIntegerRule,
  notEmptyRule
} from "@/utils/FormValidationRules";

@Component
export default class AddManualQueryDialog extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop({
    default() {
      return [];
    }
  })
  readonly dataConnectors!: Array<DataConnector>;

  private notEmpty(v: string): string | boolean {
    return notEmptyRule(
      v,
      this.$t("add-manual-query-dialog.validation.non-empty-field").toString()
    );
  }

  etlConfigurationNameRules = [this.notEmpty];
  dataConnectorRules = [
    (v: DataConnector | undefined): string | boolean => {
      return isDefinedRule(
        v,
        this.$t(
          "add-manual-query-dialog.validation.data-connector-not-specified"
        ).toString()
      );
    }
  ];
  queryRules = [
    this.notEmpty
    // TODO Possibly add some validity checks for the query
  ];
  refreshRules = [
    (v: string): string | boolean =>
      this.batch ||
      isPositiveIntegerRule(
        v,
        this.$t(
          "add-manual-query-dialog.validation.not-a-positive-integer"
        ).toString()
      )
  ];
  traceIdColumnRules = [
    this.notEmpty
    // TODO Possibly verify somehow whether query will actually return such a column. I think this would have to be a soft constraint, as I have a feeling that determining column names without executing the query is in general undecidable
  ];
  eventIdColumnRules = [
    this.notEmpty
    // TODO Possibly verify somehow whether query will actually return such a column. I think this would have to be a soft constraint, as I have a feeling that determining column names without executing the query is in general undecidable
  ];

  isSubmitting = false;

  etlConfigurationName = "";
  @Prop({
    default() {
      return undefined;
    }
  })
  dataConnector: DataConnector | undefined;
  query = "";
  batch = false;
  refresh = 60;
  enabled = false;
  traceIdColumn = "";
  eventIdColumn = "";

  async createEtlProcess() {
    if (!(this.$refs.queryForm as HTMLFormElement).validate())
      throw new Error("The provided data is invalid");
    try {
      this.isSubmitting = true;
      // const dataConnector = await this.dataStoreService.createDataConnector(
      //     this.dataStoreId,
      //     this.connectionName,
      //     this.configMode == ConfigurationMode.ConnectionString
      //         ? this.connectionString
      //         : this.connectionProperties
      // );
      this.app.success(`${this.$t("common.saving.success")}`);
      // this.$emit("submitted", dataConnector);
      this.resetForms();
    } catch (error) {
      this.app.error(`${this.$t("common.saving.failure")}`);
    } finally {
      this.isSubmitting = false;
    }
  }

  private resetForms() {
    (this.$refs.queryForm as HTMLFormElement)?.reset();
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForms();
  }
}
</script>
