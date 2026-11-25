// vendor: react-native-reusables
// project: share input focus and error handling with the 144px Figma textarea
import { cn } from "@/lib/utils";
import { Input, type InputProps } from "./input";

function Textarea({ className, ...props }: InputProps) {
  return <Input multiline textAlignVertical="top" className={cn("h-36 p-4", className)} {...props} />;
}

export { Textarea };
