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
      <v-col align="right">
        <v-btn class="mx-2" color="primary" @click="fileUploadDialog = true">
          Upload XES file
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
        <v-btn
          class="mx-2"
          color="primary"
          :to="{ name: 'pql-docs' }"
          target="_blank"
        >
          Documentation
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
        <v-btn
          color="primary"
          class="mr-4"
          :disabled="isLoadingData"
          @click="submitQuery"
        >
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
    <file-upload-dialog
      v-model="fileUploadDialog"
      :sizeLimit="fileSizeLimit"
      @cancelled="fileUploadDialog = false"
      @submitted="submitFile"
    />
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
import XesDataTable from "@/components/XesDataTable.vue";
import FileUploadDialog from "@/components/FileUploadDialog.vue";
import App from "@/App.vue";

@Component({
  components: { XesDataTable, FileUploadDialog }
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
  query = "";
  headers = new Array<string>();
  items = new Array<LogItem>();
  selectedQuery = "";
  fileUploadDialog = false;
  predefinedQueries = [
    { text: "Custom", value: "" },
    { text: "Select the first 5 logs", value: "select *\nlimit l:5" },
    {
      text: "Group traces into variants",
      value:
        "select l:name, count(t:name), e:name\n" +
        "group by ^e:name\n" +
        "order by count(t:name) desc\n" +
        "limit l:5"
    },
    {
      text: "Traces with events at weekends",
      value: "where dayofweek(^e:timestamp) in (1, 7)"
    }
  ];

  async created() {
    this.dataStoreId =
      Vue.prototype.$sessionStorage.defaultDataStoreId ??
      (await this.dataStoreService.getAll())?.[0]?.id;
  }

  queryModified() {
    this.selectedQuery = "";
  }

  async submitFile(file: File): Promise<void> {
    this.fileUploadDialog = false;
    try {
      if (file == null) return;
      if (file.size > this.fileSizeLimit)
        return this.app.error("The selected file exceeds the size limit.");
      if (this.dataStoreId == null)
        return this.app.error("No appropriate data store found to query.");

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
      if (this.dataStoreId == null)
        throw new Error("No appropriate data store found to query.");

      this.isLoadingData = true;

      this.app.info("Executing query...", -1);
      const queryResults = await this.logsService.submitUserQuery(
        this.dataStoreId,
        this.query
      );

      this.app.info("Formatting results...", -1);
      await waitForRepaint(async () => {
        const {
          headers,
          logItems
        } = this.xesProcessor.extractHierarchicalLogItemsFromAllScopes(
          queryResults
        );
        this.headers = headers;

        for (const item of logItems) {
          await waitForRepaint(() => {
            this.items.push(item);
          });
        }

        this.app.info("Query executed successfully");
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
      await this.logsService.submitUserQuery(
        this.dataStoreId,
        this.query,
        "application/zip"
      );
      this.app.info("Query executed successfully");
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isDownloading = false;
    }
  }
}
</script>
