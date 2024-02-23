import {EventSourcePolyfill, Event} from "event-source-polyfill";
import {WorkspacesApiAxiosParamCreator} from "@/openapi";
import Vue from "vue";

interface ConnectionEvent extends Event {
  status: number;
  statusText: string;
  headers: any;
}

function isConnectionEvent(event: Event): event is ConnectionEvent {
  // This is abhorrent
  return 'status' in event && 'statusText' in event;
}

export class WorkspaceObserver {
  url: string;
  callback: (componentId: string) => void;
  reauthenticate: (() => Promise<boolean>) | undefined;

  private eventSource: EventSourcePolyfill | undefined;

  constructor(apiPath: string, workspaceId: string, callback: (componentId: string) => void) {
    this.url = apiPath + WorkspacesApiAxiosParamCreator().getWorkspace(workspaceId).url;
    this.callback = callback;
  }

  close() {
    this.eventSource?.close();
    this.eventSource = undefined;
  }

  start() {
    this.eventSource?.close();
    this.eventSource = new EventSourcePolyfill(this.url, {
      headers: {
        Authorization: `Bearer ${Vue.prototype.$sessionStorage.sessionToken}`
      }
    });
    this.eventSource.onerror = (event: Event) => {
      if (isConnectionEvent(event) && event.status == 401 && this.reauthenticate != undefined) {
        this.reauthenticate().then(() => {
          this.start();
        });
      }
    };
    this.eventSource.addEventListener("update", (event) => {
      const data = JSON.parse((event as MessageEvent).data);
      const componentId = data.componentId;
      this.callback(componentId);
    });
  }
}