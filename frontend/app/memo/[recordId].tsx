import { useLocalSearchParams } from "expo-router";
import { RecordScreen } from "@/features/record/components/record-screen";

export default function MemoDetailRoute() {
  const { recordId } = useLocalSearchParams<{ recordId: string }>();
  const id = /^\d+$/.test(recordId ?? "") ? Number(recordId) : 0;
  return <RecordScreen key={id} id={Number.isSafeInteger(id) ? id : 0} />;
}
