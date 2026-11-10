import { Image, type ImageProps } from "expo-image";
import { View } from "react-native";
import { User } from "lucide-react-native";

import { Icon } from "@/components/ui/icon";
import { cn } from "@/lib/utils";
import type { CourseAuthor } from "@/features/course/model";

const MAX_VISIBLE_AUTHORS = 4;
const avatarToneClassNames = ["bg-brand-muted", "bg-highlight-muted", "bg-accent"];

type AuthorAvatarsProps = { authors: CourseAuthor[]; size: number; overlap: number; cachePolicy?: ImageProps["cachePolicy"] };

// 공개 코스 작성자(현재 참여자 전체)를 겹친 원으로 보여 준다. 공개 DTO에는 닉네임이 없어 사진·기본 아이콘만 쓴다.
export function AuthorAvatars({ authors, size, overlap, cachePolicy }: AuthorAvatarsProps) {
  const visible = authors.slice(0, MAX_VISIBLE_AUTHORS);

  return (
    <View className="flex-row" accessible accessibilityLabel={`함께 다녀온 사람 ${authors.length}명`}>
      {visible.map((author, index) => {
        return (
          <View
            key={author.id}
            className={cn("items-center justify-center overflow-hidden rounded-full border-2 border-card", avatarToneClassNames[index % avatarToneClassNames.length])}
            style={{ width: size, height: size, marginRight: index < visible.length - 1 ? -overlap : 0, zIndex: visible.length - index }}
          >
            {author.profileImageUrl ? (
              <Image source={{ uri: author.profileImageUrl }} contentFit="cover" cachePolicy={cachePolicy} style={{ width: "100%", height: "100%" }} />
            ) : (
              <Icon as={User} size={size * 0.55} className="text-icon" />
            )}
          </View>
        );
      })}
    </View>
  );
}
