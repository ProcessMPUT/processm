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
      <log-table :headers="headers" :items="items" :show-search="true" :loading="loading"></log-table>
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

  alignments?: Array<Alignment>;
  loading: boolean = true;

  open: boolean = false;
  headers: Array<Header> = [];
  items: Array<LogItem> = [];
  /**
   * The classifier used to calculate alignments.
   */
  classifier: string = "concept:name";

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
      }

      const headers = new Map<string, Header>();
      headers.set("concept:name", { text: "concept:name", value: "concept:name" });
      headers.set(this.classifier, { text: this.classifier, value: this.classifier });
      const items = new Array<LogItem>();
      let idSeq = 0;
      const log = new LogItem(XesComponentScope.Log, idSeq++);
      items.push(log);

      this.alignments?.forEach((a, i) => {
        const trace = new LogItem(XesComponentScope.Trace, idSeq++);
        trace["concept:name"] = i + 1;
        trace["_parent"] = log;
        trace["_path"] = [log._id, trace._id];
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
      });

      this.headers = [...headers.values()];
      this.items = items;
    } finally {
      this.loading = false;
    }
  }
}
</script>