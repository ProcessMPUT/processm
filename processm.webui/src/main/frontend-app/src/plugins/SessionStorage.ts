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

  static removeSession() {
    this.session.destroy();
  }
}

export default SessionStorage;
