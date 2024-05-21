<template>
  <log-table :classifier="classifier" :headers="headers" :items="items" :loading="loading" :show-search="componentMode != ComponentMode.Static"></log-table>
</template>

<style scoped></style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import App from "@/App.vue";
import LogsService from "@/services/LogsService";
import { WorkspaceComponent } from "@/models/WorkspaceComponent";
import { ComponentMode } from "./WorkspaceComponent.vue";
import XesProcessor, { LogItem, XesComponentScope } from "@/utils/XesProcessor";
import LogTable, { Header } from "@/components/LogTable.vue";

@Component({
  components: { LogTable }
})
export default class FlatLogViewComponent extends Vue {
  ComponentMode = ComponentMode;
  XesComponentScope = XesComponentScope;

  @Prop({ default: {} })
  readonly data!: WorkspaceComponent;

  @Prop({ default: null })
  readonly componentMode?: ComponentMode;
  @Inject() app!: App;
  @Inject() logsService!: LogsService;

  private readonly xesProcessor = new XesProcessor();

  headers: Array<Header> = [];
  items: Array<LogItem> = [];

  loading: boolean = false;

  classifier: string = "concept:name";

  private async refresh() {
    if (!this.data?.dataStore && !this.data?.query) return;

    this.loading = true;

    try {
      const queryResults = await this.logsService.submitUserQuery(this.data.dataStore, this.data.query);

      const { headers, logItems } = this.xesProcessor.extractHierarchicalLogItemsFromAllScopes(queryResults);

      this.classifier = headers.indexOf("concept:name") >= 0 ? "concept:name" : headers.filter((name) => !name.startsWith("_"))[0];

      this.headers = headers
        .filter((name) => !name.startsWith("_"))
        .map(
          (name) =>
            ({
              text: name,
              value: name
            } as Header)
        );
      this.items = logItems.flatMap((log) => log.flatten());
    } catch (err) {
      this.app.error(err?.response?.data?.error ?? err);
    } finally {
      this.loading = false;
    }
  }

  async mounted() {
    await this.refresh();
  }

  @Watch("data")
  async update() {
    await this.refresh();
  }
}
</script>