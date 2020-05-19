<template>
  <v-data-table
    :headers="[
      {
        text: 'User',
        value: 'username',
        filterable: true
      },
      { text: 'Role', value: 'organizationRole' }
    ]"
    :items="organizationMembers"
    item-key="id"
    light
  ></v-data-table>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import OrganizationMember from "@/models/OrganizationMember";

@Component
export default class UserList extends Vue {
  @Inject() organizationService!: OrganizationService;
  organizationMembers: Array<OrganizationMember> = [];

  async mounted() {
    const currentOrganization = this.$sessionStorage.currentOrganization;
    this.organizationMembers = await this.organizationService.getOrganizationMembers(
      currentOrganization.id
    );
  }
}
</script>
