<template>
  <v-app-bar app color="primary" absolute dense dark flat>
    <v-spacer></v-spacer>
    <v-menu left bottom offset-y>
      <template #activator="{ on }">
        <v-btn icon v-on="on" name="btn-profile">
          <v-icon>person</v-icon>
        </v-btn>
      </template>

      <v-list dense rounded>
        <v-subheader>{{ username }}</v-subheader>
        <v-list-item :to="'profile'" color="primary" name="btn-settings">
          <v-list-item-icon>
            <v-icon>settings</v-icon>
          </v-list-item-icon>
          <v-list-item-content>{{ $t("topbar.user-profile") }}</v-list-item-content>
        </v-list-item>
        <v-list-item @click.stop="signOut" name="btn-logout">
          <v-list-item-icon>
            <v-icon>logout</v-icon>
          </v-list-item-icon>
          <v-list-item-content>{{ $t("topbar.sign-out") }}</v-list-item-content>
        </v-list-item>
        <v-list-group v-model="organizationsExpanded" prepend-icon="groups3" @click.stop.prevent="">
          <template v-slot:activator>
            <v-list-item-content>
              {{ $t("users.organizations") }}
            </v-list-item-content>
          </template>
          <v-list-item-group v-model="currentOrganization" mandatory>
            <v-list-item v-for="(roleInOrganization, i) in this.organizations" :key="i" :value="i">
              <v-list-item-icon></v-list-item-icon>
              <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                  <v-list-item-content
                    v-text="roleInOrganization.organization.name"
                    @click="changeOrganization(i)"
                    v-bind="attrs"
                    v-on="on"
                  ></v-list-item-content>
                </template>
                <span>{{ $t("users.unique-organization-id") }}: {{ roleInOrganization.organization.id }}</span>
              </v-tooltip>
            </v-list-item>
          </v-list-item-group>
        </v-list-group>
      </v-list>
    </v-menu>
  </v-app-bar>
</template>

<script lang="ts">
import Vue, { watch } from "vue";
import { Component, Inject } from "vue-property-decorator";
import AccountService from "@/services/AccountService";
import { UserRoleInOrganization } from "@/openapi";

@Component
export default class TopBar extends Vue {
  @Inject() accountService!: AccountService;

  currentOrganization: number = this.$sessionStorage.currentOrganizationIndex ?? -1;
  organizationsExpanded: boolean = true;

  organizations: UserRoleInOrganization[] = [];

  async created() {
    this.organizations = await this.accountService.getUserOrganizations();
    watch(Vue.prototype.$sessionStorage.userOrganizationsRef, (newValue: UserRoleInOrganization[]) => {
      this.organizations = newValue;
    });
  }

  get username() {
    return this.$sessionStorage.userInfo?.username || "";
  }

  async signOut() {
    if (!this.$sessionStorage.sessionExists) {
      return;
    }

    await this.accountService.signOut().finally(() => this.$router.push({ name: "login" }));
  }

  changeOrganization(i: number) {
    if (this.$sessionStorage.currentOrganizationIndex != i) {
      this.$sessionStorage.currentOrganizationIndex = i;
      // Refresh page for all components to take the new organization into account
      this.$router.go(0);
    }
  }
}
</script>