<template>
  <v-dialog v-model="value" @click:outside="close" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("acl.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-data-table :headers="headers()" :items="entries" :loading="loading">
            <template v-slot:item.groupName="{ item }">
              <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                  <span v-bind="attrs" v-on="on">{{ item.groupName }}</span>
                </template>
                <span v-if="item.organization == null"> {{ $t("users.implicit") }} </span>
                <span v-else>
                  {{ $t("users.unique-group-id") }}: {{ item.groupId }}<br />
                  {{ $t("users.organization") }}: {{ item.organization?.name }}<br />
                  {{ $t("users.unique-organization-id") }}: {{ item.organization.id }}
                </span>
              </v-tooltip>
            </template>
            <template v-slot:item.role="{ item }">
              {{ $t(`users.roles.${item.role}`) }}
            </template>
            <template v-slot:item.actions="{ item }" v-if="!viewOnly">
              <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                  <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-edit-ace">
                    <v-icon small @click="aceToEdit = item">edit</v-icon>
                  </v-btn>
                </template>
                <span>{{ $t("common.edit") }}</span>
              </v-tooltip>
              <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                  <v-btn icon color="primary" dark v-bind="attrs" v-on="on" name="btn-remove-ace">
                    <v-icon small @click="removeACE(item)">delete_forever</v-icon>
                  </v-btn>
                </template>
                <span>{{ $t("common.remove") }}</span>
              </v-tooltip>
            </template>
          </v-data-table>
        </v-container>
      </v-card-text>

      <v-card-actions>
        <v-btn color="primary darken-1" text @click.stop="createNew = true" v-if="!viewOnly" name="btn-acl-dialog-add-new">
          {{ $t("common.add-new") }}
        </v-btn>

        <v-spacer></v-spacer>

        <v-btn color="primary darken-1" text @click.stop="close" name="btn-acl-dialog-close">
          {{ $t("common.close") }}
        </v-btn>
      </v-card-actions>
    </v-card>
    <ace-editor
      :value="aceToEdit !== null || createNew"
      :group-id="aceToEdit?.groupId"
      :role="aceToEdit?.role"
      :urn="urn"
      @cancelled="closeEditor"
      @submitted="saveACE"
    ></ace-editor>
  </v-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import AceEditor from "@/components/acl/AceEditor.vue";
import App from "@/App.vue";
import ACLService from "@/services/ACLService";
import { AccessControlEntry, OrganizationRole } from "@/openapi";
import { DataTableHeader } from "vuetify";

@Component({
  components: { AceEditor }
})
export default class AclDialog extends Vue {
  @Prop({ default: "" })
  readonly urn!: string;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop({ default: false })
  readonly forceViewOnly!: boolean;

  @Inject() app!: App;
  @Inject() aclService!: ACLService;
  entries: Array<AccessControlEntry> = [];
  loading: boolean = true;

  aceToEdit: AccessControlEntry | null = null;
  createNew: boolean = false;

  viewOnly = true;

  @Watch("value")
  async componentVisibilityChanged(isVisible: boolean) {
    if (isVisible) {
      this.viewOnly = this.forceViewOnly || !(await this.aclService.canModify(this.urn));
      await this.refresh();
    }
  }

  async refresh() {
    try {
      this.loading = true;
      this.entries = await this.aclService.getACLFor(this.urn);
      this.loading = false;
    } catch (e) {
      console.warn(e);
      this.app.error(e);
      this.close();
    }
  }

  closeEditor() {
    this.aceToEdit = null;
    this.createNew = false;
  }

  close() {
    this.$emit("closed");
  }

  async removeACE(ace: AccessControlEntry) {
    if (this.viewOnly) return;
    try {
      await this.aclService.removeACE(this.urn, ace.groupId);
      await this.refresh();
    } catch (e) {
      this.app.error(e);
    }
  }

  async saveACE(groupId: string | undefined, role: OrganizationRole) {
    if (this.viewOnly) return;
    try {
      if (this.createNew) {
        if (groupId === undefined) {
          this.app.error(`${this.$t("common.saving.failure")}`);
          return;
        }
        try {
          await this.aclService.createNewACE(this.urn, groupId, role);
        } catch (error) {
          this.app.error(`${this.$t("acl.update.conflict")}`);
          return;
        }
      } else {
        const oldGroupId = this.aceToEdit?.groupId;
        if (oldGroupId === undefined) {
          this.app.error(`${this.$t("common.saving.failure")}`);
          return;
        }
        await this.aclService.updateACE(this.urn, oldGroupId, role);
      }
      this.closeEditor();
      await this.refresh();
    } catch (e) {
      this.app.error(e);
    }
  }

  headers() {
    const result: DataTableHeader[] = [
      {
        text: this.$t("users.group"),
        value: "groupName",
        filterable: true
      },
      {
        text: this.$t("users.role"),
        value: "role"
      }
    ] as DataTableHeader[];
    if (!this.viewOnly)
      result.push({
        text: this.$t("common.actions"),
        value: "actions",
        align: "center",
        sortable: false
      } as DataTableHeader);
    return result;
  }
}
</script>