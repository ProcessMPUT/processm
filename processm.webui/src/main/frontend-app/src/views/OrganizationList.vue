<template>

  <v-card>
    <v-card-title>
      <v-toolbar flat>
        <v-toolbar-title> {{ $t("users.organizations") }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn color="primary" @click.stop="createNewDialog=true;">
          {{ $t("common.add-new") }}
        </v-btn>
      </v-toolbar>
    </v-card-title>
    <v-card-text>
      <v-treeview :items="organizations" item-children="children" item-key="organization.id"
                  item-text="organization.name">
        <template v-slot:prepend="{item}">
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn icon @click.stop="togglePrivate(item)" v-bind="attrs" v-on="on">
                <v-icon v-if="item.organization.isPrivate">lock</v-icon>
                <v-icon v-else>lock_open</v-icon>
              </v-btn>
            </template>
            <span v-if="item.organization.isPrivate">{{ $t("organizations.switch-to-public") }}</span>
            <span v-else>{{ $t("organizations.switch-to-private") }}</span>
          </v-tooltip>
        </template>
        <template v-slot:append="{item}">
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn icon v-if="item.organization.parentOrganizationId !== undefined && item.canDetach"
                     @click.stop="detach(item)" v-bind="attrs" v-on="on">
                <v-icon>arrow_upward</v-icon>
              </v-btn>
            </template>
            <span>{{ $t("organizations.detach") }}</span>
          </v-tooltip>
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn icon @click.stop="beginAttach(item)" v-if="item.canAttach" v-bind="attrs" v-on="on">
                <v-icon>arrow_downward</v-icon>
              </v-btn>
            </template>
            <span>{{ $t("organizations.attach") }}</span>
          </v-tooltip>
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn icon v-if="item.canRemove" v-bind="attrs" v-on="on">
                <v-icon small @click="remove(item)">delete_forever</v-icon>
              </v-btn>
            </template>
            <span>{{ $t("common.remove") }}</span>
          </v-tooltip>
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn icon v-if="item.canAttachTo" v-bind="attrs" v-on="on">
                <v-icon small @click="beginCreate(item)">add</v-icon>
              </v-btn>
            </template>
            <span>{{ $t("common.add-new") }}</span>
          </v-tooltip>
          <v-tooltip bottom>
            <template v-slot:activator="{ on, attrs }">
              <v-btn icon v-if="item.canLogin && item.organization.id != $sessionStorage.currentOrganization.id"
                     v-bind="attrs" v-on="on">
                <v-icon small @click="login(item)">input</v-icon>
              </v-btn>
            </template>
            <span>{{ $t("organizations.login") }}</span>
          </v-tooltip>
        </template>
        <template v-slot:label="{item}">
          <v-btn v-if="orgToAttach!==null && item.organization.id != orgToAttach.id && item.canAttachTo"
                 @click.stop="endAttach(item)">
            {{ item.organization.name }}
          </v-btn>
          <v-text-field
              v-model="item.organization.name"
              background-color="transparent"
              flat
              hide-details
              solo
              @blur="item.focus = false"
              @change="item.dirty = true"
              @focus="item.focus = true"
              v-else
          >
            <template v-slot:append>
              <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                  <v-btn
                      :disabled="!((item.dirty || item.focus) && item.canEdit)"
                      color="primary"
                      dark
                      icon
                      v-bind="attrs"
                      @click="editName(item)"
                      v-on="on"
                      @keyup.enter.native="editName(item)"
                  >
                    <v-icon v-show="(item.dirty || item.focus) && item.canEdit" small>edit</v-icon>
                  </v-btn>
                </template>
                {{ $t("common.edit") }}
              </v-tooltip>
            </template>
          </v-text-field>
        </template>
      </v-treeview>
      <new-dialog :value="createNewDialog" @submitted="endCreate"
                  @cancelled="createNewDialog=false; orgToAttach=null;"/>
    </v-card-text>
  </v-card>
</template>

<script lang="ts">
import Vue from "vue";
import {Component, Inject} from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import AccountService from "@/services/AccountService";
import App from "@/App.vue";
import {Organization, OrganizationRole} from "@/openapi";
import NewDialog from "@/components/NewDialog.vue";

class OrganizationTreeItem {
  organization: Organization | undefined;
  children: OrganizationTreeItem[] = [];
  canAttachTo: boolean = false;
  canAttach: boolean = false;
  canDetach: boolean = false;
  canRemove: boolean = false;
  canEdit: boolean = false;
  canLogin: boolean = false;
  dirty: boolean = false;
  focus: boolean = false;
}

@Component({
  components: {NewDialog}
})
export default class OrganizationList extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  @Inject() organizationService!: OrganizationService;

  organizations: OrganizationTreeItem[] = [];
  orgToAttach: Organization | null = null;
  createNewDialog: boolean = false;

  async mounted() {
    await this.load();
  }

  /**
   * Returns true if actual >= minimal in the business order of roles. Partially written by ChatGPT.
   */
  private atLeast(actual: OrganizationRole | undefined, minimal: OrganizationRole): boolean {
    if (actual === undefined) return false;
    const roles = Object.values(OrganizationRole);
    const actualIndex = roles.indexOf(actual);
    const minimalIndex = roles.indexOf(minimal);

    return actualIndex <= minimalIndex;
  }

  async load() {
    const perm: { [id: string]: OrganizationRole } = {};
    for (const item of this.$sessionStorage.userOrganizations) {
      if (item.organization.id !== undefined)
        perm[item.organization.id] = item.role;
    }
    const organizations = await this.organizationService.getOrganizations();
    const items: { [id: string]: OrganizationTreeItem } = {};
    for (const org of organizations) {
      if (org.id !== undefined) {
        const orgPerm = perm[org.id];
        const parentPerm = org.parentOrganizationId !== undefined ? perm[org.parentOrganizationId] : undefined;
        items[org.id] = {
          organization: org,
          canAttachTo: this.atLeast(orgPerm, OrganizationRole.Writer),
          canAttach: this.atLeast(orgPerm, OrganizationRole.Owner),
          canRemove: this.atLeast(orgPerm, OrganizationRole.Owner),
          canDetach: this.atLeast(orgPerm, OrganizationRole.Owner) || this.atLeast(parentPerm, OrganizationRole.Writer),
          canEdit: this.atLeast(orgPerm, OrganizationRole.Writer),
          canLogin: this.atLeast(orgPerm, OrganizationRole.Reader),
          dirty: false,
          focus: false,
          children: []
        };
      }
    }
    for (const org of organizations) {
      if (org.id !== undefined && org.parentOrganizationId !== undefined) {
        items[org.parentOrganizationId].children.push(items[org.id]);
      }
    }
    this.organizations = Object
        .values(items)
        .filter((org) => org.organization?.parentOrganizationId === undefined)
        .sort((a, b) => (a.organization?.name ?? "").localeCompare(b.organization?.name ?? ""))
  }

  async beginAttach(item: OrganizationTreeItem) {
    this.orgToAttach = item.organization ?? null;
  }

  async endAttach(item: OrganizationTreeItem) {
    try {
      const subOrganizationId = this.orgToAttach?.id
      const organizationId = item.organization?.id
      await this.organizationService.attach(organizationId!, subOrganizationId!)
      this.app.success(this.$t("organizations.moved").toString())
      await this.load()
    } catch (e) {
      console.error(e)
      this.app.error(this.$t("common.operation-error").toString())
    } finally {
      this.orgToAttach = null
    }
  }

  async detach(item: OrganizationTreeItem) {
    try {
      const organizationId = item.organization?.parentOrganizationId
      const subOrganizationId = item.organization?.id
      await this.organizationService.detach(organizationId!, subOrganizationId!)
      this.app.success(this.$t("organizations.moved").toString())
      await this.load()
    } catch (e) {
      console.error(e)
      this.app.error(this.$t("common.operation-error").toString())
    }
  }

  async remove(item: OrganizationTreeItem) {
    try {
      const organizationId = item.organization?.id
      await this.organizationService.removeOrganization(organizationId!)
      this.app.success(this.$t("common.removal.success").toString())
      await this.load()
    } catch (e) {
      console.error(e)
      this.app.error(this.$t("common.removal.failure").toString())
    }
  }

  async beginCreate(item: OrganizationTreeItem) {
    this.orgToAttach = item.organization ?? null;
    this.createNewDialog = true;
  }

  async endCreate(name: string) {
    try {
      const newOrg = (await this.organizationService.createOrganization(name, false))?.id!
      const parentOrgId = this.orgToAttach?.id
      if (parentOrgId !== undefined)
        await this.organizationService.attach(parentOrgId, newOrg)
      await this.load()
      this.app.success(this.$t("common.creating.success").toString())
    } catch (e) {
      this.app.error(this.$t("common.creating.error").toString())
    } finally {
      this.createNewDialog = false;
      this.orgToAttach = null;
    }
  }

  async togglePrivate(item: OrganizationTreeItem) {
    try {
      const org = item.organization!
      await this.organizationService.updateOrganization(org.id!, org.name, !org.isPrivate)
      await this.load()
      this.app.success(this.$t("common.saving.success").toString())
    } catch (e) {
      this.app.error(this.$t("common.saving.error").toString())
    }
  }

  async editName(item: OrganizationTreeItem) {
    try {
      const org = item.organization!
      //org.name contains the new name
      await this.organizationService.updateOrganization(org.id!, org.name, org.isPrivate)
      await this.load()
      this.app.success(this.$t("common.saving.success").toString())
    } catch (e) {
      this.app.error(this.$t("common.saving.error").toString())
    }
  }

  async login(item: OrganizationTreeItem) {
    const orgId = item.organization?.id!;
    if (this.$sessionStorage.currentOrganization.id != orgId) {
      this.$sessionStorage.switchToOrganization(orgId);
      // Refresh page for all components to take the new organization into account
      this.$router.go(0);
    }
  }
}
</script>
