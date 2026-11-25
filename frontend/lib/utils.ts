import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

const controlSizes = ["touch", "button-sm", "button-md", "button-lg", "input", "chip-sm", "chip-md", "icon", "icon-sm", "skeleton-heading", "skeleton-line"];

const merge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [{ text: ["display", "h1", "h2", "h3", "body", "body-sm", "label", "label-sm", "button", "button-lg", "caption", "micro"] }],
      h: [{ h: controlSizes }],
      "min-h": [{ "min-h": controlSizes }],
      w: [{ w: controlSizes }],
      "min-w": [{ "min-w": controlSizes }],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return merge(clsx(inputs));
}
