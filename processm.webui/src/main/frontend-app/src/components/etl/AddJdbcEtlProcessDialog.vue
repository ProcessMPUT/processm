<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("add-jdbc-etl-process-dialog.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form ref="queryForm" lazy-validation>
            <v-row>
              <v-col>
                <v-text-field
                    v-model="processName"
                    :label="$t('common.name')"
                    :rules="[notEmpty]"
                    data-testid="etlConfigurationName"
                ></v-text-field>
                <v-select
                    v-model="selectedDataConnectorId"
                    item-text="name"
                    item-value="id"
                    :items="dataConnectors"
                    :label="$t('data-stores.etl.data-connector')"
                    :rules="[notEmpty]"
                    required
                ></v-select>
                <v-textarea
                    v-model="query"
                    :label="$t('add-jdbc-etl-process-dialog.query')"
                    :rules="[notEmpty]"
                    data-testid="query"
                ></v-textarea>
                <v-switch
                    v-model="batch"
                    :label="$t('add-jdbc-etl-process-dialog.batch')"
                    data-testid="batch"
                ></v-switch>
                <v-text-field
                    v-model="refresh"
                    :label="$t('add-jdbc-etl-process-dialog.refresh')"
                    :disabled="batch"
                    :rules="refreshRules"
                    suffix="seconds"
                    data-testid="refresh"
                ></v-text-field>
                <v-switch
                    v-model="enabled"
                    :label="$t('add-jdbc-etl-process-dialog.enabled')"
                    data-testid="enabled"
                ></v-switch>
                <v-row>
                  <v-col>
                    <v-text-field
                        v-model="lastEventExternalId"
                        :label="$t('add-jdbc-etl-process-dialog.last-event-external-id')"
                        :rules="lastEventExternalIdRules"
                        :disabled="batch"
                    ></v-text-field>
                    <v-text-field
                        v-model="traceIdSource"
                        :label="$t('add-jdbc-etl-process-dialog.trace-id-source')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                    <v-text-field
                        v-model="eventIdSource"
                        :label="$t('add-jdbc-etl-process-dialog.event-id-source')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                  </v-col>
                  <v-col>
                    <v-select
                        v-model="lastEventExternalIdType"
                        item-text="name"
                        item-value="id"
                        :items="jdbcTypes"
                        :label="$t('add-jdbc-etl-process-dialog.last-event-external-id-type')"
                        :rules="lastEventExternalIdRules"
                        :disabled="batch"
                    ></v-select>
                    <v-text-field
                        v-model="traceIdTarget"
                        :label="$t('add-jdbc-etl-process-dialog.trace-id-target')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                    <v-text-field
                        v-model="eventIdTarget"
                        :label="$t('add-jdbc-etl-process-dialog.event-id-target')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                  </v-col>
                </v-row>
                <v-data-table
                    dense
                    :headers="[
                {
                  text: $t('add-jdbc-etl-process-dialog.source'),
                  value: 'source'
                },
                {
                  text: $t('add-jdbc-etl-process-dialog.target'),
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
                >
                  <template v-slot:top>
                    <v-toolbar
                        flat
                    >
                      <v-toolbar-title>{{ $t("add-jdbc-etl-process-dialog.mappings")}}</v-toolbar-title>
                      <v-spacer></v-spacer>
                      <v-btn
                          color="primary"
                          dark
                          class="mb-2"
                          @click="addAttribute"
                      >
                        {{ $t("add-jdbc-etl-process-dialog.new-mapping")}}
                      </v-btn>
                    </v-toolbar>
                  </template>
                  <template v-slot:[`item.actions`]="{ item }">
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <v-btn icon color="primary" dark v-bind="attrs" v-on="on">
                          <v-icon small @click="removeAttribute(item)"
                          >delete_forever
                          </v-icon
                          >
                        </v-btn>
                      </template>
                      <span>{{ $t("common.remove") }}</span>
                    </v-tooltip>
                  </template>
                  <template v-slot:[`item.source`]="{ item }">
                    <v-edit-dialog
                        :return-value.sync="item.source"
                    >
                      {{ item.source }}
                      <template v-slot:input>
                        <v-text-field v-model="item.source" :rules="[notEmpty]"></v-text-field>
                      </template>
                    </v-edit-dialog>
                  </template>
                  <template v-slot:[`item.target`]="{ item }">
                    <v-edit-dialog
                        :return-value.sync="item.target"
                    >
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
    <XESPreviewDialog
        v-model="pqlDialog"
        :data-store-id="dataStoreId"
        :query="pqlQuery"
        @cancelled="pqlDialogClosed"
    />
  </v-dialog>
</template>

<script lang="ts">
import {DataConnector} from "@/models/DataStore";
import Vue from "vue";
import {Component, Inject, Prop} from "vue-property-decorator";
import App from "@/App.vue";
import DataStoreService from "../../services/DataStoreService";
import {isPositiveIntegerRule, notEmptyRule} from "@/utils/FormValidationRules";
import {EtlProcessType} from "@/models/EtlProcess";
import {AbstractEtlProcess, EtlProcessInfo, JdbcEtlColumnConfiguration} from "@/openapi";
import PQL from "@/views/PQL.vue";
import XESPreviewDialog from "@/components/XESPreviewDialog.vue";
import JdbcEtlProcessConfiguration from "@/models/JdbcEtlProcessConfiguration";

@Component({
  components: {XESPreviewDialog, PQL}
})
export default class AddJdbcEtlProcessDialog extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Prop({default: false})
  readonly value!: boolean;
  @Prop()
  readonly dataStoreId?: string;
  @Prop()
  readonly dataConnectors?: DataConnector[];

  readonly pollingMaxRetriesCount = 10;
  readonly pollingRetriesDelay = 1000;

  readonly jdbcTypes = [
    //numeric values for the corresponding types from java.sql.Types
    {name: "VARCHAR", id: 12},
    {name: "BIGINT", id: -5},
    {name: "NUMERIC", id: 2}
  ]

  selectedDataConnectorId: string | null = null;

  private notEmpty(v: string): string | boolean {
    return notEmptyRule(
        v,
        this.$t("add-jdbc-etl-process-dialog.validation.non-empty-field").toString()
    );
  }

  private refreshRule(v: string): string | boolean {
    if (this.batch)
      return true
    return isPositiveIntegerRule(v, this.$t("add-jdbc-etl-process-dialog.validation.not-a-positive-integer").toString())
  }

  //refreshRules = [(v:string):string|boolean => this.batch?true:isPositiveIntegerRule(v, this.$t("add-jdbc-etl-process-dialog.validation.not-a-positive-integer").toString())]
  refreshRules = [this.refreshRule]

  lastEventExternalIdRule(v: string): string | boolean {
    if (this.batch)
      return true
    return this.notEmpty(v)
  }

  lastEventExternalIdRules = [this.lastEventExternalIdRule]

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

  private createJdbcConfiguration(): JdbcEtlProcessConfiguration {
    const cfg = {
      query: this.query,
      enabled: this.enabled,
      batch: this.batch,
      traceId: {source: this.traceIdSource, target: this.traceIdTarget},
      eventId: {source: this.eventIdSource, target: this.traceIdTarget},
      attributes: this.attributes
    } as JdbcEtlProcessConfiguration
    if (!this.batch) {
      cfg.refresh = this.refresh
      cfg.lastEventExternalId = this.lastEventExternalId
      cfg.lastEventExternalIdType = this.lastEventExternalIdType
    }
    return cfg
  }

  async createEtlProcess() {
    //TODO ensure the column definitions are at least non-empty
    try {
      this.validateForm()
      this.isSubmitting = true;
      const etlProcess = await this.dataStoreService.createEtlProcess(
          this.dataStoreId!,
          this.processName,
          EtlProcessType.Jdbc,
          this.selectedDataConnectorId!,
          this.createJdbcConfiguration()
      );
      this.app.success(`${this.$t("common.saving.success")}`);
      this.$emit("submitted", etlProcess);
      this.resetForm();
    } catch (error) {
      console.log(error)
      this.app.error(`${this.$t("common.saving.failure")}`);
    } finally {
      this.isSubmitting = false;
    }
  }

  private resetForm() {
    (this.$refs.queryForm as HTMLFormElement)?.reset();
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  addAttribute() {
    this.attributes.push({"source": "", "target": ""})
  }

  removeAttribute(attr: JdbcEtlColumnConfiguration) {
    const idx = this.attributes.indexOf(attr, 0)
    if (idx >= 0) {
      this.attributes.splice(idx, 1)
    }
  }

  async delay(milliseconds: number) {
    return new Promise<void>((resolve) => {
      setTimeout(resolve, milliseconds);
    });
  }

  /**
   * Remove the sampling ETL process and the resulting log after they outlive their usefulness
   */
  async cleanup() {
    if (this.dataStoreId !== undefined && this.etlProcess !== undefined && this.etlProcess.id !== undefined) {
      if (this.etlProcessInfo?.logIdentityId !== undefined)
        await this.dataStoreService.removeLog(this.dataStoreId, this.etlProcessInfo.logIdentityId)
      await this.dataStoreService.removeEtlProcess(this.dataStoreId, this.etlProcess.id)
    }
    this.etlProcessInfo = undefined
    this.etlProcess = undefined
  }

  /**
   * Called after the PQL dialog is closed
   */
  async pqlDialogClosed() {
    this.pqlDialog = false
    await this.cleanup()
  }

  private validateForm() {
    if (!(this.$refs.queryForm as HTMLFormElement).validate())
      throw new Error("The provided data is invalid");
    if (this.dataStoreId == null || this.selectedDataConnectorId == null)
      throw new Error("Data store/data connector not selected")
    for (let a of this.attributes) {
      if (this.notEmpty(a.source) != true)
        throw new Error("Attribute with an empty source column")
      if (this.notEmpty(a.target) != true)
        throw new Error("Attribute with an empty target attribute")
    }
  }

  /**
   * Create a sampling JDBC ETL process and display errors or the sample log. Remove the process and the log afterwards.
   */
  async testEtlProcess() {
    try {
      if(this.isTesting)
        return;
      this.isTesting = true
      this.validateForm()
      this.etlProcess = await this.dataStoreService.createSamplingJdbcEtlProcess(
          this.dataStoreId!,
          this.processName,
          this.selectedDataConnectorId!,
          this.createJdbcConfiguration()
      );
      if (this.etlProcess.id == null)
        return;
      this.etlProcessInfo = undefined
      for (let i = 0; i < this.pollingMaxRetriesCount; ++i) {
        await this.delay(this.pollingRetriesDelay)
        const tmp = await this.dataStoreService.getEtlProcessInfo(this.dataStoreId!, this.etlProcess.id)
        if (tmp.lastExecutionTime !== undefined || (tmp.errors !== undefined && tmp.errors.length > 0)) {
          this.etlProcessInfo = tmp
          break
        }
      }
      if (this.etlProcessInfo !== undefined) {
        if (this.etlProcessInfo.errors !== undefined && this.etlProcessInfo.errors.length >= 1) {
          this.app.error(this.etlProcessInfo.errors[0].message)
        } else {
          this.pqlQuery = "where log:identity:id=" + this.etlProcessInfo.logIdentityId
          this.pqlDialog = true
        }
      }
    } catch (error) {
      console.log(error)
      this.app.error(`${this.$t("common.testing.failure")}`);
    } finally {
      if (!this.pqlDialog)
        await this.cleanup()
      this.isTesting = false
    }
  }
}
</script>
