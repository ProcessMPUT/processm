import Vue from "vue";
import Router from "@/router";
import { BaseAPI } from "@/openapi/base";
import axios, { AxiosInstance, AxiosError, AxiosResponse } from "axios";
import {
  Configuration,
  ConfigApi,
  UsersApi,
  OrganizationsApi,
  WorkspacesApi,
  DataStoresApi,
  LogsApi
} from "@/openapi";
import createAuthRefreshInterceptor from "axios-auth-refresh";

export default abstract class BaseService {
  private readonly defaultApiPath = "/api";
  private readonly axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create();
    createAuthRefreshInterceptor(this.axiosInstance, (error) =>
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

  protected get configApi() {
    return this.getGenericClient(ConfigApi);
  }

  protected get usersApi() {
    return this.getGenericClient(UsersApi);
  }

  protected get organizationsApi() {
    return this.getGenericClient(OrganizationsApi);
  }

  protected get workspacesApi() {
    return this.getGenericClient(WorkspacesApi);
  }

  protected get dataStoresApi() {
    return this.getGenericClient(DataStoresApi);
  }

  protected get logsApi() {
    return this.getGenericClient(LogsApi);
  }

  protected ensureSuccessfulResponseCode(
    response: AxiosResponse,
    ...successfulStatusCodes: Array<number>
  ) {
    const responseStatusCode = response.status;

    if (
      successfulStatusCodes.length == 0 &&
      responseStatusCode >= 200 &&
      responseStatusCode < 300
    ) {
      return;
    } else if (successfulStatusCodes.includes(responseStatusCode)) {
      return;
    }

    throw new Error(response.data?.error ?? response.statusText);
  }

  private prolongExistingSession(
    failedRequest: AxiosError<object>,
    api: UsersApi
  ): Promise<void> {
    const expiredToken = Vue.prototype.$sessionStorage.sessionToken;
    const getAuthorizationHeaderValue = (token: string) => `Bearer ${token}`;

    return api
      .signUserIn(getAuthorizationHeaderValue(expiredToken))
      .then((tokenRefreshResponse) => {
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
        Router.push("login");
        return Promise.reject();
      });
  }
}
