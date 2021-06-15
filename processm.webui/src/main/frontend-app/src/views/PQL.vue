/* eslint-disable prettier/prettier */
<template>
  <v-container>
    <v-row no-gutters>
      <v-col>
        <v-file-input
          prepend-icon="file_upload"
          show-size
          accept=".gz,.xes"
          :rules="[
            (v) =>
              !v ||
              v.size < 5000000 ||
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
      <v-col align="right">
        <v-btn color="primary" :to="{ name: 'pql-docs' }" target="_blank"
          >Documentation</v-btn
        >
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
    <v-row>
      <v-col>
        <v-btn color="primary" @click="submitQuery"> Execute </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <tree-table
          :data="items"
          :columns="headers"
          :selectable="false"
          :expand-type="false"
          :is-fold="false"
          :border="true"
          :show-index="false"
          :loading="isLoadingData"
          :is-expanded="true"
          expand-key="expendable"
          children-prop="_children"
          id-prop="_id"
          empty-text="No data"
        >
          <template slot="scope" slot-scope="scope">
            <v-icon>$xes{{ scope.row.scope }}</v-icon>
          </template>
          <template slot="loading">
            <div class="text-center">
              <v-progress-circular
                indeterminate
                color="primary"
              ></v-progress-circular>
            </div>
          </template>
        </tree-table>
      </v-col>
    </v-row>
    <v-row>
      <v-snackbar
        color="error"
        :value="errorMessage != null"
        :timeout="errorTimeout"
      >
        {{ errorMessage }}
        <v-btn dark text @click="errorMessage = null">Close</v-btn>
      </v-snackbar>
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
import { Component, Inject } from "vue-property-decorator";
import LogsService from "@/services/LogsService";
import DataSourceService from "@/services/DataSourceService";
import XesProcessor, { LogItem } from "@/utils/XesProcessor";
import TreeTable from "tree-table-vue-extend";

class LogHeader {
  constructor(readonly key: string, title: string | null = null) {
    this.title = title ?? key;
  }

  readonly title: string;
  type?: string;
  template?: string;
}

@Component({
  components: { TreeTable }
})
export default class PQL extends Vue {
  @Inject() logsService!: LogsService;
  @Inject() dataSourceService!: DataSourceService;
  private readonly xesProcessor = new XesProcessor();
  private dataSourceId: string | null = null;
  readonly errorTimeout = 3000;
  fileSizeLimit = 5242880;
  isLoadingData = false;
  isUploading = false;
  selectedFile: File | null = null;
  query = "";
  headers = new Array<LogHeader>();
  items = new Array<LogItem>();
  dataLoadingError: string | null = null;
  errorMessage: string | null = null;
  selectedQuery = "";
  predefinedQueries = [
    { text: "Custom", value: "" },
    { text: "Query 1", value: "<query1>" },
    { text: "Query 2", value: "<query2>" }
  ];

  async created() {
    this.dataSourceId =
      Vue.prototype.$sessionStorage.defaultDataSourceId ??
      (await this.dataSourceService.getAll())?.[0]?.id;
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
        return alert("The selected file size exceeds limit.");
      if (this.dataSourceId == null)
        return alert("No appropriate data source found to query.");

      this.isUploading = true;
      await this.logsService.uploadLogFile(
        this.dataSourceId,
        this.selectedFile
      );
    } catch (err) {
      alert(err);
      this.errorMessage = err;
    } finally {
      this.isUploading = false;
    }
  }

  async submitQuery(): Promise<void> {
    try {
      this.headers = [new LogHeader("expendable", "")];
      this.items = [];
      this.dataLoadingError = null;
      if (this.dataSourceId == null)
        return alert("No appropriate data source found to query.");

      this.isLoadingData = true;
      const queryResults = await this.logsService.submitUserQuery(
        this.dataSourceId,
        this.query
      );

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
        }

        this.headers.push(header);
      });
      this.items = logItems;
    } catch (err) {
      alert(err);
      this.dataLoadingError = err;
    } finally {
      this.isLoadingData = false;
    }
  }
}
</script>
