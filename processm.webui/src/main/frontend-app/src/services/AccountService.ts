import Vue from "vue";
import UserAccount from "@/models/UserAccount";
import BaseService from "./BaseService";
import UserOrganization from "@/models/UserOrganization";
import { UserAccountInfo } from "@/openapi";

export default class AccountService extends BaseService {
  public async signIn(login: string, password: string) {
    const response = await this.usersApi.signUserIn(
      undefined,
      {
        login: login,
        password: password
      },
      { skipAuthRefresh: true }
    );

    if (response.status != 201) {
      throw new Error(response.statusText);
    }

    Vue.prototype.$sessionStorage.sessionToken = response.data.authorizationToken;
  }

  public async signOut() {
    const response = await this.usersApi.signUserOut().finally(() => Vue.prototype.$sessionStorage.removeSession());

    if (![204, 404].includes(response.status)) {
      throw new Error(response.statusText);
    }
  }

  public async registerNewAccount(userEmail: string, organizationName: string, userPassword: string) {
    const response = await this.usersApi.createAccount({
      organizationName: organizationName,
      userEmail: userEmail,
      userPassword: userPassword
    });

    if (response.status != 201) {
      throw new Error(response.statusText);
    }
  }

  public async getAccountDetails(): Promise<UserAccount> {
    const response = await this.usersApi.getUserAccountDetails();

    if (response.status != 200) {
      throw new Error(response.statusText);
    }

    const accountDetails = response.data;

    return (Vue.prototype.$sessionStorage.userInfo = new UserAccount(accountDetails.email, accountDetails.locale));
  }

  public async changePassword(currentPassword: string, newPassword: string) {
    const response = await this.usersApi.changeUserPassword({
      currentPassword: currentPassword,
      newPassword: newPassword
    });

    if (response.status != 202) {
      throw new Error(response.statusText);
    }
  }

  public async changeLocale(locale: string) {
    const response = await this.usersApi.changeUserLocale({
      locale: locale
    });

    if (response.status != 202) {
      throw new Error(response.statusText);
    }
  }

  public async getOrganizations(): Promise<UserOrganization[]> {
    const response = await this.usersApi.getUserOrganizations();

    if (response.status != 200) {
      throw new Error(response.statusText);
    }

    return (Vue.prototype.$sessionStorage.userOrganizations = response.data);
  }

  public async getUsers(email?: string, limit?: number): Promise<Array<UserAccountInfo>> {
    const response = await this.usersApi.getUsers(email, limit);
    if (response.status != 200) throw new Error(response.statusText);
    return response.data;
  }
}
