<template>
  <v-navigation-drawer app :miniVariant="minimized" permanent>
    <v-list>
      <v-list-item class="px-2" @click.stop="toggleMinimized">
        <v-list-item-avatar rounded="0">
          <v-img max-height="48" max-width="48" src="favicon.png" />
        </v-list-item-avatar>
        <v-list-item-title class="title">
          {{ $t("navigation.menu") }}
        </v-list-item-title>
      </v-list-item>

      <v-divider></v-divider>

      <v-list-item :to="{ name: 'home' }" exact color="primary" name="goto-home">
        <v-list-item-action>
          <v-icon>dashboard</v-icon>
        </v-list-item-action>
        <v-list-item-content>
          {{ $t("navigation.workspaces") }}
        </v-list-item-content>
      </v-list-item>
      <v-list-item :to="{ name: 'users' }" color="primary" name="goto-users">
        <v-list-item-action>
          <v-icon>people</v-icon>
        </v-list-item-action>
        <v-list-item-content>{{ $t("navigation.users") }}</v-list-item-content>
      </v-list-item>
      <v-list-item :to="{ name: 'data-stores' }" color="primary" name="goto-data-stores">
        <v-list-item-action>
          <v-icon>folder</v-icon>
        </v-list-item-action>
        <v-list-item-content>{{ $t("navigation.data-stores") }}</v-list-item-content>
      </v-list-item>
      <v-list-item :to="{ name: 'pql-interpreter' }" color="primary" name="goto-pql-interpreter">
        <v-list-item-action>
          <v-icon>code</v-icon>
        </v-list-item-action>
        <v-list-item-content>{{ $t("navigation.pql-interpreter") }}</v-list-item-content>
      </v-list-item>
      <v-list-item :to="{ name: 'about' }" color="primary">
        <v-list-item-action>
          <v-icon>info</v-icon>
        </v-list-item-action>
        <v-list-item-content>{{ $t("navigation.about", app.config) }}</v-list-item-content>
      </v-list-item>
    </v-list>
  </v-navigation-drawer>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Inject } from "vue-property-decorator";
import App from "@/App.vue";

@Component
export default class AppNavigation extends Vue {
  @Inject()
  app!: App;

  minimized = false;

  toggleMinimized() {
    this.minimized = !this.minimized;
  }
}
</script>
