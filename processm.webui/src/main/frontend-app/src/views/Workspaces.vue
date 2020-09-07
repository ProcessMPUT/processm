<template>
  <v-container class="pb-0 px-0">
    <v-row class="workspace mx-0">
      <v-tabs-items v-model="currentWorkspaceIndex">
        <v-tab-item v-for="workspace in workspaces" :key="workspace.index">
          <workspace-area :workspaceId="workspace.id" />
        </v-tab-item>
      </v-tabs-items>
    </v-row>
    <v-row class="workspace-selector mx-0">
      <v-tabs
        v-model="currentWorkspaceIndex"
        background-color="primary lighten-2"
        center-active
        show-arrows
      >
        <v-menu top offset-y>
          <template #activator="{ on }">
            <v-btn tile color="primary lighten-1" v-on="on">
              <v-icon>more_vert</v-icon>
            </v-btn>
          </template>

          <v-list flat dense>
            <v-list-item
              @click.stop="removeWorkspace"
              :disabled="!(workspaces.length > 1)"
            >
              <v-list-item-icon
                ><v-icon>delete_forever</v-icon></v-list-item-icon
              >
              <v-list-item-title>{{ $t("common.remove") }}</v-list-item-title>
            </v-list-item>
            <v-divider />
            <v-list-item @click.stop="workspaceRenamingDialog = true">
              <v-list-item-icon
                ><v-icon>drive_file_rename_outline</v-icon></v-list-item-icon
              >
              <v-list-item-title>{{ $t("common.rename") }}</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-tab v-for="workspace in workspaces" :key="workspace.index">{{
          workspace.name
        }}</v-tab>
        <v-btn tile color="primary lighten-1" @click="createWorkspace">
          <v-icon>add_box</v-icon>
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

.container .v-tabs-items {
  width: 100%;
}

.container {
  display: flex;
  flex-flow: column;
  height: 100%;
  align-items: stretch;
}

.container .workspace {
  flex: 1 1 auto;
  overflow: scroll;
  width: 100%;
}

.container .workspace-selector {
  flex: 0 1 auto;
  width: 100%;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import WorkspaceArea from "@/components/workspace/WorkspaceArea.vue";
import RenameDialog from "@/components/RenameDialog.vue";
import WorkspaceService from "@/services/WorkspaceService";
import Workspace from "@/models/Workspace";

@Component({
  components: { WorkspaceArea, RenameDialog }
})
export default class Workspaces extends Vue {
  @Inject() workspaceService!: WorkspaceService;
  workspaceRenamingDialog = false;
  currentWorkspaceIndex = 0;
  workspaces: Array<Workspace> = [];

  async created() {
    this.workspaces = await this.workspaceService.getAll();
  }

  get currentWorkspaceName(): string {
    return this.workspaces[this.currentWorkspaceIndex]?.name || "";
  }

  async createWorkspace() {
    const workspaceName = `${this.$i18n.t("workspace.default-name")}${this
      .workspaces.length + 1}`;
    const newWorkspace = await this.workspaceService.createWorkspace(
      workspaceName
    );

    this.workspaces.push(newWorkspace);
  }

  async removeWorkspace() {
    const removedWorkspaceIndex = this.currentWorkspaceIndex;

    await this.workspaceService.removeWorkspace(
      this.workspaces[removedWorkspaceIndex].id
    );
    this.currentWorkspaceIndex = Math.max(0, this.currentWorkspaceIndex - 1);
    this.workspaces.splice(removedWorkspaceIndex, 1);
  }

  async renameWorkspace(newName: string) {
    this.workspaceRenamingDialog = false;

    const currentWorkspace = this.workspaces[this.currentWorkspaceIndex];

    currentWorkspace.name = newName;
    await this.workspaceService.updateWorkspace(currentWorkspace);
  }
}
</script>
