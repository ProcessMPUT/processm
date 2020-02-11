<template>
  <v-container class="d-flex align-stretch pb-0 px-0" fluid fill-height>
    <v-row align="end" class="ma-0">
      <v-tabs-items v-model="currentWorkspace">
        <v-tab-item v-for="workspace in workspaces" :key="workspace.name">
          <v-card>{{ workspace.content }}</v-card>
        </v-tab-item>
      </v-tabs-items>

      <v-tabs v-model="currentWorkspace" background-color="primary" show-arrows>
        <v-tab v-for="workspace in workspaces" :key="workspace.name">{{
          workspace.name
        }}</v-tab>
      </v-tabs>
    </v-row>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";

@Component
export default class Workspaces extends Vue {
  currentWorkspace: any = null;

  get workspaces(): Array<any> {
    return [
      { name: "Workspace1", content: "Tab 1 Content" },
      { name: "Workspace2", content: "Tab 2 Content" },
      { name: "Workspace3", content: "Tab 3 Content" },
      { name: "Workspace4", content: "Tab 4 Content" },
      { name: "Workspace5", content: "Tab 5 Content" }
    ];
  }
}
</script>
