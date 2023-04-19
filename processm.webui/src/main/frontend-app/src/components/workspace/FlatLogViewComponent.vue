<template>
  <div class="log-view">
    <div class="scroll-container">
      <v-card-title v-if="componentMode !== ComponentMode.Static">
        <v-text-field v-model="search" append-icon="magnify" :label="$t('common.search')" single-line
                      hide-details></v-text-field>
      </v-card-title>
      <v-data-table
        ref="table"
        :headers="headers"
        :items="items"
        :disable-pagination="true"
        :loading="loading"
        :search="search"
        group-by="_parentId"
        :custom-group="groupItems"
        :custom-sort="sortItems"
        :must-sort="true"
        :multi-sort="false"
        :sort-by="classifier"
        :header-props="{
          'sort-icon': ''
        }"
        :hide-default-footer="true"
        dense
      >
        <template v-slot:group.header="{ headers, items, isOpen, toggle }">
          <td :colspan="headers.length" v-if="items.every((item) => item.scope === XesComponentScope.Event)">
            <v-btn icon plain @click="toggle">
              <v-icon>{{ isOpen ? "remove" : "add" }}</v-icon>
            </v-btn>
            <v-breadcrumbs :items="items" dense class="d-inline overflow-x-auto">
              <template v-slot:divider>&gt;</template>
              <template v-slot:item="{ item }">
                <li v-if="!!item[classifier]">{{ item[classifier] }}</li>
                <li v-if="!item[classifier]" class="font-weight-light">{{$t("workspace.component.flat-log.no-data")}}</li>
              </template>
            </v-breadcrumbs>
          </td>
        </template>

        <template v-slot:item.scope="{ item }">
          <v-icon :title="item.scope">$xes{{ item.scope }}</v-icon>
        </template>
      </v-data-table>
    </div>
  </div>
</template>

<style scoped>
.log-view,
.scroll-container {
  height: 100%;
}

.log-view {
  overflow: hidden;
  padding-bottom: 1.2em;
}

.scroll-container {
  overflow: auto;
}
</style>
<style>
.log-view .v-breadcrumbs li:nth-child(even) {
  padding: 0 6px;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop } from "vue-property-decorator";
import App from "@/App.vue";
import LogsService from "@/services/LogsService";
import { WorkspaceComponent } from "@/models/WorkspaceComponent";
import { ComponentMode } from "./WorkspaceComponent.vue";
import XesProcessor, { LogItem, XesComponentScope } from "@/utils/XesProcessor";
import { ItemGroup } from "vuetify";

@Component
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

  search: string = "";

  classifier: string = "concept:name";

  groupItems(items: LogItem[], groupBy: string[], groupDesc: boolean[]): ItemGroup<LogItem>[] {
    const table: any = this.$refs.table;
    const groups = items.reduce((result, item) => {
      switch (item.scope) {
        case XesComponentScope.Log:
          result[item._path as string] = [item];
          break;
        case XesComponentScope.Trace:
          result[`${item._path}a`] = [item];
          break;
        case XesComponentScope.Event: {
          const group = `${(item._parent as LogItem)._path}b`;
          (result[group] = result[group] || []).push(item);
          const instance = table.$vnode.componentInstance;
          instance.$set(instance.openCache, group, false);
        }
      }
      return result;
    }, {} as Record<string, LogItem[]>);

    const out = Object.keys(groups).map((key) => ({ name: key, items: groups[key] } as ItemGroup<LogItem>));

    return out;
  }

  async mounted() {
    this.loading = true;

    try {
      const queryResults = await this.logsService.submitUserQuery(this.data.dataStore, this.data.query);

      // console.log(queryResults);

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

  sortItems(items: any[], sortBy: string): any[] {
    // HACK: this function does not sort but sets the classifier based on the header clicked
    // Unfortunately, v-data-table does not offer an option to handle header click event otherwise.
    this.classifier = sortBy[1];
    return items;
  }
}

interface Header {
  text: string;
  value: string;
}
</script>
