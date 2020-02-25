import _Vue from "vue";
import VueSession from "vue-session";
import UserAccount from "@/models/UserAccount";

class SessionStorage {
  static install(Vue: typeof _Vue, options = {}) {
    Vue.use(VueSession, options);
    Vue.prototype.$sessionStorage = this;
  }

  private static readonly TokenKey = "session_token";
  private static readonly UserInfoKey = "user_info";
  private static get session() {
    return _Vue.prototype.$session;
  }

  static getSessionToken(): string {
    return this.session.get(this.TokenKey);
  }

  static setSessionToken(token: string) {
    this.session.set(this.TokenKey, token);
  }

  static sessionExists(): boolean {
    return this.session.has(this.TokenKey);
  }

  static getUserInfo(): UserAccount {
    return this.session.get(this.UserInfoKey);
  }

  static setUserInfo(userInfo: UserAccount) {
    this.session.set(this.UserInfoKey, userInfo);
  }

  static removeSession() {
    this.session.destroy();
  }
}

export default SessionStorage;
