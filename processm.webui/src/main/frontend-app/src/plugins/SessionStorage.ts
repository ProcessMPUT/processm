import _Vue, { Ref, ref, watch } from "vue";
import VueSession from "vue-session";
import UserAccount from "@/models/UserAccount";
import { Organization, UserRoleInOrganization } from "@/openapi";

class SessionStorage {
  static install(Vue: typeof _Vue, options = {}) {
    Vue.use(VueSession, options);
    Vue.prototype.$sessionStorage = this;
    this.userOrganizationsRef.value = this.session.get(this.UserOrganizationsKey);
  }

  private static readonly TokenKey = "session_token";
  private static readonly UserInfoKey = "user_info";
  private static readonly UserOrganizationsKey = "user_organizations";
  private static readonly CurrentOrganizationIndexKey = "current_organization";
  private static readonly DefaultDataStoreKey = "default_data_source";

  /**
   * This is exposed only to be used in `watch` clauses. To access, use the getter/setter
   */
  public static readonly userOrganizationsRef: Ref<UserRoleInOrganization[]> = ref([]);

  private static get session() {
    return _Vue.prototype.$session;
  }

  static get sessionToken(): string {
    return this.session.get(this.TokenKey);
  }

  static set sessionToken(token: string) {
    this.session.set(this.TokenKey, token);
  }

  static get sessionExists(): boolean {
    return this.session.has(this.TokenKey);
  }

  static get userInfo(): UserAccount {
    return this.session.get(this.UserInfoKey);
  }

  static set userInfo(userInfo: UserAccount) {
    this.session.set(this.UserInfoKey, userInfo);
  }

  static get userOrganizations(): UserRoleInOrganization[] {
    return this.userOrganizationsRef.value;
  }

  static set userOrganizations(userOrganizations: UserRoleInOrganization[]) {
    let orgId: string | undefined = undefined;
    if (this.sessionExists && this.session !== undefined) orgId = this.currentOrganization?.id;
    this.userOrganizationsRef.value = userOrganizations;
    this.session.set(this.UserOrganizationsKey, userOrganizations);
    if (orgId !== undefined && this.currentOrganization?.id != orgId) this.switchToOrganization(orgId);
  }

  static get currentOrganizationIndex(): number {
    return this.session.get(this.CurrentOrganizationIndexKey);
  }

  static set currentOrganizationIndex(organizationIndex: number) {
    this.session.set(this.CurrentOrganizationIndexKey, organizationIndex);
  }

  static get currentOrganization(): Organization | undefined {
    if (this.currentOrganizationIndex < 0 || this.currentOrganizationIndex >= this.userOrganizations.length) return undefined;
    return this.userOrganizations[this.currentOrganizationIndex]?.organization;
  }

  static set defaultDataStoreId(dataStoreId: string) {
    this.session.set(this.DefaultDataStoreKey, dataStoreId);
  }

  static get defaultDataStoreId(): string {
    return this.session.get(this.DefaultDataStoreKey);
  }

  static removeSession() {
    this.session.destroy();
  }

  static switchToOrganization(organizationId: string): boolean {
    const idx = this.userOrganizations.findIndex((role) => role.organization.id == organizationId);
    if (idx < 0) return false;
    this.currentOrganizationIndex = idx;
    return true;
  }
}

export default SessionStorage;
