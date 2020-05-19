<template>
  <v-container fluid>
    <v-layout column>
      <v-card>
        <v-card-text>
          <v-container>
            <v-row class="mb-6">
              <v-label>{{ $sessionStorage.userInfo.username }}</v-label>
            </v-row>
            <v-row>
              <v-select
                v-model="selectedLocale"
                :items="$i18n.availableLocales | objectify"
                :label="$t('user-profile.language')"
                @change="updateLocale"
                dense
                outlined
              >
                <template v-slot:selection="data">
                  <span
                    v-bind:class="[
                      'flag-icon flag-icon-' + $t('flag', data.item.value),
                      'mr-2'
                    ]"
                  />
                  {{ $t("language-name", data.item.value) }}
                </template>
                <template v-slot:item="data">
                  <span
                    v-bind:class="[
                      'flag-icon flag-icon-' + $t('flag', data.item.value),
                      'mr-2'
                    ]"
                  />
                  {{ $t("language-name", data.item.value) }}
                </template>
              </v-select>
            </v-row>
            <v-layout>
              <v-btn
                color="primary lighten-2"
                @click.stop="passwordChangeDialog = true"
              >
                {{ $t("user-profile.change-password") }}
              </v-btn>
              <v-spacer></v-spacer>
            </v-layout>
          </v-container>
        </v-card-text>
      </v-card>
    </v-layout>
    <password-change-dialog
      v-model="passwordChangeDialog"
      @cancelled="passwordChangeDialog = false"
      @submitted="submitNewPassword"
    />
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
import PasswordChangeDialog from "@/components/PasswordChangeDialog.vue";
import AccountService from "@/services/AccountService";

@Component({
  components: { PasswordChangeDialog },
  filters: {
    objectify(input: Array<string>) {
      return input.map(item => {
        return { value: item };
      });
    }
  }
})
export default class UserProfile extends Vue {
  @Inject() accountService!: AccountService;
  selectedLocale = "";
  passwordChangeDialog = false;

  mounted() {
    this.selectedLocale = this.$i18n.locale; // .$i18n.locale;
  }

  async updateLocale() {
    if (this.$i18n.availableLocales.includes(this.selectedLocale)) {
      this.$i18n.locale = this.selectedLocale;

      try {
        const locale = this.$t("code", this.selectedLocale).toString();
        await this.accountService.changeLocale(locale);
      } catch (error) {
        //TODO: display the error on the global snackbar
      }
    }
  }

  async submitNewPassword(currentPassword: string, newPassword: string) {
    try {
      await this.accountService.changePassword(currentPassword, newPassword);
      this.passwordChangeDialog = false;
    } catch (error) {
      //TODO: display the error on the global snackbar
    }
  }
}
</script>
