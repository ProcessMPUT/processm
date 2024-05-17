<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="40%" max-height="80%" @keydown.esc="cancel">
    <v-card>
      <v-card-title class="headline">
        {{ $t("process-details-dialog.title", { name: this.etlProcess !== null && this.etlProcess !== undefined ? this.etlProcess.name : "" }) }}
      </v-card-title>
      <v-card-text>
        <v-expansion-panels flat multiple :value="[0, 1, 2, 3]">
          <v-expansion-panel>
            <v-expansion-panel-header>
              <section>{{ $t("common.summary") }}</section>
            </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-simple-table v-show="info != null">
                <template v-slot:default>
                  <thead>
                    <tr>
                      <th class="text-left">{{ $t("common.name") }}</th>
                      <th class="text-left">{{ $t("common.value") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>{{ $t("process-details-dialog.log-identity-id") }}</td>
                      <td>{{ info != null ? info.logIdentityId : "" }}</td>
                    </tr>
                    <tr>
                      <td>{{ $t("common.type") }}</td>
                      <td>{{ etlProcess != null ? etlProcess.type : "" }}</td>
                    </tr>
                    <tr>
                      <td>{{ $t("data-stores.etl.last-execution-time") }}</td>
                      <td>{{ etlProcess != null ? etlProcess.lastExecutionTime : "" }}</td>
                    </tr>
                  </tbody>
                </template>
              </v-simple-table>
            </v-expansion-panel-content>
          </v-expansion-panel>
          <v-expansion-panel>
            <v-expansion-panel-header>
              <section>{{ $t("process-details-dialog.errors") }}</section>
            </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-data-table
                v-show="info != null"
                :headers="[
                  { text: $t('process-details-dialog.message'), value: 'message', width: '30%' },
                  { text: $t('process-details-dialog.exception'), value: 'exception', width: '50%' },
                  { text: $t('process-details-dialog.time'), value: 'time', width: '20%' }
                ]"
                :items="info ? info.errors : []"
                dense
              >
                <template v-slot:[`item.exception`]="{ item }">
                  <v-textarea :readonly="true" :value="item.exception" class="exception" />
                </template>
              </v-data-table>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn color="secondary" text @click.stop="cancel" name="btn-process-details-dialog-cancel">
          {{ $t("common.close") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style>
.exception textarea {
  font-family: monospace;
  white-space: pre;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import { EtlProcess, EtlProcessInfo } from "@/openapi";
import App from "@/App.vue";
import LogsService from "@/services/LogsService";
import DataStoreService from "@/services/DataStoreService";

@Component
export default class ProcessDetailsDialog extends Vue {
  @Inject() app!: App;
  @Inject() logsService!: LogsService;
  @Inject() dataStoreService!: DataStoreService;
  @Prop()
  readonly dataStoreId: string | undefined;
  @Prop()
  readonly etlProcess: EtlProcess | undefined | null;
  @Prop({ default: false })
  readonly value!: boolean;

  private info: EtlProcessInfo | null = null;

  @Watch("value")
  componentVisibilityChanged(isVisible: boolean) {
    if (isVisible) this.refresh();
    else this.info = null;
  }

  async refresh() {
    if (this.dataStoreId != undefined && this.etlProcess !== undefined && this.etlProcess !== null) {
      this.info = await this.dataStoreService.getEtlProcessInfo(this.dataStoreId, this.etlProcess.id!);
    }
  }

  cancel() {
    this.$emit("cancelled");
  }
}
</script>
