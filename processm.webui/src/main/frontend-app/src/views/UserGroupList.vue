<template>
  <v-container :fluid="true" v-if="hasOrganization()">
    <v-data-table
      show-expand
      @update:expanded="expanded"
      :headers="[
        {
          text: $t('users.group'),
          value: 'name',
          filterable: true,
          focus: false
        },
        {
          text: '',
          value: 'isImplicit',
          filterable: false,
          sortable: false
        },
        {
          text: $t('users.organization'),
          value: 'organizationId',
          filterable: true
        },
        {
          text: $t('common.actions'),
          value: 'actions',
          align: 'center',
          sortable: false
        }
      ]"
      :items="groups"
      :loading="loading"
      item-key="id"
    >
      <template v-slot:top>
        <v-toolbar flat>
          <v-toolbar-title> {{ $t("users.groups") }} {{ $t("common.in") }} {{ organization.name }}</v-toolbar-title>
          <v-spacer></v-spacer>
          <v-dialog v-model="newDialog" max-width="600px" @input.capture="resetNewDialog">
            <template v-slot:activator="{ on, attrs }">
              <v-btn color="primary" v-bind="attrs" v-on="on" name="btn-add-new-group">
                {{ $t("common.add-new") }}
              </v-btn>
            </template>
            <v-card>
              <v-card-title>{{ $t("common.add-new") }}</v-card-title>
              <v-card-text>
                <v-form id="newGroupForm" ref="newGroupForm" v-model="isNewValid" @submit.prevent="addGroup">
                  <v-text-field name="text-new-group-name" v-model="newName" :rules="[(v) => !!v || $t('users.group-empty')]"></v-text-field>
                </v-form>
              </v-card-text>
              <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn color="primary darken-1" text @click="newDialog = false">
                  {{ $t("common.cancel") }}
                </v-btn>

                <v-btn :disabled="!isNewValid" color="primary darken-1" form="newGroupForm" type="submit" name="btn-new-group-submit">
                  {{ $t("common.save") }}
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-dialog>
        </v-toolbar>
      </template>

      <template v-slot:item.name="{ item }">
        <v-text-field
          v-model="item.name"
          :readonly="item.isImplicit || item.isShared"
          background-color="transparent"
          flat
          hide-details
          solo
          @blur="item.focus = false"
          @change="item.dirty = true"
          @focus="item.focus = true"
        >
          <template v-slot:prepend>
            <v-tooltip bottom>
              <template v-slot:activator="{ on, attrs }">
                <span v-bind="attrs" v-on="on" style="font-size: x-small; color: #7f7f7f">{{ item.id.substr(0, 8) }}</span>
              </template>
              <span>{{ $t("users.unique-group-id") }}: {{ item.id }}</span>
            </v-tooltip>
          </template>
          <template v-slot:append>
            <v-tooltip bottom>
              <template v-slot:activator="{ on, attrs }">
                <v-btn
                  :disabled="!item.dirty && (!item.focus || item.isImplicit || item.isShared)"
                  color="primary"
                  dark
                  icon
                  v-bind="attrs"
                  @click="editGroup(item)"
                  v-on="on"
                  @keyup.enter.native="editGroup(item)"
                  name="btn-edit-group-submit"
                >
                  <v-icon v-show="item.dirty || (item.focus && !item.isImplicit && !item.isShared)" small>edit</v-icon>
                </v-btn>
              </template>
              {{ $t("common.edit") }}
            </v-tooltip>
          </template>
        </v-text-field>
      </template>

      <template v-slot:expanded-item="{ headers, item }">
        <td :colspan="headers.length">
          <v-data-table
            v-if="!item.isImplicit"
            :loading="item.members === undefined"
            dense
            :headers="[
              {
                text: $t('common.name'),
                value: 'name',
                filterable: true,
                sortable: true,
                focus: false
              },
              {
                text: $t('common.email'),
                value: 'email',
                filterable: true,
                sortable: true,
                focus: false
              },
              {
                text: $t('common.actions'),
                value: 'actions',
                align: 'center',
                sortable: false
              }
            ]"
            :items="item.members"
          >
            <template v-slot:item.name="{ item }">
              <span>{{ item.username }}</span>
            </template>
            <template v-slot:item.email="{ item }">
              <span>{{ item.userEmail }}</span>
            </template>
            <template v-slot:item.actions="props">
              <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                  <v-btn color="primary" dark icon v-bind="attrs" v-on="on" v-if="!item.isShared && !item.isImplicit" name="btn-remove-group-member">
                    <v-icon small @click="removeMember(props.item, item)">delete_forever</v-icon>
                  </v-btn>
                </template>
                <span>{{ $t("common.remove") }}</span>
              </v-tooltip>
            </template>
          </v-data-table>
        </td>
      </template>

      <template v-slot:item.isImplicit="{ item }">
        <v-chip v-if="item.isImplicit" color="primary" small>
          {{ $t("users.implicit") }}
        </v-chip>
        <v-chip v-if="item.isShared" color="secondary" small>
          {{ $t("users.shared") }}
        </v-chip>
      </template>

      <template v-slot:item.organizationId="{ item }">
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <span v-bind="attrs" v-on="on">{{ organizations[item.organizationId]?.name ?? item.organizationId }}</span>
          </template>
          <span>{{ $t("users.unique-organization-id") }}: {{ item.organizationId }}</span>
        </v-tooltip>
      </template>

      <template v-slot:item.actions="{ item, index }">
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn
              :disabled="groups[index].isImplicit || groups[index].isShared"
              color="primary"
              dark
              icon
              v-bind="attrs"
              v-on="on"
              name="btn-add-group-member"
            >
              <v-icon small @click="addMemberToGroup(item)">add</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.add-new") }}</span>
        </v-tooltip>
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn :disabled="groups[index].isImplicit || groups[index].isShared" color="primary" dark icon v-bind="attrs" v-on="on" name="btn-remove-group">
              <v-icon small @click="removeGroup(item)">delete_forever</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.remove") }}</span>
        </v-tooltip>
      </template>
    </v-data-table>
    <v-dialog v-model="addMemberDialog" max-width="600px">
      <v-card>
        <v-card-title>{{ $t("common.add-new") }}</v-card-title>
        <v-card-text>
          <v-form id="newMemberForm" ref="newMemberForm" v-model="isNewMemberValid" @submit.prevent="addMember">
            <combo-box-with-search
              :label="$t('common.email')"
              :rules="[(v) => (!!v && /^([a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})*$/.test(v)) || $t('registration-form.validation.email-format')]"
              :search="searchUsers"
              :value.sync="newMember"
            ></combo-box-with-search>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="primary darken-1" text @click="addMemberDialog = false">
            {{ $t("common.cancel") }}
          </v-btn>

          <v-btn :disabled="!isNewMemberValid" color="primary darken-1" form="newMemberForm" type="submit" name="btn-submit-new-group-member">
            {{ $t("common.save") }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-dialog v-model="objectRemovalDialog" max-width="400px">
      <v-card>
        <v-card-title>{{ $t("common.warning") }}</v-card-title>
        <v-card-text>
          {{ $t("users.remove-objects") }}
          <v-list dense>
            <v-list-item v-for="(item, i) in objectsToRemove" :key="i">
              <v-list-item-content>
                <v-list-item-title v-text="item.type"></v-list-item-title>
                {{ item.name }}
              </v-list-item-content>
            </v-list-item>
          </v-list>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="primary darken-1" text @click="objectRemovalDialog = false">
            {{ $t("common.cancel") }}
          </v-btn>

          <v-btn color="primary darken-1" text @click="actualRemoveGroup()" name="btn-removal-dialog-submit">
            {{ $t("common.remove") }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
  <v-card v-else>
    <v-card-text>{{ $t("users.no-organization-error") }}</v-card-text>
  </v-card>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import { EntityID, EntityType, Group, Organization, UserInfo } from "@/openapi";
import ComboBoxWithSearch from "@/components/ComboBoxWithSearch.vue";
import App from "@/App.vue";
import GroupService from "@/services/GroupService";
import AccountService from "@/services/AccountService";
import WorkspaceService from "@/services/WorkspaceService";
import DataStoreService from "@/services/DataStoreService";

interface EnhancedGroup extends Group {
  focus: boolean;
  dirty: boolean;
  members: Array<UserInfo> | undefined;
}

@Component({
  components: { ComboBoxWithSearch }
})
export default class UserGroupList extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  @Inject() groupService!: GroupService;
  @Inject() workspaceService!: WorkspaceService;
  @Inject() dataStoreService!: DataStoreService;
  @Inject() organizationService!: OrganizationService;

  loading = true;

  groups: Array<EnhancedGroup> = [];
  organizations: { [id: string]: Organization } = {};
  organization = this.$sessionStorage.currentOrganization;

  newDialog = false;
  newName = "";
  isNewValid = false;

  addNewMemberTo: EnhancedGroup | undefined = undefined;
  addMemberDialog = false;
  newMember = "";
  isNewMemberValid = false;

  objectRemovalDialog = false;
  objectsToRemove: Array<EntityID> = [];
  groupIdToRemove: string | undefined = undefined;

  async mounted() {
    this.organizations = {};
    for (const org of await this.organizationService.getOrganizations()) {
      if (org.id !== undefined) {
        this.organizations[org.id] = org;
      }
    }
    console.log(this.organizations);
    await this.refreshGroups();
  }

  async refreshGroups() {
    if (!this.hasOrganization()) return;
    try {
      this.loading = true;
      const groups = await this.groupService.getUserGroups(this.organization?.id!);
      this.groups = groups.map((g) => Object.assign({ focus: false, dirty: false, members: undefined }, g));
    } catch (e) {
      this.app.error(e);
    } finally {
      this.loading = false;
    }
  }

  resetNewDialog() {
    this.newName = "";
  }

  async addGroup() {
    try {
      console.assert(this.newName != "", "newName: " + this.newName);
      await this.groupService.createGroup(this.organization?.id!, this.newName);
      this.newDialog = false;
      this.resetNewDialog();
      this.app.info(this.$t("users.group-added").toString());
      await this.refreshGroups();
    } catch (e) {
      this.app.error(e);
    }
  }

  async editGroup(group: EnhancedGroup) {
    try {
      console.assert(group.organizationId !== undefined);
      console.assert(group.id !== undefined);
      await this.groupService.updateGroup(group.organizationId!, group.id!, group.name);
      group.dirty = false;
      this.app.info(this.$t("users.group-updated").toString());
    } catch (e) {
      this.app.error(e);
    }
  }

  async removeGroup(group: Group) {
    try {
      console.assert(group.id !== undefined);
      this.groupIdToRemove = group.id!;
      const entities = await this.groupService.getSoleOwnershipObjects(this.organization?.id!, group.id!);
      if (entities.length > 0) {
        this.objectsToRemove = entities;
        this.objectRemovalDialog = true;
      } else await this.actualRemoveGroup();
    } catch (e) {
      this.app.error(e);
    }
  }

  async actualRemoveGroup() {
    try {
      console.assert(this.groupIdToRemove !== undefined);
      for (const entity of this.objectsToRemove) {
        switch (entity.type) {
          case EntityType.Workspace:
            await this.workspaceService.removeWorkspace(entity.id);
            break;
          case EntityType.DataStore:
            await this.dataStoreService.removeDataStore(entity.id);
            break;
        }
      }
      await this.groupService.removeGroup(this.organization?.id!, this.groupIdToRemove!);
      this.groups = this.groups.filter((item) => item.id != this.groupIdToRemove);
      this.app.info(this.$t("users.group-removed").toString());
    } catch (e) {
      this.app.error(e);
    } finally {
      this.groupIdToRemove = undefined;
      this.objectRemovalDialog = false;
      this.objectsToRemove = [];
    }
  }

  async addMemberToGroup(group: EnhancedGroup) {
    this.newMember = "";
    this.addMemberDialog = true;
    this.addNewMemberTo = group;
  }

  async searchUsers(value: string): Promise<Array<string>> {
    const list = await this.accountService.getUsers(value);
    return Promise.resolve(list.map((a) => a.email));
  }

  async addMember() {
    try {
      const group = this.addNewMemberTo;
      if (group === undefined || group.organizationId === undefined || group.id === undefined) {
        this.app.error(this.$t("common.saving.failure").toString());
        return;
      }
      this.addMemberDialog = false;
      //Filter is necessary to prevent a weird problem when one email address is a prefix of another
      const users = (await this.accountService.getUsers(this.newMember)).filter((u) => u.email == this.newMember);
      if (users.length != 1) {
        this.app.error(this.$t("users.unknown-email").toString());
        return;
      }
      if (await this.groupService.addMember(group.organizationId, group.id, users[0].id)) {
        this.app.success(this.$t("users.group-updated").toString());
        group.members = undefined;
        group.members = await this.getMembers(group);
      } else {
        this.app.error(this.$t("common.saving.failure").toString());
      }
    } catch (e) {
      this.app.error(e);
    }
  }

  async getMembers(group: Group): Promise<UserInfo[] | undefined> {
    const organizationId = group.organizationId;
    const groupId = group.id;
    if (organizationId !== undefined && groupId !== undefined) {
      return await this.groupService.getMembers(organizationId, groupId);
    } else {
      this.app.error(this.$t("common.operation-error").toString());
    }
  }

  async expanded(expandedGroups: Array<EnhancedGroup>) {
    for (const group of expandedGroups) {
      if (group.members === undefined && !group.isImplicit) group.members = await this.getMembers(group);
    }
  }

  async removeMember(member: UserInfo, group: EnhancedGroup) {
    try {
      if (group.isImplicit || group.isShared || group.organizationId === undefined || group.id === undefined || member.id === undefined) {
        this.app.error(this.$t("common.operation-error").toString());
        return;
      }
      if (await this.groupService.removeMember(group.organizationId, group.id, member.id)) {
        this.app.success(this.$t("users.group-updated").toString());
        group.members = undefined;
        group.members = await this.getMembers(group);
      } else {
        this.app.error(this.$t("common.saving.failure").toString());
      }
    } catch (e) {
      this.app.error(e);
    }
  }

  hasOrganization() {
    return this.organization !== undefined;
  }
}
</script>