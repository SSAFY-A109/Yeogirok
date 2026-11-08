import type { MyTipDetailDto, TipOperationResponseDto, TipStatusDto } from "../../../features/tip/model";
import { mockDateTime } from "./footprint";

export type MockTip = {
  ownerId: number;
  detail: MyTipDetailDto;
  lastStatus: TipStatusDto;
  pending: { readyAt: number; candidate: MyTipDetailDto } | null;
};

export type MockTipUpdate = { tipId: number; request: string; result: TipOperationResponseDto };

export function createMyTipFixtures(): MockTip[] {
  const categories = ["PHOTO", "RESTAURANT", "CAFE", "TIP"];

  const tips: MockTip[] = categories.map((category, index) => {
    const tipId = index + 1;
    const date = mockDateTime(Date.now() - index * 86_400_000);

    return {
      ownerId: 1,
      detail: { tipId, category, body: "한적할 때 들러 보세요.", photos: [], status: "PUBLISHED", version: 1, publishedAt: date, createdAt: date, updatedAt: date },
      lastStatus: { tipId, status: "PUBLISHED", version: 1, steps: { media: "SKIPPED", spatial: "SUCCEEDED", content: "SUCCEEDED" }, jobId: null, operation: null, operationStatus: null, error: null, retryable: false },
      pending: null,
    };
  });

  const first = tips[0];
  return [...tips, { ...first, ownerId: 2, detail: { ...first.detail, tipId: 99 }, lastStatus: { ...first.lastStatus, tipId: 99 } }];
}
