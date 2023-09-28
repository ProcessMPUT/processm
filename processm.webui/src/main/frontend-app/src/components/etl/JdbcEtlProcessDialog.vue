<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("jdbc-etl-process-dialog.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form ref="queryForm" lazy-validation>
            <v-row>
              <v-col>
                <v-text-field v-model="processName" :label="$t('common.name')" :rules="[notEmpty]" data-testid="etlConfigurationName"></v-text-field>
                <v-select
                  v-model="selectedDataConnectorId"
                  :items="dataConnectors"
                  :label="$t('data-stores.etl.data-connector')"
                  :rules="[notEmpty]"
                  item-text="name"
                  item-value="id"
                  required
                ></v-select>
                <v-textarea
                  v-model="query"
                  :hint="$t('jdbc-etl-process-dialog.query-hint')"
                  :label="$t('jdbc-etl-process-dialog.query')"
                  :rules="[notEmpty]"
                  data-testid="query"
                ></v-textarea>
                <v-switch v-model="batch" data-testid="batch">
                  <template v-slot:label>
                    {{ $t("jdbc-etl-process-dialog.batch") }}&nbsp;
                    <v-tooltip bottom max-width="600px">
                      <template v-slot:activator="{ on, attr }">
                        <v-icon v-bind="attr" v-on="on">help</v-icon>
                      </template>
                      <span>{{ $t("jdbc-etl-process-dialog.batch-hint") }}</span>
                    </v-tooltip>
                  </template>
                </v-switch>
                <v-text-field
                  v-model="refresh"
                  :disabled="batch"
                  :hint="$t('jdbc-etl-process-dialog.refresh-hint')"
                  :label="$t('jdbc-etl-process-dialog.refresh')"
                  :rules="refreshRules"
                  data-testid="refresh"
                  suffix="seconds"
                ></v-text-field>
                <v-switch v-model="enabled" :label="$t('jdbc-etl-process-dialog.enabled')" data-testid="enabled"></v-switch>
                <v-row>
                  <v-col>
                    <v-text-field
                      v-model="lastEventExternalId"
                      :disabled="batch"
                      :hint="$t('jdbc-etl-process-dialog.last-event-external-id-hint')"
                      :label="$t('jdbc-etl-process-dialog.last-event-external-id')"
                      :rules="lastEventExternalIdRules"
                    ></v-text-field>
                    <v-text-field
                      v-model="traceIdSource"
                      :hint="$t('jdbc-etl-process-dialog.trace-id-source-hint')"
                      :label="$t('jdbc-etl-process-dialog.trace-id-source')"
                      :rules="[notEmpty]"
                    ></v-text-field>
                    <v-text-field
                      v-model="eventIdSource"
                      :hint="$t('jdbc-etl-process-dialog.event-id-source-hint')"
                      :label="$t('jdbc-etl-process-dialog.event-id-source')"
                      :rules="[notEmpty]"
                    ></v-text-field>
                  </v-col>
                  <v-col>
                    <v-select
                      v-model="lastEventExternalIdType"
                      :disabled="batch"
                      :hint="$t('jdbc-etl-process-dialog.last-event-external-id-type-hint')"
                      :items="jdbcTypes"
                      :label="$t('jdbc-etl-process-dialog.last-event-external-id-type')"
                      :rules="lastEventExternalIdRules"
                      item-text="name"
                      item-value="id"
                    ></v-select>
                    <v-text-field
                      v-model="traceIdTarget"
                      :hint="$t('jdbc-etl-process-dialog.trace-id-target-hint')"
                      :label="$t('jdbc-etl-process-dialog.trace-id-target')"
                      :rules="[notEmpty]"
                      placeholder="identity:id"
                    ></v-text-field>
                    <v-text-field
                      v-model="eventIdTarget"
                      :hint="$t('jdbc-etl-process-dialog.event-id-target-hint')"
                      :label="$t('jdbc-etl-process-dialog.event-id-target')"
                      :rules="[notEmpty]"
                      placeholder="identity:id"
                    ></v-text-field>
                  </v-col>
                </v-row>
                <v-data-table
                  :headers="[
                    {
                      text: $t('jdbc-etl-process-dialog.source'),
                      value: 'source'
                    },
                    {
                      text: $t('jdbc-etl-process-dialog.target'),
                      value: 'target'
                    },
                    {
                      text: $t('common.actions'),
                      value: 'actions',
                      align: 'center',
                      sortable: false
                    }
                  ]"
                  :items="attributes"
                  dense
                >
                  <template v-slot:top>
                    <v-toolbar flat>
                      <v-toolbar-title
                        >{{ $t("jdbc-etl-process-dialog.mappings") }}
                        <v-tooltip bottom max-width="600px">
                          <template v-slot:activator="{ on, attrs }">
                            <v-icon v-bind="attrs" v-on="on">help</v-icon>
                          </template>
                          <span>{{ $t("jdbc-etl-process-dialog.mappings-hint") }}</span>
                        </v-tooltip>
                      </v-toolbar-title>
                      <v-spacer></v-spacer>
                      <v-btn class="mb-2" color="primary" dark @click="addAttribute">
                        {{ $t("jdbc-etl-process-dialog.new-mapping") }}
                      </v-btn>
                    </v-toolbar>
                  </template>
                  <template v-slot:[`item.actions`]="{ item }">
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <v-btn icon color="primary" dark v-bind="attrs" v-on="on">
                          <v-icon small @click="removeAttribute(item)">delete_forever</v-icon>
                        </v-btn>
                      </template>
                      <span>{{ $t("common.remove") }}</span>
                    </v-tooltip>
                  </template>
                  <template v-slot:[`item.source`]="{ item }">
                    <v-edit-dialog :return-value.sync="item.source">
                      {{ item.source }}
                      <template v-slot:input>
                        <v-text-field v-model="item.source" :rules="[notEmpty]"></v-text-field>
                      </template>
                    </v-edit-dialog>
                  </template>
                  <template v-slot:[`item.target`]="{ item }">
                    <v-edit-dialog :return-value.sync="item.target">
                      {{ item.target }}
                      <template v-slot:input>
                        <v-text-field v-model="item.target" :rules="[notEmpty]"></v-text-field>
                      </template>
                    </v-edit-dialog>
                  </template>
                </v-data-table>
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

        <v-btn color="secondary" text @click.stop="testEtlProcess">
          {{ $t("common.test") }}
        </v-btn>

        <v-btn :loading="isSubmitting" color="primary" data-testid="submit" text @click.stop="saveEtlProcess">
          {{ $t("common.save") }}
        </v-btn>
      </v-card-actions>
    </v-card>
    <XESPreviewDialog v-model="pqlDialog" :data-store-id="dataStoreId" :query="pqlQuery" @cancelled="pqlDialogClosed" />
  </v-dialog>
</template>

<script lang="ts">
import { DataConnector } from "@/models/DataStore";
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import App from "@/App.vue";
import DataStoreService from "../../services/DataStoreService";
import { isPositiveIntegerRule, notEmptyRule } from "@/utils/FormValidationRules";
import { AbstractEtlProcess, EtlProcessInfo, EtlProcessType, JdbcEtlColumnConfiguration, JdbcEtlProcess } from "@/openapi";
import PQL from "@/views/PQL.vue";
import XESPreviewDialog from "@/components/XESPreviewDialog.vue";
import JdbcEtlProcessConfiguration from "@/models/JdbcEtlProcessConfiguration";

@Component({
  components: { XESPreviewDialog, PQL }
})
export default class JdbcEtlProcessDialog extends Vue {
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
  readonly initialConfig: JdbcEtlProcess | null = null;

  readonly pollingMaxRetriesCount = 10;
  readonly pollingRetriesDelay = 1000;

  readonly jdbcTypes = [
    //numeric values for the corresponding types from java.sql.Types
    { name: "VARCHAR", id: 12 },
    { name: "BIGINT", id: -5 },
    { name: "NUMERIC", id: 2 }
  ];

  selectedDataConnectorId: string | null = null;
  //refreshRules = [(v:string):string|boolean => this.batch?true:isPositiveIntegerRule(v, this.$t("jdbc-etl-process-dialog.validation.not-a-positive-integer").toString())]
  refreshRules = [this.refreshRule];
  lastEventExternalIdRules = [this.lastEventExternalIdRule];

  lastEventExternalIdRule(v: string): string | boolean {
    if (this.batch) return true;
    return this.notEmpty(v);
  }

  async saveEtlProcess() {
    //TODO ensure the column definitions are at least non-empty
    try {
      this.validateForm();
      this.isSubmitting = true;
      const etlProcess = await this.dataStoreService.saveEtlProcess(
        this.dataStoreId!,
        this.processName,
        EtlProcessType.Jdbc,
        this.selectedDataConnectorId!,
        this.createJdbcConfiguration(),
        this.initialConfig?.id
      );
      this.app.success(`${this.$t("common.saving.success")}`);
      this.$emit("submitted", etlProcess);
      this.resetForm();
    } catch (error) {
      console.log(error);
      this.app.error(`${this.$t("common.saving.failure")}`);
    } finally {
      this.isSubmitting = false;
    }
  }

  addAttribute() {
    this.attributes.push({ source: "", target: "" });
  }

  isSubmitting = false;

  processName = "";
  query = "";
  batch = false;
  refresh = 60;
  enabled = false;
  traceIdSource = "";
  traceIdTarget = "";
  eventIdSource = "";
  eventIdTarget = "";
  attributes: Array<JdbcEtlColumnConfiguration> = [];
  lastEventExternalId: string | undefined = "";
  lastEventExternalIdType: number | undefined = this.jdbcTypes[0].id;

  isTesting = false;
  pqlDialog = false;
  pqlQuery = "";
  etlProcess: AbstractEtlProcess | undefined = undefined;
  etlProcessInfo: EtlProcessInfo | undefined = undefined;

  @Watch("initialConfig")
  setInitialConfig() {
    const etl = this.initialConfig;
    const cfg = etl?.configuration;

    console.log(etl);

    this.processName = etl?.name || "";
    this.selectedDataConnectorId = etl?.dataConnectorId || "";
    this.query = cfg?.query || this.query;
    this.enabled = cfg?.enabled || false;
    this.batch = cfg?.batch || false;
    this.traceIdSource = cfg?.traceId.source || this.traceIdSource;
    this.traceIdTarget = cfg?.traceId.target || this.traceIdTarget;
    this.eventIdSource = cfg?.eventId.source || this.eventIdSource;
    this.eventIdTarget = cfg?.eventId.target || this.eventIdTarget;
    this.attributes = cfg?.attributes || this.attributes;
    this.refresh = cfg?.refresh || this.refresh;
    this.lastEventExternalId = cfg?.lastEventExternalId;
    this.lastEventExternalIdType = cfg?.lastEventExternalIdType;
  }

  removeAttribute(attr: JdbcEtlColumnConfiguration) {
    const idx = this.attributes.indexOf(attr, 0);
    if (idx >= 0) {
      this.attributes.splice(idx, 1);
    }
  }

  /**
   * Remove the sampling ETL process and the resulting log after they outlive their usefulness
   */
  async cleanup() {
    if (this.dataStoreId !== undefined && this.etlProcess !== undefined && this.etlProcess.id !== undefined) {
      if (this.etlProcessInfo?.logIdentityId !== undefined) await this.dataStoreService.removeLog(this.dataStoreId, this.etlProcessInfo.logIdentityId);
      await this.dataStoreService.removeEtlProcess(this.dataStoreId, this.etlProcess.id);
    }
    this.etlProcessInfo = undefined;
    this.etlProcess = undefined;
  }

  private resetForm() {
    (this.$refs.queryForm as HTMLFormElement)?.reset();
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  /**
   * Called after the PQL dialog is closed
   */
  async pqlDialogClosed() {
    this.pqlDialog = false;
    await this.cleanup();
  }

  /**
   * Create a sampling JDBC ETL process and display errors or the sample log. Remove the process and the log afterwards.
   */
  async testEtlProcess() {
    try {
      if (this.isTesting) return;
      this.isTesting = true;
      this.validateForm();
      this.etlProcess = await this.dataStoreService.createSamplingJdbcEtlProcess(
        this.dataStoreId!,
        this.processName,
        this.selectedDataConnectorId!,
        this.createJdbcConfiguration()
      );
      if (this.etlProcess.id == null) return;
      this.etlProcessInfo = undefined;
      for (let i = 0; i < this.pollingMaxRetriesCount; ++i) {
        await this.delay(this.pollingRetriesDelay);
        const tmp = await this.dataStoreService.getEtlProcessInfo(this.dataStoreId!, this.etlProcess.id);
        if (tmp.lastExecutionTime !== undefined || (tmp.errors !== undefined && tmp.errors.length > 0)) {
          this.etlProcessInfo = tmp;
          break;
        }
      }
      if (this.etlProcessInfo !== undefined) {
        if (this.etlProcessInfo.errors !== undefined && this.etlProcessInfo.errors.length >= 1) {
          this.app.error(this.etlProcessInfo.errors[0].message);
        } else {
          this.pqlQuery = "where log:identity:id=" + this.etlProcessInfo.logIdentityId;
          this.pqlDialog = true;
        }
      }
    } catch (error) {
      console.log(error);
      this.app.error(`${this.$t("common.testing.failure")}`);
    } finally {
      if (!this.pqlDialog) await this.cleanup();
      this.isTesting = false;
    }
  }

  async delay(milliseconds: number) {
    return new Promise<void>((resolve) => {
      setTimeout(resolve, milliseconds);
    });
  }

  private notEmpty(v: string): string | boolean {
    return notEmptyRule(v, this.$t("jdbc-etl-process-dialog.validation.non-empty-field").toString());
  }

  private refreshRule(v: string): string | boolean {
    if (this.batch) return true;
    return isPositiveIntegerRule(v, this.$t("jdbc-etl-process-dialog.validation.not-a-positive-integer").toString());
  }

  private createJdbcConfiguration(): JdbcEtlProcessConfiguration {
    const cfg = {
      query: this.query,
      enabled: this.enabled,
      batch: this.batch,
      traceId: { source: this.traceIdSource, target: this.traceIdTarget },
      eventId: { source: this.eventIdSource, target: this.traceIdTarget },
      attributes: this.attributes
    } as JdbcEtlProcessConfiguration;
    if (!this.batch) {
      cfg.refresh = this.refresh;
      cfg.lastEventExternalId = this.lastEventExternalId;
      cfg.lastEventExternalIdType = this.lastEventExternalIdType;
    }
    return cfg;
  }

  private validateForm() {
    if (!(this.$refs.queryForm as HTMLFormElement).validate()) throw new Error("The provided data is invalid");
    if (this.dataStoreId == null || this.selectedDataConnectorId == null) throw new Error("Data store/data connector not selected");
    for (let a of this.attributes) {
      if (this.notEmpty(a.source) != true) throw new Error("Attribute with an empty source column");
      if (this.notEmpty(a.target) != true) throw new Error("Attribute with an empty target attribute");
    }
  }
}
</script>
