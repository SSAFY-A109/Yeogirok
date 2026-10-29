import type { ComponentProps } from "react";

import { cn } from "@/lib/utils";
import { Button } from "./button";
import { assets, DesignAsset } from "./design-asset";
import { Text } from "./text";

type SocialLoginButtonProps = Omit<ComponentProps<typeof Button>, "children" | "variant" | "size"> & {
  provider: "kakao" | "google";
};

export function SocialLoginButton({ provider, className, accessibilityLabel, ...props }: SocialLoginButtonProps) {
  const label = provider === "kakao" ? "카카오 로그인" : "Google 계정으로 로그인";

  return (
    <Button
      size="lg"
      variant="outline"
      accessibilityLabel={accessibilityLabel ?? label}
      className={cn("w-full rounded-full px-5", provider === "kakao" ? "border-transparent bg-auth-kakao active:bg-auth-kakao" : "gap-2.5 border-auth-google-border bg-white active:bg-muted", className)}
      {...props}
    >
      <DesignAsset source={assets[provider]} contentFit="contain" className="size-5" accessible={false} />
      <Text className={cn("text-button-lg", provider === "kakao" ? "text-black/85" : "text-auth-google-foreground")}>{label}</Text>
    </Button>
  );
}

export function KakaoLoginButton(props: Omit<SocialLoginButtonProps, "provider">) {
  return <SocialLoginButton provider="kakao" {...props} />;
}

export function GoogleLoginButton(props: Omit<SocialLoginButtonProps, "provider">) {
  return <SocialLoginButton provider="google" {...props} />;
}
