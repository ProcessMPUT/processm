/* eslint-disable prettier/prettier */
<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("xes-preview-dialog.title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-row>
            <v-col>
              <xes-data-table
                :disable-pagination="false"
                :empty-text="$t('common.no-data')"
                :headers="headers"
                :is-expanded="false"
                :is-fold="false"
                :items="items"
                :items-per-page-options="[1, 5, -1]"
                :keep-tree-expand="false"
                :loading="isLoadingData"
                :selectable="false"
                children-prop="_children"
                id-prop="_id"
              />
            </v-col>
          </v-row>
        </v-container>
      </v-card-text>
    </v-card>
  </v-dialog>
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
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import LogsService from "@/services/LogsService";
import DataStoreService from "@/services/DataStoreService";
import XesProcessor, { LogItem } from "@/utils/XesProcessor";
import App from "@/App.vue";

const XesDataTable = () => import("@/components/XesDataTable.vue");

@Component({
  components: { XesDataTable }
})
export default class PQL extends Vue {
  @Inject() app!: App;
  @Inject() logsService!: LogsService;
  @Inject() dataStoreService!: DataStoreService;
  private readonly xesProcessor = new XesProcessor();
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  private dataStoreId?: string;
  @Prop()
  private query?: string;

  isLoadingData = false;
  isUploading = false;
  isDownloading = false;
  headers = new Array<string>();
  items = new Array<LogItem>();

  cancel() {
    this.$emit("cancelled");
  }

  @Watch("value")
  componentVisibilityChanged(isVisible: boolean) {
    if (!isVisible) return;
    this.submit();
  }

  async submit() {
    if (this.isLoadingData) return;
    if (this.query === undefined || this.query == "") return;
    try {
      this.headers = [];
      this.items = [];
      if (this.dataStoreId == null) throw new Error(this.$t("xes-preview-dialog.no-data-store").toString());

      this.isLoadingData = true;

      this.app.info(this.$t("xes-preview-dialog.executing-query").toString(), -1);
      let start = new Date().getTime();
      const queryResults = await this.logsService.submitUserQuery(this.dataStoreId, this.query);

      const executionTime = new Date().getTime() - start;
      this.app.info(this.$t("xes-preview-dialog.query-executed", { executionTime: executionTime }).toString(), -1);
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
        this.app.info(
          this.$t("xes-preview-dialog.results-retrieved", {
            executionTime: executionTime,
            formattingTime: formattingTime
          }).toString()
        );
      });
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isLoadingData = false;
    }
  }
}
</script>
