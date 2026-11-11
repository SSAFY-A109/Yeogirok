import { parseInviteToken } from "@/features/room/route-params";

export function redirectSystemPath({ path, initial }: { path: string; initial: boolean }): string | null {
  if (!/^https?:\/\//i.test(path)) {
    return path;
  }

  const [address] = path.split(/[?#]/, 1);
  const match = /^https:\/\/([^/]+)\/invite\/([^/\s]+)\/?$/.exec(address);
  const token = match?.[1].toLowerCase() === "k15a109.p.ssafy.io" ? parseInviteToken(match[2]) : null;

  if (token !== null && token !== "." && token !== "..") {
    return `/invite/${token}`;
  }

  return initial ? "/" : null;
}
