import { createContext, useContext, useState, type ReactNode } from "react";

import { useLoginProvider } from "@/features/auth/api/queries";
import type { SocialProvider } from "@/features/auth/model";
import { mockProfile } from "./my-data";

type MyMockState = {
  loginProvider: SocialProvider | null;
  previewLogin: (provider: SocialProvider) => void;
  published: boolean;
  setPublished: (published: boolean) => void;
  reset: () => void;
};

const MyMockContext = createContext<MyMockState | null>(null);

export function MyMockProvider({ children }: { children: ReactNode }) {
  const login = useLoginProvider();
  const [previewProvider, setPreviewProvider] = useState<SocialProvider | null>(null);
  const loginProvider = previewProvider ?? login.data?.provider ?? (login.data?.hasSession === false ? mockProfile.provider : null);
  const [published, setPublished] = useState(true);

  return (
    <MyMockContext.Provider value={{
      loginProvider,
      previewLogin: setPreviewProvider,
      published,
      setPublished,

      reset: () => {
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
