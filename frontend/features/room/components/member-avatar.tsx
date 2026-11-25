import { Image } from "expo-image";
import { useState } from "react";
import { View } from "react-native";

import { Text } from "@/components/ui/text";
import { toHttpsUrl } from "@/lib/url";
import { cn } from "@/lib/utils";

/** Figma 아바타 배경 순서(bg/brand-soft → bg/coral-soft → bg/selected). */
const AVATAR_TONES = ["bg-brand-muted", "bg-highlight-muted", "bg-accent"] as const;
const AVATAR_SIZES = { sm: "h-6 w-6", md: "h-7 w-7", lg: "h-10 w-10" } as const;

type MemberAvatarProps = {
  initial: string;
  /** 소셜 로그인 때 받은 프로필 사진(서버 `profileImageUrl`). 없거나 못 불러오면 이름 첫 글자를 그린다. */
  photoUrl?: string | null;
  /** 같은 사람이 화면마다 같은 색이 되도록 목록 순서나 id를 넘긴다. */
  toneIndex: number;
  /** `sm` 24(장소 행의 담은 사람), `md` 28(겹친 아바타), `lg` 40(초대 화면 일행 목록). */
  size?: "sm" | "md" | "lg";
  /** 겹쳐 놓을 때 흰 테두리로 구분한다. */
  bordered?: boolean;
  className?: string;
};

/**
 * 일행 아바타. 프로필 사진이 있으면 사진을, 없으면 이름 첫 글자를 그린다(2026-11-17 사용자 요청으로 사진 추가).
 * 사진을 못 불러오면(주소 만료, 네트워크) 그 주소로 다시 시도하지 않고 첫 글자로 돌아간다.
 */
export function MemberAvatar({ initial, photoUrl, toneIndex, size = "md", bordered = false, className }: MemberAvatarProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const shownPhotoUrl = photoUrl && photoUrl !== failedUrl ? photoUrl : null;

  return (
    <View
      accessible={false}
      className={cn(
        "items-center justify-center overflow-hidden rounded-full",
        AVATAR_TONES[Math.abs(toneIndex) % AVATAR_TONES.length],
        AVATAR_SIZES[size],
        bordered && "border-2 border-card",
        className,
      )}
    >
      {shownPhotoUrl ? (
        <Image
          source={{ uri: toHttpsUrl(shownPhotoUrl) }}
          contentFit="cover"
          style={{ width: "100%", height: "100%" }}
          onError={() => {
            setFailedUrl(shownPhotoUrl);
          }}
        />
      ) : (
        <Text className={cn(size === "lg" ? "text-label" : "text-micro", "text-foreground")}>{initial}</Text>
      )}
    </View>
  );
}

type AvatarStackProps = {
  members: { id: number; initial: string; profileImageUrl?: string | null }[];
  /** 겹쳐 보여 줄 최대 인원. */
  max?: number;
};

/** 겹친 아바타(Figma `Members (겹친 아바타)`, 6px씩 겹침). */
export function AvatarStack({ members, max = 3 }: AvatarStackProps) {
  return (
    <View accessible={false} className="flex-row">
      {members.slice(0, max).map((member, index) => {
        return (
          <MemberAvatar
            key={member.id}
            initial={member.initial}
            photoUrl={member.profileImageUrl}
            toneIndex={index}
            bordered
            className={index < Math.min(members.length, max) - 1 ? "-mr-1.5" : undefined}
          />
        );
      })}
    </View>
  );
}
