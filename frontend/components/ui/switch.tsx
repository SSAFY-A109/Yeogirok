// vendor: react-native-reusables
// project: Figma 44x26 switch assets, with a 44px touch target
import * as SwitchPrimitive from "@rn-primitives/switch";
import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";

function Switch({ className, ...props }: ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root hitSlop={9} className={cn("h-[26px] w-touch shrink-0 rounded-full", props.disabled && "opacity-50", className)} {...props}>
      <DesignAsset source={props.checked ? assets.switchOn : assets.switchOff} contentFit="contain" className="h-[26px] w-touch" accessible={false} />
    </SwitchPrimitive.Root>
  );
}

export { Switch };
