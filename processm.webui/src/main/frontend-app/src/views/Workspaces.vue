<template>
  <v-container class="d-flex align-stretch pb-0 px-0" fluid fill-height>
    <v-row align="stretch" class="ma-0">
      <v-col cols="12" class="pa-0">
        <v-tabs-items v-model="currentWorkspaceIndex">
          <v-tab-item v-for="workspace in workspaces" :key="workspace.index">
            <workspace />
          </v-tab-item>
        </v-tabs-items>
      </v-col>
    </v-row>
    <v-row align="end" class="ma-0">
      <v-tabs
        v-model="currentWorkspaceIndex"
        background-color="primary lighten-2"
        center-active
        show-arrows
      >
        <v-menu top offset-y>
          <template v-slot:activator="{ on }">
            <v-btn tile color="primary lighten-1" v-on="on">
              <v-icon>more_vert</v-icon>
            </v-btn>
          </template>

          <v-list flat>
            <v-list-item
              @click.stop="removeWorkspace"
              :disabled="!(defaultWorkspaces.length > 1)"
            >
              <v-list-item-title>{{ $t("common.remove") }}</v-list-item-title>
            </v-list-item>
            <v-divider />
            <v-list-item @click.stop="workspaceRenamingDialog = true">
              <v-list-item-title>{{ $t("common.rename") }}</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-tab v-for="workspace in workspaces" :key="workspace.index">{{
          workspace.name
        }}</v-tab>
        <v-btn tile color="primary lighten-1" @click="createWorkspace">
          <v-icon>mdi-plus</v-icon>
        </v-btn>
      </v-tabs>
      <rename-dialog
        v-model="workspaceRenamingDialog"
        @cancelled="workspaceRenamingDialog = false"
        @newNameSubmitted="renameWorkspace"
        :old-name="currentWorkspaceName"
      />
    </v-row>
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
import Workspace from "@/components/Workspace.vue";
import RenameDialog from "@/components/RenameDialog.vue";

@Component({
  components: { Workspace, RenameDialog }
})
export default class Workspaces extends Vue {
  private readonly defaultWorkspaces = [
    { index: 1, name: "Workspace1", content: "W1" },
    { index: 2, name: "Workspace2", content: "W2" }
  ];

  workspaceRenamingDialog = false;
  currentWorkspaceIndex = 0;

  get currentWorkspaceName(): string {
    return this.defaultWorkspaces[this.currentWorkspaceIndex]?.name || "";
  }

  get workspaces(): Array<object> {
    return this.defaultWorkspaces;
  }

  createWorkspace() {
    const lastWorkspaceIndex =
      this.defaultWorkspaces.length > 0
        ? this.defaultWorkspaces[this.defaultWorkspaces.length - 1].index
        : 0;
    const newWorkspaceIndex = lastWorkspaceIndex + 1;

    this.defaultWorkspaces.push({
      index: newWorkspaceIndex,
      name: `Workspace${newWorkspaceIndex}`,
      content: `W${newWorkspaceIndex}`
    });
  }

  removeWorkspace() {
    const removedWorkspaceIndex = this.currentWorkspaceIndex;

    this.currentWorkspaceIndex = Math.max(0, this.currentWorkspaceIndex - 1);
    this.defaultWorkspaces.splice(removedWorkspaceIndex, 1);
  }

  renameWorkspace(newName: string) {
    this.workspaceRenamingDialog = false;
    this.defaultWorkspaces[this.currentWorkspaceIndex].name = newName;
  }
}
</script>
