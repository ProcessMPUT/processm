<template>
  <v-container :fluid="true">
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
              <v-btn color="primary" v-bind="attrs" v-on="on"  name="btn-add-new-group">
                {{ $t("common.add-new") }}
              </v-btn>
            </template>
            <v-card>
              <v-card-title>{{ $t("common.add-new") }}</v-card-title>
              <v-card-text>
                <v-form id="newGroupForm" ref="newGroupForm" v-model="isNewValid" @submit.prevent="addGroup">
                  <v-text-field v-model="newName" :rules="[(v) => !!v || $t('users.group-empty')]"></v-text-field>
                </v-form>
              </v-card-text>
              <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn color="primary darken-1" text @click="newDialog = false">
                  {{ $t("common.cancel") }}
                </v-btn>

                <v-btn :disabled="!isNewValid" color="primary darken-1" form="newGroupForm" type="submit">
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
              ]
              "
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
                  <v-btn color="primary" dark icon
                         v-bind="attrs"
                         v-on="on" v-if="!item.isShared && !item.isImplicit">
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
        {{ organizations.find((o) => o.id === item.organizationId)?.name ?? item.organizationId }}
      </template>

      <template v-slot:item.actions="{ item, index }">
        <!--
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn :disabled="groups[index].isImplicit || groups[index].isShared" color="primary" dark icon v-bind="attrs" v-on="on">
              <v-icon small @click="editGroup(item)">edit</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.edit") }}</span>
        </v-tooltip>
        -->
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn :disabled="groups[index].isImplicit || groups[index].isShared" color="primary" dark icon
                   v-bind="attrs"
                   v-on="on">
              <v-icon small @click="addMemberToGroup(item)">add</v-icon>
            </v-btn>
          </template>
          <span>{{ $t("common.add-new") }}</span>
        </v-tooltip>
        <v-tooltip bottom>
          <template v-slot:activator="{ on, attrs }">
            <v-btn :disabled="groups[index].isImplicit || groups[index].isShared" color="primary" dark icon
                   v-bind="attrs"
                   v-on="on">
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

          <v-btn :disabled="!isNewMemberValid" color="primary darken-1" form="newMemberForm" type="submit">
            {{ $t("common.save") }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import {Component, Inject} from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import {Group, Organization, UserInfo} from "@/openapi";
import ComboBoxWithSearch from "@/components/ComboBoxWithSearch.vue";
import App from "@/App.vue";
import GroupService from "@/services/GroupService";
import AccountService from "@/services/AccountService";


interface EnhancedGroup extends Group {
  focus: boolean;
  dirty: boolean;
  members: Array<UserInfo> | undefined;
}

@Component({
  components: {ComboBoxWithSearch}
})
export default class UserGroupList extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  @Inject() groupService!: GroupService;
  @Inject() organizationService!: OrganizationService;

  loading = true;

  groups: Array<EnhancedGroup> = [];
  organizations: Array<Organization> = [];
  organization = this.$sessionStorage.currentOrganization;

  newDialog = false;
  newName = "";
  isNewValid = false;

  addNewMemberTo: EnhancedGroup | undefined = undefined;
  addMemberDialog = false;
  newMember = "";
  isNewMemberValid = false;

  async mounted() {
    this.organizations = await this.organizationService.getOrganizations();
    this.refreshGroups();
  }

  async refreshGroups() {
    try {
      this.loading = true;
      const groups = await this.groupService.getUserGroups(this.organization.id!);
      this.groups = groups.map((g) => Object.assign({focus: false, dirty: false, members: undefined}, g));
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
      await this.groupService.createGroup(this.organization.id!, this.newName);
      this.refreshGroups();
      this.newDialog = false;
      this.resetNewDialog();
      this.app.info(this.$t("users.group-added").toString());
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
      await this.groupService.removeGroup(this.organization.id!, group.id!);
      this.groups = this.groups.filter((item) => item != group);
      this.app.info(this.$t("users.group-removed").toString());
    } catch (e) {
      this.app.error(e);
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
      if (group.members === undefined && !group.isImplicit)
        group.members = await this.getMembers(group);
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
}
</script>
