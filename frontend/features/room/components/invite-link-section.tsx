import { Copy, Share2 } from "lucide-react-native";
import { Share, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useRoomInvitation } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { copyToClipboard } from "@/lib/native/clipboard";

type InviteLinkSectionProps = {
  roomId: number;
  roomName: string;
  /** 정원이 차면 링크를 받지 않고 복사·공유를 막는다(Figma W08-F). */
  isFull: boolean;
};

/** 화면에 보이는 초대 주소(`yeogirok.app/j/7KQ2`). 복사·공유에는 전체 URL을 쓴다. */
function toDisplayUrl(url: string): string {
  return url.replace(/^https?:\/\//, "");
}

/** 초대 링크 복사와 공유 버튼(Figma W08 `Invite link`·`Share link`). 부모의 간격 안에 두 덩어리로 놓인다. */
export function InviteLinkSection({ roomId, roomName, isFull }: InviteLinkSectionProps) {
  const toast = useToast();
  const invitation = useRoomInvitation(roomId, { enabled: !isFull });
  const url = isFull ? null : invitation.data?.url ?? null;

  const handleCopy = async (inviteUrl: string) => {
    try {
      await copyToClipboard(inviteUrl);
      toast.show({ message: "초대 링크를 복사했어요" });
    } catch {
      toast.show({ message: "초대 링크를 복사하지 못했어요. 다시 시도해 주세요." });
    }
  };

  const handleShare = async (inviteUrl: string) => {
    try {
      await Share.share({ message: `${roomName} 여행방에 초대해요\n${inviteUrl}` });
    } catch {
      toast.show({ message: "초대 링크를 공유하지 못했어요. 다시 시도해 주세요." });
    }
  };

  const renderLinkRow = () => {
    if (!isFull && invitation.isError) {
      return (
        <>
          <View className="min-h-12 flex-1 justify-center rounded-sm bg-muted px-4 py-3">
            <Text accessibilityRole="alert" className="text-body-sm text-destructive">
              {getRoomErrorMessage(invitation.error, ROOM_ERROR_FALLBACK.invitation)}
            </Text>
          </View>
          <Button
            variant="outline"
            className="h-12 border-border"
            loading={invitation.isFetching}
            loadingLabel="받는 중"
            onPress={() => {
              void invitation.refetch();
            }}
            testID="invite.link-retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </>
      );
    }

    return (
      <>
        {!isFull && invitation.isPending ? (
          <Skeleton accessible accessibilityLabel="초대 링크를 받는 중" className="h-12 flex-1 rounded-sm" />
        ) : (
          <View className="h-12 flex-1 justify-center rounded-sm bg-muted px-4">
            {url === null ? null : (
              <Text className="text-body-sm text-foreground" numberOfLines={1} testID="invite.link">
                {toDisplayUrl(url)}
              </Text>
            )}
          </View>
        )}
        <Button
          variant="outline"
          className="h-12 gap-1 border-border"
          disabled={url === null}
          accessibilityLabel="초대 링크 복사"
          onPress={() => {
            if (url !== null) {
              void handleCopy(url);
            }
          }}
          testID="invite.copy"
        >
          <Icon as={Copy} size={16} />
          <Text>복사</Text>
        </Button>
      </>
    );
  };

  return (
    <>
      <View className="gap-2">
        <Text className="text-label text-foreground">초대 링크</Text>
        <View className="flex-row gap-2">{renderLinkRow()}</View>
      </View>
      <Button
        variant="outline"
        className="h-button-lg w-full"
        disabled={url === null}
        onPress={() => {
          if (url !== null) {
            void handleShare(url);
          }
        }}
        testID="invite.share"
      >
        <Icon as={Share2} size={18} />
        <Text>카카오톡 메시지로 링크 공유하기</Text>
      </Button>
    </>
  );
}
