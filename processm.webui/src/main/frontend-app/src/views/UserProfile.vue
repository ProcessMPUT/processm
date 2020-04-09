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
                dense
                outlined
              >
                <template v-slot:selection="data">
                  <span
                    v-bind:class="[
                      'flag-icon flag-icon-' + $t('iso', data.item.value),
                      'mr-2'
                    ]"
                  />
                  {{ $t("language-name", data.item.value) }}
                </template>
                <template v-slot:item="data">
                  <span
                    v-bind:class="[
                      'flag-icon flag-icon-' + $t('iso', data.item.value),
                      'mr-2'
                    ]"
                  />
                  {{ $t("language-name", data.item.value) }}
                </template>
              </v-select>
            </v-row>
          </v-container>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="primary" @click.stop="updateSettings">
            <v-icon left dark>save</v-icon>
            {{ $t("user-profile.save") }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-layout>
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
import { Component } from "vue-property-decorator";

@Component({
  filters: {
    objectify(input: Array<string>) {
      return input.map(item => {
        return { value: item };
      });
    }
  }
})
export default class UserProfile extends Vue {
  selectedLocale = "";

  mounted() {
    this.selectedLocale = this.$i18n.locale; // .$i18n.locale;
  }

  updateSettings() {
    if (this.$i18n.availableLocales.includes(this.selectedLocale)) {
      this.$i18n.locale = this.selectedLocale;
    }
  }
}
</script>
