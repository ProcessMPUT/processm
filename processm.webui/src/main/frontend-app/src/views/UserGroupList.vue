<template>
  <v-data-table
    :headers="[
      {
        text: $t('users.group'),
        value: 'name',
        filterable: true
      },
      {
        text: $t('users.implicit'),
        value: 'isImplicit',
        filterable: true
      },
      {
        text: $t('users.shared'),
        value: 'isShared',
        filterable: true
      },
      {
        text: $t('users.organization'),
        value: 'organizationId', // TODO: replace with organization name
        filterable: true
      },
      {
        text: $t('common.actions'),
        value: 'actions',
        align: 'center',
        sortable: false
      }
    ]"
    :items="groups"
    :loading="loading"
    item-key="id"
  >
    <template v-slot:top>
      <v-toolbar flat>
        <v-toolbar-title> {{ $t("users.groups") }} {{ $t("common.in") }} {{ organization.name }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-dialog v-model="newDialog" max-width="600px" @input.capture="resetNewDialog">
          <template v-slot:activator="{ on, attrs }">
            <v-btn color="primary" v-bind="attrs" v-on="on">
              {{ $t("common.add-new") }}
            </v-btn>
          </template>
          <v-card>
            <v-card-title>{{ $t("common.add-new") }}</v-card-title>
            <v-card-text>
              <v-form id="newForm" ref="newForm" v-model="isNewValid" @submit.prevent="addGroup">
                <v-text-field v-model="newName" :rules="[(v) => !!v || $t('users.group-empty')]"></v-text-field>
              </v-form>
            </v-card-text>
            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn color="primary darken-1" text @click="newDialog = false">
                {{ $t("common.cancel") }}
              </v-btn>

              <v-btn :disabled="!isNewValid" color="primary darken-1" form="newForm" type="submit">
                {{ $t("common.save") }}
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>
      </v-toolbar>
    </template>

    <template v-slot:item.actions="{ item, index }">
      <v-tooltip bottom>
        <template v-slot:activator="{ on, attrs }">
          <v-btn :disabled="groups[index].isImplicit || groups[index].isShared" color="primary" dark icon v-bind="attrs" v-on="on">
            <v-icon small @click="removeGroup(item)">delete_forever</v-icon>
          </v-btn>
        </template>
        <span>{{ $t("users.exclude-member") }}</span>
      </v-tooltip>
    </template>
  </v-data-table>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import { Group } from "@/openapi";
import ComboBoxWithSearch from "@/components/ComboBoxWithSearch.vue";
import App from "@/App.vue";
import GroupService from "@/services/GroupService";

@Component({
  components: { ComboBoxWithSearch }
})
export default class UserGroupList extends Vue {
  @Inject() app!: App;
  @Inject() groupService!: GroupService;
  @Inject() organizationService!: OrganizationService;

  loading = true;

  groups: Array<Group> = [];
  organization = this.$sessionStorage.currentOrganization;

  newDialog = false;
  newName = "";
  isNewValid = false;

  async mounted() {
    this.refreshGroups();
  }

  async refreshGroups() {
    try {
      this.loading = true;
      this.groups = await this.groupService.getUserGroups(this.organization.id!);
    } catch (e) {
      this.app.error(e);
    } finally {
      this.loading = false;
    }
  }

  resetNewDialog() {
    this.newName = "";
  }

  async addGroup() {
    try {
      console.assert(this.newName != "", "newName: " + this.newName);
      console.debug("adding group ", this.newName);
      await this.groupService.createGroup(this.organization.id!, this.newName);
      this.refreshGroups();
      this.newDialog = false;
      this.resetNewDialog();
      this.app.info(this.$t("users.group-added").toString());
    } catch (e) {
      this.app.error(e);
    }
  }

  async updateGroup() {
    try {
      // TODO

      this.app.info(this.$t("users.group-updated").toString());
    } catch (e) {
      this.app.error(e);
    }
  }

  async removeGroup(group: Group) {
    try {
      console.assert(group.id !== undefined);
      await this.groupService.removeGroup(this.organization.id!, group.id!);
      this.groups = this.groups.filter((item) => item != group);
      this.app.info(this.$t("users.group-removed").toString());
    } catch (e) {
      this.app.error(e);
    }
  }
}
</script>
