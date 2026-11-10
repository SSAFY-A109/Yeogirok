import { router } from "expo-router";
import { View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useAcceptRoomInvitation } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK, ROOM_FULL_CODE, ROOM_INVITE_INVALID_CODE } from "@/features/room/errors";
import { ROOM_MEMBER_LIMIT } from "@/features/room/model";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";

/** 이전 화면으로 돌아간다. 링크로 바로 들어와 돌아갈 화면이 없으면 여행방 목록으로 간다. */
function leaveJoinScreen() {
  if (router.canGoBack()) {
    router.back();
    return;
  }

  router.replace("/rooms");
}

/**
 * 초대 링크로 들어온 사람의 참여 확인 화면(API-025). 링크만으로 바로 참여시키지 않고 "참여하기"를 눌러야 참여한다.
 * 참여하기 전에는 서버가 여행방 내용을 주지 않아 여행방 이름을 보여 줄 수 없다.
 * `inviteToken`이 `null`이면 링크가 올바르지 않다는 안내를 보여 준다.
 */
export function JoinRoomScreen({ inviteToken }: { inviteToken: string | null }) {
  return (
    <Screen>
      <ScreenHeader title="여행방 초대" onBack={leaveJoinScreen} testID="join-room.header" />
      {inviteToken === null ? <InvalidInviteNotice /> : <JoinRoomContent inviteToken={inviteToken} />}
    </Screen>
  );
}

function JoinRoomContent({ inviteToken }: { inviteToken: string }) {
  const toast = useToast();
  const acceptInvitation = useAcceptRoomInvitation();
  const getIdempotencyKey = useIdempotencyKeyFor();
  const { error } = acceptInvitation;

  if (isApiError(error) && error.code === ROOM_INVITE_INVALID_CODE) {
    return <InvalidInviteNotice />;
  }

  if (isApiError(error) && error.code === ROOM_FULL_CODE) {
    return <RoomFullNotice />;
  }

  const join = () => {
    if (acceptInvitation.isPending) {
      return;
    }

    acceptInvitation.mutate(
      { inviteToken, idempotencyKey: getIdempotencyKey(inviteToken) },
      {
        onSuccess: ({ roomId }) => {
          router.replace({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId) } });
          toast.show({ message: "여행방에 참여했어요" });
        },
      },
    );
  };

  return (
    <>
      <View className="flex-1 justify-center gap-2 px-5" testID="join-room.confirm">
        <Text accessibilityRole="header" className="text-h1 text-foreground">
          여행방에 초대받았어요
        </Text>
        <Text className="text-body-sm text-foreground-secondary">
          참여하면 일행과 함께 장소를 담고 일정을 고칠 수 있어요. 여행방 내용은 참여한 뒤에 볼 수 있어요.
        </Text>
      </View>
      <BottomCta>
        {error !== null ? (
          <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="w-full text-caption text-destructive" testID="join-room.error">
            {getRoomErrorMessage(error, ROOM_ERROR_FALLBACK.join)}
          </Text>
        ) : null}
        <Button size="lg" className="w-full" loading={acceptInvitation.isPending} loadingLabel="참여하는 중" onPress={join} testID="join-room.submit">
          <Text>참여하기</Text>
        </Button>
      </BottomCta>
    </>
  );
}

// #region 안내

function InvalidInviteNotice() {
  return (
    <JoinRoomNotice
      title="초대 링크가 올바르지 않아요"
      description="링크를 보낸 일행에게 초대 링크를 다시 받아 주세요."
      testID="join-room.invalid"
    />
  );
}

/** 정원이 찬 여행방(Figma W08-F: 받은 링크로 들어와도 참여되지 않고 정원 안내를 보여 준다). */
function RoomFullNotice() {
  return (
    <JoinRoomNotice
      title="여행방 정원이 가득 찼어요"
      description={`여행방은 최대 ${ROOM_MEMBER_LIMIT}명까지 함께할 수 있어요. 누가 나가면 같은 링크로 참여할 수 있어요.`}
      testID="join-room.full"
    />
  );
}

function JoinRoomNotice({ title, description, testID }: { title: string; description: string; testID: string }) {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID={testID}>
      <View className="gap-2">
        <Text accessibilityRole="alert" className="text-center text-h3 text-foreground">
          {title}
        </Text>
        <Text className="text-center text-body-sm text-foreground-secondary">{description}</Text>
      </View>
      <Button
        variant="outline"
        onPress={() => {
          router.replace("/rooms");
        }}
        testID={`${testID}-back`}
      >
        <Text>여행방 목록으로</Text>
      </Button>
    </View>
  );
}

// #endregion
