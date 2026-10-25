export type SearchableCommand = { id: string; title: string; detail: string; keywords?: string };
export function filterCommands<T extends SearchableCommand>(items: T[], query: string): T[] {
  const words = query.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean);
  return items.filter(item => words.every(word => `${item.title} ${item.detail} ${item.keywords ?? ""}`.toLocaleLowerCase().includes(word))).slice(0, 80);
}
