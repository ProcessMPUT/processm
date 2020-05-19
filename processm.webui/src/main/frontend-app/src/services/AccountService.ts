import Vue from "vue";
import UserAccount from "@/models/UserAccount";
import BaseService from "./BaseService";
import UserOrganization from "@/models/UserOrganization";

export default class AccountService extends BaseService {
  public async signIn(login: string, password: string) {
    const response = await this.usersApi.signUserIn(
      undefined,
      {
        data: { login, password }
      },
      { skipAuthRefresh: true }
    );

    if (response.status != 201) {
      throw new Error(response.statusText);
    }

    Vue.prototype.$sessionStorage.sessionToken =
      response.data.data.authorizationToken;
  }

  public async signOut() {
    const response = await this.usersApi
      .signUserOut()
      .finally(() => Vue.prototype.$sessionStorage.removeSession());

    if (![204, 404].includes(response.status)) {
      throw new Error(response.statusText);
    }
  }

  public async registerNewAccount(userEmail: string, organizationName: string) {
    const response = await this.usersApi.createAccount({
      data: { userEmail, organizationName }
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

    const accountDetails = response.data.data;

    return (Vue.prototype.$sessionStorage.userInfo = new UserAccount(
      accountDetails.userEmail,
      accountDetails.locale
    ));
  }

  public async changePassword(currentPassword: string, newPassword: string) {
    const response = await this.usersApi.changeUserPassword({
      data: { currentPassword, newPassword }
    });

    if (response.status != 202) {
      throw new Error(response.statusText);
    }
  }

  public async changeLocale(locale: string) {
    const response = await this.usersApi.changeUserLocale({
      data: { locale }
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

    return (Vue.prototype.$sessionStorage.userOrganizations =
      response.data.data);
  }
}
