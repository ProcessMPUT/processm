<template>
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex xs12 sm8 md4>
        <v-card class="elevation-12" min-width="470">
          <v-toolbar color="primary" dark flat>
            <v-img class="mr-3" max-height="48" max-width="48" src="favicon.png" />
            <v-toolbar-title>{{ $t("login-form.title") }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-alert v-if="app.config.loginMessage" text type="info">
              {{ app.config.loginMessage }}
            </v-alert>
            <v-form id="loginForm" ref="loginForm" v-model="isValidForm" @submit.prevent="authenticate">
              <v-text-field
                :label="$t('common.email')"
                v-model="username"
                name="username"
                prepend-icon="person"
                type="text"
                :rules="[(v) => /.+@.+\..+/.test(v) || $t('login-form.validation.email-format')]"
              ></v-text-field>

              <v-text-field
                id="password"
                :label="$t('login-form.password')"
                v-model="password"
                name="password"
                prepend-icon="lock"
                type="password"
                :rules="[(v) => !!v || $t('login-form.validation.password-empty')]"
                @keypress.enter="authenticate"
              ></v-text-field>
              <v-layout>
                <v-btn v-if="!app.config.demoMode" color="primary" name="btn-register" text to="register">
                  {{ $t("login-form.register-account") }}
                </v-btn>
                <v-btn v-if="!app.config.demoMode" color="primary" text to="reset-password">
                  {{ $t("login-form.reset-password") }}
                </v-btn>
                <v-spacer></v-spacer>
                <v-btn color="primary" name="btn-login" form="loginForm" type="submit">
                  {{ $t("login-form.login") }}
                </v-btn>
              </v-layout>
            </v-form>
          </v-card-text>
        </v-card>
        <v-snackbar v-model="errorMessage" :timeout="errorTimeout" color="error">
          {{ $t("login-form.error-box.failed") }}
          <v-btn dark text @click="errorMessage = false">{{ $t("common.close") }}</v-btn>
        </v-snackbar>
      </v-flex>
    </v-layout>
    <v-dialog v-model="selectOrganizationDialog" max-width="500px">
      <template>
        <v-card title="Dialog">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>{{ $t("users.organizations") }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            {{ $t("users.select-organization") }}:
            <v-select v-model="selectedOrganizationId" item-value="id" :items="organizations" item-text="name" name="combo-organization">
              <template v-slot:item="{ parent, item, on, attrs }">
                <v-tooltip bottom>
                  <template v-slot:activator="{ on, attrs }">
                    <span v-bind="attrs" v-on="on">{{ item.name }}</span>
                  </template>
                  <span>{{ $t("users.unique-organization-id") }}: {{ item.id }}</span>
                </v-tooltip>
              </template>
            </v-select>
          </v-card-text>

          <v-card-actions>
            <v-spacer></v-spacer>

            <v-btn color="primary" @click="organizationSelected" name="btn-select-organization">
              {{ $t("common.submit") }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </template>
    </v-dialog>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import AccountService from "@/services/AccountService";
import { Organization, UserRoleInOrganization } from "@/openapi";
import App from "@/App.vue";

@Component
export default class Login extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  readonly errorTimeout = 3000;
  isValidForm = false;
  errorMessage = false;
  username = "";
  password = "";
  selectOrganizationDialog: boolean = false;
  organizations: Organization[] = [];
  selectedOrganizationId: string | null = null;

  async mounted() {
    // Manually call to resetLocale to overwrite the locale of the user that just logged out
    // Since there's no user at this point, the login page will display in the first available language in the order of preference of the web browser
    this.app.resetLocale();
  }

  async authenticate() {
    if (!this.isValidForm) {
      return (this.$refs.loginForm as Vue & {
        validate: () => boolean;
      }).validate();
    }

    try {
      await this.accountService.signIn(this.username, this.password);

      await this.accountService.updateUserInfo();
      this.app.resetLocale();
      const organizations = await this.accountService.getUserOrganizations();
      this.setCurrentOrganization(organizations);
    } catch (error) {
      console.error(error);
      this.errorMessage = true;
    }
  }

  setLanguage(language: string) {
    this.app.setLocale(language);
  }

  private goHome() {
    this.$router.push({ name: "home" });
  }

  setCurrentOrganization(userOrganizations: UserRoleInOrganization[]) {
    if (userOrganizations.length > 1) {
      this.organizations = userOrganizations.map((it) => it.organization);
      this.selectedOrganizationId = userOrganizations[0].organization.id ?? null;
      this.selectOrganizationDialog = true;
    } else if (userOrganizations.length == 1) {
      this.$sessionStorage.currentOrganizationIndex = 0;
      this.goHome();
    } else {
      this.app.error(this.$t("users.no-organization-error").toString());
      this.goHome();
    }
  }

  organizationSelected() {
    if (this.selectedOrganizationId !== null) {
      this.selectOrganizationDialog = false;

      this.$sessionStorage.currentOrganizationIndex = this.organizations.findIndex((org) => org.id == this.selectedOrganizationId);
      this.selectedOrganizationId = null;
      this.goHome();
    }
  }
}
</script>
