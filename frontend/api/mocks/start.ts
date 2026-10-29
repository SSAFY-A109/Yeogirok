import { onUnhandledRequest } from "./on-unhandled-request";

export async function startMocking() {
  await import("./polyfills");
  const { server } = await import("./native");
  server.listen({ onUnhandledRequest });
}
