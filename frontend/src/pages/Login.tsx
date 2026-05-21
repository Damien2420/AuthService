import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { Controller,useForm } from "react-hook-form";
import { FaDiscord } from "react-icons/fa";
import { FaLine } from "react-icons/fa";
import { FcGoogle } from "react-icons/fc";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { z } from "zod";

import logoImageSrc from "@/assets/logo(120).png";
import { Button } from "@/components/animate-ui/components/buttons/button";
import { Checkbox } from "@/components/animate-ui/components/radix/checkbox";
import { BorderBeam } from "@/components/ui/border-beam";
import { Card, CardAction,CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError,FieldGroup, FieldLabel, FieldSeparator, FieldSet } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { ErrorAlert } from "@/components/user-defined/error-alert";
import { ForgotCredentialsDialog } from "@/components/user-defined/forgot-credentials-dialog";
import { LoadingWrapper } from "@/components/user-defined/loading-wrapper";
import Logo from "@/components/user-defined/logo";
import { MotionWrapper } from "@/components/user-defined/motion-wrapper";
import { ENV } from "@/config/env";
import type { ErrorDetail } from "@/constants/error-mapping";
import { useAuth } from "@/contexts/AuthContext";
import useTurnstile from "@/hooks/use-turnstile";
import { ApiError } from "@/types/api-error";
import { handleApiError } from "@/utils/api-error-handler";
import ApiClient from "@/utils/api-util";
import { initAndPromptGoogleOneTap } from "@/utils/gis-util";
import { logger } from "@/utils/logger";
import { ValidationUtil } from "@/utils/validation-util";

// 定義表單驗證模式
const loginFormSchema = z.object({
    username: z.string()
        .min(1, "帳號不可為空")
        .refine(val => !ValidationUtil.hasNonAlphanumeric(val), {
            message: "帳號僅能包含數字與英文字母"
        }),
    password: z.string()
        .min(6, "密碼需大於 6 位數")
        .refine(val => !ValidationUtil.hasNonAlphanumeric(val), {
            message: "密碼僅能包含數字與英文字母"
        }),
    rememberMe: z.boolean().optional(),
});

// 提取表單驗證模式變成型別
type LoginFormSchemaTypes = z.infer<typeof loginFormSchema>;

/**
 * Login 組件的 Props 介面
 */
interface LoginProps {
  /** 切換分頁的回呼函數（從 MainPage Tab 使用時傳入，獨立路由時為可選） */
  onSwitchTab?: (value: string) => void;
}

/**
 * 登入頁面組件
 *
 * 提供系統的靜態與社交登入入口。整合了 React Hook Form 與 Zod 進行即時表單校驗。
 * 支援 Staggered Entrance 動效，並負責處理身分驗證後的路由導向或錯誤回顯。
 *
 * @param props - 組件屬性
 * @param props.onSwitchTab - 切換分頁的回呼函數
 * @returns 渲染後的登入卡片介面
 */
const Login = ({ onSwitchTab }: LoginProps) => {
  const navigate = useNavigate();
  const { setAuth } = useAuth();

  const { register, control, handleSubmit, getValues, formState: { errors } } = useForm<LoginFormSchemaTypes>({
    resolver: zodResolver(loginFormSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
    defaultValues: {
      username: "",
      password: "",
      rememberMe: false,
    },
  });
  const [isLoading, setIsLoading] = useState(false);
  const [globalError, setGlobalError] = useState<ErrorDetail | null>(null);
  const [socialLoadingId, setSocialLoadingId] = useState<string | null>(null);
  const [isResending, setIsResending] = useState(false);
  const loginUsernameRef = useRef<string>("");
  
  const { isTurnstileReady, turnstileRef, resetTurnstileWidgetUponError, turnstileTokenRef } = useTurnstile({ onError: () => setGlobalError({
      userMessage: "Cloudflare 驗證載入失敗，請重新整理頁面",
      severity: "warning",
      actionMsg: "重新整理",
      debugCode: "TURNSTILE_ERROR",
    })});

  // 初始化 GIS One Tap，讓瀏覽器顯示 FedCM 原生登入提示
  useEffect(() => {
    const onSuccess = (data: { accessToken: string; username: string }) => {
      setAuth(data.accessToken, data.username);
      navigate("/welcome");
    };
    const onFailure = (error: unknown) => {
      setGlobalError(handleApiError(error));
    };
    initAndPromptGoogleOneTap(onSuccess, onFailure);
  }, [navigate, setAuth]);

  const onSubmit = async (data: LoginFormSchemaTypes) => {
    setIsLoading(true);
    setGlobalError(null);

    // 取得 Turnstile Token（callback 儲存於 ref，能進到此處代表按鈕已 enable，token 一定存在）
    const turnstileToken = turnstileTokenRef.current ?? undefined;

    try {
      const response = await ApiClient.login({ ...data, turnstileToken });
      if (response.success === true) {
        // 設定認證狀態到 AuthContext（不再使用 localStorage）
        setAuth(response.data.accessToken, response.data.username);
        navigate("/welcome");
      } else {
        setGlobalError(handleApiError({ errorKey: "UNKNOWN", message: response.message }));
      }
    } catch (error: unknown) {
      if (!ApiError.isApiError(error)) {
        logger.error('[Login] 錯誤', error as Error);
      }
      const errorDetail = handleApiError(error);
      // 記錄當下 username，供重寄驗證信使用
      loginUsernameRef.current = getValues("username");
      setGlobalError(errorDetail);
      // 登入失敗後重置 Turnstile Widget
      resetTurnstileWidgetUponError()
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendVerification = async () => {
    const username = loginUsernameRef.current;
    if (!username) return;
    setIsResending(true);
    try {
      await ApiClient.resendVerification(username);
      toast.success("驗證信已寄出，請至信箱查收");
    } catch (error: unknown) {
      const errorDetail = handleApiError(error);
      toast.error(errorDetail.userMessage);
    } finally {
      setIsResending(false);
    }
  };

  const handleSocialLogin = (provider: string) => {
    setSocialLoadingId(provider);
    window.location.href = `${ENV.API_BASE_URL}/oauth2/authorization/${provider}`;
  };

  return (
    <MotionWrapper variantsType="container" className="rounded-xl overflow-hidden">
      <Card className="w-fit min-w-100 max-w-lg mx-auto shadow-2xl backdrop-blur-sm bg-card/95">
        <CardHeader className="py-6 px-10">
          <MotionWrapper className="flex flex-1 flex-col items-start gap-1">
            <CardTitle className="text-3xl font-extrabold tracking-tight">
              登入
            </CardTitle>
            <CardDescription className="whitespace-nowrap text-muted-foreground/80 tracking-wide">
              歡迎回來 Yeet！
            </CardDescription>
          </MotionWrapper>
          <CardAction>
            <motion.div
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              <Button
                variant="ghost"
                size="icon-xxl"
                className="cursor-pointer hover:bg-transparent"
              >
                <Logo
                  src={logoImageSrc}
                  avatarClassName="size-20 drop-shadow-md"
                />
              </Button>
            </motion.div>
          </CardAction>
        </CardHeader>
        <CardContent className="grid gap-6 px-10">
          {globalError && (
            <MotionWrapper>
              <ErrorAlert
                title={globalError.userMessage}
                action={globalError.actionMsg}
                severity={globalError.severity}
                onClose={() => setGlobalError(null)}
                actionLabel={globalError.debugCode === "AUTH_403_02" ? (isResending ? "寄送中..." : "點此驗證帳號") : undefined}
                onAction={globalError.debugCode === "AUTH_403_02" && !isResending ? handleResendVerification : undefined}
              />
            </MotionWrapper>
          )}
          <form onSubmit={handleSubmit(onSubmit)}>
            {/* Turnstile Invisible Widget 掛載點 */}
            <div ref={turnstileRef} className="hidden" />
            <FieldSet className="space-y-4">
              <FieldGroup>
                <MotionWrapper>
                  <Field data-invalid={errors.username && "true"}>
                    <FieldLabel
                      htmlFor="username"
                      required
                      showIndicator={false}
                      className="text-xs font-bold uppercase tracking-widest text-muted-foreground"
                    >
                      帳號
                    </FieldLabel>
                    <Input
                      {...register("username")}
                      id="username"
                      autoComplete="off"
                      placeholder="請輸入帳號"
                      aria-invalid={errors.username && "true"}
                      maxLength={100}
                      className="bg-muted/30 focus:bg-background transition-colors duration-300"
                      required
                    />
                    <FieldError>{errors.username?.message}</FieldError>
                  </Field>
                </MotionWrapper>
                <MotionWrapper>
                  <Field data-invalid={errors.password && "true"}>
                    <FieldLabel
                      htmlFor="password"
                      required
                      showIndicator={false}
                      className="text-xs font-bold uppercase tracking-widest text-muted-foreground"
                    >
                      密碼
                    </FieldLabel>
                    <Input
                      {...register("password")}
                      id="password"
                      type="password"
                      autoComplete="off"
                      placeholder="請輸入密碼"
                      aria-invalid={errors.password && "true"}
                      maxLength={100}
                      className="bg-muted/30 focus:bg-background transition-colors duration-300"
                      required
                    />
                    <FieldError>{errors.password?.message}</FieldError>
                  </Field>
                </MotionWrapper>
              </FieldGroup>
            </FieldSet>
            <MotionWrapper className="flex items-center justify-between mt-6">
              <div className="flex items-center space-x-2.5">
                <Controller
                  control={control}
                  name="rememberMe"
                  render={({ field }) => (
                    <Checkbox
                      id="remember-me"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  )}
                />
                <Label
                  htmlFor="remember-me"
                  className="cursor-pointer text-sm font-medium opacity-80 hover:opacity-100 transition-opacity"
                >
                  記住我
                </Label>
              </div>
              <ForgotCredentialsDialog
                trigger={
                  <Button
                    type="button"
                    variant="link"
                    className="text-sm hover:no-underline font-medium p-0 h-auto"
                  >
                    忘記密碼？
                  </Button>
                }
              />
            </MotionWrapper>
            <MotionWrapper className="mt-8">
              <Button
                type="submit"
                className="w-full font-bold text-base h-11 bg-primary hover:shadow-lg hover:shadow-primary/20 transition-all active:scale-[0.98]"
                disabled={isLoading || !isTurnstileReady}
              >
                <LoadingWrapper isLoading={isLoading}>登入</LoadingWrapper>
              </Button>
            </MotionWrapper>
          </form>
        </CardContent>
        <CardFooter className="flex-col gap-3 px-10">
          <MotionWrapper className="w-full">
            <FieldSeparator className="mb-6 text-xs font-semibold text-muted-foreground-alt/50">
              快捷登入
            </FieldSeparator>
            <div className="flex items-center justify-center gap-6">
              {[
                {
                  id: "google",
                  icon: <FcGoogle className="size-6" />,
                  label: "Google",
                },
                {
                  id: "discord",
                  icon: <FaDiscord className="size-6 text-[#5865F2]" />,
                  label: "Discord",
                },
                {
                  id: "line",
                  icon: <FaLine className="size-6 text-[#00B900]" />,
                  label: "Line",
                },
              ].map((provider) => {
                const isThisLoading = socialLoadingId === provider.id;
                const isAnyLoading = socialLoadingId !== null;
                return (
                  <motion.div
                    key={provider.id}
                    whileHover={isAnyLoading ? {} : { y: -4, scale: 1.05 }}
                    whileTap={isAnyLoading ? {} : { scale: 0.9 }}
                  >
                    <Button
                      variant="outline"
                      size="icon"
                      className="size-12 rounded-full border-muted-foreground/10 hover:border-transparent hover:bg-primary/5 shadow-sm transition-colors"
                      onClick={() => handleSocialLogin(provider.id)}
                      disabled={isAnyLoading}
                      aria-label={`${provider.label} Login`}
                    >
                      {isThisLoading ? <Spinner className="size-4" /> : provider.icon}
                    </Button>
                  </motion.div>
                );
              })}
            </div>
          </MotionWrapper>

          <MotionWrapper className="flex items-center gap-1 text-sm mt-4">
            <span className="text-muted-foreground">還沒加入會員嗎？</span>
            <Button
              variant="link"
              size="sm"
              className="h-auto p-0 font-bold hover:no-underline"
              onClick={() => onSwitchTab ? onSwitchTab("register") : navigate("/")}
            >
              點此註冊！
            </Button>
          </MotionWrapper>
        </CardFooter>
      </Card>
      <BorderBeam
        duration={6}
        size={400}
        borderWidth={2}
        className="from-transparent via-red-500 to-transparent"
      />
      <BorderBeam
        duration={6}
        delay={3}
        size={400}
        borderWidth={2}
        className="from-transparent via-blue-500 to-transparent"
      />
    </MotionWrapper>
  );
};

export default Login;
