import { router, type Href } from "expo-router";
import { useState } from "react";
import { View } from "react-native";

import { isApiError } from "@/api/errors";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { AvatarStack } from "@/features/room/components/member-avatar";
import { cn } from "@/lib/utils";
import { useRecord } from "../api/queries";
import { recordError } from "../errors";
import { recordDateLabel, type RecordDetail } from "../model";
import { RecordDraftScreen } from "./record-draft-screen";
import { RecordFrame } from "./record-frame";
import { RecordIssue, RecordLoading } from "./record-list-screen";
import { RecordPublicationScreen, RecordPublishedCourseScreen } from "./record-publication-screen";
import { RecordTimeline } from "./record-timeline";
import { RecordUploadScreen } from "./record-upload-screen";

const steps = [
  { title: "사진과 영수증 올리기", description: "사진을 먼저 올리고 영수증을 확인해요." },
  { title: "AI 초안 확인", description: "다녀간 곳과 방문 시각을 확인해요." },
  { title: "사진 연결과 이야기", description: "일정마다 사진을 넣고 느낀 점을 써요." },
  { title: "여행기 완성", description: "일행과 함께 만든 여행기를 완성해요." },
];

export function RecordScreen({ id }: { id: number }) {
  const exit = () => {
    return router.canGoBack() ? router.back() : router.replace("/memo" as Href);
  };

  if (id <= 0) {
    return <RecordFrame title="내 기록" onBack={exit}><RecordIssue message="여행 기록을 찾을 수 없어요." /></RecordFrame>;
  }

  return <RecordContent id={id} exit={exit} />;
}

function RecordContent({ id, exit }: { id: number; exit: () => void }) {
  const record = useRecord(id);
  const [mode, setMode] = useState<"hub" | "upload" | "review" | "edit" | "publish" | "published">("hub");
  const [reviewKey, setReviewKey] = useState<string | null>(null);

  const hub = () => {
    return setMode("hub");
  };

  const accessLost = isApiError(record.error) && [401, 403, 404].includes(record.error.status);

  if (!record.data || accessLost) {
    return <RecordFrame title="내 기록" onBack={exit}>
      {record.isPending ? <RecordLoading /> : <RecordIssue message={recordError(record.error, "여행 기록을 불러오지 못했어요.")} onRetry={() => {
        return void record.refetch();
      }} />}
    </RecordFrame>;
  }

  const data = record.data;

  if (mode === "publish") {
    return <RecordPublicationScreen record={data} onBack={hub} />;
  }

  if (mode === "published") {
    return <RecordPublishedCourseScreen courseId={data.publishedCourseId} onBack={hub} />;
  }

  if (mode === "edit" || (mode === "hub" && data.status === "COMPLETED")) {
    return <RecordTimeline key={mode} record={data} editing={mode === "edit"} onBack={mode === "edit" ? hub : exit}
      onEdit={() => {
        return setMode("edit");
      }} onOpenPublication={() => {
        setMode(data.publishedCourseId === null ? "publish" : "published");
      }} onComplete={hub} />;
  }

  if (mode === "review" && reviewKey) {
    return <RecordDraftScreen record={data} draftKey={reviewKey} onBack={() => {
      return setMode("upload");
    }} onSaved={() => {
      return setMode("edit");
    }} />;
  }

  if (mode === "upload") {
    return <RecordUploadScreen record={data} onBack={hub} onReview={(key) => {
      setReviewKey(key); setMode("review");
    }}
    onRefresh={() => {
      return void record.refetch();
    }} />;
  }

  return <RecordHub record={data} onBack={exit} onNext={() => {
    return setMode(data.progress.stage === "EDIT" ? "edit" : "upload");
  }} />;
}

function RecordHub({ record, onBack, onNext }: { record: RecordDetail; onBack: () => void; onNext: () => void }) {
  const stage = record.progress.stage;
  const step = stage === "EDIT" ? 2 : stage === "PROCESSING" || stage === "REVIEW" ? 1 : 0;
  const label = stage === "EDIT" ? "기록 편집하기" : stage === "REVIEW" ? "초안 확인하기" :
    stage === "PROCESSING" ? "초안 진행 상태 보기" : stage === "RECEIPT_REVIEW" ? "영수증 확인하기" :
      stage === "READY_TO_GENERATE" ? "업로드 자료 확인하기" : "사진과 영수증 올리기";

  return <RecordFrame title={record.title} onBack={onBack} trailing={<View className="pr-4"><AvatarStack members={record.members} /></View>}
    footer={<Button size="lg" className="w-full" disabled={stage === "UNKNOWN"} onPress={onNext} testID="memo.next-step"><Text>{label}</Text></Button>}>
    <View className="flex-row flex-wrap items-center gap-2">
      <Badge><Text>작성 중</Text></Badge>
      <Text className="flex-1 text-caption text-foreground-secondary">
        {[recordDateLabel(record.startDate, record.endDate), record.members.map((member) => {
          return member.nickname;
        }).join(", ")].filter(Boolean).join(" / ")}
      </Text>
    </View>
    <Text accessibilityRole="header" className="text-h1">기록을 채워 볼까요?</Text>
    <View className="overflow-hidden rounded-md border border-border">
      {steps.map(({ title, description }, index) => {
        return (
          <View key={title} className={cn("min-h-16 flex-row items-center gap-3 p-4", index !== 0 && "border-t border-border", step === index && "bg-accent")}>
            <View className={cn("h-7 w-7 items-center justify-center rounded-full", step === index ? "bg-primary" : "bg-muted")}>
              <Text className={cn("text-label-sm", step === index ? "text-primary-foreground" : "text-muted-foreground")}>{index + 1}</Text>
            </View>
            <View className="flex-1 gap-1">
              <Text className={cn("text-label", step === index && "text-primary")}>{title}</Text>
              <Text className="text-caption text-muted-foreground">{description}</Text>
            </View>
          </View>
        );
      })}
    </View>
    {stage === "UNKNOWN" && <RecordIssue message="기록의 진행 상태를 확인할 수 없어요." />}
  </RecordFrame>;
}
