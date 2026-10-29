// vendor: react-native-reusables
// project: Figma badge tones, including the approved Brand and Success showcase colors
import { Slot } from "@rn-primitives/slot";
import { cva, type VariantProps } from "class-variance-authority";
import type { ComponentProps } from "react";
import { View } from "react-native";

import { cn } from "@/lib/utils";
import { TextClassContext } from "./text";

const badgeVariants = cva("min-h-6 shrink-0 flex-row items-center justify-center gap-1 self-start rounded-xs px-2 py-1", {
  variants: { tone: { neutral: "bg-muted", brand: "bg-primary-pressed", coral: "bg-muted", success: "bg-badge-correct" } },
  defaultVariants: { tone: "neutral" },
});

const badgeTextVariants = cva("text-label-sm", {
  variants: { tone: { neutral: "text-foreground-secondary", brand: "text-primary-foreground", coral: "text-foreground-secondary", success: "text-primary-foreground" } },
  defaultVariants: { tone: "neutral" },
});

type BadgeProps = ComponentProps<typeof View> & VariantProps<typeof badgeVariants> & { asChild?: boolean };

function Badge({ className, tone, asChild, ...props }: BadgeProps) {
  const Component = asChild ? Slot : View;
  return (
    <TextClassContext.Provider value={badgeTextVariants({ tone })}>
      <Component className={cn(badgeVariants({ tone }), className)} {...props} />
    </TextClassContext.Provider>
  );
}

export { Badge, badgeTextVariants, badgeVariants };
export type { BadgeProps };
