<template>
  <v-data-table
    :headers="[
      {
        text: $t('users.user'),
        value: 'email',
        filterable: true
      },
      {
        text: $t('users.role'),
        value: 'organizationRole'
      },
      {
        text: $t('common.actions'),
        value: 'actions',
        align: 'center',
        sortable: false
      }
    ]"
    :items="members"
    :loading="loading"
    item-key="id"
  >
    <template v-slot:top>
      <v-toolbar flat>
        <v-toolbar-title> {{ $t("users.users") }} {{ $t("common.in") }} {{ organization.name }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-dialog v-model="newDialog" max-width="600px">
          <template v-slot:activator="{ on, attrs }">
            <v-btn v-bind="attrs" v-on="on" color="primary">
              {{ $t("common.add-new") }}
            </v-btn>
          </template>
          <v-card>
            <v-card-title>{{ $t("common.add-new") }}</v-card-title>
            <v-card-text>
              <v-form id="newForm" v-model="isNewValid" @submit.prevent="includeMember">
                <combo-box-with-search :value.sync="newUser" :search="searchUsers"></combo-box-with-search>
                <v-select v-model="newRole" :items="roles"></v-select>
              </v-form>
            </v-card-text>
            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn color="primary darken-1" text @click.stop="newDialog = false">
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

    <template v-slot:item.email="{ item, index }">
      {{ item.email }}
      <v-chip v-if="members[index].email === $sessionStorage.userInfo.username" color="primary" small>
        {{ $t("users.you") }}
      </v-chip>
    </template>

    <template v-slot:item.organizationRole="{ item, index }">
      <v-select
        flat
        solo
        background-color="transparent"
        v-model="members[index].organizationRole"
        :items="roles"
        hide-details="auto"
        :disabled="members[index].email === $sessionStorage.userInfo.username"
      ></v-select>
    </template>

    <template v-slot:item.actions="{ item, index }">
      <v-tooltip bottom>
        <template v-slot:activator="{ on, attrs }">
          <v-btn :disabled="members[index].email === $sessionStorage.userInfo.username" color="primary" dark icon v-bind="attrs" v-on="on">
            <v-icon small @click="excludeMember(item)">delete_forever</v-icon>
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
import { OrganizationMember, OrganizationRole } from "@/openapi/api";
import ComboBoxWithSearch from "@/components/ComboBoxWithSearch.vue";
import AccountService from "@/services/AccountService";
import App from "@/App.vue";

@Component({
  components: { ComboBoxWithSearch }
})
export default class UserList extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  @Inject() organizationService!: OrganizationService;
  members: Array<OrganizationMember> = [];
  loading = true;

  /**
   * Controls visibility of the add user dialog.
   */
  newDialog = false;
  newUser = "";
  newRole = OrganizationRole.Reader;
  isNewValid = false;

  roles = [OrganizationRole.Owner, OrganizationRole.Writer, OrganizationRole.Reader];
  organization = this.$sessionStorage.currentOrganization;

  async loadMembers() {
    this.loading = true;
    this.members = await this.organizationService.getOrganizationMembers(this.organization.id);
    this.loading = false;
  }

  async mounted() {
    await this.loadMembers();
  }

  async searchUsers(value: string): Promise<Array<string>> {
    const list = await this.accountService.getUsers(value);
    return Promise.resolve(list.map((a) => a.email));
  }

  async includeMember() {
    try {
      await this.organizationService.addMember(this.organization.id, this.newUser, this.newRole);
      await this.loadMembers();
      this.newDialog = false;
    } catch (e) {
      this.app.error(e);
    }
  }

  async excludeMember(member: OrganizationMember) {
    try {
      // TODO
    } catch (e) {
      this.app.error(e);
    }
  }

  async updateRole(member: OrganizationMember) {
    try {
      // TODO
    } catch (e) {
      this.app.error(e);
    }
  }
}
</script>
