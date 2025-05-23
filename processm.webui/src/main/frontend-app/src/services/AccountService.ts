import Vue from "vue";
import UserAccount from "@/models/UserAccount";
import BaseService from "./BaseService";
import { UserAccountInfo, UserRoleInOrganization } from "@/openapi";
import { AxiosAuthRefreshRequestConfig } from "axios-auth-refresh";

export default class AccountService extends BaseService {
  public async signIn(login: string, password: string) {
    const response = await this.usersApi.signUserIn(
      undefined,
      {
        login: login,
        password: password
      },
      { skipAuthRefresh: true } as AxiosAuthRefreshRequestConfig
    );

    console.assert(response.status == 201, response.statusText);

    Vue.prototype.$sessionStorage.sessionToken = response.data.authorizationToken;
  }

  public async signOut() {
    const response = await this.usersApi
      .signUserOut({
        validateStatus: (status: number) => status == 204 || status == 404
      })
      .finally(() => Vue.prototype.$sessionStorage.removeSession());

    console.assert(response.status == 204 || response.status == 404, response.statusText);
  }

  public async registerNewAccount(userEmail: string, userPassword: string, newOrganization: boolean, organizationName?: string) {
    const response = await this.usersApi.createAccount({
      userEmail: userEmail,
      userPassword: userPassword,
      newOrganization: newOrganization,
      organizationName: organizationName
    });

    console.assert(response.status == 201, response.statusText);
  }

  /**
   * Retrieve the user's email and locale from the server. The retrieved value is returned and, as a side effect,
   * stored in `$sessionStorage.userInfo`.
   */
  public async updateUserInfo(): Promise<UserAccount> {
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

    console.assert(response.status == 204, response.statusText);
  }

  public async changeLocale(locale: string) {
    const response = await this.usersApi.changeUserLocale({
      locale: locale
    });
    if (response.status == 200) {
      const accountDetails = response.data;
      return (Vue.prototype.$sessionStorage.userInfo = new UserAccount(accountDetails.email, accountDetails.locale));
    } else return undefined;
  }

  public async getUserOrganizations(): Promise<UserRoleInOrganization[]> {
    const response = await this.usersApi.getUserOrganizations();

    console.assert(response.status == 200, response.statusText);

    return (Vue.prototype.$sessionStorage.userOrganizations = response.data);
  }

  public async getUsers(email?: string, limit?: number): Promise<Array<UserAccountInfo>> {
    const response = await this.usersApi.getUsers(email, limit);
    if (response.status != 200) throw new Error(response.statusText);
    return response.data;
  }

  public async requestPasswordReset(userEmail: string) {
    const response = await this.usersApi.resetPasswordRequestPost({ email: userEmail });

    console.assert(response.status == 202, response.statusText);
  }

  public async resetPassword(token: string, newPassword: string) {
    const response = await this.usersApi.resetPasswordTokenPost(token, {
      currentPassword: "",
      newPassword: newPassword
    });

    console.assert(response.status == 200, response.statusText);
  }
}
