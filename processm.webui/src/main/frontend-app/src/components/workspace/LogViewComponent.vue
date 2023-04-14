<template>
  <div class="log-view">
    <div class="scroll-container">
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
    </div>
  </div>
</template>

<style>
.log-view, .scroll-container {
  height: 100%;
}

.log-view {
  overflow: hidden;
  padding-bottom: 1.15em;
}

.scroll-container {
  overflow: auto;
}

.log-view .zk-table__cell-inner {
  padding: 1px 1px;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop } from "vue-property-decorator";
import XesProcessor, { LogItem } from "@/utils/XesProcessor";
import { WorkspaceComponent } from "@/models/WorkspaceComponent";
import { ComponentMode } from "./WorkspaceComponent.vue";
import LogsService from "@/services/LogsService";
import { waitForRepaint } from "@/utils/waitForRepaint";
import App from "@/App.vue";
import XesDataTable from "@/components/XesDataTable.vue";

@Component({ components: { XesDataTable } })
export default class LogViewComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: WorkspaceComponent;

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;

  @Inject() app!: App;
  @Inject() logsService!: LogsService;

  private readonly xesProcessor = new XesProcessor();

  headers = new Array<string>();
  items = new Array<LogItem>();

  isLoadingData = false;

  async mounted() {
    this.isLoadingData = true;
    try {
      if (!this.data?.dataStore) throw Error(this.$t("component.edit.validation.datastore-empty").toString());

      if (!this.data?.query) throw Error(this.$t("component.edit.validation.query-empty").toString());

      const queryResults = await this.logsService.submitUserQuery(this.data.dataStore, this.data.query);

      await waitForRepaint(async () => {
        const { headers, logItems } = this.xesProcessor.extractHierarchicalLogItemsFromAllScopes(queryResults);
        this.headers = headers;

        for (const item of logItems) {
          await waitForRepaint(() => {
            this.items.push(item);
          });
        }
      });
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.isLoadingData = false;
    }
  }
}
</script>
