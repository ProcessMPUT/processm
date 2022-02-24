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
                        v-model="traceIdSource"
                        :label="$t('add-jdbc-etl-process-dialog.traceIdSource')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                    <v-text-field
                        v-model="eventIdSource"
                        :label="$t('add-jdbc-etl-process-dialog.eventIdSource')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                  </v-col>
                  <v-col>
                    <v-text-field
                        v-model="traceIdTarget"
                        :label="$t('add-jdbc-etl-process-dialog.traceIdTarget')"
                        :rules="[notEmpty]"
                    ></v-text-field>
                    <v-text-field
                        v-model="eventIdTarget"
                        :label="$t('add-jdbc-etl-process-dialog.eventIdTarget')"
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
import {DataConnector} from "@/models/DataStore";
import Vue from "vue";
import {Component, Inject, Prop} from "vue-property-decorator";
import App from "@/App.vue";
import DataStoreService from "../../services/DataStoreService";
import {isDefinedRule, isPositiveIntegerRule, notEmptyRule} from "@/utils/FormValidationRules";
import {EtlProcessType} from "@/models/EtlProcess";
import {JdbcEtlColumnConfiguration} from "@/openapi";

@Component
export default class AddJdbcEtlProcessDialog extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Prop({default: false})
  readonly value!: boolean;
  @Prop()
  readonly dataStoreId?: string;
  @Prop()
  readonly dataConnectors?: DataConnector[];


  selectedDataConnectorId: string | null = null;

  private notEmpty(v: string): string | boolean {
    return notEmptyRule(
        v,
        this.$t("add-jdbc-etl-process-dialog.validation.non-empty-field").toString()
    );
  }

  dataConnectorRules = [
    (v: DataConnector | undefined): string | boolean => {
      return isDefinedRule(
          v,
          this.$t(
              "add-jdbc-etl-process-dialog.validation.data-connector-not-specified"
          ).toString()
      );
    }
  ];
  refreshRules = [
    (v: string): string | boolean =>
        this.batch ||
        isPositiveIntegerRule(
            v,
            this.$t(
                "add-jdbc-etl-process-dialog.validation.not-a-positive-integer"
            ).toString()
        )
  ];

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

  async createEtlProcess() {
    //TODO ensure the column definitions are at least non-empty
    console.log("createEtlProcess 1")
    if (!(this.$refs.queryForm as HTMLFormElement).validate())
      throw new Error("The provided data is invalid");
    console.log("createEtlProcess 2")
    if (this.dataStoreId == null || this.selectedDataConnectorId == null)
      return;
    console.log("createEtlProcess 3")
    try {
      this.isSubmitting = true;
      console.log("createEtlProcess 4")
      const etlProcess = await this.dataStoreService.createEtlProcess(
          this.dataStoreId,
          this.processName,
          EtlProcessType.Jdbc,
          this.selectedDataConnectorId,
          {
            query: this.query,
            refresh: this.refresh,
            enabled: this.enabled,
            batch: this.batch,
            traceId: {source: this.traceIdSource, target: this.traceIdTarget},
            eventId: {source: this.eventIdSource, target: this.traceIdTarget},
            aux: this.attributes
          }
      );
      console.log("createEtlProcess 5")
      this.app.success(`${this.$t("common.saving.success")}`);
      console.log("createEtlProcess 6")
      this.$emit("submitted", etlProcess);
      console.log("createEtlProcess 7")
      this.resetForm();
      console.log("createEtlProcess 8")
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

  removeAttribute(attr:JdbcEtlColumnConfiguration) {
    const idx = this.attributes.indexOf(attr, 0)
    if(idx >= 0) {
      this.attributes.splice(idx, 1)
    }
  }
}
</script>
