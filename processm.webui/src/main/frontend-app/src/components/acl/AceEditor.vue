<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="400" @keydown.esc="cancel">
    <v-card>
      <v-card-title class="headline">
        {{ $t("acl.ace-dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form ref="editor">
            <v-row v-if="groupId == null">
              <v-col>
                <v-combobox :label="$t('users.group')" v-model="newGroup" :items="availableGroups" item-text="name" item-value="id" name="ace-editor-group">
                  <template v-slot:item="{ parent, item, on, attrs }">
                    <v-tooltip bottom>
                      <template v-slot:activator="{ on, attrs }">
                        <span v-bind="attrs" v-on="on">{{ item.name }}</span>
                      </template>
                      <span v-if="item.organizationId == null"> {{ $t("users.implicit") }} </span>
                      <span v-else>
                        {{ $t("users.unique-group-id") }}: {{ item.id }}<br />
                        {{ $t("users.organization") }}: {{ item.organizationName }}<br />
                        {{ $t("users.unique-organization-id") }}: {{ item.organizationId }}
                      </span>
                    </v-tooltip>
                  </template>
                </v-combobox>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-select v-model="newRole" :items="roles" item-text="name" item-value="value" :label="$t('users.role')" name="ace-editor-role"></v-select>
              </v-col>
            </v-row>
          </v-form>
        </v-container>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn color="primary" text @click.stop="cancel" name="btn-ace-editor-cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn color="primary" text @click.stop="save" :disabled="newGroup === null && groupId === null" name="btn-ace-editor-submit">
          {{ $t("common.submit") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import { Group, OrganizationRole } from "@/openapi";
import ACLService from "@/services/ACLService";
import App from "@/App.vue";

@Component({})
export default class AceEditor extends Vue {
  @Prop({ default: null })
  readonly groupId!: string | null;
  @Prop({ default: null })
  readonly role!: OrganizationRole | null;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop({ default: null })
  readonly urn!: string | null;

  @Inject() app!: App;
  @Inject() aclService!: ACLService;

  newGroup: Group | null = null;
  newRole: OrganizationRole = OrganizationRole.None;
  readonly roles = [OrganizationRole.None, OrganizationRole.Owner, OrganizationRole.Writer, OrganizationRole.Reader].map((r) => ({
    name: this.$t(`users.roles.${r}`),
    value: r
  }));

  availableGroups: Array<Group> = [];

  @Watch("value")
  async componentVisibilityChanged(isVisible: boolean) {
    if (isVisible) {
      if (this.groupId === null && this.urn !== null) {
        this.availableGroups = await this.aclService.getAvailableGroups(this.urn);
      } else {
        this.availableGroups = [];
      }
      if (this.groupId === null && this.availableGroups.length == 0) {
        this.app.error(`${this.$t("acl.no-candidates")}`);
        this.cancel();
      }
      this.newGroup = null;
      this.newRole = this.role ?? OrganizationRole.None;
      for (let group of this.availableGroups) {
        if (group.id == this.groupId) {
          this.newGroup = group;
          break;
        }
      }
    }
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  save() {
    this.$emit("submitted", this.newGroup?.id, this.newRole);
  }

  private resetForm() {
    (this.$refs.editor as HTMLFormElement).reset();
  }
}
</script>