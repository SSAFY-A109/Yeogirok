import { useState } from "react";
import { ScrollView, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { Info, Lock } from "lucide-react-native";

import { Badge } from "@/components/ui/badge";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useCategories } from "@/features/common/api/queries";
import { useDeleteFootprint, useFootprint, useUpdateGuestbook } from "@/features/footprint/api/queries";
import { getFootprintErrorMessage, isFootprintConflict, isFootprintUnavailable } from "@/features/footprint/errors";
import { GUESTBOOK_LIMIT, formatFootprintDate, guestbookLength, normalizeGuestbookBody, type FootprintDetail } from "@/features/footprint/model";
import { MyFootprintMap } from "./my-footprint-map";
import { MyLoading, MyPage, MyUnavailable } from "./my-page";

export function MyGuestbookScreen() {
  const { footprintId } = useLocalSearchParams<{ footprintId?: string }>();
  const id = typeof footprintId === "string" && /^\d+$/.test(footprintId) ? Number(footprintId) : 0;
  return <MyPage title="방명록" testID="my-guestbook.screen">{Number.isSafeInteger(id) && id > 0 ? <GuestbookDetail key={id} id={id} /> : <MyUnavailable />}</MyPage>;
}

function GuestbookDetail({ id }: { id: number }) {
  const query = useFootprint(id);

  if (query.isPending) {
    return <MyLoading />;
  }

  if (isFootprintUnavailable(query.error)) {
    return <MyUnavailable title={getFootprintErrorMessage(query.error)} />;
  }

  if (!query.data) {
    return <MyUnavailable title={getFootprintErrorMessage(query.error)} onRetry={() => {
      void query.refetch();
    }} />;
  }

  return <GuestbookForm footprint={query.data} reload={async () => {
    const result = await query.refetch();
    return result.isSuccess ? result.data : null;
  }} />;
}

function GuestbookForm({ footprint, reload }: { footprint: FootprintDetail; reload: () => Promise<FootprintDetail | null> }) {
  const [base, setBase] = useState(footprint);
  const [body, setBody] = useState(footprint.body ?? "");
  const [deleting, setDeleting] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);
  const [reviewed, setReviewed] = useState(false);
  const categories = useCategories();
  const update = useUpdateGuestbook();
  const deletion = useDeleteFootprint();
  const toast = useToast();
  const error = update.error ?? deletion.error;
  const conflicted = isFootprintConflict(error);
  const busy = update.isPending || deletion.isPending || reloading;
  const length = guestbookLength(body);

  function done(message: string) {
    toast.show({ message });

    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace("/my/footprints");
    }
  }

  async function readLatest() {
    setReloading(true);
    const latest = await reload();
    setReloading(false);
    setReloadFailed(latest === null);

    if (latest) {
      setBase(latest);
      setReviewed(true);
      update.reset();
      deletion.reset();
    }
  }

  if (isFootprintUnavailable(error)) {
    return <MyUnavailable title={getFootprintErrorMessage(error)} />;
  }

  return (
    <>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerClassName="pb-6">
        <MyFootprintMap footprints={[footprint]} selected />
        <View className="gap-6 px-5 pt-4">
          <View className="gap-1">{footprint.place && <Text className="text-label">{footprint.place}</Text>}{footprint.address && <Text className="text-caption text-foreground-secondary">{footprint.address}</Text>}<Text className="text-caption text-foreground-secondary">{formatFootprintDate(footprint.visitedAt)} 방문</Text></View>
          <View className="gap-3"><Text className="text-label">카테고리</Text><View className="flex-row flex-wrap gap-1">{footprint.categoryCodes.map((code) => {
            const category = categories.data?.find((item) => {
              return item.code === code;
            });

            return <Badge key={code}><Text>{category?.name ?? code}</Text></Badge>;
          })}</View></View>
          <View className="gap-3"><View className="flex-row items-center gap-2"><Text className="text-label">방명록</Text><View className="flex-row items-center gap-1 rounded-full bg-muted px-2 py-1"><Icon as={Lock} size={12} className="text-icon" /><Text className="text-label-sm text-foreground-secondary">나만 볼 수 있어요</Text></View></View><Textarea value={body} editable={!busy} onChangeText={(value) => {
            setBody(normalizeGuestbookBody(value));
          }} placeholder="여행의 순간을 기록해 보세요" accessibilityLabel="나만 보는 방명록, 최대 200자" testID="my-guestbook.body" /><Text className={length > GUESTBOOK_LIMIT ? "text-right text-caption text-destructive tabular-nums" : "text-right text-caption text-muted-foreground tabular-nums"}>{length} / {GUESTBOOK_LIMIT}</Text></View>
          <View className="flex-row gap-2"><Icon as={Info} size={16} className="text-muted-foreground" /><Text className="flex-1 text-caption text-muted-foreground">고쳐도 방문 횟수는 늘지 않아요. 위치 방문 시각 카테고리는 바꿀 수 없어요.</Text></View>
          {error && <Text accessibilityRole="alert" className="text-body-sm text-destructive" testID="my-guestbook.save-error">{getFootprintErrorMessage(error)}</Text>}
          {reloadFailed && <Text accessibilityRole="alert" className="text-body-sm text-destructive">최신 내용을 불러오지 못했어요.</Text>}
          {conflicted && <Button variant="outline" loading={reloading} onPress={() => {
            void readLatest();
          }}><Text>최신 내용 확인</Text></Button>}
          {reviewed && <Text className="text-caption text-muted-foreground">현재 저장된 방명록 · {base.body ?? "방명록 없음"}</Text>}
          <Button variant="outline" disabled={busy || conflicted} testID="my-guestbook.delete" onPress={() => {
            update.reset();
            setDeleting(true);
          }}><Text className="text-destructive">발자취 삭제</Text></Button>
        </View>
      </ScrollView>
      <BottomCta><Button size="lg" className="w-full" loading={update.isPending} disabled={busy || conflicted || length > GUESTBOOK_LIMIT || (body.trim() ? body : null) === base.body} testID="my-guestbook.save" onPress={() => {
        deletion.reset();
        update.mutate({ id: base.id, version: base.version, body: body.trim() ? body : "" }, { onSuccess: () => {
          done("방명록을 저장했어요");
        } });
      }}><Text>저장</Text></Button></BottomCta>
      <ConfirmDialog open={deleting} onOpenChange={setDeleting} title="이 발자취를 삭제할까요?" description="방명록과 지도에 남긴 발자취가 함께 삭제돼요." confirmLabel="삭제" confirmTone="destructive" loading={deletion.isPending} testID="my-guestbook.delete-dialog" onConfirm={() => {
        deletion.mutate({ id: base.id, version: base.version }, { onSuccess: () => {
          setDeleting(false);
          done("발자취를 삭제했어요");
        },

        onError: () => {
          setDeleting(false);
        } });
      }} />
    </>
  );
}
