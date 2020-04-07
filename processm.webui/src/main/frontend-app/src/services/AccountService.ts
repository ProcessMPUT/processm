import Vue from "vue";
import { UsersApiFactory, UsersApi, Configuration } from "@/openapi";
import UserAccount from "@/models/UserAccount";

export default class AccountService {
  private get usersApi(): UsersApi {
    const token = Vue.prototype.$sessionStorage.sessionToken;
    const config = new Configuration();
    if (token) {
      config.accessToken = token;
    }
    return UsersApiFactory(config, "http://localhost:8081/api") as UsersApi;
  }

  public async signIn(username: string, password: string) {
    const response = await this.usersApi.signUserIn(undefined, {
      data: { username, password }
    });

    if (response.status != 201) {
      throw new Error(response.statusText);
    }

    Vue.prototype.$sessionStorage.sessionToken =
      response.data.data.authorizationToken;
  }

  public async prolongSession() {
    const expiredToken = Vue.prototype.$sessionStorage.sessionToken;
    const response = await this.usersApi.signUserIn(expiredToken);

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
      accountDetails.username,
      accountDetails.locale
    ));
  }
}
