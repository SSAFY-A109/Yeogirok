// vendor: react-native-reusables
// project: Figma 24px checkbox and original check asset, with a 44px touch target
import * as CheckboxPrimitive from "@rn-primitives/checkbox";
import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";

function Checkbox({ className, ...props }: ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      hitSlop={10}
      className={cn("size-6 shrink-0 items-center justify-center rounded-xs border-[1.5px] border-input bg-card", props.checked && "border-primary bg-primary", props.disabled && "opacity-50", className)}
      {...props}
    >
      <CheckboxPrimitive.Indicator>
        <DesignAsset source={assets.check} contentFit="contain" className="size-4" accessible={false} />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

export { Checkbox };
