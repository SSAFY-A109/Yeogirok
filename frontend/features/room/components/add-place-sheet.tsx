import { View } from "react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { AddPlaceHeading, AddPlaceOptions, type AddPlaceSource } from "@/features/room/components/add-place-options";

type AddPlaceSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 항목을 누르면 부른다. 시트를 닫고 이동하는 일은 부르는 쪽이 한다. */
  onSelect: (source: AddPlaceSource) => void;
};

/** 장소 담기 시트(Figma W09-A). 코스·AI 추천·검색 세 경로를 한곳에 모은다. */
export function AddPlaceSheet({ open, onOpenChange, onSelect }: AddPlaceSheetProps) {
  return (
    <BottomSheet visible={open} onClose={() => {
      onOpenChange(false);
    }} closeLabel="장소 담기 닫기">
      <View className="gap-2 px-5 pb-4 pt-2">
        <AddPlaceHeading />
        <AddPlaceOptions onSelect={onSelect} testIDPrefix="add-place-sheet." />
      </View>
    </BottomSheet>
  );
}
