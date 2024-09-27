<template>
  <v-dialog v-model="value" persistent fullscreen hide-overlay transition="dialog-bottom-transition">
    <v-card>
      <v-toolbar dark color="primary">
        <v-btn icon dark @click="closeConfiguration" name="btn-close-configuration">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer />
        <v-toolbar-title
          >{{ dataStoreName }}
          <v-tooltip bottom max-width="600px">
            <template v-slot:activator="{ on, attrs }">
              <v-icon color="accent" v-bind="attrs" v-on="on">help</v-icon>
            </template>
            <span>{{ $t("data-stores.page-hint") }}</span>
          </v-tooltip>
        </v-toolbar-title>
        <v-spacer />
      </v-toolbar>

      <v-expansion-panels flat multiple :value="[0, 1, 2, 3]">
        <v-expansion-panel>
          <v-expansion-panel-header>
            <section>{{ $t("common.summary") }}</section>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular v-show="isLoadingDataStore" color="primary" indeterminate></v-progress-circular>
            </div>
            <v-simple-table v-show="!isLoadingDataStore">
              <template v-slot:default>
                <thead>
                  <tr>
                    <th class="text-left">{{ $t("common.name") }}</th>
                    <th class="text-left">{{ $t("common.value") }}</th>
                  </tr>
                </thead>
                <tbody v-if="dataStore != null">
                  <tr>
                    <td>ID</td>
                    <td>{{ dataStore.id }}</td>
                  </tr>
                  <tr>
                    <td>{{ $t("common.name") }}</td>
                    <td>
                      {{ dataStore.name }}
                      <v-tooltip bottom>
                        <template v-slot:activator="{ on, attrs }">
                          <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-data-connector-rename">
                            <v-icon small @click="renameDataStoreDialog = true">edit</v-icon>
                          </v-btn>
                        </template>
                        <span>{{ $t("common.rename") }}</span>
                      </v-tooltip>
                    </td>
                  </tr>
                  <tr>
                    <td>{{ $t("common.created-at") }}</td>
                    <td>{{ dataStore.createdAt }}</td>
                  </tr>
                  <tr>
                    <td>{{ $t("data-stores.size") }}</td>
                    <td>{{ dataStore?.size != null ? (dataStore.size / 1024 / 1024).toFixed(2) : "?" }} MB</td>
                  </tr>
                </tbody>
              </template>
            </v-simple-table>
          </v-expansion-panel-content>
        </v-expansion-panel>
        <v-expansion-panel>
          <v-expansion-panel-header>
            <template v-slot:default="{ open }">
              <div>
                {{ $t("data-stores.logs") }}
                <v-tooltip bottom max-width="600px">
                  <template v-slot:activator="{ on, attrs }">
                    <v-icon v-bind="attrs" v-on="on">help</v-icon>
                  </template>
                  <span>{{ $t("data-stores.logs-hint") }}</span>
                </v-tooltip>
                <v-fade-transition leave-absolute>
                  <div v-if="open" class="add-button-group">
                    <v-tooltip bottom max-width="600px">
                      <template v-slot:activator="{ on, attrs }">
                        <div v-on="on" class="d-inline-block" @click.stop>
                          <v-btn class="mx-2" color="primary" v-bind="attrs" @click.stop="fileUploadDialog = true">
                            {{ $t("data-stores.upload-xes-file.title") }}
                            <v-progress-circular v-show="isUploading" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
                          </v-btn>
                        </div>
                      </template>
                      <span>{{ $t("data-stores.upload-xes-file.description") }}</span>
                    </v-tooltip>
                  </div>
                  <span v-else> ({{ xesLogItems.length }}) </span>
                </v-fade-transition>
              </div>
            </template>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular v-show="isLoadingXesLogs" color="primary" indeterminate></v-progress-circular>
            </div>
            <xes-data-table
              v-show="!isLoadingXesLogs"
              :items="xesLogItems"
              :headers="xesLogHeaders"
              :is-fold="false"
              :selectable="true"
              :is-expanded="false"
              :loading="isLoadingXesLogs"
              :keep-tree-expand="false"
              :empty-text="$t('common.no-data')"
              :disable-pagination="false"
              :disable-hierarchy="true"
              :items-per-page="5"
              :items-per-page-options="[1, 5, -1]"
              :removable="true"
              children-prop="_children"
              @removed="removeLogs"
            />
          </v-expansion-panel-content>
        </v-expansion-panel>
        <v-expansion-panel class="data-connectors">
          <v-expansion-panel-header>
            <template v-slot:default="{ open }">
              <div>
                {{ $t("data-stores.data-connectors") }}
                <v-tooltip bottom max-width="600px">
                  <template v-slot:activator="{ on, attrs }">
                    <v-icon v-bind="attrs" v-on="on">help</v-icon>
                  </template>
                  <span>{{ $t("data-stores.data-connectors-hint") }}</span>
                </v-tooltip>
                <v-fade-transition leave-absolute>
                  <div v-if="open" class="add-button-group">
                    <v-btn
                      class="mx-2"
                      color="primary"
                      @click.stop="
                        dataConnectorToEdit = null;
                        dataConnectorDialog = true;
                      "
                      name="btn-add-data-connector"
                    >
                      {{ $t("data-stores.add-data-connector") }}
                    </v-btn>
                  </div>
                  <span v-else> ({{ dataConnectors.length }}) </span>
                </v-fade-transition>
              </div>
            </template>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular v-show="isLoadingDataConnectors" color="primary" indeterminate></v-progress-circular>
            </div>
            <v-data-table
              v-show="!isLoadingDataConnectors"
              dense
              show-expand
              :headers="[
                {
                  text: $t('common.name'),
                  value: 'name'
                },
                {
                  text: $t('data-stores.last-connection-status'),
                  value: 'lastConnectionStatus'
                },
                {
                  text: $t('data-stores.last-connection-status-timestamp'),
                  value: 'lastConnectionStatusTimestamp'
                },
                {
                  text: $t('common.actions'),
                  value: 'actions',
                  align: 'center',
                  sortable: false
                }
              ]"
              :items="dataConnectors"
            >
              <template v-slot:[`item.lastConnectionStatus`]="{ item }">
                <v-icon v-if="item.lastConnectionStatus">check_circle_outline</v-icon>
                <v-icon v-else>error_outline</v-icon>
              </template>
              <template v-slot:[`item.lastConnectionStatusTimestamp`]="{ item }">
                <v-icon v-if="item.lastConnectionStatusTimestamp == null">all_inclusive</v-icon>
                <span v-else>{{ item.lastConnectionStatusTimestamp }}</span>
              </template>
              <template v-slot:[`item.actions`]="{ item }">
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-data-connector-rename">
                      <v-icon small @click="dataConnectorIdToRename = item.id">abc</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("common.rename") }}</span>
                </v-tooltip>
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-data-connector-edit">
                      <v-icon
                        small
                        @click="
                          dataConnectorToEdit = item;
                          dataConnectorDialog = true;
                        "
                        >edit
                      </v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("common.edit") }}</span>
                </v-tooltip>
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-data-connector-remove">
                      <v-icon small @click="removeDataConnector(item)">delete_forever</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("common.remove") }}</span>
                </v-tooltip>
              </template>
              <template v-slot:expanded-item="{ headers, item }">
                <td :colspan="headers.length">
                  <ul v-if="Object.keys(item.properties).length > 0">
                    <li v-for="(value, name, index) in item.properties" :key="index">{{ capitalize(name.replace("-", " ")) }}: {{ value }}</li>
                  </ul>
                  <span v-else>
                    {{ $t("data-stores.data-connector-no-properties") }}
                  </span>
                </td>
              </template>
            </v-data-table>
          </v-expansion-panel-content>
        </v-expansion-panel>
        <v-expansion-panel>
          <v-expansion-panel-header>
            <template v-slot:default="{ open }">
              <div>
                {{ $t("data-stores.etl-processes") }}
                <v-tooltip bottom max-width="600px">
                  <template v-slot:activator="{ on, attrs }">
                    <v-icon v-bind="attrs" v-on="on">help</v-icon>
                  </template>
                  <span>{{ $t("data-stores.etl-processes-hint") }}</span>
                </v-tooltip>
                <v-fade-transition leave-absolute>
                  <div v-if="open" class="add-button-group">
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <div v-on="on" class="d-inline-block" @click.stop>
                          <v-btn
                            color="primary"
                            class="mx-2"
                            :disabled="dataConnectors.length == 0"
                            @click.stop="automaticEtlProcessDialogVisible = true"
                            v-bind="attrs"
                            name="btn-add-automatic-etl-process"
                          >
                            {{ $t("data-stores.automatic-etl-process.title") }}
                          </v-btn>
                        </div>
                      </template>
                      <span>{{
                        dataConnectors.length == 0 ? $t("data-stores.data-connector-required") : $t("data-stores.automatic-etl-process.description")
                      }}</span>
                    </v-tooltip>
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <div v-on="on" class="d-inline-block" @click.stop>
                          <v-btn
                            color="primary"
                            class="mx-2"
                            :disabled="dataConnectors.length == 0"
                            @click.stop="jdbcEtlProcessDialogVisible = true"
                            v-bind="attrs"
                          >
                            {{ $t("data-stores.jdbc-etl-process.title") }}
                          </v-btn>
                        </div>
                      </template>
                      <span>{{ dataConnectors.length == 0 ? $t("data-stores.data-connector-required") : $t("data-stores.jdbc-etl-process.description") }}</span>
                    </v-tooltip>
                  </div>
                  <span v-else> ({{ etlProcesses.length }}) </span>
                </v-fade-transition>
              </div>
            </template>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular v-show="isLoadingEtlProcesses" color="primary" indeterminate></v-progress-circular>
            </div>
            <v-data-table
              v-show="!isLoadingEtlProcesses"
              dense
              :headers="[
                {
                  text: $t('common.name'),
                  value: 'name'
                },
                {
                  text: $t('data-stores.etl.data-connector'),
                  value: 'dataConnectorId'
                },
                {
                  text: $t('common.type'),
                  value: 'type'
                },
                {
                  text: $t('data-stores.etl.last-execution-time'),
                  value: 'lastExecutionTime'
                },
                {
                  text: $t('common.actions'),
                  value: 'actions',
                  align: 'center',
                  sortable: false
                }
              ]"
              :items="etlProcesses"
            >
              <template v-slot:[`item.actions`]="{ item }">
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on">
                      <v-icon small @click="changeEtlActivationStatus(item)">
                        {{ item.isActive ? "pause_circle_outline" : "play_circle_outline" }}
                      </v-icon>
                    </v-btn>
                  </template>
                  <span>{{ item.isActive ? $t("common.deactivate") : $t("common.activate") }}</span>
                </v-tooltip>
                <v-tooltip bottom v-if="canBeTriggered(item)">
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-trigger-jdbc-etl-process">
                      <v-icon small @click="trigger(item)">replay</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("data-stores.jdbc-etl-process.trigger") }}</span>
                </v-tooltip>
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on">
                      <v-icon small @click="editEtlProcess(item)">edit</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("common.edit") }}</span>
                </v-tooltip>
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn color="primary" dark icon v-bind="attrs" v-on="on">
                      <v-icon small @click="removeEtlProcess(item)">delete_forever</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("common.remove") }}</span>
                </v-tooltip>
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-show-etl-process-details">
                      <v-icon small @click="showEtlProcessDetails(item)">info</v-icon>
                    </v-btn>
                  </template>
                  <span>{{ $t("common.details") }}</span>
                </v-tooltip>
              </template>
              <template v-slot:[`item.dataConnectorId`]="{ item }">
                {{ getDataConnectorName(item.dataConnectorId) }}
              </template>
            </v-data-table>
          </v-expansion-panel-content>
        </v-expansion-panel>
      </v-expansion-panels>
    </v-card>
    <data-connector-dialog
      v-model="dataConnectorDialog"
      :data-store-id="dataStoreId"
      @cancelled="dataConnectorDialog = false"
      @submitted="dataConnectorsModified"
      :initial-connector="dataConnectorToEdit"
    />
    <automatic-etl-process-dialog
      v-model="automaticEtlProcessDialogVisible"
      :data-store-id="dataStoreId"
      :data-connectors="dataConnectors"
      :initial-config="etlProcessToEdit"
      @cancelled="
        automaticEtlProcessDialogVisible = false;
        etlProcessToEdit = null;
      "
      @submitted="handleEtlProcessChange"
    />
    <file-upload-dialog v-model="fileUploadDialog" :sizeLimit="fileSizeLimit" @cancelled="fileUploadDialog = false" @submitted="submitFile" />
    <rename-dialog
      :value="dataConnectorIdToRename != null"
      :old-name="dataConnectorNameToRename"
      @cancelled="dataConnectorIdToRename = null"
      @submitted="renameDataConnector"
    />
    <rename-dialog :value="renameDataStoreDialog" :old-name="dataStore?.name" @cancelled="renameDataStoreDialog = false" @submitted="renameDataStore" />
    <jdbc-etl-process-dialog
      v-model="jdbcEtlProcessDialogVisible"
      :data-store-id="dataStoreId"
      :dataConnectors="dataConnectors"
      :initial-config="etlProcessToEdit"
      @cancelled="
        jdbcEtlProcessDialogVisible = false;
        etlProcessToEdit = null;
      "
      @submitted="handleEtlProcessChange"
    />
    <process-details-dialog
      :value="processDetailsDialogEtlProcess !== null"
      :data-store-id="dataStoreId"
      :etl-process="processDetailsDialogEtlProcess"
      @cancelled="processDetailsDialogEtlProcess = null"
    ></process-details-dialog>
  </v-dialog>
</template>

<style scoped>
.add-button-group {
  text-align: end;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import DataStoreService from "@/services/DataStoreService";
import DataStore, { DataConnector } from "@/models/DataStore";
import DataConnectorDialog from "@/components/data-connections/DataConnectorDialog.vue";
import LogsService from "@/services/LogsService";
import { waitForRepaint } from "@/utils/waitForRepaint";
import XesProcessor, { LogItem } from "@/utils/XesProcessor";
import FileUploadDialog from "@/components/FileUploadDialog.vue";
import RenameDialog from "@/components/RenameDialog.vue";
import AutomaticEtlProcessDialog from "@/components/etl/AutomaticEtlProcessDialog.vue";
import { capitalize } from "@/utils/StringCaseConverter";
import App from "@/App.vue";
import JdbcEtlProcessDialog from "@/components/etl/JdbcEtlProcessDialog.vue";
import ProcessDetailsDialog from "@/components/etl/ProcessDetailsDialog.vue";
import { EtlProcess, EtlProcessType, JdbcEtlProcess } from "@/openapi";
import LogTable from "@/components/LogTable.vue";

const XesDataTable = () => import("@/components/XesDataTable.vue");

@Component({
  components: {
    LogTable,
    ProcessDetailsDialog,
    JdbcEtlProcessDialog: JdbcEtlProcessDialog,
    DataConnectorDialog,
    XesDataTable,
    FileUploadDialog,
    RenameDialog,
    AutomaticEtlProcessDialog: AutomaticEtlProcessDialog
  }
})
export default class DataStoreConfiguration extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Inject() logsService!: LogsService;
  private readonly xesProcessor = new XesProcessor();
  dataConnectorDialog = false;
  automaticEtlProcessDialogVisible = false;
  jdbcEtlProcessDialogVisible = false;
  fileUploadDialog = false;
  isUploading = false;
  dataConnectors: DataConnector[] = [];
  etlProcesses: EtlProcess[] = [];
  dataStore: DataStore | null = null;
  xesLogHeaders: string[] = [];
  xesLogItems: LogItem[] = [];
  isLoadingXesLogs = false;
  isLoadingDataStore = false;
  isLoadingDataConnectors = false;
  isLoadingEtlProcesses = false;
  dataConnectorIdToRename: string | null = null;
  capitalize = capitalize;
  processDetailsDialogEtlProcess: EtlProcess | null = null;
  etlProcessToEdit: EtlProcess | null = null;
  renameDataStoreDialog = false;
  dataConnectorToEdit: DataConnector | null = null;

  @Prop({ default: false })
  readonly value!: boolean;

  @Prop({ default: null })
  readonly dataStoreId!: string | null;

  private readonly getLogsQuery = "select log:concept:name, log:identity:id, log:lifecycle:model";
  private readonly fileSizeLimit = this.app.config.maxUploadSize;

  get dataConnectorNameToRename(): string | null {
    return this.dataConnectors.find((connector) => connector.id == this.dataConnectorIdToRename)?.name || null;
  }

  @Watch("value")
  componentVisibilityChanged(isVisible: boolean) {
    if (!isVisible) return;
    if (this.dataStoreId == null) return this.app.error(this.$t("data-stores.data-store-not-found").toString());

    this.loadDataStore();
    this.loadXesLogs();
    this.loadDataConnectors();
    this.loadEtlProcesses();
  }

  async loadDataStore() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingDataStore = true;
      this.dataStore = await this.dataStoreService.getDataStore(this.dataStoreId);
    } finally {
      this.isLoadingDataStore = false;
    }
  }

  async loadDataConnectors() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingDataConnectors = true;
      this.dataConnectors = await this.dataStoreService.getDataConnectors(this.dataStoreId);
    } finally {
      this.isLoadingDataConnectors = false;
    }
  }

  async loadEtlProcesses() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingEtlProcesses = true;
      this.etlProcesses = await this.dataStoreService.getEtlProcesses(this.dataStoreId);
    } finally {
      this.isLoadingEtlProcesses = false;
    }
  }

  async removeLogs(logsIds: string[]) {
    try {
      await Promise.all(
        logsIds.map(async (logId) => {
          if (this.dataStoreId == null) return;

          await this.logsService.removeLog(this.dataStoreId, logId);
        })
      );
      this.displaySuccessfulRemovalMessage();
      await this.loadXesLogs();
    } catch (error) {
      this.displayFailedRemovalMessage();
    }
  }

  async loadXesLogs() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingXesLogs = true;
      this.xesLogHeaders = [];
      this.xesLogItems = [];

      const queryResults = await this.logsService.submitUserQuery(this.dataStoreId, this.getLogsQuery);

      await waitForRepaint(async () => {
        const { headers, logItems } = this.xesProcessor.extractLogItemsFromLogScope(queryResults);
        this.xesLogHeaders = headers;

        for (const item of logItems) {
          await waitForRepaint(() => {
            this.xesLogItems.push(item);
          });
        }
      });
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isLoadingXesLogs = false;
    }
  }

  async submitFile(file: File): Promise<void> {
    this.fileUploadDialog = false;
    try {
      if (file == null) return;
      if (file.size > this.fileSizeLimit) return this.app.error(this.$t("data-stores.file-size-exceeded").toString());
      if (this.dataStoreId == null) return this.app.error(this.$t("data-stores.data-store-not-found").toString());

      this.isUploading = true;
      await this.logsService.uploadLogFile(this.dataStoreId, file);
      this.app.success(this.$t("data-stores.successful-file-upload").toString());
      await this.loadXesLogs();
    } catch (err) {
      this.app.error(err);
    } finally {
      this.isUploading = false;
    }
  }

  async dataConnectorsModified() {
    this.dataConnectorDialog = false;
    await this.loadDataConnectors();
  }

  async handleEtlProcessChange() {
    this.automaticEtlProcessDialogVisible = false;
    this.jdbcEtlProcessDialogVisible = false;
    await this.loadEtlProcesses();
  }

  async renameDataConnector(newName: string) {
    try {
      if (
        this.dataStoreId != null &&
        this.dataConnectorIdToRename != null &&
        (await this.dataStoreService.updateDataConnector(this.dataStoreId, this.dataConnectorIdToRename, {
          id: this.dataConnectorIdToRename,
          name: newName,
          properties: {}
        }))
      ) {
        const dataConnector = this.dataConnectors.find((dataConnector) => dataConnector.id == this.dataConnectorIdToRename);

        if (dataConnector != null) dataConnector.name = newName;
      }
    } catch (error) {
      this.app.error(error);
    } finally {
      this.dataConnectorIdToRename = null;
    }
  }

  async removeDataConnector(dataConnector: DataConnector) {
    if (this.dataStoreId == null) return;

    const isRemovalConfirmed = await this.$confirm(
      `${this.$t("data-stores.data-connector-removal-confirmation", {
        dataConnectorName: dataConnector.name
      })}`,
      {
        title: `${this.$t("common.warning")}`,
        buttonTrueText: this.$t("common.yes").toString(),
        buttonFalseText: this.$t("common.no").toString()
      }
    );

    if (!isRemovalConfirmed) return;

    try {
      await this.dataStoreService.removeDataConnector(this.dataStoreId, dataConnector.id);
      this.displaySuccessfulRemovalMessage();
      this.dataConnectors.splice(this.dataConnectors.indexOf(dataConnector), 1);
    } catch (error) {
      this.displayFailedRemovalMessage();
    }
  }

  async editEtlProcess(etlProcess: EtlProcess) {
    if (this.dataStoreId == null) return;

    this.etlProcessToEdit = etlProcess;
    switch (etlProcess.type) {
      case EtlProcessType.Automatic:
        this.automaticEtlProcessDialogVisible = true;
        break;
      case EtlProcessType.Jdbc:
        this.jdbcEtlProcessDialogVisible = true;
        break;
      default:
        this.app.error(`Unknown ETL process type: ${etlProcess.type}.`);
        return;
    }
  }

  async removeEtlProcess(etlProcess: EtlProcess) {
    if (this.dataStoreId == null) return;

    const isRemovalConfirmed = await this.$confirm(
      `${this.$t("data-stores.etl.removal-confirmation", {
        etlProcessName: etlProcess.name
      })}`,
      {
        title: `${this.$t("common.warning")}`,
        buttonTrueText: this.$t("common.yes").toString(),
        buttonFalseText: this.$t("common.no").toString()
      }
    );

    if (!isRemovalConfirmed) return;

    try {
      await this.dataStoreService.removeEtlProcess(this.dataStoreId, etlProcess.id!);
      this.displaySuccessfulRemovalMessage();
      this.etlProcesses.splice(this.etlProcesses.indexOf(etlProcess), 1);
    } catch (error) {
      this.displayFailedRemovalMessage();
    }
  }

  async trigger(etlProcess: EtlProcess) {
    if (this.dataStoreId == null) return;
    await this.dataStoreService.triggerEtlProcess(this.dataStoreId, etlProcess.id!);
  }

  canBeTriggered(etlProcess: EtlProcess) {
    if (etlProcess.type != EtlProcessType.Jdbc) return false;
    const cfg = (etlProcess as JdbcEtlProcess)?.configuration;
    if (cfg === undefined) return true;
    return !(cfg.batch && cfg.lastEventExternalId !== undefined);
  }

  closeConfiguration() {
    this.$emit("closed");
  }

  displaySuccessfulRemovalMessage() {
    this.app.success(`${this.$t("common.removal.success")}`);
  }

  displayFailedRemovalMessage() {
    this.app.error(`${this.$t("common.removal.failure")}`);
  }

  async changeEtlActivationStatus(etlProcess: EtlProcess) {
    if (this.dataStoreId == null) return;

    const isConfirmed =
      !etlProcess.isActive ||
      (await this.$confirm(
        `${this.$t("data-stores.etl.deactivation-confirmation", {
          etlProcessName: etlProcess.name
        })}`,
        {
          title: `${this.$t("common.warning")}`,
          buttonTrueText: this.$t("common.yes").toString(),
          buttonFalseText: this.$t("common.no").toString()
        }
      ));

    if (!isConfirmed) return;

    try {
      await this.dataStoreService.changeEtlProcessActivationState(this.dataStoreId, etlProcess.id!, !etlProcess.isActive);
      etlProcess.isActive = !etlProcess.isActive;
      this.app.success(`${this.$t("common.operation-successful")}`);
    } catch (error) {
      this.app.error(error);
    }
  }

  get dataStoreName() {
    return this.dataStore?.name ?? "";
  }

  getDataConnectorName(dataConnectorId: string) {
    return this.dataConnectors.find((dataConnector) => dataConnector.id == dataConnectorId)?.name || "";
  }

  showEtlProcessDetails(etlProcess: EtlProcess) {
    this.processDetailsDialogEtlProcess = etlProcess;
  }

  async renameDataStore(newName: string) {
    try {
      if (this.dataStore == null) return;
      const success = await this.dataStoreService.updateDataStore(this.dataStore.id, {
        id: this.dataStore.id,
        name: newName
      });
      if (!success) return;
      this.dataStore.name = newName;
      this.$emit("changed", this.dataStore);
      this.app.success(`${this.$t("common.operation-successful")}`);
    } catch (error) {
      this.app.error(`${this.$t("common.saving.failure")}`);
    }
    this.renameDataStoreDialog = false;
  }
}
</script>