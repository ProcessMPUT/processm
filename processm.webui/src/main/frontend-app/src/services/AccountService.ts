import axios from "axios";
import UserAccount from "@/models/UserAccount";

export default class AccountService {
  public async signIn(
    username: string,
    password: string
  ): Promise<{ userData: UserAccount; token: string }> {
    const response = await axios.post("/api/account/session", {
      username,
      password
    });

    return response.data;
  }

  public async signOut(sessionToken: string): Promise<boolean> {
    const response = await axios.delete(`/api/account/session/${sessionToken}`);

    return [200, 404].includes(response.status);
  }
}
