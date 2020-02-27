<template>
  <v-app-bar app color="primary" absolute dense dark flat>
    <v-spacer></v-spacer>
    <v-menu left bottom offset-y>
      <template v-slot:activator="{ on }">
        <v-btn icon v-on="on">
          <v-icon>person</v-icon>
        </v-btn>
      </template>

      <v-list dense rounded>
        <v-subheader>{{ username }}</v-subheader>
        <v-list-item :to="'profile'" color="primary">
          <v-list-item-icon><v-icon>settings</v-icon></v-list-item-icon>
          <v-list-item-content>{{
            $t("topbar.user-profile")
          }}</v-list-item-content>
        </v-list-item>
        <v-list-item @click.stop="signOut">
          <v-list-item-icon><v-icon>logout</v-icon></v-list-item-icon>
          <v-list-item-content>{{ $t("topbar.sign-out") }}</v-list-item-content>
        </v-list-item>
      </v-list>
    </v-menu>
  </v-app-bar>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import AccountService from "@/services/AccountService";

@Component
export default class TopBar extends Vue {
  @Inject() accountService!: AccountService;

  get username() {
    return this.$sessionStorage.userInfo?.username || "";
  }

  async signOut() {
    if (!this.$sessionStorage.sessionExists) {
      return;
    }

    if (this.accountService.signOut(this.$sessionStorage.sessionToken)) {
      this.$sessionStorage.removeSession();
      this.$router.push({ name: "login" });
    }
  }
}
</script>
