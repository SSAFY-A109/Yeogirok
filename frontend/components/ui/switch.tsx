// vendor: react-native-reusables
// project: Figma 44x26 track and 22px thumb, animated with reduced-motion support
import * as SwitchPrimitive from "@rn-primitives/switch";
import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";

function Switch({ className, ...props }: ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      hitSlop={9}
      className={cn(
        "h-[26px] w-touch shrink-0 flex-row items-center rounded-full p-0.5 transition-colors duration-fast ease-in-out motion-reduce:transition-none",
        props.checked ? "bg-primary" : "bg-border-strong",
        props.disabled && "opacity-50",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        className={cn(
          "size-[22px] rounded-full bg-background transition-transform duration-fast ease-in-out motion-reduce:transition-none",
          props.checked ? "translate-x-[18px]" : "translate-x-0",
        )}
      />
    </SwitchPrimitive.Root>
  );
}

export { Switch };
