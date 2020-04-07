export default class UserAccount {
  constructor(public username: string, public locale: string) {}

  get language(): string {
    return this.locale?.substring(0, 2);
  }
}
