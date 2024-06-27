<template>
  <v-container :fluid="true">
    <v-data-table
      :headers="[
        {
          text: $t('common.name'),
          value: 'name',
          filterable: true
        },
        {
          text: $t('common.created-at'),
          value: 'createdAt'
        },
        {
          text: $t('common.actions'),
          value: 'actions',
          align: 'center',
          sortable: false
        }
      ]"
      :items="dataStores"
      :loading="loading"
      item-key="id"
    >
      <template v-slot:top>
        <v-toolbar flat>
          <v-toolbar-title>
            {{ $t("data-stores.page-title") }}
            <v-tooltip bottom max-width="600px">
              <template v-slot:activator="{ on, attrs }">
                <v-icon v-bind="attrs" v-on="on">help</v-icon>
              </template>
              <span>{{ $t("data-stores.page-hint") }}</span>
            </v-tooltip>
          </v-toolbar-title>
          <v-spacer></v-spacer>
          <v-dialog v-model="newDialog" max-width="600px">
            <template v-slot:activator="{ on, attrs }">
              <v-btn v-bind="attrs" v-on="on" color="primary" name="btn-add-new">
                {{ $t("common.add-new") }}
              </v-btn>
            </template>
            <v-card>
              <v-card-title>{{ $t("common.add-new") }}</v-card-title>
              <v-card-text>
                <v-form>
                  <v-text-field v-model="newName" name="new-name" :hint="$t('data-stores.name-hint')" :label="$t('common.name')" type="text"></v-text-field>
                </v-form>
              </v-card-text>
              <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn color="secondary" text @click="newDialog = false">
                  {{ $t("common.cancel") }}
                </v-btn>
                <v-btn color="primary" text name="btn-add-new-confirm" @click="addNew">
                  {{ $t("common.submit") }}
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-dialog>
        </v-toolbar>
      </template>
      <template v-slot:item.actions="{ item }">
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-rename-data-store">
              <v-icon small @click="dataStoreIdToRename = item.id">edit</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.rename") }}</span>
        </v-tooltip>
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-configure-data-store">
              <v-icon small @click="configureDataStore(item.id)">settings</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.configure") }}</span>
        </v-tooltip>
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-delete-data-store">
              <v-icon small @click="removeDataStore(item)">delete_forever</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.remove") }}</span>
        </v-tooltip>
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-data-store-security">
              <v-icon small @click="configureACL(item.id)">security</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.security") }}</span>
        </v-tooltip>
      </template>
    </v-data-table>
    <data-store-configuration
      :data-store-id="dataStoreIdToConfigure"
      :value="dataStoreIdToConfigure != null"
      @closed="closeDataStoreConfiguration"
      @changed="dataStoreChanged"
    />
    <acl-dialog :value="dataStoreUrn != null" :urn="dataStoreUrn" @closed="dataStoreUrn = null" :force-view-only="false" />
    <rename-dialog
      :value="dataStoreIdToRename != null"
      :old-name="dataStoreNameToRename"
      @cancelled="dataStoreIdToRename = null"
      @submitted="renameDataStore"
    />
  </v-container>
</template>
<style scoped>
.data-connectors {
  text-align: center;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import DataStoreService from "@/services/DataStoreService";
import DataStore from "@/models/DataStore";
import DataStoreConfiguration from "@/views/DataStoreConfiguration.vue";
import RenameDialog from "@/components/RenameDialog.vue";
import App from "@/App.vue";
import AclDialog from "@/components/acl/AclDialog.vue";

@Component({
  components: { AclDialog, DataStoreConfiguration, RenameDialog }
})
export default class DataStores extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  dataStores: Array<DataStore> = [];
  loading = true;
  newDialog = false;
  newName = "";
  dataStoreIdToConfigure: string | null = null;
  dataStoreIdToRename: string | null = null;
  aclDialogDisplayed: boolean = false;
  dataStoreUrn: string | null = null;

  get dataStoreNameToRename(): string | null {
    return this.dataStores.find((dataStore) => dataStore.id == this.dataStoreIdToRename)?.name || null;
  }

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

  async renameDataStore(newName: string) {
    try {
      if (
        this.dataStoreIdToRename != null &&
        (await this.dataStoreService.updateDataStore(this.dataStoreIdToRename, {
          id: this.dataStoreIdToRename,
          name: newName
        }))
      ) {
        const dataStore = this.dataStores.find((dataStore) => dataStore.id == this.dataStoreIdToRename);

        if (dataStore != null) dataStore.name = newName;
      }
    } catch (error) {
      this.app.error(`${this.$t("common.saving.failure")}`);
    }
    this.dataStoreIdToRename = null;
  }

  async removeDataStore(dataStore: DataStore) {
    const isRemovalConfirmed = await this.$confirm(
      `${this.$t("data-stores.removal-confirmation", {
        dataStoreName: dataStore.name
      })}`,
      {
        title: `${this.$t("common.warning")}`,
        buttonTrueText: this.$t("common.yes").toString(),
        buttonFalseText: this.$t("common.no").toString()
      }
    );

    if (!isRemovalConfirmed) return;

    try {
      await this.dataStoreService.removeDataStore(dataStore.id);
      this.dataStores.splice(this.dataStores.indexOf(dataStore), 1);
      this.app.success(`${this.$t("common.removal.success")}`);
    } catch (error) {
      this.app.error(`${this.$t("common.removal.failure")}`);
    }
  }

  configureDataStore(dataStoreId: string) {
    this.dataStoreIdToConfigure = dataStoreId;
  }

  closeDataStoreConfiguration() {
    this.dataStoreIdToConfigure = null;
  }

  configureACL(dataStoreId: string) {
    this.dataStoreUrn = "urn:processm:db/data_stores/" + dataStoreId;
  }

  dataStoreChanged(dataStore: DataStore) {
    const idx = this.dataStores.findIndex((ds) => ds.id == dataStore.id);
    if (idx >= 0) this.dataStores[idx] = dataStore;
  }
}
</script>