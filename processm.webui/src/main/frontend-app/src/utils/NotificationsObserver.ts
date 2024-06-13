import { Event, EventSourcePolyfill } from "event-source-polyfill";
import Vue from "vue";
import { ComponentUpdatedEvent } from "@/services/NotificationService";

interface ConnectionEvent extends Event {
  status: number;
  statusText: string;
  headers: any;
}

function isConnectionEvent(event: Event): event is ConnectionEvent {
  // This is abhorrent
  return "status" in event && "statusText" in event;
}

export class NotificationsObserver {
  url: string;
  callback: (event: ComponentUpdatedEvent) => void;
  reauthenticate: (() => Promise<boolean>) | undefined;

  private eventSource: EventSourcePolyfill | undefined;

  constructor(apiPath: string, callback: (event: ComponentUpdatedEvent) => void) {
    this.url = apiPath + "/notifications";
    this.callback = callback;
  }

  close() {
    this.eventSource?.close();
    this.eventSource = undefined;
  }

  async start() {
    this.eventSource?.close();
    this.eventSource = new EventSourcePolyfill(this.url, {
      headers: {
        Authorization: `Bearer ${Vue.prototype.$sessionStorage.sessionToken}`
      }
    });
    this.eventSource.onerror = (event: Event) => {
      if (isConnectionEvent(event) && event.status == 401 && this.reauthenticate != undefined) {
        this.reauthenticate().then(async () => {
          await this.start();
        });
      }
    };
    this.eventSource.addEventListener("update", (event) => {
      this.callback(JSON.parse((event as MessageEvent).data) as ComponentUpdatedEvent);
    });
  }
}
