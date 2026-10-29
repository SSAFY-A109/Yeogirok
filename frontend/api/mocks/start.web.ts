import { setupWorker } from "msw/browser";

import { handlers } from "./handlers";
import { onUnhandledRequest } from "./on-unhandled-request";

export async function startMocking() {
  const worker = setupWorker(...handlers);
  await worker.start({ onUnhandledRequest });
}
