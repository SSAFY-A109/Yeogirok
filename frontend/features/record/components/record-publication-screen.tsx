import { useMutation, useQueryClient } from "@tanstack/react-query";
import { LockKeyhole } from "lucide-react-native";
import { useState } from "react";
import { View } from "react-native";

import { isApiError } from "@/api/errors";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Switch } from "@/components/ui/switch";
import { Text } from "@/components/ui/text";
import { Textarea } from "@/components/ui/textarea";
import { courseKeys, useCourse } from "@/features/course/api/queries";
import { getCourseErrorMessage, isCourseUnavailable } from "@/features/course/errors";
import type { PublicPhotoDto } from "@/features/course/model";
import { CourseDetailContent } from "@/features/explore/components/course-detail-screen";
import { recordKeys, useRecordWrite } from "../api/queries";
import { previewPublication, publishRecord } from "../api/record.api";
import { recordError } from "../errors";
import type { PublicationInput, RecordDetail, RecordPlace } from "../model";
import { RecordField } from "./record-fields";
import { RecordFrame } from "./record-frame";
import { RecordIssue, RecordLoading } from "./record-list-screen";
import { RecordRouteMap } from "./record-route-map";

type PublicStop = { visitId: number; place: RecordPlace; photos: PublicPhotoDto[] };

export function RecordPublishedCourseScreen({ courseId, onBack }: { courseId: number | null; onBack: () => void }) {
  const course = useCourse(courseId);
  const unavailable = courseId === null || isCourseUnavailable(course.error);

  return <RecordFrame centered scroll={false} title="공개 코스 보기" onBack={onBack}>
    {unavailable || course.isError || !course.data ? (
      <View className="px-5 pt-2">
        {unavailable ? <RecordIssue message="공개가 해제되었거나 삭제된 코스예요." />
          : course.isError ? <RecordIssue message={getCourseErrorMessage(course.error)} onRetry={() => {
            void course.refetch();
          }} /> : <RecordLoading />}
      </View>
    ) : <CourseDetailContent course={course.data} placesTitle="공개된 장소" afterPlaces={<PublicationPrivacyNotice />} />}
  </RecordFrame>;
}

export function RecordPublicationScreen({ record, onBack }: { record: RecordDetail; onBack: () => void }) {
  const [title, setTitle] = useState(record.title);
  const [description, setDescription] = useState<string>();
  const [includePhotos, setIncludePhotos] = useState(false);
  const client = useQueryClient();
  const write = useRecordWrite(record.id);
  const preview = useMutation({
    mutationFn: (body: PublicationInput) => {
      return previewPublication(record.id, body);
    },

    retry: false,
    gcTime: 0,

    onError: (error) => {
      if (isApiError(error) && error.code === "C004") {
        return client.invalidateQueries({ queryKey: recordKeys.detail(record.id) });
      }
    },
  });
  const available = record.status === "COMPLETED" && record.publishedCourseId === null && record.actualVisits.length > 0;
  const confirmed = preview.data;
  const submitted = preview.variables;
  const stale = submitted?.version !== record.version ||
    (isApiError(write.error) && ["C004", "CO010"].includes(write.error.code));

  if (confirmed && submitted) {
    const body = { ...submitted, selectionHash: confirmed.selectionHash };
    const hasPublicPlaces = confirmed.preview.places.length > 0;

    return <RecordFrame centered scroll={false} title="공개 미리보기" onBack={() => {
      if (!write.isPending) {
        preview.reset();
        write.reset();
      }
    }} footer={
      <Button className="w-full" size="lg" loading={write.isPending} disabled={!available || stale || !hasPublicPlaces}
        testID="memo.publish-confirm" onPress={async () => {
          const saved = await write.run(`publish:${confirmed.selectionHash}`, async (key) => {
            await publishRecord(record.id, body, key);
          });

          if (saved) {
            void client.invalidateQueries({ queryKey: courseKeys.all });
            onBack();
          }
        }}><Text>코스로 공개</Text></Button>
    }>
      <CourseDetailContent course={confirmed.preview} afterPlaces={<>
        {confirmed.excluded.length > 0 && <Text className="text-caption text-muted-foreground">공개 대상에서 {confirmed.excluded.length}건이 제외됐어요.</Text>}
        {confirmed.warnings.map((warning, index) => {
          return <Text key={`${index}-${warning}`} className="text-caption text-foreground-secondary">{warning}</Text>;
        })}
        <PublicationPrivacyNotice />
        {!hasPublicPlaces && <RecordIssue message="공개할 장소가 없어요." />}
        {(stale || write.isError || !available) && <RecordIssue message={
          record.publishedCourseId !== null ? "이미 공개 중인 코스예요." : stale
            ? "기록이 바뀌었어요. 돌아가서 새 미리보기를 확인해 주세요." : recordError(write.error, "여행기를 완성한 뒤 공개할 수 있어요.")
        } />}
      </>} />
    </RecordFrame>;
  }

  return <RecordFrame centered title="코스로 공개" onBack={onBack} footer={
    <Button size="lg" className="w-full" loading={preview.isPending} disabled={!available || !title.trim()}
      testID="memo.publish-preview" onPress={() => {
        preview.mutate({ title: title.trim(), description, includePhotos, version: record.version });
      }}><Text>공개 미리보기</Text></Button>
  }>
    <RecordField label="코스 이름" value={title} onChange={setTitle} />
    <View className="gap-2">
      <Text className="text-label">설명</Text>
      <Textarea accessibilityLabel="코스 설명" placeholder="코스 소개 (선택)" className="h-24" value={description ?? ""} onChangeText={setDescription} />
    </View>
    <PublicationRoute stops={record.actualVisits.map((visit) => {
      return {
        visitId: visit.id, place: visit.place, photos: visit.photos,
      };
    })} dayCount={new Set(record.actualVisits.map((visit) => {
      return visit.visitDate;
    })).size} includePhotos={includePhotos} />
    <View className="min-h-touch flex-row items-center justify-between gap-4">
      <View className="flex-1 gap-1">
        <Text className="text-label">사진 함께 공개</Text>
        <Text className="text-caption text-foreground-secondary">일정에 연결한 사진이 코스에 함께 보여요.</Text>
      </View>
      <Switch checked={includePhotos} onCheckedChange={setIncludePhotos} accessibilityLabel="사진 함께 공개" testID="memo.publish-photos" />
    </View>
    <PublicationPrivacyNotice />
    {preview.isError && <RecordIssue message={recordError(preview.error, "공개 미리보기를 불러오지 못했어요.")} />}
  </RecordFrame>;
}

function PublicationRoute({ stops, dayCount, includePhotos }: {
  stops: PublicStop[]; dayCount: number; includePhotos: boolean;
}) {
  return <View className="gap-2">
    <View className="flex-row items-center justify-between gap-2">
      <Text accessibilityRole="header" className="text-label">공개될 경로</Text>
      <Text className="text-caption text-foreground-secondary">{dayCount}일 · 장소 {stops.length}곳</Text>
    </View>
    <RecordRouteMap stops={stops.map(({ visitId, place }) => {
      return { id: String(visitId), place };
    })} />
    <View className="overflow-hidden rounded-md border border-border">
      {stops.map(({ visitId, place, photos }, index) => {
        return (
          <View key={visitId} className={index === 0 ? "gap-2 px-4 py-2" : "gap-2 border-t border-border px-4 py-2"}>
            <View className="flex-row items-center gap-3">
              <View className="h-5 w-5 items-center justify-center rounded-full bg-primary">
                <Text className="text-micro text-primary-foreground">{index + 1}</Text>
              </View>
              <Text className="flex-1 text-label">{place.name}</Text>
              {includePhotos && <Text className="text-caption text-muted-foreground">사진 {photos.length}장</Text>}
            </View>
          </View>
        );
      })}
    </View>
  </View>;
}

function PublicationPrivacyNotice() {
  return <View className="flex-row items-center gap-2 rounded-md bg-muted p-4">
    <Icon as={LockKeyhole} size={16} className="text-icon" />
    <Text className="flex-1 text-caption text-foreground-secondary">이야기, 영수증, 결제 내용, 방명록은 공개되지 않아요.</Text>
  </View>;
}
