<template>
  <v-container>
    <v-row no-gutters>
      <v-alert text type="info">{{ $t("pql-interpreter.page-hint") }}</v-alert>
      <v-alert v-if="app.config.demoMode" type="warning">{{ $t("pql-interpreter.demo-mode") }}</v-alert>
      <v-col align="left">
        <v-select v-model="dataStoreId" :items="availableDataStores" dense item-text="name" item-value="id" label="Select data store"></v-select>
      </v-col>
      <v-col align="right">
        <v-btn class="mx-2" color="primary" @click="fileUploadDialog = true">
          <v-icon>upload_file</v-icon>
          {{ $t("pql-interpreter.upload-xes-file.title") }}
          <v-progress-circular v-show="isUploading" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
        </v-btn>
        <v-btn :title="$t('pql-interpreter.pql-documentation-hint')" :to="{ name: 'pql-docs' }" class="mx-2" color="primary" target="_blank">
          <v-icon>description</v-icon>
          {{ $t("pql-interpreter.pql-documentation") }}
        </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <p class="text-subtitle-1">{{ $t("pql-interpreter.logs") }}</p>
        <div class="text-center">
          <v-progress-circular v-show="isLoadingXesLogs" color="primary" indeterminate></v-progress-circular>
        </div>
        <xes-data-table
          v-show="!isLoadingXesLogs"
          :disable-hierarchy="true"
          :disable-pagination="false"
          :empty-text="$t('common.no-data')"
          :headers="xesLogHeaders"
          :is-expanded="false"
          :is-fold="false"
          :items="xesLogItems"
          :items-per-page="5"
          :items-per-page-options="[1, 5, -1]"
          :keep-tree-expand="false"
          :loading="isLoadingXesLogs"
          :removable="false"
          :selectable="true"
          children-prop="_children"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-select
          v-model="selectedQuery"
          :items="predefinedQueries"
          :label="$t('pql-interpreter.select-query')"
          dense
          @change="(selectedQuery) => (query = selectedQuery)"
        ></v-select>
      </v-col>
    </v-row>
    <v-row no-gutters>
      <v-col>
        <v-textarea
          outlined
          auto-grow
          rows="4"
          background-color="grey lighten-4"
          class="pql-code"
          :hint="$t('pql-interpreter.query-hint')"
          :label="$t('pql-interpreter.query')"
          v-model="query"
          @input="queryModified"
          name="query"
        >
        </v-textarea>
      </v-col>
    </v-row>
    <v-row no-gutters>
      <v-col>
        <v-btn :disabled="isLoadingData" class="mr-4" color="primary" name="btn-submit-query" @click="submitQuery">
          {{ $t("pql-interpreter.execute") }}
          <v-progress-circular v-show="isLoadingData" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
        </v-btn>
        <v-btn color="primary" @click="download">
          {{ $t("pql-interpreter.download") }}
          <v-progress-circular v-show="isDownloading" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
        </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <xes-data-table
          :items="items"
          :headers="headers"
          :is-fold="false"
          :loading="isLoadingData"
          :is-expanded="false"
          :keep-tree-expand="false"
          :empty-text="$t('common.no-data')"
          :disable-pagination="false"
          :items-per-page="5"
          :items-per-page-options="[1, 5, -1]"
          :selectable="false"
          children-prop="_children"
          id-prop="_id"
        />
      </v-col>
    </v-row>
    <file-upload-dialog v-model="fileUploadDialog" :sizeLimit="fileSizeLimit" @cancelled="fileUploadDialog = false" @submitted="submitFile" />
  </v-container>
</template>

<style scoped>
::v-deep .pql-code textarea {
  font-family: monospace;
}

::v-deep tr.log {
  background: #cccccc;
}

::v-deep tr.trace {
  background: #dddddd;
}

::v-deep tr.event {
  background: #eeeeee;
}
</style>

<script lang="ts">
import Vue from "vue";
import { waitForRepaint } from "@/utils/waitForRepaint";
import { Component, Inject, Watch } from "vue-property-decorator";
import LogsService from "@/services/LogsService";
import DataStoreService from "@/services/DataStoreService";
import XesProcessor, { LogItem } from "@/utils/XesProcessor";
import FileUploadDialog from "@/components/FileUploadDialog.vue";
import App from "@/App.vue";
import DataStore from "@/models/DataStore";

const XesDataTable = () => import("@/components/XesDataTable.vue");

@Component({
  components: { XesDataTable, FileUploadDialog }
})
export default class PQL extends Vue {
  @Inject() app!: App;
  @Inject() logsService!: LogsService;
  @Inject() dataStoreService!: DataStoreService;
  private readonly xesProcessor = new XesProcessor();
  private dataStoreId: string | null = null;
  availableDataStores: Array<DataStore> = [];
  fileSizeLimit = this.app.config.maxUploadSize;
  isLoadingData = false;
  isUploading = false;
  isDownloading = false;
  isLoadingDataStores = false;

  xesLogHeaders: string[] = [];
  xesLogItems: LogItem[] = [];
  isLoadingXesLogs = false;
  readonly getLogsQuery = "select log:concept:name, log:identity:id, log:lifecycle:model";

  query = "";
  headers = new Array<string>();
  items = new Array<LogItem>();
  selectedQuery = "";
  fileUploadDialog = false;
  readonly predefinedQueries = [
    { text: "Custom", value: "" },
    { text: "UC1: Read the entire database", value: "select *" },
    {
      text: "UC2: Read all event logs named Hospital log",
      value: "where l:name='Hospital log'"
    },
    {
      text: "UC3: Filter the Hospital log to the traces with the diagnosis set",
      value: "where l:name='Hospital log' and [t:Diagnosis] is not null"
    },
    {
      text: "UC4: Filter the Hospital log to the traces with diagnosis maligniteit cervix and events raised in the nursing ward",
      value: "where l:name='Hospital log' and [t:Diagnosis]='maligniteit cervix' and group='Nursing ward'"
    },
    {
      text: "UC5: Select the events in the Hospital log that occurred on weekends and filter out events on workdays",
      value: "where l:name='Hospital log' and dayofweek(timestamp) in (1,7)"
    },
    {
      text: "UC6: Select the entire traces with any event that occurred on weekend and filter out the traces without events on weekends",
      value: "where l:name='Hospital log' and dayofweek(^timestamp) in (1,7)"
    },
    {
      text: "UC7: Select the names of all event logs in the database",
      value: "select l:name\nlimit t:1, e:1"
    },
    {
      text: "UC8: Select the names of all event logs and all attributes of their traces and events",
      value: "select l:name, t:*, e:*"
    },
    {
      text: "UC9: Count the traces in the Hospital log",
      value: "select count(^t:name)\nwhere l:name='Hospital log'"
    },
    {
      text: "UC10: Select the begin and the end timestamps of the trace and calculate its duration",
      value: "select l:name, min(^timestamp), max(^timestamp), max(^timestamp)-min(^timestamp)\ngroup by t:name"
    },
    {
      text: "UC11: Group the traces into variants, where two traces belong to the same variant if their sequences of events equal on their names",
      value: "group by ^name"
    },
    {
      text: "UC12: Order events using their timestamps rather than the recorded order",
      value: "order by e:timestamp"
    },
    {
      text: "UC13: Order traces descending using their duration",
      value: "group by t:name\norder by max(^e:timestamp)-min(^e:timestamp) desc"
    },
    {
      text: "UC14: Limit the numbers of returned event logs and traces per log to the top 5 and 10, respectively",
      value: "limit l:5, t:10"
    },
    {
      text: "UC15: Read the next 10 traces in the top 5 event logs",
      value: "limit l:5, t:10\noffset t:10"
    },
    {
      text:
        "UC16: Retrieve the top 30 most common trace variants in the Hospital log based on the names of activities, the total number of the traces, the total number of occurrences per trace variant, and the names of the activities",
      value: "select l:name, count(^t:name), count(t:name), name\nwhere l:name='Hospital log'\ngroup by l:name, ^name\norder by count(t:name) desc\nlimit t:30"
    },
    {
      text: "UC17: Retrieve the top 30 longest traces in the Hospital log, their names and duration, and all attributes of theirs respective events",
      value:
        "select l:name, t:name, max(^timestamp)-min(^timestamp), e:*\nwhere l:name='Hospital log'\ngroup by t:name\norder by max(^timestamp)-min(^timestamp) desc\nlimit t:30"
    }
  ];

  async created() {
    try {
      this.isLoadingDataStores = true;
      this.availableDataStores = await this.dataStoreService.getAll();
    } finally {
      this.isLoadingDataStores = false;
    }

    if (this.availableDataStores.length > 0) this.dataStoreId = this.availableDataStores[0].id;
  }

  queryModified() {
    this.selectedQuery = "";
  }

  async submitFile(file: File): Promise<void> {
    this.fileUploadDialog = false;
    try {
      if (file == null) return;
      if (file.size > this.fileSizeLimit) return this.app.error("The selected file exceeds the size limit.");
      if (this.dataStoreId == null) return this.app.error("No appropriate data store found to query.");

      this.isUploading = true;
      await this.logsService.uploadLogFile(this.dataStoreId, file);
      this.app.info("File uploaded successfully.");
    } catch (err) {
      this.app.error(err);
    } finally {
      this.isUploading = false;
      await this.loadXesLogs();
    }
  }

  async submitQuery() {
    if (this.isLoadingData) return;
    try {
      this.headers = [];
      this.items = [];
      if (this.dataStoreId == null) throw new Error("No appropriate data store found to query.");

      this.isLoadingData = true;

      this.app.info("Executing query...", -1);
      let start = new Date().getTime();
      const queryResults = await this.logsService.submitUserQuery(this.dataStoreId, this.query);

      const executionTime = new Date().getTime() - start;
      this.app.info("Query executed and results retrieved in " + executionTime + "ms. Formatting results...", -1);
      start = new Date().getTime();

      await waitForRepaint(async () => {
        const { headers, logItems } = this.xesProcessor.extractHierarchicalLogItemsFromAllScopes(queryResults);
        this.headers = headers;

        await waitForRepaint(() => {
          this.items.push(...logItems);
        });

        const formattingTime = new Date().getTime() - start;
        this.app.info("Query executed and results retrieved in " + executionTime + "ms. Formatted results in " + formattingTime + "ms.");
      });
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isLoadingData = false;
    }
  }

  async download() {
    try {
      if (this.dataStoreId == null) throw new Error("No appropriate data store found to query.");

      this.isDownloading = true;

      this.app.info("Executing query...", -1);
      const start = new Date().getTime();
      await this.logsService.submitUserQuery(this.dataStoreId, this.query, "application/zip");
      const executionTime = new Date().getTime() - start;
      this.app.info("Query executed and results retrieved in " + executionTime + "ms.");
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isDownloading = false;
    }
  }

  /**
   * Loads the list of event logs.
   */
  @Watch("dataStoreId")
  async loadXesLogs() {
    if (this.dataStoreId == null) return;
    try {
      this.isLoadingXesLogs = true;
      this.xesLogHeaders = [];
      this.xesLogItems = [];

      const queryResults = await this.logsService.submitUserQuery(this.dataStoreId, this.getLogsQuery, "application/json", false, false);

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
}
</script>
