/* eslint-disable prettier/prettier */
<template>
  <v-container>
    <v-row no-gutters>
      <v-alert type="info" text>
        The system comes preloaded with several demonstrative event logs. You
        can upload your own XES event log below. Raw *.xes files and the
        compressed *.xes.gz files are supported.
      </v-alert>
      <v-alert type="warning">
        DO NOT upload event logs containing personal, sensitive, and/or
        classified information. The uploaded event logs will be stored in the
        system for an undefined period of time, e.g., until scheduled clean-up
        or system update. It will be also shared with the other users of this
        system. The uploaded files will be public available. The system operator
        is not responsible for the content made public in this way.
      </v-alert>
      <v-col>
        <v-file-input
          prepend-icon="file_upload"
          show-size
          accept=".gz,.xes"
          :rules="[
            (v) =>
              !v ||
              v.size < fileSizeLimit ||
              `Log file size should be less than ${
                fileSizeLimit / 1024 / 1024
              } MB!`
          ]"
          label="XES file input"
          dense
          @change="selectFile"
        ></v-file-input>
      </v-col>
      <v-col>
        <v-btn small color="primary" @click="submitFile">
          Upload
          <v-icon right>upload_file</v-icon>
          <v-progress-circular
            v-show="isUploading"
            indeterminate
            :width="3"
            :size="20"
            color="secondary"
            class="ml-2"
          ></v-progress-circular>
        </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-alert type="info" text>
          Type PQL query below and click [Execute] to see query results or
          [Download XES] to download the resulting XES files. Use the drop-down
          list for predefined queries. Use the [Documentation] button to open
          the PQL documentation.
        </v-alert>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-select
          v-model="selectedQuery"
          :items="predefinedQueries"
          label="Select query"
          @change="(selectedQuery) => (query = selectedQuery)"
          dense
        ></v-select>
      </v-col>
      <v-col align="right">
        <v-btn color="primary" :to="{ name: 'pql-docs' }" target="_blank">
          Documentation
        </v-btn>
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
        >
        </v-textarea>
      </v-col>
    </v-row>
    <v-row no-gutters>
      <v-col>
        <v-btn color="primary" @click="submitQuery" class="mr-4">
          Execute
          <v-progress-circular
            v-show="isLoadingData"
            indeterminate
            :width="3"
            :size="20"
            color="secondary"
            class="ml-2"
          ></v-progress-circular>
        </v-btn>
        <v-btn color="primary" @click="download">
          Download XES
          <v-progress-circular
            v-show="isDownloading"
            indeterminate
            :width="3"
            :size="20"
            color="secondary"
            class="ml-2"
          ></v-progress-circular>
        </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-alert type="info" text>
          This view shows at most 10 logs, 30 traces per log, and 90 events per
          trace. For downloading the limits for the numbers of traces and events
          are 10 times larger.
        </v-alert>
        <tree-table
          :data="items"
          :columns="headers"
          :selectable="false"
          :expand-type="false"
          :is-fold="false"
          :border="true"
          :show-index="false"
          :is-expanded="false"
          :keep-tree-expand="false"
          expand-key="scope"
          children-prop="_children"
          id-prop="_id"
          empty-text="No data"
        >
          <template slot="scope" slot-scope="scope">
            <v-icon>$xes{{ scope.row.scope }}</v-icon>
          </template>
        </tree-table>
      </v-col>
    </v-row>
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
import TreeTable from "tree-table-vue-extend";
import App from "@/App.vue";

class LogHeader {
  constructor(
    readonly key: string,
    title: string | null = null,
    width: number | null = null
  ) {
    this.title = title ?? key;
    this.width = width;
  }

  readonly title: string;
  type?: string;
  template?: string;
  width?: number | null;
}

@Component({
  components: { TreeTable }
})
export default class PQL extends Vue {
  @Inject() app!: App;
  @Inject() logsService!: LogsService;
  @Inject() dataStoreService!: DataStoreService;
  private readonly xesProcessor = new XesProcessor();
  private dataStoreId: string | null = null;
  fileSizeLimit = 5242880;
  isLoadingData = false;
  isUploading = false;
  isDownloading = false;
  selectedFile: File | null = null;
  query = "";
  headers = new Array<LogHeader>();
  items = new Array<LogItem>();
  selectedQuery = "";
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
      text:
        "UC4: Filter the Hospital log to the traces with diagnosis maligniteit cervix and events raised in the nursing ward",
      value:
        "where l:name='Hospital log' and [t:Diagnosis]='maligniteit cervix' and group='Nursing ward'"
    },
    {
      text:
        "UC5: Select the events in the Hospital log that occurred on weekends and filter out events on workdays",
      value: "where l:name='Hospital log' and dayofweek(timestamp) in (1,7)"
    },
    {
      text:
        "UC6: Select the entire traces with any event that occurred on weekend and filter out the traces without events on weekends",
      value: "where l:name='Hospital log' and dayofweek(^timestamp) in (1,7)"
    },
    {
      text: "UC7: Select the names of all event logs in the database",
      value: "select l:name\nlimit t:1, e:1"
    },
    {
      text:
        "UC8: Select the names of all event logs and all attributes of their traces and events",
      value: "select l:name, t:*, e:*"
    },
    {
      text: "UC9: Count the traces in the Hospital log",
      value: "select count(^t:name)\nwhere l:name='Hospital log'"
    },
    {
      text:
        "UC10: Select the begin and the end timestamps of the trace and calculate its duration",
      value:
        "select l:name, min(^timestamp), max(^timestamp), max(^timestamp)-min(^timestamp)\ngroup by t:name"
    },
    {
      text:
        "UC11: Group the traces into variants, where two traces belong to the same variant if their sequences of events equal on their names",
      value: "group by ^name"
    },
    {
      text:
        "UC12: Order events using their timestamps rather than the recorded order",
      value: "order by e:timestamp"
    },
    {
      text: "UC13: Order traces descending using their duration",
      value:
        "group by t:name\norder by max(^e:timestamp)-min(^e:timestamp) desc"
    },
    {
      text:
        "UC14: Limit the numbers of returned event logs and traces per log to the top 5 and 10, respectively",
      value: "limit l:5, t:10"
    },
    {
      text: "UC15: Read the next 10 traces in the top 5 event logs",
      value: "limit l:5, t:10\noffset t:10"
    },
    {
      text:
        "UC16: Retrieve the top 30 most common trace variants in the Hospital log based on the names of activities, the total number of the traces, the total number of occurrences per trace variant, and the names of the activities",
      value:
        "select l:name, count(^t:name), count(t:name), name\nwhere l:name='Hospital log'\ngroup by l:name, ^name\norder by count(t:name) desc\nlimit t:30"
    },
    {
      text:
        "UC17: Retrieve the top 30 longest traces in the Hospital log, their names and duration, and all attributes of theirs respective events",
      value:
        "select l:name, t:name, max(^timestamp)-min(^timestamp), e:*\nwhere l:name='Hospital log'\ngroup by t:name\norder by max(^timestamp)-min(^timestamp) desc\nlimit t:30"
    }
  ];

  async created() {
    this.dataStoreId =
      Vue.prototype.$sessionStorage.defaultDataStoreId ??
      (await this.dataStoreService.getAll())?.[0]?.id;
  }

  selectFile(file: File) {
    this.selectedFile = file;
  }

  queryModified() {
    this.selectedQuery = "";
  }

  async submitFile(): Promise<void> {
    try {
      if (this.selectedFile == null) return;
      if (this.selectedFile.size > this.fileSizeLimit)
        return this.app.error("The selected file exceeds the size limit.");
      if (this.dataStoreId == null)
        return this.app.error("No appropriate data store found to query.");

      this.isUploading = true;
      await this.logsService.uploadLogFile(this.dataStoreId, this.selectedFile);
      this.selectedFile = null;
      this.app.info("File uploaded successfully.");
    } catch (err) {
      this.app.error(err);
    } finally {
      this.isUploading = false;
    }
  }

  async submitQuery() {
    try {
      this.headers = [];
      this.items = [];
      if (this.dataStoreId == null)
        throw new Error("No appropriate data store found to query.");

      this.isLoadingData = true;

      this.app.info("Executing query...", -1);
      let start = new Date().getTime();
      const queryResults = await this.logsService.submitUserQuery(
        this.dataStoreId,
        this.query
      );

      const executionTime = new Date().getTime() - start;
      this.app.info(
        "Query executed and results retrieved in " +
          executionTime +
          "ms. Formatting results...",
        -1
      );
      start = new Date().getTime();

      await waitForRepaint(async () => {
        const {
          headers,
          logItems
        } = this.xesProcessor.extractHierarchicalLogItemsFromAllScopes(
          queryResults
        );

        headers.forEach((headerName: string) => {
          if (headerName.startsWith("_")) return headers;

          const header = new LogHeader(headerName);

          if (headerName == "scope") {
            header.type = "template";
            header.template = headerName;
            header.width = 112;
          }

          this.headers.push(header);
        });
        if (this.headers.length === 0)
          this.headers.push(new LogHeader("scope"));

        for (const item of logItems) {
          await waitForRepaint(() => {
            this.items.push(item);
          });
        }

        const formattingTime = new Date().getTime() - start;
        this.app.info(
          "Query executed and results retrieved in " +
            executionTime +
            "ms. Formatted results in " +
            formattingTime +
            "ms."
        );
      });
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isLoadingData = false;
    }
  }

  async download() {
    try {
      if (this.dataStoreId == null)
        throw new Error("No appropriate data store found to query.");

      this.isDownloading = true;

      this.app.info("Executing query...", -1);
      const start = new Date().getTime();
      await this.logsService.submitUserQuery(
        this.dataStoreId,
        this.query,
        "application/zip"
      );
      const executionTime = new Date().getTime() - start;
      this.app.info(
        "Query executed and results retrieved in " + executionTime + "ms."
      );
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isDownloading = false;
    }
  }
}
</script>
