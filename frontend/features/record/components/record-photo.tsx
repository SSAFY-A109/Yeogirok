import { RefreshCw } from "lucide-react-native";
import { useState } from "react";
import { Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { MediaImage } from "@/components/ui/media-photo";
import { MemberAvatar } from "@/features/room/components/member-avatar";
import { cn } from "@/lib/utils";
import { getRecordFileAccess } from "../api/record.api";
import type { RecordDetail, RecordPhoto as Photo } from "../model";

export function RecordPhoto({ record, photo, className, showUploader = true }: {
  record: RecordDetail; photo: Photo; className?: string; showUploader?: boolean;
}) {
  const [replacement, setReplacement] = useState<{ original: string; url: string } | null>(null);
  const [failedUri, setFailedUri] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const member = record.members.find((item) => {
    return item.id === photo.uploadedBy;
  });

  const uri = replacement && replacement.original === photo.url ? replacement.url : photo.url;

  return <View className={cn("h-16 w-16 items-center justify-center overflow-hidden rounded-md bg-brand-muted", className)}>
    {!uri || failedUri === uri ? (
      <Pressable accessibilityRole="button" accessibilityLabel="사진 다시 불러오기" disabled={loading} className="h-full w-full items-center justify-center"
        onPress={async () => {
          setLoading(true);

          try {
            const access = await getRecordFileAccess(record.id, "images", photo.imageId);
            setReplacement({ original: photo.url, url: access.url });
            setFailedUri(null);
          } catch {
            setFailedUri(uri);
          } finally {
            setLoading(false);
          }
        }}><Icon as={RefreshCw} size={18} className="text-muted-foreground" /></Pressable>
    ) : (
      <MediaImage uri={uri} cachePolicy="none"
        contentFit="cover" accessibilityLabel="기록 사진" onError={() => {
          setFailedUri(uri);
        }} style={{ width: "100%", height: "100%" }} />
    )}
    {showUploader && <MemberAvatar size="sm" initial={member?.initial ?? "?"} toneIndex={member?.id ?? 0} bordered className="absolute bottom-1 right-1" />}
  </View>;
}
