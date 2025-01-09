<template>
  <v-container class="px-0" fluid>
    <v-row class="workspace mx-0">
      <v-tabs-items v-model="currentWorkspaceIndex" style="overflow: auto">
        <v-tab-item v-for="workspace in workspaces" :key="workspace.id">
          <workspace-area :workspaceId="workspace.id" :view-only="isViewOnly(workspace)" />
        </v-tab-item>
      </v-tabs-items>
    </v-row>
    <v-row class="workspace-selector mx-0">
      <v-tabs v-model="currentWorkspaceIndex" background-color="primary lighten-2" center-active show-arrows>
        <v-menu top offset-y v-if="isWriter">
          <template #activator="{ on }">
            <v-btn tile color="primary lighten-1" v-on="on" name="btn-workspace-hamburger">
              <v-icon>more_vert</v-icon>
            </v-btn>
          </template>

          <v-list flat dense>
            <v-list-item @click.stop="removeWorkspace" :disabled="!(workspaces.length > 1)" v-if="isOwner">
              <v-list-item-icon>
                <v-icon>delete_forever</v-icon>
              </v-list-item-icon>
              <v-list-item-title>{{ $t("common.remove") }}</v-list-item-title>
            </v-list-item>
            <v-divider v-if="isOwner" />
            <v-list-item @click.stop="workspaceNameToRename = currentWorkspaceName">
              <v-list-item-icon>
                <v-icon>drive_file_rename_outline</v-icon>
              </v-list-item-icon>
              <v-list-item-title>{{ $t("common.rename") }}</v-list-item-title>
            </v-list-item>
            <v-list-item @click.stop="aclDialogDisplayed = true" name="btn-workspace-security" v-if="isOwner">
              <v-list-item-icon>
                <v-icon>security</v-icon>
              </v-list-item-icon>
              <v-list-item-title>{{ $t("common.security") }}</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-tab v-for="workspace in workspaces" :id="`workspace-tab-${workspace.id}`" :key="workspace.id">
          {{ workspace.name }}
        </v-tab>
        <v-btn tile color="primary lighten-1" @click="createWorkspace" name="btn-create-workspace">
          <v-icon>add_box</v-icon>
        </v-btn>
      </v-tabs>
      <rename-dialog
        :value="workspaceNameToRename != null"
        @cancelled="workspaceNameToRename = null"
        @submitted="renameWorkspace"
        :old-name="workspaceNameToRename"
      />
      <acl-dialog :force-view-only="false" :urn="currentWorkspaceUrn" :value="aclDialogDisplayed" @closed="aclDialogDisplayed = false" />
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
import { Component, Inject, Watch } from "vue-property-decorator";
import WorkspaceArea from "@/components/workspace/WorkspaceArea.vue";
import RenameDialog from "@/components/RenameDialog.vue";
import WorkspaceService from "@/services/WorkspaceService";
import AclDialog from "@/components/acl/AclDialog.vue";
import { Workspace } from "@/openapi";

@Component({
  components: { AclDialog, WorkspaceArea, RenameDialog }
})
export default class Workspaces extends Vue {
  @Inject() workspaceService!: WorkspaceService;
  workspaceNameToRename: string | null = null;
  currentWorkspaceIndex = 0;
  workspaces: Array<Workspace> = [];
  aclDialogDisplayed: boolean = false;
  isWriter: boolean = true;
  isOwner: boolean = true;

  async created() {
    this.workspaces = await this.workspaceService.getAll();
    this.workspaceChanged();
  }

  get currentWorkspaceName(): string {
    return this.workspaces[this.currentWorkspaceIndex]?.name || "";
  }

  get currentWorkspaceUrn(): string {
    const id = this.workspaces[this.currentWorkspaceIndex]?.id;
    return "urn:processm:db/workspaces/" + id;
  }

  async createWorkspace() {
    let counter = 0;
    let name = "";
    do {
      name = `${this.$i18n.t("workspace.default-name")} ${++counter}`;
    } while (this.workspaces.find((w) => w.name == name));

    const newWorkspace = await this.workspaceService.createWorkspace(name);

    this.workspaces.push(newWorkspace);
  }

  async removeWorkspace() {
    const removedWorkspaceIndex = this.currentWorkspaceIndex;

    await this.workspaceService.removeWorkspace(this.workspaces[removedWorkspaceIndex].id!);
    this.workspaces.splice(removedWorkspaceIndex, 1);
    this.currentWorkspaceIndex = Math.max(0, this.currentWorkspaceIndex - 1);
  }

  async renameWorkspace(newName: string) {
    const currentWorkspace = this.workspaces[this.currentWorkspaceIndex];

    currentWorkspace.name = newName;
    await this.workspaceService.updateWorkspace(currentWorkspace);
    this.workspaceNameToRename = null;
  }

  private isViewOnly(workspace: Workspace) {
    return workspace.role != "owner" && workspace.role != "writer";
  }

  @Watch("currentWorkspaceIndex")
  private workspaceChanged() {
    const currentWorkspace = this.workspaces[this.currentWorkspaceIndex];
    this.isOwner = currentWorkspace.role == "owner";
    this.isWriter = this.isOwner || currentWorkspace.role == "writer";
  }
}
</script>
