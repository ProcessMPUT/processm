import { NotificationsObserver } from "@/utils/NotificationsObserver";
import BaseService from "@/services/BaseService";
import Vue from "vue";

export interface ComponentUpdatedEvent {
  changeType: string;
  workspaceId: string;
  componentId: string;
  workspaceName: string;
  componentName: string;
}

export default class NotificationService extends BaseService {
  public subscribe(callback: (event: ComponentUpdatedEvent) => void) {
    if (!Vue.prototype.$sessionStorage.sessionExists) return;
    const observer = new NotificationsObserver(this.defaultApiPath, callback);
    observer.reauthenticate = async () => {
      if (Vue.prototype.$sessionStorage.sessionExists) {
        await this.prolongExistingSession(undefined);
        return true;
      } else {
        return false;
      }
    };
    return observer;
  }
}
