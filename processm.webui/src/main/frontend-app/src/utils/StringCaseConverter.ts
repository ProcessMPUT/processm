export function kebabize(value: string): string {
  return value
    .replace(/\B([A-Z])(?=[a-z])/g, "-$1")
    .replace(/\B([a-z0-9])([A-Z])/g, "$1-$2")
    .toLowerCase();
}

export function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
