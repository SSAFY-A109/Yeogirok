// vendor: react-native-reusables
// project: Figma 22px radio and original selected asset, with a 44px touch target
import * as RadioGroupPrimitive from "@rn-primitives/radio-group";
import { createContext, useContext, type ComponentProps } from "react";

import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";

const RadioValueContext = createContext<string | undefined>(undefined);

function RadioGroup({ className, ...props }: ComponentProps<typeof RadioGroupPrimitive.Root>) {
  return (
    <RadioValueContext.Provider value={props.value}>
      <RadioGroupPrimitive.Root className={cn("gap-6", className)} {...props} />
    </RadioValueContext.Provider>
  );
}

function RadioGroupItem({ className, ...props }: ComponentProps<typeof RadioGroupPrimitive.Item>) {
  const value = useContext(RadioValueContext);
  return (
    <RadioGroupPrimitive.Item
      hitSlop={11}
      className={cn("size-[22px] shrink-0 items-center justify-center rounded-full border-[1.5px] border-input bg-card", value === props.value && "border-transparent", props.disabled && "opacity-50", className)}
      {...props}
    >
      <RadioGroupPrimitive.Indicator className="absolute size-[22px]">
        <DesignAsset source={assets.radioOn} contentFit="contain" className="size-[22px]" accessible={false} />
      </RadioGroupPrimitive.Indicator>
    </RadioGroupPrimitive.Item>
  );
}

export { RadioGroup, RadioGroupItem };
