import { Log } from "@/models/Xes";
import BaseService from "./BaseService";
import DuplicateKeysJsonParser from "@/utils/DuplicateKeysJsonParser";

export default class LogsService extends BaseService {
  private readonly jsonParser = new DuplicateKeysJsonParser();

  public async uploadLogFile(dataStoreId: string, file: File): Promise<void> {
    const response = await this.logsApi.uploadLogFile(dataStoreId, file);

    if (response.status != 201) {
      throw new Error(response.statusText);
    }
  }

  public async submitUserQuery(
    dataStoreId: string,
    query?: string,
    accept: "application/json" | "application/zip" = "application/json"
  ): Promise<Array<Log>> {
    let options;
    if (accept == "application/json")
      options = {
        responseType: "text",
        transformResponse: (data: string) => {
          if (data === "") return data;
          try {
            return this.jsonParser.parseFromJson(data);
          } catch {
            return JSON.parse(data);
          }
        }
      };
    else {
      options = {
        responseType: "blob"
      };
    }

    const response = await this.logsApi.submitLogsQuery(
      dataStoreId,
      accept,
      query,
      options
    );
    this.ensureSuccessfulResponseCode(response);

    if (accept == "application/json") {
      return response.data.data.reduce(
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (logs: Log[], item: { log: Log } | any) => {
          if (item != null) {
            logs.push(item.log);
          }

          return logs;
        },
        new Array<Log>()
      );
    } else {
      const blob = new Blob([response.data as any], { type: accept });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = "xes.zip";
      link.click();
      URL.revokeObjectURL(link.href);
      return new Array<Log>();
    }
  }
}
