<template>
  <div class="log-view">
    <div class="scroll-container">
      <v-card-title v-if="showSearch">
        <v-text-field v-model="search" :label="$t('common.search')" append-icon="magnify" hide-details single-line></v-text-field>
      </v-card-title>
      <v-data-table
        ref="table"
        :custom-group="groupItems"
        :custom-sort="sortItems"
        :disable-pagination="true"
        :header-props="{
          'sort-icon': ''
        }"
        :headers="headers"
        :hide-default-footer="true"
        :items="items"
        :loading="loading"
        :multi-sort="false"
        :must-sort="true"
        :search="search"
        :sort-by="classifier"
        dense
        group-by="_parentId"
      >
        <template v-slot:group.header="{ headers, items, isOpen, toggle }">
          <td v-if="items.every((item) => item.scope === XesComponentScope.Event)" :colspan="headers.length">
            <v-btn icon plain @click="toggle">
              <v-icon>{{ isOpen ? "remove" : "add" }}</v-icon>
            </v-btn>
            <v-breadcrumbs :items="items" class="d-inline overflow-x-auto" dense>
              <template v-slot:divider>&gt;</template>
              <template v-slot:item="{ item }">
                <v-tooltip v-if="!!item.type && item.type != DeviationType.None" bottom max-width="600px">
                  <template v-slot:activator="{ on, attrs }">
                    <li :class="deviationToClass(item)" v-bind="attrs" v-on="on">{{ getLabel(item) }}</li>
                  </template>
                  <span>{{ deviationToTitle(item) }}</span>
                </v-tooltip>
                <li v-if="item.type === undefined || item.type == DeviationType.None">{{ getLabel(item) }}</li>
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

.log-view ul.v-breadcrumbs {
  padding-left: 6px;
}

.scroll-container {
  overflow: auto;
}

.log-deviation {
  text-decoration: underline #ff0000 wavy 0.1em;
  position: relative;
  z-index: 1;
}

.model-deviation {
  text-decoration: underline #ff00ff wavy 0.1em;
  position: relative;
  z-index: 1;
}

.model-deviation.silent {
  text-decoration: underline #505050 wavy 0.05em;
}
</style>
<style>
.log-view .v-breadcrumbs li {
  padding: 0;
}

.log-view .v-breadcrumbs li.v-breadcrumbs__divider {
  padding: 0 6px;
}
</style>

<script lang="ts">
import { Component, Prop, Vue } from "vue-property-decorator";
import { LogItem, XesComponentScope } from "@/utils/XesProcessor";
import { ItemGroup } from "vuetify";
import { DeviationType } from "@/openapi";
import { LocaleMessages } from "vue-i18n";

@Component
export default class LogTable extends Vue {
  DeviationType = DeviationType;
  XesComponentScope = XesComponentScope;

  @Prop({ default: [] })
  headers!: Array<Header>;
  @Prop({ default: [] })
  items!: Array<LogItem>;
  @Prop({ default: false })
  loading!: boolean;
  @Prop({ default: "" })
  search!: string;
  @Prop({ default: "concept:name" })
  classifier!: string;
  @Prop({ default: false })
  showSearch!: boolean;

  groupItems(items: LogItem[], groupBy: string[], groupDesc: boolean[]): ItemGroup<LogItem>[] {
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
        }
      }
      return result;
    }, {} as Record<string, LogItem[]>);

    const collapse = () => {
      const table: any = this.$refs.table;
      const instance = table.$vnode.componentInstance;
      for (const group in groups) {
        instance.$set(instance.openCache, group, false);
      }
    };

    // if LogTable is initially hidden, the first call to groupItems() is within render() when $refs.table is not set yet
    if (this.$refs.table !== undefined) collapse();
    else setTimeout(collapse, 0);

    const out = Object.keys(groups).map((key) => ({ name: key, items: groups[key] } as ItemGroup<LogItem>));
    return out;
  }

  sortItems(items: any[], sortBy: string): any[] {
    // HACK: this function does not sort but sets the classifier based on the header clicked
    // Unfortunately, v-data-table does not offer an option to handle header click event otherwise.
    this.classifier = sortBy[1];
    return items;
  }

  deviationToClass(item?: LogItem): string {
    switch (item?.type) {
      case DeviationType.ModelDeviation:
        if (item["_isSilent"]) return "model-deviation silent";
        return "model-deviation";
      case DeviationType.LogDeviation:
        return "log-deviation";
      default:
        return "";
    }
  }

  getLabel(item: LogItem) {
    return item[this.classifier] !== undefined ? item[this.classifier] : this.$t("workspace.component.flat-log.no-data");
  }

  deviationToTitle(item?: LogItem): string | LocaleMessages {
    switch (item?.type) {
      case DeviationType.ModelDeviation:
        if (item["_isSilent"]) return this.$t("workspace.component.flat-log.model-deviation-silent");
        return this.$t("workspace.component.flat-log.model-deviation");
      case DeviationType.LogDeviation:
        return this.$t("workspace.component.flat-log.log-deviation");
      default:
        return "";
    }
  }
}

export interface Header {
  text: string;
  value: string;
}
</script>
