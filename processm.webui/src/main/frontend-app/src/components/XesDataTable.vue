<template>
  <!-- Setting v-container.fluid to 'true' makes the container take up all the full width. -->
  <v-container :fluid="true">
    <v-row>
      <tree-table
        ref="treeTable"
        v-bind="$attrs"
        :data="filteredItems"
        :columns="columns"
        :expand-type="false"
        :border="true"
        :tree-type="true"
        :expand-key="scopeFieldName"
        :selectable="removable"
        @checkbox-click="entryChecked"
      >
        <template :slot="scopeFieldName" slot-scope="scope">
          <v-icon>$xes{{ scope.row.scope }}</v-icon>
        </template>
      </tree-table>
    </v-row>
    <v-row>
      <v-col>
        <v-btn
          color="primary"
          v-if="removable"
          @click="removeSelectedEntries"
          :disabled="selectedEntries.length == 0"
        >
          {{ $t("common.remove") }}
        </v-btn>
      </v-col>
      <v-col>
        <v-data-footer
          v-if="!disablePagination && filteredItems.length > 0"
          v-bind="$attrs"
          :disable-pagination="disablePagination"
          :options.sync="options"
          :pagination.sync="pagination"
          @update:options="updateOptions"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";
import TreeTable from "tree-table-vue-extend";
import { DataOptions, DataPagination } from "vuetify";

class LogHeader {
  constructor(
    readonly key: string,
    title: string | null = null,
    width: number | null = null
  ) {
    this.title = title ?? key;
    this.width = width;
  }

  readonly title: string;
  type?: string;
  template?: string;
  width?: number | null;
}

@Component({
  components: { TreeTable }
})
export default class XesDataTable extends Vue {
  @Prop({ default: 1 })
  readonly itemsPerPage!: number;

  @Prop({ default: false })
  readonly disablePagination!: boolean;

  @Prop({ default: () => [] })
  readonly items?: [];

  @Prop({ default: () => [] })
  readonly headers?: [];

  @Prop({ default: false })
  readonly removable!: boolean;

  readonly xesLogAttributeName = "identity:id";
  selectedEntries = new Array<string>();

  entryChecked(entry: { [attribute: string]: string | undefined }) {
    const identityAttribute = entry[this.xesLogAttributeName];

    if (identityAttribute == null) return;

    const entryIndex = this.selectedEntries.indexOf(identityAttribute);

    if (entryIndex > -1) {
      this.selectedEntries.splice(entryIndex, 1);
    } else {
      this.selectedEntries.push(identityAttribute);
    }
  }

  async removeSelectedEntries() {
    const isRemovalConfirmed = await this.$confirm(
      `${this.$t("xes-data-table.logs-removal-confirmation", {
        count: this.selectedEntries.length
      })}`,
      {
        title: `${this.$t("common.warning")}`
      }
    );

    if (isRemovalConfirmed) {
      this.$emit("removed", this.selectedEntries);
    }

    this.selectedEntries = [];
  }

  private readonly scopeFieldName = "scope";
  private readonly options: DataOptions = {
    page: 1,
    itemsPerPage: this.itemsPerPage,
    sortBy: [],
    sortDesc: [],
    groupBy: [],
    groupDesc: [],
    multiSort: false,
    mustSort: false
  };
  private readonly pagination: DataPagination = {
    page: 0,
    itemsPerPage: 0,
    pageStart: 0,
    pageStop: 0,
    pageCount: 0,
    itemsLength: 0
  };

  created() {
    this.options.itemsPerPage = this.itemsPerPage;
    this.updateOptions(this.options);
  }

  @Watch("items")
  itemsDataChanged() {
    this.pagination.itemsLength = this.allItems.length;
    this.updateOptions(this.options);
  }

  @Watch("headers")
  headersDataChanged() {
    const headers = new Array<LogHeader>();

    this.headers?.reduce((headers: LogHeader[], headerName: string) => {
      if (headerName.startsWith("_")) return headers;

      const header = new LogHeader(headerName);

      if (headerName == this.scopeFieldName) {
        header.type = "template";
        header.template = headerName;
        header.width = 112;
      }

      headers.push(header);

      return headers;
    }, headers);

    if (headers.length === 0) headers.push(new LogHeader(this.scopeFieldName));

    this.columns = headers;
  }

  updateOptions(options: DataOptions) {
    const itemsPerPage =
      options.itemsPerPage == -1 ? this.allItems.length : options.itemsPerPage;
    this.pagination.itemsPerPage = itemsPerPage;
    this.pagination.pageCount = Math.ceil(this.allItems.length / itemsPerPage);
    this.pagination.page = options.page;
    this.pagination.pageStart = options.itemsPerPage * (options.page - 1);
    this.pagination.pageStop = this.pagination.pageStart + itemsPerPage;
    this.filteredItems = this.disablePagination
      ? this.allItems
      : (this.allItems.slice(
          this.pagination.pageStart,
          this.pagination.pageStop
        ) as []);
  }

  private get allItems(): [] {
    return this.items as [];
  }

  private columns: LogHeader[] = [];
  private filteredItems?: [];
}
</script>
