<template>
  <v-dialog v-model="open" fullscreen>
    <template v-slot:activator="{ on, attrs }">
      <v-btn icon v-bind="attrs" v-on="on">
        <v-icon>format_list_numbered</v-icon>
      </v-btn>
    </template>
    <v-card>
      <v-toolbar color="primary" dark>
        <v-btn dark icon name="btn-close" @click="open = false">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer />
        <v-toolbar-title>
          {{ $t("alignments-dialog.title", { name: name }) }}
          <v-tooltip bottom max-width="600px">
            <template v-slot:activator="{ on, attrs }">
              <v-icon color="accent" v-bind="attrs" v-on="on">help</v-icon>
            </template>
            <span>{{ $t("alignments-dialog.page-hint") }}</span>
          </v-tooltip>
        </v-toolbar-title>
        <v-spacer />
      </v-toolbar>
      <v-card-title>
        <v-text-field v-model="searchTerm" :label="$t('common.search')" append-icon="magnify" hide-details single-line></v-text-field>
      </v-card-title>
      <log-table :headers="headers" :items="items" :show-search="false" :loading="loading"></log-table>
      <v-pagination v-model="page" :length="nPages" total-visible="6" />
    </v-card>
  </v-dialog>
</template>

<style scoped></style>

<script lang="ts">
import { Component, Inject, Prop, Vue, Watch } from "vue-property-decorator";
import LogTable, { Header } from "@/components/LogTable.vue";
import { Alignment, AlignmentKPIReport, DeviationType } from "@/openapi";
import { LogItem, XesComponentScope } from "@/utils/XesProcessor";
import WorkspaceService from "@/services/WorkspaceService";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";

interface SearchCacheItem {
  term: string;
  traces: Array<Array<LogItem>>;
  age: number;
}

@Component({
  components: { LogTable }
})
export default class AlignmentsDialog extends Vue {
  @Inject() workspaceService!: WorkspaceService;

  @Prop({ default: "(no-name)" })
  name?: string;

  @Prop()
  readonly workspaceId!: string;
  @Prop()
  componentId!: string;
  @Prop({ default: 10 })
  pageSize!: number;
  @Prop({ default: 50 })
  maxCacheSize!: number;

  alignments?: Array<Alignment>;
  loading: boolean = true;

  open: boolean = false;
  headers: Array<Header> = [];
  items: Array<LogItem> = [];
  private log = new LogItem(XesComponentScope.Log, 0);
  private allTraces: Array<Array<LogItem>> = [];
  private filteredTraces: Array<Array<LogItem>> = [];
  /**
   * The classifier used to calculate alignments.
   */
  classifier: string = "concept:name";

  page: number = 1;
  nPages: number = 0;

  searchTerm: string = "";
  private searchCache: Array<SearchCacheItem> = [];
  private cacheAge: number = 0;

  @Watch("page")
  pageChanged() {
    this.loading = true;
    try {
      this.items = [this.log];
      this.items.push(...this.filteredTraces.slice((this.page - 1) * this.pageSize, this.page * this.pageSize).flat());
    } finally {
      this.loading = false;
    }
  }

  @Watch("open")
  async opened() {
    if (!this.open) return;

    this.loading = true;
    try {
      if (this.alignments === undefined) {
        const data: WorkspaceComponentModel & {
          data: { alignmentKPIReport?: AlignmentKPIReport };
        } = await this.workspaceService.getComponent(this.workspaceId, this.componentId);
        this.alignments = data.data.alignmentKPIReport?.alignments;

        const headers = new Map<string, Header>();
        headers.set("concept:name", { text: "concept:name", value: "concept:name" });
        headers.set(this.classifier, { text: this.classifier, value: this.classifier });

        let idSeq = 1;
        this.alignments?.forEach((a, i) => {
          const trace = new LogItem(XesComponentScope.Trace, idSeq++);
          trace["concept:name"] = i + 1;
          trace["_parent"] = this.log;
          trace["_path"] = [this.log._id, trace._id];
          const items: Array<LogItem> = [];
          items.push(trace);

          for (const step of a.steps) {
            const event = new LogItem(XesComponentScope.Event, idSeq++);
            event.type = step.type;
            event["_parent"] = trace;
            event["_path"] = [...(trace["_path"] as Array<number>), event._id];
            switch (step.type) {
              case DeviationType.None:
              case DeviationType.LogDeviation:
                Object.assign(event, step.logMove);
                for (const attribute of Object.getOwnPropertyNames(step.logMove)) {
                  if (!headers.has(attribute)) headers.set(attribute, { text: attribute, value: attribute });
                }
                break;
              case DeviationType.ModelDeviation:
                const mm = step.modelMove as { name: string; isSilent: boolean };
                event[this.classifier] = mm.name;
                event["_isSilent"] = mm.isSilent;
                break;
            }
            items.push(event);
          }

          this.allTraces.push(items);
        });

        this.headers = [...headers.values()];
      }
      this.search();
    } finally {
      this.loading = false;
    }
  }

  private matches(item: LogItem, text: string): boolean {
    for (const h of this.headers) {
      const v = String(item[h.value]);
      if (v.includes(text)) return true;
    }
    return false;
  }

  @Watch("searchTerm")
  search() {
    let addToCache = false;
    if (this.searchTerm == "") {
      this.filteredTraces = this.allTraces;
    } else {
      let hit: SearchCacheItem | undefined = undefined;
      for (const item of this.searchCache) {
        if (this.searchTerm.includes(item.term) && (hit === undefined || hit.term.length < item.term.length)) hit = item;
      }
      if (hit !== undefined && hit.age < this.cacheAge) hit.age = ++this.cacheAge;
      if (hit?.term == this.searchTerm) {
        this.filteredTraces = hit.traces;
      } else {
        const source = hit?.traces ?? this.allTraces;
        this.filteredTraces = source.filter((trace) => {
          return (
            trace.findIndex((event) => {
              return this.matches(event, this.searchTerm);
            }) >= 0
          );
        });
        addToCache = source.length > 0;
      }
    }
    console.debug("Search term", this.searchTerm, "add to cache", addToCache);
    if (addToCache) {
      let oldestAt: number | undefined = undefined;
      for (let i = 0; i < this.searchCache.length; ++i) {
        const item = this.searchCache[i];
        if (oldestAt === undefined || this.searchCache[oldestAt].age > item.age) oldestAt = i;
      }
      const newEntry = { term: this.searchTerm, traces: this.filteredTraces, age: ++this.cacheAge };
      if (oldestAt !== undefined && this.searchCache.length >= this.maxCacheSize) this.searchCache[oldestAt] = newEntry;
      else this.searchCache.push(newEntry);
      console.debug(
        "Cache",
        this.searchCache.map((entry) => entry.term + "/" + entry.age)
      );
    }
    this.nPages = Math.ceil(this.filteredTraces.length / this.pageSize);
    this.page = Math.min(this.nPages, Math.max(this.page, 1));
    this.pageChanged();
  }
}
</script>