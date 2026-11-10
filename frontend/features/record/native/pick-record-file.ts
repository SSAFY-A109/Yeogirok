import { getDocumentAsync } from "expo-document-picker";
import { File } from "expo-file-system";

import { ApiError } from "@/api/errors";
import type { UploadFile } from "@/features/media/upload";

const PHOTO_LIMIT = 20 * 1024 * 1024;
const supported = ["image/jpeg", "image/png", "image/webp"];

export async function pickRecordFile(recordId: number, purpose: "RECORD_PHOTO" | "RECEIPT"): Promise<UploadFile | null> {
  const result = await getDocumentAsync({ type: supported, copyToCacheDirectory: true, base64: false });

  if (result.canceled) {
    return null;
  }

  const asset = result.assets[0];
  const localCopy = asset.file ? null : new File(asset.uri);
  let body: ArrayBuffer;

  try {
    if (purpose === "RECORD_PHOTO" && asset.size !== undefined && asset.size > PHOTO_LIMIT) {
      throw new ApiError(413, "M001", "");
    }

    body = await (asset.file ?? localCopy!).arrayBuffer();
  } finally {
    if (localCopy?.exists) {
      localCopy.delete();
    }

    if (asset.file) {
      URL.revokeObjectURL(asset.uri);
    }
  }

  if (purpose === "RECORD_PHOTO" && body.byteLength > PHOTO_LIMIT) {
    throw new ApiError(413, "M001", "");
  }

  const bytes = new Uint8Array(body, 0, Math.min(body.byteLength, 12));
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;

  const png = [137, 80, 78, 71, 13, 10, 26, 10].every((value, index) => {
    return bytes[index] === value;
  });

  const webp = String.fromCharCode(...bytes.slice(0, 4)) === "RIFF" && String.fromCharCode(...bytes.slice(8)) === "WEBP";
  const contentType = jpeg ? "image/jpeg" : png ? "image/png" : webp ? "image/webp" : null;

  if (!contentType) {
    throw new ApiError(415, "M002", "");
  }

  return { metadata: { recordId, purpose, contentType, fileName: asset.name, sizeBytes: body.byteLength }, body };
}
