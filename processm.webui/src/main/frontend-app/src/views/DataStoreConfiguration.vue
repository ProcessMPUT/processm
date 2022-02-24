<template>
  <v-dialog
    v-model="value"
    fullscreen
    hide-overlay
    transition="dialog-bottom-transition"
  >
    <v-card>
      <v-toolbar dark color="primary">
        <v-btn icon dark @click="closeConfiguration">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer />
        <v-toolbar-title>{{ dataStoreName }}</v-toolbar-title>
        <v-spacer />
      </v-toolbar>

      <v-expansion-panels flat multiple :value="[0, 1, 2, 3]">
        <v-expansion-panel>
          <v-expansion-panel-header>
            <section>{{ $t("common.summary") }}</section>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular
                v-show="isLoadingDataStore"
                indeterminate
                color="primary"
              ></v-progress-circular>
            </div>
            <v-simple-table v-show="!isLoadingDataStore">
              <template v-slot:default>
                <thead>
                  <tr>
                    <th class="text-left">{{ $t("common.name") }}</th>
                    <th class="text-left">{{ $t("common.value") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in dataStoreSummary" :key="item.field">
                    <td>{{ item.field }}</td>
                    <td>{{ item.value }}</td>
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
                <v-fade-transition leave-absolute>
                  <div v-if="open" class="add-button-group">
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <div v-on="on" class="d-inline-block" @click.stop>
                          <v-btn
                            color="primary"
                            class="mx-2"
                            @click.stop="fileUploadDialog = true"
                            v-bind="attrs"
                          >
                            {{ $t("data-stores.upload-xes-file.title") }}
                            <v-progress-circular
                              v-show="isUploading"
                              indeterminate
                              :width="3"
                              :size="20"
                              color="secondary"
                              class="ml-2"
                            ></v-progress-circular>
                          </v-btn>
                        </div>
                      </template>
                      <span>{{
                        $t("data-stores.upload-xes-file.description")
                      }}</span>
                    </v-tooltip>
                  </div>
                  <span v-else> ({{ xesLogItems.length }}) </span>
                </v-fade-transition>
              </div>
            </template>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular
                v-show="isLoadingXesLogs"
                indeterminate
                color="primary"
              ></v-progress-circular>
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
                <v-fade-transition leave-absolute>
                  <div v-if="open" class="add-button-group">
                    <v-btn
                      color="primary"
                      class="mx-2"
                      @click.stop="addDataConnectorDialog = true"
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
              <v-progress-circular
                v-show="isLoadingDataConnectors"
                indeterminate
                color="primary"
              ></v-progress-circular>
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
                <v-icon v-if="item.lastConnectionStatus"
                  >check_circle_outline</v-icon
                >
                <v-icon v-else>error_outline</v-icon>
              </template>
              <template
                v-slot:[`item.lastConnectionStatusTimestamp`]="{ item }"
              >
                <v-icon v-if="item.lastConnectionStatusTimestamp == null"
                  >all_inclusive</v-icon
                >
                <span v-else>{{ item.lastConnectionStatusTimestamp }}</span>
              </template>
              <template v-slot:[`item.actions`]="{ item }">
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on">
                      <v-icon small @click="dataConnectorIdToRename = item.id"
                        >edit</v-icon
                      >
                    </v-btn>
                  </template>
                  <span>{{ $t("common.rename") }}</span>
                </v-tooltip>
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn icon color="primary" dark v-bind="attrs" v-on="on">
                      <v-icon small @click="removeDataConnector(item)"
                        >delete_forever</v-icon
                      >
                    </v-btn>
                  </template>
                  <span>{{ $t("common.remove") }}</span>
                </v-tooltip>
              </template>
              <template v-slot:expanded-item="{ headers, item }">
                <td :colspan="headers.length">
                  <ul v-if="Object.keys(item.properties).length > 0">
                    <li
                      v-for="(value, name, index) in item.properties"
                      :key="index"
                    >
                      {{ capitalize(name.replace("-", " ")) }}: {{ value }}
                    </li>
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
                <v-fade-transition leave-absolute>
                  <div v-if="open" class="add-button-group">
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <div v-on="on" class="d-inline-block" @click.stop>
                          <v-btn
                            color="primary"
                            class="mx-2"
                            :disabled="dataConnectors.length == 0"
                            @click.stop="addAutomaticEtlProcessDialog = true"
                            v-bind="attrs"
                          >
                            {{ $t("data-stores.add-automatic-process.title") }}
                          </v-btn>
                        </div>
                      </template>
                      <span>{{
                        dataConnectors.length == 0
                          ? $t("data-stores.data-connector-required")
                          : $t("data-stores.add-automatic-process.description")
                      }}</span>
                    </v-tooltip>
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <div v-on="on" class="d-inline-block" @click.stop>
                          <v-btn
                            color="primary"
                            class="mx-2"
                            :disabled="dataConnectors.length == 0"
                            @click.stop="addJdbcEtlProcessDialog = true"
                            v-bind="attrs"
                          >
                            {{ $t("data-stores.add-jdbc-etl-process.title") }}
                          </v-btn>
                        </div>
                      </template>
                      <span>{{
                        dataConnectors.length == 0
                          ? $t("data-stores.data-connector-required")
                          : $t("data-stores.add-jdbc-etl-process.description")
                      }}</span>
                    </v-tooltip>
                  </div>
                  <span v-else> ({{ etlProcesses.length }}) </span>
                </v-fade-transition>
              </div>
            </template>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <div class="text-center">
              <v-progress-circular
                v-show="isLoadingEtlProcesses"
                indeterminate
                color="primary"
              ></v-progress-circular>
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
                      <v-icon small @click="removeEtlProcess(item)"
                        >delete_forever</v-icon
                      >
                    </v-btn>
                  </template>
                  <span>{{ $t("common.remove") }}</span>
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
    <add-data-connector-dialog
      v-model="addDataConnectorDialog"
      :data-store-id="dataStoreId"
      @cancelled="addDataConnectorDialog = false"
      @submitted="addDataConnector"
    />
    <add-automatic-etl-process-dialog
      v-model="addAutomaticEtlProcessDialog"
      :data-store-id="dataStoreId"
      :data-connectors="dataConnectors"
      @cancelled="addAutomaticEtlProcessDialog = false"
      @submitted="addAutomaticEtlProcess"
    />
    <file-upload-dialog
      v-model="fileUploadDialog"
      :sizeLimit="fileSizeLimit"
      @cancelled="fileUploadDialog = false"
      @submitted="submitFile"
    />
    <rename-dialog
      :value="dataConnectorIdToRename != null"
      :old-name="dataConnectorNameToRename"
      @cancelled="dataConnectorIdToRename = null"
      @submitted="renameDataConnector"
    />
    <add-jdbc-etl-process-dialog
      v-model="addJdbcEtlProcessDialog"
      :data-store-id="dataStoreId"
      :dataConnectors="dataConnectors"
      @cancelled="addJdbcEtlProcessDialog = false"
      @submitted="addJdbcEtlProcess"
    />
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
import EtlProcess from "@/models/EtlProcess";
import AddDataConnectorDialog from "@/components/data-connections/AddDataConnectorDialog.vue";
import XesDataTable from "@/components/XesDataTable.vue";
import LogsService from "@/services/LogsService";
import { waitForRepaint } from "@/utils/waitForRepaint";
import XesProcessor, { LogItem } from "@/utils/XesProcessor";
import FileUploadDialog from "@/components/FileUploadDialog.vue";
import RenameDialog from "@/components/RenameDialog.vue";
import AddAutomaticEtlProcessDialog from "@/components/etl/AddAutomaticEtlProcessDialog.vue";
import { capitalize } from "@/utils/StringCaseConverter";
import App from "@/App.vue";
import AddJdbcEtlProcessDialog from "@/components/etl/AddJdbcEtlProcessDialog.vue";

@Component({
  components: {
    AddJdbcEtlProcessDialog,
    AddDataConnectorDialog,
    XesDataTable,
    FileUploadDialog,
    RenameDialog,
    AddAutomaticEtlProcessDialog
  }
})
export default class DataStoreConfiguration extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Inject() logsService!: LogsService;
  private readonly xesProcessor = new XesProcessor();
  addDataConnectorDialog = false;
  addAutomaticEtlProcessDialog = false;
  addJdbcEtlProcessDialog = false;
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

  @Prop({ default: false })
  readonly value!: boolean;

  @Prop({ default: null })
  readonly dataStoreId!: string | null;

  private readonly getLogsQuery =
    "select log:concept:name, log:identity:id, log:lifecycle:model";
  private readonly fileSizeLimit = 5242880;

  @Watch("value")
  componentVisibilityChanged(isVisible: boolean) {
    if (!isVisible) return;
    if (this.dataStoreId == null)
      return this.app.error(
        this.$t("data-stores.data-store-not-found").toString()
      );

    this.loadDataStore();
    this.loadXesLogs();
    this.loadDataConnectors();
    this.loadEtlProcesses();
  }

  async loadDataStore() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingDataStore = true;
      this.dataStore = await this.dataStoreService.getDataStore(
        this.dataStoreId
      );
    } finally {
      this.isLoadingDataStore = false;
    }
  }

  async loadDataConnectors() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingDataConnectors = true;
      this.dataConnectors = await this.dataStoreService.getDataConnectors(
        this.dataStoreId
      );
    } finally {
      this.isLoadingDataConnectors = false;
    }
  }

  async loadEtlProcesses() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingEtlProcesses = true;
      this.etlProcesses = await this.dataStoreService.getEtlProcesses(
        this.dataStoreId
      );
    } finally {
      this.isLoadingEtlProcesses = false;
    }
  }

  async loadXesLogs() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingXesLogs = true;
      this.xesLogHeaders = [];
      this.xesLogItems = [];

      const queryResults = await this.logsService.submitUserQuery(
        this.dataStoreId,
        this.getLogsQuery
      );

      await waitForRepaint(async () => {
        const {
          headers,
          logItems
        } = this.xesProcessor.extractLogItemsFromLogScope(queryResults);
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
      if (file.size > this.fileSizeLimit)
        return this.app.error(
          this.$t("data-stores.file-size-exceeded").toString()
        );
      if (this.dataStoreId == null)
        return this.app.error(
          this.$t("data-stores.data-store-not-found").toString()
        );

      this.isUploading = true;
      await this.logsService.uploadLogFile(this.dataStoreId, file);
      this.app.info(this.$t("data-stores.successful-file-upload").toString());
      await this.loadXesLogs();
    } catch (err) {
      this.app.error(err);
    } finally {
      this.isUploading = false;
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

  async renameDataConnector(newName: string) {
    if (
      this.dataStoreId != null &&
      this.dataConnectorIdToRename != null &&
      (await this.dataStoreService.updateDataConnector(
        this.dataStoreId,
        this.dataConnectorIdToRename,
        {
          id: this.dataConnectorIdToRename,
          name: newName,
          properties: {}
        }
      ))
    ) {
      const dataConnector = this.dataConnectors.find(
        (dataConnector) => dataConnector.id == this.dataConnectorIdToRename
      );

      if (dataConnector != null) dataConnector.name = newName;
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
        title: `${this.$t("common.warning")}`
      }
    );

    if (!isRemovalConfirmed) return;

    try {
      await this.dataStoreService.removeDataConnector(
        this.dataStoreId,
        dataConnector.id
      );
      this.displaySuccessfulRemovalMessage();
      this.dataConnectors.splice(this.dataConnectors.indexOf(dataConnector), 1);
    } catch (error) {
      this.displayFailedRemovalMessage();
    }
  }

  async addDataConnector() {
    this.addDataConnectorDialog = false;
    await this.loadDataConnectors();
  }

  async addAutomaticEtlProcess() {
    this.addAutomaticEtlProcessDialog = false;
    await this.loadEtlProcesses();
  }

  async removeEtlProcess(etlProcess: EtlProcess) {
    if (this.dataStoreId == null) return;

    const isRemovalConfirmed = await this.$confirm(
      `${this.$t("data-stores.etl.removal-confirmation", {
        etlProcessName: etlProcess.name
      })}`,
      {
        title: `${this.$t("common.warning")}`
      }
    );

    if (!isRemovalConfirmed) return;

    try {
      await this.dataStoreService.removeEtlProcess(
        this.dataStoreId,
        etlProcess.id
      );
      this.displaySuccessfulRemovalMessage();

      this.etlProcesses.splice(this.etlProcesses.indexOf(etlProcess), 1);
    } catch (error) {
      this.displayFailedRemovalMessage();
    }
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

  getDataConnectorName(dataConnectorId: string) {
    return (
      this.dataConnectors.find(
        (dataConnector) => dataConnector.id == dataConnectorId
      )?.name || ""
    );
  }

  get dataStoreName() {
    return this.dataStore?.name ?? "";
  }

  get dataStoreSummary() {
    return this.dataStore != null
      ? [
          {
            field: "ID",
            value: this.dataStore.id
          },
          {
            field: this.$t("common.name"),
            value: this.dataStore.name
          },
          {
            field: this.$t("common.created-at"),
            value: this.dataStore.createdAt
          },
          {
            field: this.$t("data-stores.size"),
            value: `${
              this.dataStore?.size != null
                ? (this.dataStore.size / 1024 / 1024).toFixed(2)
                : "?"
            } MB`
          }
        ]
      : [];
  }

  get dataConnectorNameToRename(): string | null {
    return (
      this.dataConnectors.find(
        (dataStore) => dataStore.id == this.dataConnectorIdToRename
      )?.name || null
    );
  }

  async addJdbcEtlProcess() {
    this.addJdbcEtlProcessDialog = false;
    await this.loadEtlProcesses();
  }
}
</script>
