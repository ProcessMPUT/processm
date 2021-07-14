import { Log } from "@/models/Xes";
import BaseService from "./BaseService";
import DuplicateKeysJsonParser from "@/utils/DuplicateKeysJsonParser";

export default class LogsService extends BaseService {
  private readonly jsonParser = new DuplicateKeysJsonParser();

  public async uploadLogFile(dataSourceId: string, file: File): Promise<void> {
    const response = await this.logsApi.uploadLogFile(dataSourceId, file);

    if (response.status != 201) {
      throw new Error(response.statusText);
    }
  }

  public async submitUserQuery(
    dataSourceId: string,
    query?: string
  ): Promise<Array<Log>> {
    const response = await this.logsApi.submitLogsQuery(dataSourceId, query, {
      responseType: "text",
      transformResponse: (data: string) => {
        try {
          return this.jsonParser.parseFromJson(data);
        } catch {
          return JSON.parse(data);
        }
      }
    });
    if (response.status != 200) {
      throw new Error(response.statusText);
    }

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
  }
}
