<template>
  <v-container>
    <edit-component-dialog
      v-if="displayEditModal"
      v-model="displayEditModal"
      :component-details="displayedComponentDetails"
      :workspace-id="workspaceId"
      @close="close"
      @component-updated="close"
    ></edit-component-dialog>
  </v-container>
</template>
<script lang="ts">
import EditComponentDialog from "@/components/workspace/EditComponentDialog.vue";
import Vue from "vue";
import { Inject, Prop } from "vue-property-decorator";
import App from "@/App.vue";
import Component from "vue-class-component";
import { WorkspaceComponent as WorkspaceComponentModel } from "@/models/WorkspaceComponent";
import WorkspaceService from "@/services/WorkspaceService";

@Component({
  components: {
    EditComponentDialog
  }
})
export default class EditComponentView extends Vue {
  @Inject() app!: App;
  @Inject() workspaceService!: WorkspaceService;

  @Prop()
  readonly workspaceId!: string;
  @Prop()
  readonly componentId!: string;

  displayEditModal = false;

  displayedComponentDetails?: WorkspaceComponentModel;

  async mounted() {
    this.displayedComponentDetails = await this.workspaceService.getComponent(this.workspaceId, this.componentId);
    this.displayEditModal = true;
  }

  close() {
    this.app.$router.go(-1);
  }
}
</script>