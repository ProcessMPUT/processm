<template>
  <v-data-table
      :headers="[
      {
        text: 'Name',
        value: 'name',
        filterable: true
      },
      {
        text: 'Created at',
        value: 'createdAt'
      }
    ]"
      :items="dataSources"
      :loading="loading"
      item-key="id"
  ></v-data-table>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import DataSourceService from "@/services/DataSourceService";
import DataSource from "@/models/DataSource";

@Component
export default class DataSourcesList extends Vue {
  @Inject() dataSourceService!: DataSourceService;
  dataSources: Array<DataSource> = [];
  loading = true;

  async mounted() {
    this.loading = true;
    this.dataSources = await this.dataSourceService.getAll();
    this.loading = false;
  }
}
</script>
