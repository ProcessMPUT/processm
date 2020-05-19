import _Vue from "vue";
import VueSession from "vue-session";
import UserAccount from "@/models/UserAccount";
import UserOrganization from "@/models/UserOrganization";

class SessionStorage {
  static install(Vue: typeof _Vue, options = {}) {
    Vue.use(VueSession, options);
    Vue.prototype.$sessionStorage = this;
  }

  private static readonly TokenKey = "session_token";
  private static readonly UserInfoKey = "user_info";
  private static readonly UserOrganizationsKey = "user_organizations";
  private static readonly CurrentOrganizationIndexKey = "current_organization";
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

  static get userOrganizations(): UserOrganization[] {
    return this.session.get(this.UserOrganizationsKey);
  }

  static set userOrganizations(userOrganizations: UserOrganization[]) {
    this.session.set(this.UserOrganizationsKey, userOrganizations);
  }

  static get currentOrganizationIndex(): number {
    return this.session.get(this.CurrentOrganizationIndexKey);
  }

  static set currentOrganizationIndex(organizationIndex: number) {
    this.session.set(this.CurrentOrganizationIndexKey, organizationIndex);
  }

  static get currentOrganization(): UserOrganization {
    return this.userOrganizations[this.currentOrganizationIndex];
  }

  static removeSession() {
    this.session.destroy();
  }
}

export default SessionStorage;
