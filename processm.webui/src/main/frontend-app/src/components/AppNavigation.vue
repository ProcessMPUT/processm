<template>
  <v-navigation-drawer app :miniVariant="minimized" permanent>
    <v-list>
      <v-list-item class="px-2" @click.stop="toggleMinimized">
        <v-list-item-avatar>
          <v-icon>menu</v-icon>
        </v-list-item-avatar>
        <v-list-item-title class="title">
          {{ $t("navigation.menu") }}
        </v-list-item-title>
      </v-list-item>

      <v-divider></v-divider>

      <v-list-item to="/" exact color="primary">
        <v-list-item-action>
          <v-icon>dashboard</v-icon>
        </v-list-item-action>
        <v-list-item-content>
          {{ $t("navigation.workspaces") }}
        </v-list-item-content>
      </v-list-item>
      <v-list-item to="/about" color="primary">
        <v-list-item-action>
          <v-icon>info</v-icon>
        </v-list-item-action>
        <v-list-item-content>{{ $t("navigation.about") }}</v-list-item-content>
      </v-list-item>
    </v-list>
  </v-navigation-drawer>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";

@Component
export default class AppNavigation extends Vue {
  minimized = false;

  toggleMinimized() {
    this.minimized = !this.minimized;
  }
}
</script>
