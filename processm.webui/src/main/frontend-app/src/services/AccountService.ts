import Vue from "vue";
import { UsersApiFactory, UsersApi, Configuration } from "@/openapi";

export default class AccountService {
  usersApiFactory!: () => UsersApi;
  private get usersApi(): UsersApi {
    const token = Vue.prototype.$sessionStorage.sessionToken;
    const config = new Configuration();
    if (token) {
      config.accessToken = token;
    }
    console.log(token);
    return UsersApiFactory(config, "http://localhost:8080/api") as UsersApi;
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
}
