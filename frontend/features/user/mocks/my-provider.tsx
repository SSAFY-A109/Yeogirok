import { createContext, useContext, useState, type ReactNode } from "react";

import { useLoginProvider } from "@/features/auth/api/queries";
import type { SocialProvider } from "@/features/auth/model";
import { mockFootprints, mockProfile, mockTips, type MyFootprint, type MyTip } from "./my-data";

type MyMockState = {
  loginProvider: SocialProvider | null;
  previewLogin: (provider: SocialProvider) => void;
  tips: MyTip[];
  footprints: MyFootprint[];
  published: boolean;
  setPublished: (published: boolean) => void;
  saveTip: (tip: MyTip) => void;
  deleteTip: (id: string) => void;
  saveGuestbook: (id: string, body: string) => void;
  reset: () => void;
};

const MyMockContext = createContext<MyMockState | null>(null);

export function MyMockProvider({ children }: { children: ReactNode }) {
  const login = useLoginProvider();
  const [previewProvider, setPreviewProvider] = useState<SocialProvider | null>(null);
  const loginProvider = previewProvider ?? login.data?.provider ?? (login.data?.hasSession === false ? mockProfile.provider : null);
  const [tips, setTips] = useState(mockTips);
  const [footprints, setFootprints] = useState(mockFootprints);
  const [published, setPublished] = useState(true);

  return (
    <MyMockContext.Provider value={{
      loginProvider,
      previewLogin: setPreviewProvider,
      tips,
      footprints,
      published,
      setPublished,

      saveTip: (tip) => {
        setTips((items) => {
          return items.map((item) => {
            return item.id === tip.id ? tip : item;
          });
        });
      },

      deleteTip: (id) => {
        setTips((items) => {
          return items.filter((item) => {
            return item.id !== id;
          });
        });
      },

      saveGuestbook: (id, body) => {
        setFootprints((items) => {
          return items.map((item) => {
            return item.id === id ? { ...item, body } : item;
          });
        });
      },

      reset: () => {
        setTips(mockTips);
        setFootprints(mockFootprints);
        setPublished(true);
      },
    }}>
      {children}
    </MyMockContext.Provider>
  );
}

export function useMyMock() {
  const context = useContext(MyMockContext);

  if (!context) {
    throw new Error("MyMockProvider 안에서 사용해야 해요.");
  }

  return context;
}
