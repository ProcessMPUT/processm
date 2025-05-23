const jdbcRegEx = /^(jdbc:\w+:.+(:\d+)?[;:/]|mongodb|couchdb)(.*)$/;
const ipV4RegEx = /^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$/;
const hostnameRegEx = /^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]*[A-Za-z0-9])$/;

export function notEmptyRule(
  value: string,
  errorMessage: string
): string | boolean {
  return !!value || errorMessage;
}

export function isDefinedRule(
  value: any | undefined,
  errorMessage: string
): string | boolean {
  return value !== undefined || errorMessage;
}

export function connectionStringFormatRule(
  value: string,
  errorMessage: string
): string | boolean {
  return jdbcRegEx.test(value) || errorMessage;
}

export function ipV4Rule(
  value: string,
  errorMessage: string
): string | boolean {
  return ipV4RegEx.test(value) || errorMessage;
}

export function hostnameRule(
  value: string,
  errorMessage: string
): string | boolean {
  return hostnameRegEx.test(value) || errorMessage;
}

export function hostnameOrIpV4Rule(
  value: string,
  errorMessage: string
): string | boolean {
  return ipV4RegEx.test(value) || hostnameRegEx.test(value) || errorMessage;
}

export function portNumberRule(
  value: string,
  errorMessage: string
): string | boolean {
  const portNumber = Number(value);
  return (
    (Number.isInteger(portNumber) && portNumber >= 0 && portNumber <= 65535) ||
    errorMessage
  );
}

export function isPositiveIntegerRule(
  value: string,
  errorMessage: string
): string | boolean {
  const int = Number(value);
  return (Number.isInteger(int) && int > 0) || errorMessage;
}

export function isNonNegativeIntegerRule(
    value: string,
    errorMessage: string
): string | boolean {
  const int = Number(value);
  return (Number.isInteger(int) && int >= 0) || errorMessage;
}

export function isPercentRule(value: string, errorMessage: string): string | boolean {
  const v = Number(value);
  return (v >= 0 && v <= 100) || errorMessage;
}
