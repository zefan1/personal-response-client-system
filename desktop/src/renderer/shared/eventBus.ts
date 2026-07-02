type Handler<T = unknown> = (payload: T) => void;

const handlers = new Map<string, Set<Handler>>();

export const eventBus = {
  emit<T>(event: string, payload: T): void {
    handlers.get(event)?.forEach((handler) => handler(payload));
  },
  on<T>(event: string, handler: Handler<T>): () => void {
    const set = handlers.get(event) ?? new Set<Handler>();
    set.add(handler as Handler);
    handlers.set(event, set);
    return () => set.delete(handler as Handler);
  }
};
