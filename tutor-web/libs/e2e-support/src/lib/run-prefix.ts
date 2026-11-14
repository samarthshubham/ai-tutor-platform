export function createRunPrefix(
  now: number = Date.now(),
  random: () => number = Math.random,
): string {
  const suffix = Math.floor(random() * 36 ** 4)
    .toString(36)
    .padStart(4, '0');
  return `e2e-${now.toString(36)}-${suffix}`;
}

export const runPrefix = createRunPrefix();

export function withRunPrefix(name: string): string {
  return `${runPrefix} ${name}`;
}
