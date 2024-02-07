<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="400">
    <v-card>
      <v-card-title class="headline">
        {{ $t("acl.ace-dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form ref="editor">
            <v-row v-if="groupId == null">
              <v-col>
                <v-combobox v-model="newGroup" :items="availableGroups" :label="$t('users.group')" item-text="name" item-value="id"></v-combobox>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-select v-model="newRole" :items="roles" :label="$t('users.role')" item-text="name" item-value="value"></v-select>
              </v-col>
            </v-row>
          </v-form>
        </v-container>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn color="primary" text @click.stop="cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn :disabled="newGroup === null && groupId === null" color="primary" text @click.stop="save">
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
