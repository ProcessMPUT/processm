<template>
  <v-container class="d-flex align-stretch pb-0 px-0" fluid fill-height>
    <v-row align="stretch" class="ma-0">
      <v-col cols="12" class="pa-0">
        <v-tabs-items v-model="currentWorkspace">
          <v-tab-item v-for="workspace in workspaces" :key="workspace.name">
            <workspace />
          </v-tab-item>
        </v-tabs-items>
      </v-col>
    </v-row>
    <v-row align="end" class="ma-0">
      <v-tabs v-model="currentWorkspace" background-color="primary" show-arrows>
        <v-tab
          v-for="workspace in workspaces"
          :key="workspace.name"
          v-on="on"
          >{{ workspace.name }}</v-tab
        >
        <v-btn class="ma-1" @click="createWorkspace">
          <v-icon>mdi-plus</v-icon>
        </v-btn>
      </v-tabs>
    </v-row>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import Workspace from "@/components/Workspace.vue";

@Component({
  components: { Workspace }
})
export default class Workspaces extends Vue {
  private readonly defaultWorkspaces = [
    { name: "Workspace1", content: "W1" },
    { name: "Workspace2", content: "W2" }
  ];

  currentWorkspace = this.defaultWorkspaces[0];

  get workspaces(): Array<object> {
    return this.defaultWorkspaces;
  }

  createWorkspace() {
    const workspaceNumber = this.defaultWorkspaces.length + 1;
    this.defaultWorkspaces.push({
      name: `Workspace${workspaceNumber}`,
      content: `W${workspaceNumber}`
    });
  }

  removeWorkspace(workspaceNumber: number) {
    this.defaultWorkspaces.splice(workspaceNumber - 1, 1);
  }
}
</script>
