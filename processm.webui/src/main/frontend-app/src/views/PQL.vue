/* eslint-disable prettier/prettier */
<template>
  <v-container>
    <v-row no-gutters>
      <v-alert type="info" text>
        The system comes preloaded with several demonstrative event logs. You can upload your own XES event log below. Raw *.xes files and the compressed
        *.xes.gz files are supported.
      </v-alert>
      <v-alert type="warning">
        DO NOT upload event logs containing personal, sensitive, and/or classified information. The uploaded event logs will be stored in the system for an
        undefined period of time, e.g., until scheduled clean-up or system update. It will be also shared with the other users of this system. The uploaded
        files will be public available. The system operator is not responsible for the content made public in this way.
      </v-alert>
      <v-col align="left">
        <v-select v-model="dataStoreId" :items="availableDataStores" dense item-text="name" item-value="id" label="Select data store"></v-select>
      </v-col>
      <v-col align="right">
        <v-btn class="mx-2" color="primary" @click="fileUploadDialog = true">
          Upload XES file
          <v-icon right>upload_file</v-icon>
          <v-progress-circular v-show="isUploading" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
        </v-btn>
        <v-btn :to="{ name: 'pql-docs' }" class="mx-2" color="primary" target="_blank"> Documentation</v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-alert type="info" text>
          Type PQL query below and click [Execute] to see query results or [Download XES] to download the resulting XES files. Use the drop-down list for
          predefined queries. Use the [Documentation] button to open the PQL documentation.
        </v-alert>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-select v-model="selectedQuery" :items="predefinedQueries" dense label="Select query" @change="(selectedQuery) => (query = selectedQuery)"></v-select>
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
          label="Query"
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
          Execute
          <v-progress-circular v-show="isLoadingData" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
        </v-btn>
        <v-btn color="primary" @click="download">
          Download XES
          <v-progress-circular v-show="isDownloading" :size="20" :width="3" class="ml-2" color="secondary" indeterminate></v-progress-circular>
        </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-alert type="info" text>
          This view shows at most 10 logs, 30 traces per log, and 90 events per trace. For downloading the limits for the numbers of traces and events are 10
          times larger.
        </v-alert>
        <xes-data-table
          :items="items"
          :headers="headers"
          :is-fold="false"
          :loading="isLoadingData"
          :is-expanded="false"
          :keep-tree-expand="false"
          :empty-text="$t('common.no-data')"
          :disable-pagination="false"
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
import { Component, Inject } from "vue-property-decorator";
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
  query = "";
  headers = new Array<string>();
  items = new Array<LogItem>();
  selectedQuery = "";
  fileUploadDialog = false;
  predefinedQueries = [
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

        for (const item of logItems) {
          await waitForRepaint(() => {
            this.items.push(item);
          });
        }

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
}
</script>
