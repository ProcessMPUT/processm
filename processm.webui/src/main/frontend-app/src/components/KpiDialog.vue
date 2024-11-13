<template>
  <v-dialog v-model="value" @click:outside="value = false" max-width="600" @keydown.esc="value = false">
    <template v-slot:activator="{ on, attrs }">
      <v-btn icon v-bind="attrs" v-on="on">
        <v-icon>table_view</v-icon>
      </v-btn>
    </template>
    <v-data-table dense :loading="loading" :items="this.items" group-by="group" sort-by="key">
      <template v-slot:top>
        <v-toolbar color="primary" dark>
          <v-btn dark icon name="btn-close" @click="value = false">
            <v-icon>arrow_back</v-icon>
          </v-btn>
          <v-spacer />
          <v-toolbar-title>
            {{ $t("kpi-dialog.title") }}
          </v-toolbar-title>
          <v-spacer />
        </v-toolbar>
      </template>
      <template v-slot:item="{ item }">
        <tr>
          <td>{{ item.key }}</td>
          <td>{{ item.value }}</td>
        </tr>
      </template>
      <template v-slot:group.header="{ group }">
        <th colspan="2">{{ $t(group) }}</th>
      </template>
      <template v-slot:header="{ header }">
        <th>{{ $t("kpi-dialog.kpi") }}</th>
        <th>{{ $t("kpi-dialog.value") }}</th>
      </template>
    </v-data-table>
  </v-dialog>
</template>

<script lang="ts">
import { Component, Inject, Prop, Vue, Watch } from "vue-property-decorator";
import { AlignmentKPIHolder } from "@/components/Graph.vue";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import WorkspaceService from "@/services/WorkspaceService";
import { AlignmentKPIReport } from "@/openapi";

@Component
export default class KpiDialog extends Vue {
  @Inject() workspaceService!: WorkspaceService;
  @Prop()
  readonly workspaceId!: string;
  @Prop()
  componentId!: string;
  value: boolean = false;
  loading: boolean = false;
  report: AlignmentKPIReport | null = null;
  items: { key: string; kpi: string; value: string; group: string }[] = [];

  intFormat = Intl.NumberFormat(this.$i18n.locale, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  });
  numberFormat = Intl.NumberFormat(this.$i18n.locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  @Watch("value")
  async show() {
    if (!this.value) return;
    this.loading = true;
    const data: WorkspaceComponentModel & {
      data: AlignmentKPIHolder;
    } = await this.workspaceService.getComponent(this.workspaceId, this.componentId);
    this.report = data.data.alignmentKPIReport !== undefined ? data.data.alignmentKPIReport : null;
    this.items = [];
    if (this.report !== undefined && this.report !== null) {
      for (const e of Object.entries(this.report?.modelKPI)) {
        const kpi = e[0];
        const key = `kpi.${kpi}`;
        this.items.push({
          key: `${this.$te(key) ? this.$t(key) : kpi}`,
          kpi: e[0],
          value: this.intFormat.format(e[1]),
          group: "kpi-dialog.model-kpi"
        });
      }
      for (const e of Object.entries(this.report?.logKPI)) {
        let kpi = e[0];
        let key = `kpi.${kpi}`;
        const d = this.report?.logKPI[kpi];
        let value = "";

        if (kpi == "urn:processm:statistics/count") {
          key = "kpi-dialog.log-count";
          value = this.intFormat.format(d.median!);
        } else {
          const f = this.numberFormat.format;
          let unit = "";
          if (kpi.endsWith("_time")) unit = ` ${this.$t("kpi.days")}`;
          if (kpi.startsWith("cost:total:")) {
            unit = " " + kpi.substr("cost:total:".length);
            key = "kpi.cost:total";
            kpi = "cost:total";
          }
          if (d.standardDeviation == 0 && d.average == d.min && d.min == d.max) value = `${f(d.average!)}${unit}`;
          else value = `${f(d.average!)} ± ${f(d.standardDeviation!)}${unit} [${f(d.min!)}, ${f(d.max!)}]`;
        }
        this.items.push({
          key: `${this.$te(key) ? this.$t(key) : kpi}`,
          kpi: kpi,
          value: value,
          group: "kpi-dialog.log-kpi"
        });
      }
    }
    this.loading = false;
  }
}
</script>