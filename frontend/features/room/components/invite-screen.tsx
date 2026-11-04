import { router } from "expo-router";
import { ChevronDown, X } from "lucide-react-native";
import { useState } from "react";
import { FlatList, Pressable, View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { HeaderIconButton, ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRoom } from "@/features/room/api/queries";
import { InviteLinkSection } from "@/features/room/components/invite-link-section";
import { MemberAvatar } from "@/features/room/components/member-avatar";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { isRoomFull, type RoomDetail, type RoomMember } from "@/features/room/model";

/** 일행이 많을 때 처음 보여 주는 인원. 나머지는 "외 N명 더 보기"로 펼친다(Figma W08-F). */
const COLLAPSED_MEMBER_COUNT = 3;

const HTTP_NOT_FOUND = 404;

function closeInvite() {
  router.back();
}

/** 일행 초대(Figma W08, 정원이 차면 W08-F). `roomId`가 `null`이면 여행방을 찾을 수 없다는 안내를 보여 준다. */
export function InviteScreen({ roomId }: { roomId: number | null }) {
  return (
    <Screen>
      <ScreenHeader
        title="일행 초대"
        leading={null}
        trailing={<HeaderIconButton icon={X} accessibilityLabel="닫기" onPress={closeInvite} testID="invite.close" />}
        testID="invite.header"
      />
      {roomId === null ? <InviteRoomNotFound /> : <InviteRoomContent roomId={roomId} />}
    </Screen>
  );
}

function InviteRoomContent({ roomId }: { roomId: number }) {
  const room = useRoom(roomId);

  if (room.isPending) {
    return (
      <>
        <InviteSkeleton />
        <InviteDoneCta />
      </>
    );
  }

  if (room.isError) {
    if (isApiError(room.error) && room.error.status === HTTP_NOT_FOUND) {
      return <InviteRoomNotFound />;
    }

    return (
      <InviteRoomError
        message={getRoomErrorMessage(room.error, ROOM_ERROR_FALLBACK.load)}
        retrying={room.isFetching}
        onRetry={() => {
          void room.refetch();
        }}
      />
    );
  }

  return (
    <>
      <InviteBody room={room.data} />
      <InviteDoneCta />
    </>
  );
}

// #region 본문

function InviteBody({ room }: { room: RoomDetail }) {
  const [membersExpanded, setMembersExpanded] = useState(false);
  const isFull = isRoomFull(room);
  const hiddenMemberCount = membersExpanded ? 0 : Math.max(room.members.length - COLLAPSED_MEMBER_COUNT, 0);
  const visibleMembers = hiddenMemberCount > 0 ? room.members.slice(0, COLLAPSED_MEMBER_COUNT) : room.members;

  return (
    <FlatList
      className="flex-1"
      contentContainerClassName="px-5 pb-6 pt-4"
      data={visibleMembers}
      keyExtractor={(member) => {
        return String(member.id);
      }}
      renderItem={({ item, index }) => {
        return <InviteMemberRow member={item} toneIndex={index} />;
      }}
      ListHeaderComponent={
        <View className="gap-6">
          <View className="gap-2">
            <Text className="text-h1 text-foreground">{room.name}</Text>
            <Text className="text-body-sm text-foreground-secondary">
              함께 장소를 담고 일정을 고칠 수 있어요. 여행이 끝나면 같은 사람들이 기록을 함께 채워요.
            </Text>
          </View>
          <InviteLinkSection roomId={room.id} roomName={room.name} isFull={isFull} />
          <View className="gap-2">
            <Text accessibilityRole="header" className="text-h3 text-foreground">
              {`참여한 사람 ${room.memberCount}/${room.memberLimit}명`}
            </Text>
            {isFull ? (
              <Text className="text-caption text-highlight" testID="invite.full-note">
                정원이 가득 찼어요. 누가 나가면 다시 초대할 수 있어요
              </Text>
            ) : (
              <Text className="text-caption text-muted-foreground">{`최대 ${room.memberLimit}명까지 함께할 수 있어요`}</Text>
            )}
          </View>
        </View>
      }
      ListFooterComponent={
        hiddenMemberCount > 0 ? (
          <MoreMembersButton
            count={hiddenMemberCount}
            onPress={() => {
              setMembersExpanded(true);
            }}
          />
        ) : undefined
      }
      testID="invite.member-list"
    />
  );
}

function InviteMemberRow({ member, toneIndex }: { member: RoomMember; toneIndex: number }) {
  return (
    <View
      accessible
      accessibilityLabel={member.isMe ? `${member.nickname}, 나` : member.nickname}
      className="mt-2 flex-row items-center gap-3 py-2"
    >
      <MemberAvatar initial={member.initial} toneIndex={toneIndex} size="lg" />
      <View className="flex-1">
        <Text className="text-label text-foreground" numberOfLines={1}>
          {member.nickname}
        </Text>
        {member.isMe ? <Text className="text-caption text-muted-foreground">나</Text> : null}
      </View>
    </View>
  );
}

function MoreMembersButton({ count, onPress }: { count: number; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ expanded: false }}
      className="mt-2 flex-row items-center gap-1 self-start rounded-sm py-2 active:bg-accent"
      hitSlop={4}
      onPress={onPress}
      testID="invite.more-members"
    >
      <Text className="text-label text-foreground-secondary">{`외 ${count}명 더 보기`}</Text>
      <Icon as={ChevronDown} className="text-icon" size={16} />
    </Pressable>
  );
}

function InviteDoneCta() {
  return (
    <BottomCta>
      <Button size="lg" className="w-full" onPress={closeInvite} testID="invite.done">
        <Text>완료</Text>
      </Button>
    </BottomCta>
  );
}

// #endregion

// #region 상태

function InviteSkeleton() {
  return (
    <View accessible accessibilityLabel="일행 초대 정보를 불러오는 중" className="flex-1 gap-6 px-5 pt-4" testID="invite.loading">
      <View className="gap-2">
        <Skeleton className="h-8 w-skeleton-heading" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-skeleton-line" />
      </View>
      <View className="gap-2">
        <Skeleton className="h-5 w-14" />
        <Skeleton className="h-12 w-full rounded-sm" />
      </View>
      <Skeleton className="h-button-lg w-full rounded-sm" />
      <View className="gap-2">
        <Skeleton className="h-7 w-skeleton-heading" />
        {Array.from({ length: COLLAPSED_MEMBER_COUNT }, (_, index) => {
          return (
            <View key={index} className="flex-row items-center gap-3 py-2">
              <Skeleton className="h-10 w-10 rounded-full" />
              <Skeleton className="h-5 w-16" />
            </View>
          );
        })}
      </View>
    </View>
  );
}

function InviteRoomError({ message, retrying, onRetry }: { message: string; retrying: boolean; onRetry: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID="invite.error">
      <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
        {message}
      </Text>
      <Button variant="outline" loading={retrying} loadingLabel="불러오는 중" onPress={onRetry} testID="invite.retry">
        <Text>다시 시도</Text>
      </Button>
    </View>
  );
}

function InviteRoomNotFound() {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID="invite.not-found">
      <Text accessibilityRole="alert" className="text-center text-h3 text-foreground">
        여행방을 찾을 수 없어요
      </Text>
      <Button variant="outline" onPress={closeInvite} testID="invite.not-found-back">
        <Text>돌아가기</Text>
      </Button>
    </View>
  );
}

// #endregion
