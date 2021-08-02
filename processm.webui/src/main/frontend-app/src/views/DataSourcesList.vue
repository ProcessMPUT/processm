<template>
  <v-data-table
    :headers="[
      {
        text: $t('data-sources.name'),
        value: 'name',
        filterable: true
      },
      {
        text: $t('data-sources.created-at'),
        value: 'createdAt'
      }
    ]"
    :items="dataSources"
    :loading="loading"
    item-key="id"
  >
    <template v-slot:top>
      <v-toolbar flat>
        <v-toolbar-title>Data sources</v-toolbar-title>
        <v-divider class="mx-4" inset vertical></v-divider>
        <v-spacer></v-spacer>
        <v-dialog v-model="newDialog" max-width="600px">
          <template v-slot:activator="{ on, attrs }">
            <v-btn v-bind="attrs" v-on="on" color="primary">
              {{ $t("data-sources.new") }}
            </v-btn>
          </template>
          <v-card>
            <v-card-title>{{ $t("data-sources.new") }}</v-card-title>
            <v-card-text>
              <v-form>
                <v-text-field
                  :label="$t('data-sources.name')"
                  type="text"
                  v-model="newName"
                ></v-text-field>
              </v-form>
            </v-card-text>
            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn color="secondary" text @click="newDialog = false">
                {{ $t("common.cancel") }}
              </v-btn>
              <v-btn color="primary" text @click="addNew">
                {{ $t("data-sources.new") }}
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>
      </v-toolbar>
    </template>
  </v-data-table>
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
  newDialog = false;
  newName = "";

  async mounted() {
    this.loading = true;
    this.dataSources = await this.dataSourceService.getAll();
    this.loading = false;
  }

  async addNew() {
    this.loading = true;
    this.newDialog = false;
    await this.dataSourceService.createDataStore(this.newName);
    this.dataSources = await this.dataSourceService.getAll();
    this.loading = false;
    this.newName = "";
  }
}
</script>
