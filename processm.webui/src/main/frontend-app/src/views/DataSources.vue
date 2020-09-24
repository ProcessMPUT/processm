<template>
  <v-container class="pb-0 px-0"> </v-container>
</template>

<style scoped></style>

<script lang="ts">
import Vue from "vue";
import Inject from "vue-property-decorator";
import DataSourceService from "@/services/DataSourceService";
import DataSource from "@/models/DataSource";

export default class DataSources extends Vue {
  @Inject() dataSourceService!: DataSourceService;
  dataSources: Array<DataSource> = [];

  async created() {
    this.dataSources = await this.dataSourceService.getAll();
  }
}
</script>
