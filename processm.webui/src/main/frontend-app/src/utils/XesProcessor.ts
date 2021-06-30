import {
  Log,
  Trace,
  XesAttribute,
  XesComponent,
  isXesAttribute
} from "@/models/Xes";

export enum XesComponentScope {
  Log = "Log",
  Trace = "Trace",
  Event = "Event"
}

export class LogItem {
  constructor(readonly scope: XesComponentScope, readonly _id: number) {}

  readonly _children: LogItem[] = [];
  [key: string]: {};
}

export default class XesProcessor {
  private readonly keysWithArrayValue = new Set([
    "date",
    "boolean",
    "float",
    "int",
    "string",
    "classifier",
    "extension"
  ]);

  private logItemFactories = new Map<
    XesComponentScope,
    (number: number) => LogItem
  >([
    [
      XesComponentScope.Log,
      (id: number) => new LogItem(XesComponentScope.Log, id)
    ],
    [
      XesComponentScope.Trace,
      (id: number) => new LogItem(XesComponentScope.Trace, id)
    ],
    [
      XesComponentScope.Event,
      (id: number) => new LogItem(XesComponentScope.Event, id)
    ]
  ]);

  public extractHierarchicalLogItemsFromAllScopes(
    logs: Log[]
  ): { headers: string[]; logItems: LogItem[] } {
    let iterator = 0;

    const headers = new Set<string>();
    const logItems = this.ensureArray(logs).map((log) => {
      const extractedLog = this.extractElementAttributesValuesFromXesComponent(
        log,
        XesComponentScope.Log,
        iterator++
      );

      Object.keys(extractedLog).forEach((attribute) => headers.add(attribute));
      this.ensureArray(log.trace).reduce((traces: LogItem[], trace: Trace) => {
        if (trace != null) {
          const extractedTrace = this.extractElementAttributesValuesFromXesComponent(
            trace,
            XesComponentScope.Trace,
            iterator++
          );

          Object.keys(extractedTrace).forEach((attribute) =>
            headers.add(attribute)
          );
          this.ensureArray(trace.event).reduce(
            (events: LogItem[], event: XesComponent) => {
              if (event != null) {
                const extractedEvent = this.extractElementAttributesValuesFromXesComponent(
                  event,
                  XesComponentScope.Event,
                  iterator++
                );
                events.push(extractedEvent);
                Object.keys(extractedEvent).forEach((attribute) =>
                  headers.add(attribute)
                );
              }
              return events;
            },
            extractedTrace._children
          );
          traces.push(extractedTrace);
        }
        return traces;
      }, extractedLog._children);
      return extractedLog;
    });
    return { headers: Array.from(headers), logItems };
  }

  private extractElementAttributesValuesFromXesComponent(
    component: XesComponent,
    componentScope: XesComponentScope,
    itemId: number
  ): LogItem {
    const logItemFactory = this.logItemFactories.get(componentScope);

    if (logItemFactory == null) throw new Error("Unrecognized item scope");

    const logItem = logItemFactory(itemId);

    Object.keys(component).forEach((xesAttributeName) => {
      if (this.keysWithArrayValue.has(xesAttributeName)) {
        this.extractValuesFromAttributeOrArrayOfAttributes(
          component[xesAttributeName as keyof XesComponent]
        ).forEach(
          (attributeValue, attributeName) =>
            (logItem[attributeName] = attributeValue)
        );
      }
    });

    return logItem;
  }

  private extractValuesFromAttributeOrArrayOfAttributes(
    attributes: XesAttribute | XesAttribute[] | undefined
  ): Map<string, string> {
    const extractedAttributes = new Map<string, string>();

    this.ensureArray(attributes).forEach((attribute) => {
      if (!isXesAttribute(attribute)) return;

      extractedAttributes.set(
        attribute["@key"],
        attribute["@value"]?.toString() ?? ""
      );
    });

    return extractedAttributes;
  }

  private ensureArray<TObject>(
    singleObjectOrCollection: TObject | TObject[] | null | undefined
  ): TObject[] {
    if (singleObjectOrCollection == null) return [];

    return Array.isArray(singleObjectOrCollection)
      ? singleObjectOrCollection
      : [singleObjectOrCollection];
  }
}
