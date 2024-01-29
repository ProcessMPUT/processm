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
        <template v-slot:append="{item}">
          <v-btn icon v-if="item.organization.parentOrganizationId !== undefined && item.canDetach"
                 @click.stop="detach(item)">
            <v-icon>arrow_upward</v-icon>
          </v-btn>
          <v-btn icon @click.stop="beginAttach(item)" v-if="item.canAttach">
            <v-icon>arrow_downward</v-icon>
          </v-btn>
          <v-btn icon v-if="item.canRemove">
            <v-icon small @click="remove(item)">delete_forever</v-icon>
          </v-btn>
          <v-btn icon v-if="item.canAttachTo">
            <v-icon small @click="beginCreate(item)">add</v-icon>
          </v-btn>
        </template>
        <template v-slot:label="{item}">
          <v-btn v-if="orgToAttach!==null && item.organization.id != orgToAttach.id && item.canAttachTo"
                 @click.stop="endAttach(item)">
            {{ item.organization.name }}
          </v-btn>
          <span v-else>
        {{ item.organization.name }}
      </span>
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
          children: []
        };
      }
    }
    for (const org of organizations) {
      if (org.id !== undefined && org.parentOrganizationId !== undefined) {
        items[org.parentOrganizationId].children.push(items[org.id]);
      }
    }
    this.organizations = Object.values(items).filter((org) => org.organization?.parentOrganizationId === undefined);
  }

  async beginAttach(item: OrganizationTreeItem) {
    this.orgToAttach = item.organization ?? null;
  }

  async endAttach(item: OrganizationTreeItem) {
    try {
      const subOrganizationId = this.orgToAttach?.id
      const organizationId = item.organization?.id
      await this.organizationService.attach(organizationId!, subOrganizationId!)
      this.app.success(this.$t("users.organization-moved").toString())
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
      this.app.success(this.$t("users.organization-moved").toString())
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
      this.app.success(this.$t("common.creating.error").toString())
    } finally {
      this.createNewDialog = false;
      this.orgToAttach = null;
    }
  }
}
</script>
