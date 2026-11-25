import { router } from "expo-router";

import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useLeaveRoom } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomDetail } from "@/features/room/model";

const LEAVE_COPY = {
  title: "여행방에서 나갈까요?",
  description: "나가면 이 여행방의 장소와 일정을 더 볼 수 없어요. 내가 담은 장소는 일행에게 그대로 남아요. 다시 들어오려면 초대 링크가 필요해요.",
  confirmLabel: "나가기",
  loadingLabel: "나가는 중",
} as const;

const LAST_MEMBER_COPY = {
  title: "마지막 일행이에요",
  description: "지금 나가면 여행방이 삭제되고 담은 장소와 일정도 모두 사라져요. 되돌릴 수 없어요.",
  confirmLabel: "나가고 삭제하기",
  loadingLabel: "삭제하는 중",
} as const;

type LeaveRoomDialogProps = {
  room: Pick<RoomDetail, "id" | "memberCount">;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

/**
 * 여행방 나가기 확인(Figma W06-L, 남은 사람이 나 혼자면 W06-L2). 나가면 여행방 목록까지 화면을 정리하고 토스트를 띄운다.
 * 실패하면 다이얼로그를 그대로 두고 오류 문구를 보여 준다.
 */
export function LeaveRoomDialog({ room, open, onOpenChange }: LeaveRoomDialogProps) {
  const toast = useToast();
  const leaveRoom = useLeaveRoom();
  // 마지막 일행이 나가면 서버가 여행방을 지운다. 응답에는 삭제 여부가 없어 나가기 전 인원으로 판단한다.
  const isLastMember = room.memberCount <= 1;
  const copy = isLastMember ? LAST_MEMBER_COPY : LEAVE_COPY;

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      leaveRoom.reset();
    }

    onOpenChange(next);
  };

  const confirm = () => {
    leaveRoom.mutate(room.id, {
      onSuccess: () => {
        onOpenChange(false);
        router.dismissTo("/rooms");
        toast.show({ message: isLastMember ? "여행방을 삭제했어요" : "여행방에서 나왔어요" });
      },
    });
  };

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={copy.title}
      description={copy.description}
      confirmLabel={copy.confirmLabel}
      confirmTone="destructive"
      onConfirm={confirm}
      loading={leaveRoom.isPending}
      loadingLabel={copy.loadingLabel}
      testID="leave-room-dialog"
    >
      {leaveRoom.error === null ? null : (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-caption text-destructive" testID="leave-room-dialog.error">
          {getRoomErrorMessage(leaveRoom.error, ROOM_ERROR_FALLBACK.leave)}
        </Text>
      )}
    </ConfirmDialog>
  );
}
