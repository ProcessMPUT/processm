import Vue from "vue";
import { BaseAPI } from "@/openapi/base";
import axios, { AxiosInstance, AxiosError } from "axios";
import { Configuration } from "@/openapi";
import { UsersApi } from "@/openapi";
import createAuthRefreshInterceptor from "axios-auth-refresh";

export default abstract class BaseService {
  private readonly defaultApiPath = "/api";
  private readonly axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create();
    createAuthRefreshInterceptor(this.axiosInstance, error =>
      this.prolongExistingSession(error, this.usersApi)
    );
  }

  protected getGenericClient<ApiClient extends BaseAPI>(
    apiClientCtor: new (
      configuration: Configuration,
      basePath: string,
      axios: AxiosInstance
    ) => ApiClient
  ): ApiClient {
    const token = Vue.prototype.$sessionStorage.sessionToken;
    return new apiClientCtor(
      { accessToken: token },
      this.defaultApiPath,
      this.axiosInstance
    );
  }

  protected get usersApi() {
    return this.getGenericClient(UsersApi);
  }

  private prolongExistingSession(
    failedRequest: AxiosError<object>,
    api: UsersApi
  ): Promise<void> {
    const expiredToken = Vue.prototype.$sessionStorage.sessionToken;
    const getAuthorizationHeaderValue = (token: string) => `Bearer ${token}`;

    return api
      .signUserIn(getAuthorizationHeaderValue(expiredToken))
      .then(tokenRefreshResponse => {
        const newToken = tokenRefreshResponse.data.data.authorizationToken;
        Vue.prototype.$sessionStorage.sessionToken = newToken;

        if (failedRequest.response != null) {
          failedRequest.response.config.headers[
            "Authorization"
          ] = getAuthorizationHeaderValue(newToken);
        }
        return Promise.resolve();
      })
      .catch(() => {
        Vue.prototype.$sessionStorage.removeSession();
        return Promise.reject();
      });
  }
}