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
    :loading="loading"
    item-key="id"
  >
    <template v-slot:top>
      <v-toolbar flat>
        <v-toolbar-title>{{ $t("users.users") }}</v-toolbar-title>
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
              <registration-form
                @cancel="newDialog = false"
                @success="newDialog = false"
              ></registration-form>
            </v-card-text>
          </v-card>
        </v-dialog>
      </v-toolbar>
    </template>
  </v-data-table>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import OrganizationMember from "@/models/OrganizationMember";
import RegistrationForm from "@/components/RegistrationForm.vue";

@Component({
  components: { RegistrationForm }
})
export default class UserList extends Vue {
  @Inject() organizationService!: OrganizationService;
  organizationMembers: Array<OrganizationMember> = [];
  loading = true;
  newDialog = false;

  async mounted() {
    this.loading = true;
    const currentOrganization = this.$sessionStorage.currentOrganization;
    this.organizationMembers = await this.organizationService.getOrganizationMembers(
      currentOrganization.id
    );
    this.loading = false;
  }
}
</script>
