<template>
  <v-treeview :items="organizations" item-children="children" item-key="organization.id" item-text="organization.name">
    <template v-slot:append="{item}">
      <v-btn icon v-if="item.organization.parentOrganizationId !== undefined" @click.stop="detach(item)">
        <v-icon>arrow_upward</v-icon>
      </v-btn>
      <v-btn icon @click.stop="beginAttach(item)">
        <v-icon>arrow_downward</v-icon>
      </v-btn>
      <v-btn icon>
        <v-icon small @click="remove(item)">delete_forever</v-icon>
      </v-btn>
    </template>
    <template v-slot:label="{item}">
      <v-btn v-if="orgToAttach!==null" @click.stop="endAttach(item)">
        {{ item.organization.name }}
      </v-btn>
      <span v-else>
        {{ item.organization.name }}
      </span>
    </template>
  </v-treeview>
</template>

<script lang="ts">
import Vue from "vue";
import {Component, Inject} from "vue-property-decorator";
import OrganizationService from "@/services/OrganizationService";
import AccountService from "@/services/AccountService";
import App from "@/App.vue";
import {Organization} from "@/openapi";

class OrganizationTreeItem {
  organization: Organization | undefined;
  children: OrganizationTreeItem[] = [];
}

@Component
export default class OrganizationList extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  @Inject() organizationService!: OrganizationService;

  organizations: OrganizationTreeItem[] = [];
  orgToAttach: Organization | null = null;

  async mounted() {
    await this.load();
  }

  async load() {
    const organizations = await this.organizationService.getOrganizations();
    const items: { [id: string]: OrganizationTreeItem } = {};
    for (const org of organizations) {
      if (org.id !== undefined) {
        items[org.id] = {organization: org, children: []};
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

}
</script>
