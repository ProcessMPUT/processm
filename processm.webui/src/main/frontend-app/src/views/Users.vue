<template>
  <v-container
    class="d-flex align-stretch pa-0 align-content-start"
    fluid
    fill-height
  >
    <v-row class="mx-0">
      <v-tabs
        v-model="tab"
        background-color="accent"
        centered
        light
        icons-and-text
        grow
      >
        <v-tab href="#users">
          {{ $t("users.users") }}
          <v-icon>group</v-icon>
        </v-tab>

        <v-tab href="#groups">
          {{ $t("users.groups") }}
          <v-icon>group_work</v-icon>
        </v-tab>
      </v-tabs>
    </v-row>
    <v-row class="ma-0">
      <v-col cols="12">
        <v-tabs-items v-model="tab">
          <v-tab-item value="users">
            <user-list />
          </v-tab-item>
          <v-tab-item value="groups">
            <user-group-list />
          </v-tab-item>
        </v-tabs-items>
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped>
.v-tabs button.v-btn {
  min-width: 30px;
  width: 30px;
  height: 100%;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import { OrganizationMember } from "@/openapi";
import UserList from "./UserList.vue";
import UserGroupList from "./UserGroupList.vue";

@Component({
  components: { UserList, UserGroupList }
})
export default class Users extends Vue {
  @Inject() organizationService!: OrganizationService;
  organizationMembers: Array<OrganizationMember> = [];
  tab = 0;

  async mounted() {
    const currentOrganization = this.$sessionStorage.currentOrganization;
    this.organizationMembers = await this.organizationService.getOrganizationMembers(
      currentOrganization.id
    );
  }
}
</script>
