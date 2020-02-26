<template>
  <v-app-bar app color="primary" absolute dense dark flat>
    <v-spacer></v-spacer>
    <v-menu left bottom offset-y>
      <template v-slot:activator="{ on }">
        <v-btn icon v-on="on">
          <v-icon>person</v-icon>
        </v-btn>
      </template>

      <v-list dense>
        <v-list-item>
          <v-list-item-icon><v-icon>logout</v-icon></v-list-item-icon>
          <v-list-item-title>{{ $t("topbar.sign-out") }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </v-app-bar>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";

@Component
export default class TopBar extends Vue {}
</script>
