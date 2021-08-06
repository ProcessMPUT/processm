<template>
  <v-data-table
    :headers="[
      {
        text: $t('data-stores.name'),
        value: 'name',
        filterable: true
      },
      {
        text: $t('data-stores.created-at'),
        value: 'createdAt'
      }
    ]"
    :items="dataStores"
    :loading="loading"
    item-key="id"
  >
    <template v-slot:top>
      <v-toolbar flat>
        <v-toolbar-title>Data stores</v-toolbar-title>
        <v-divider class="mx-4" inset vertical></v-divider>
        <v-spacer></v-spacer>
        <v-dialog v-model="newDialog" max-width="600px">
          <template v-slot:activator="{ on, attrs }">
            <v-btn v-bind="attrs" v-on="on" color="primary">
              {{ $t("data-stores.new") }}
            </v-btn>
          </template>
          <v-card>
            <v-card-title>{{ $t("data-stores.new") }}</v-card-title>
            <v-card-text>
              <v-form>
                <v-text-field
                  :label="$t('data-stores.name')"
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
                {{ $t("data-stores.new") }}
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
import DataStoreService from "@/services/DataStoreService";
import DataStore from "@/models/DataStore";

@Component
export default class DataStoresList extends Vue {
  @Inject() dataStoreService!: DataStoreService;
  dataStores: Array<DataStore> = [];
  loading = true;
  newDialog = false;
  newName = "";

  async mounted() {
    this.loading = true;
    this.dataStores = await this.dataStoreService.getAll();
    this.loading = false;
  }

  async addNew() {
    this.loading = true;
    this.newDialog = false;
    await this.dataStoreService.createDataStore(this.newName);
    this.dataStores = await this.dataStoreService.getAll();
    this.loading = false;
    this.newName = "";
  }
}
</script>
