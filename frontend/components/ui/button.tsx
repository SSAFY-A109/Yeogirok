// vendor: react-native-reusables
// project: Figma button tokens, disabled colors, and loading state
import { cva, type VariantProps } from "class-variance-authority";
import type { ComponentProps } from "react";
import { Pressable } from "react-native";

import { cn } from "@/lib/utils";
import { Spinner } from "./spinner";
import { Text, TextClassContext } from "./text";

const buttonVariants = cva("group shrink-0 flex-row items-center justify-center gap-2 rounded-sm", {
  variants: {
    variant: {
      default: "bg-primary active:bg-primary-pressed",
      secondary: "bg-secondary active:bg-accent",
      outline: "border border-ring bg-card active:bg-accent",
    },
    size: {
      default: "min-h-button-md px-4 py-2",
      lg: "min-h-button-lg px-6 py-2",
    },
  },
  defaultVariants: { variant: "default", size: "default" },
});

const buttonTextVariants = cva("font-sans", {
  variants: {
    variant: {
      default: "text-primary-foreground",
      secondary: "text-secondary-foreground",
      outline: "text-secondary-foreground",
    },
    size: { default: "text-button", lg: "text-button-lg" },
  },
  defaultVariants: { variant: "default", size: "default" },
});

type ButtonProps = ComponentProps<typeof Pressable> & VariantProps<typeof buttonVariants> & {
  loading?: boolean;
  loadingLabel?: string;
};

function Button({ className, variant = "default", size = "default", disabled = false, loading = false, loadingLabel = "처리 중", children, accessibilityState, ...props }: ButtonProps) {
  return (
    <TextClassContext.Provider value={cn(buttonTextVariants({ variant, size }), disabled && "text-disabled-foreground")}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ ...accessibilityState, disabled: disabled || loading, busy: loading }}
        className={cn(buttonVariants({ variant, size }), className, disabled && "border-border bg-disabled")}
        disabled={disabled || loading}
        {...props}
      >
        {loading ? (
          <>
            <Spinner tone={variant === "default" ? "light" : "brand"} accessible={false} />
            <Text>{loadingLabel}</Text>
          </>
        ) : children}
      </Pressable>
    </TextClassContext.Provider>
  );
}

export { Button, buttonTextVariants, buttonVariants };
export type { ButtonProps };
