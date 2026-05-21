import { useState } from "react";

import { ThemeTogglerButton } from "@/components/animate-ui/components/buttons/theme-toggler";
import { Tabs, TabsContent, TabsContents, TabsList, TabsTrigger } from "@/components/animate-ui/components/radix/tabs";
import Container from "@/components/user-defined/container";

import Login from "./Login";
import Register from "./Register";

const MainPage = () => {
  const [activeTab, setActiveTab] = useState("login");

  return (
    <Container className="relative min-h-screen flex flex-col items-center justify-center overflow-hidden">
      <ThemeTogglerButton
        modes={['light', 'dark']}
        variant="ghost"
        className="fixed bottom-4 right-4 z-50 cursor-pointer"
      />

      {/* One Tap 掛載容器，color-scheme: light 防止深色模式污染 Google iframe */}
      <div id="g-one-tap-container" className="fixed top-4 right-4 z-50" style={{ colorScheme: 'light' }} />

      <Tabs value={activeTab} onValueChange={setActiveTab} className="z-10">
        <TabsList>
          <TabsTrigger value="login">登入</TabsTrigger>
          <TabsTrigger value="register">註冊</TabsTrigger>
        </TabsList>
        <TabsContents className="overflow-visible">
          <TabsContent value="login">
            <Login onSwitchTab={setActiveTab} />
          </TabsContent>
          <TabsContent value="register">
            <Register onSwitchTab={setActiveTab} />
          </TabsContent>
        </TabsContents>
      </Tabs>
    </Container>
  );
};

export default MainPage;
