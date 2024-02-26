import Vue from "vue";
import Router from "@/router";
import { BaseAPI } from "@/openapi/base";
import axios, { AxiosError, AxiosInstance } from "axios";
import { ConfigApi, Configuration, DataStoresApi, GroupsApi, LogsApi, OrganizationsApi, UsersApi, WorkspacesApi } from "@/openapi";
import createAuthRefreshInterceptor from "axios-auth-refresh";

export default abstract class BaseService {
  protected readonly defaultApiPath = "/api";
  private readonly axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create();
    createAuthRefreshInterceptor(this.axiosInstance, this.prolongExistingSession);
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      (error) => {
        const response = error.response;
        throw new Error(response?.data?.error ?? response?.statusText ?? error.message);
      }
    );
  }

  protected getGenericClient<ApiClient extends BaseAPI>(
    apiClientCtor: new (configuration: Configuration, basePath: string, axios: AxiosInstance) => ApiClient
  ): ApiClient {
    const token = Vue.prototype.$sessionStorage.sessionToken;
    return new apiClientCtor(new Configuration({ accessToken: token }), this.defaultApiPath, this.axiosInstance);
  }

  protected get configApi() {
    return this.getGenericClient(ConfigApi);
  }

  protected get usersApi() {
    return this.getGenericClient(UsersApi);
  }

  protected get groupsApi() {
    return this.getGenericClient(GroupsApi);
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

  protected prolongExistingSession(failedRequest: AxiosError<object> | undefined): Promise<void> {
    const expiredToken = Vue.prototype.$sessionStorage.sessionToken;
    const getAuthorizationHeaderValue = (token: string) => `Bearer ${token}`;

    return new UsersApi()
      .signUserIn(getAuthorizationHeaderValue(expiredToken))
      .then((tokenRefreshResponse) => {
        const newToken = tokenRefreshResponse.data.authorizationToken;
        Vue.prototype.$sessionStorage.sessionToken = newToken;

        if (failedRequest?.response != undefined) {
          failedRequest.response.config.headers["Authorization"] = getAuthorizationHeaderValue(newToken);
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
