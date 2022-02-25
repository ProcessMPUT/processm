import JdbcEtlColumnConfiguration from "@/models/JdbcEtlColumnConfiguration";

export default interface JdbcEtlProcessConfiguration {
    query: string;
    refresh: number | undefined;
    enabled: boolean;
    batch: boolean;
    traceId: JdbcEtlColumnConfiguration;
    eventId: JdbcEtlColumnConfiguration;
    attributes: JdbcEtlColumnConfiguration[];
    lastEventExternalId: string | undefined;
    lastEventExternalIdType: number | undefined;
}