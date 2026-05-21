import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect,useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import z from "zod";

import logoImageSrc from "@/assets/logo(120).png";
import { Button } from "@/components/animate-ui/components/buttons/button";
import { BorderBeam } from "@/components/ui/border-beam";
import { Card, CardAction,CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError,FieldGroup, FieldLabel, FieldSet } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ErrorAlert } from "@/components/user-defined/error-alert";
import { LoadingWrapper } from "@/components/user-defined/loading-wrapper";
import Logo from "@/components/user-defined/logo";
import { MotionWrapper } from "@/components/user-defined/motion-wrapper";
import type { ErrorDetail } from "@/constants/error-mapping";
import { useAuth } from "@/contexts/AuthContext";
import useTurnstile from "@/hooks/use-turnstile";
import { ApiError } from "@/types/api-error";
import { handleApiError } from "@/utils/api-error-handler";
import ApiClient from "@/utils/api-util";
import { logger } from "@/utils/logger";
import { ValidationUtil } from "@/utils/validation-util";

/** 驗證碼重寄倒數秒數 */
const SEND_VERIFICATION_LETTER_COUNTDOWN_SECONDS = 60;

const registerFormSchema = z.object({
    username: z.string()
        .min(6, "使用者名稱長度需在 6 到 20 個字元之間")
        .max(20, "使用者名稱長度需在 6 到 20 個字元之間")
        .refine(val => !ValidationUtil.hasNonAlphanumeric(val), {
            message: "使用者名稱僅能包含數字與英文字母"
        }),
    password: z.string()
        .min(6, "密碼需在 6 到 20 個字元之間")
        .max(20, "密碼超出長度限制")
        .refine(val => !ValidationUtil.hasNonAlphanumeric(val), {
            message: "密碼僅能包含數字與英文字母"
        }),
    email: z.email("請輸入有效的電子郵件地址"),
    verificationCode: z.string()
        .min(1, "請輸入驗證碼")
        .max(10, "驗證碼格式錯誤"),
    confirmPassword: z.string()
        .min(1, "請再次輸入密碼")
        .max(20, "密碼超出長度限制")
        .refine(val => !ValidationUtil.hasNonAlphanumeric(val), {
            message: "密碼僅能包含數字與英文字母"
        }),
}).refine((data) => data.password === data.confirmPassword, {
    message: "密碼不一致",
    path: ["confirmPassword"],
});

type registerFormType = z.infer<typeof registerFormSchema>;

/**
 * Register 組件的 Props 介面
 */
interface RegisterProps {
  /** 切換分頁的回呼函數 */
  onSwitchTab: (value: string) => void;
}

/**
 * 註冊頁面組件
 *
 * 提供系統的新使用者註冊介面。整合了表單驗證機制，支援密碼與確認密碼的一致性比對。
 * 負責處理註冊請求並引導完成後的跳轉。
 *
 * @param props - 組件屬性
 * @param props.onSwitchTab - 切換分頁的回呼函數
 * @returns 渲染後的註冊卡片介面
 */
const Register = ({ onSwitchTab }: RegisterProps) => {

    const { register, handleSubmit, watch, formState: { errors } } = useForm<registerFormType>({
        resolver: zodResolver(registerFormSchema),
        mode: "onSubmit",
        reValidateMode: "onChange",
        defaultValues: {
            username: "",
            password: "",
            email: "",
            verificationCode: "",
        }
    });

    const [isLoading, setIsLoading] = useState(false);
    const [globalError, setGlobalError] = useState<ErrorDetail | null>(null);
    const [countdown, setCountdown] = useState(0);
    const [isResendDisabled, setIsResendDisabled] = useState(false);
    const [isSendingCode, setIsSendingCode] = useState(false);
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const navigate = useNavigate();
    const { setAuth } = useAuth();
    const emailValue = watch("email");

    const { isTurnstileReady, turnstileRef, resetTurnstileWidgetUponError, turnstileTokenRef } = useTurnstile({ onError: () => setGlobalError({
      userMessage: "Cloudflare 驗證載入失敗，請重新整理頁面",
      severity: "warning",
      actionMsg: "重新整理",
      debugCode: "TURNSTILE_ERROR",
    })});

    // 組件卸載時清理 timer，避免記憶體洩漏
    useEffect(() => {
        return () => {
            if (timerRef.current) {
                clearInterval(timerRef.current);
            }
        };
    }, []);

    // 啟動倒數計時
    const startCountdown = (seconds: number) => {
        setCountdown(seconds);
        setIsResendDisabled(true);
        timerRef.current = setInterval(() => {
            setCountdown((prev) => {
                if (prev <= 1) {
                    clearInterval(timerRef.current!);
                    timerRef.current = null;
                    setIsResendDisabled(false);
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    };

    // 發送驗證碼
    const handleSendVerificationCode = async () => {
        // 檢查 email 是否有效
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailValue || !emailRegex.test(emailValue)) {
            toast.error("請先輸入有效的電子郵件地址");
            return;
        }

        try {
            setIsResendDisabled(true);
            setIsSendingCode(true);
            await ApiClient.sendEmailVerification(emailValue);
            toast.success("驗證碼已寄出，請檢查您的信箱");
            startCountdown(SEND_VERIFICATION_LETTER_COUNTDOWN_SECONDS);
        } catch (error: unknown) {
            const errorDetail = handleApiError(error);
            toast.error(errorDetail.userMessage);
            setIsResendDisabled(false);
        } finally {
            setIsSendingCode(false);
        }
    };

    const onSubmit = async (data: registerFormType) => {
        setIsLoading(true);
        setGlobalError(null);

        // 取得 Turnstile Token（callback 儲存於 ref，能進到此處代表按鈕已 enable，token 一定存在）
        const turnstileToken = turnstileTokenRef.current ?? undefined;

        try {
            const response = await ApiClient.register({
                email: data.email,
                username: data.username,
                password: data.password,
                verificationCode: data.verificationCode,
                turnstileToken,
            });
            setAuth(response.data.accessToken, response.data.username);
            navigate("/welcome");
        } catch (error: unknown) {
            if (!ApiError.isApiError(error)) {
                logger.error('[Register] 錯誤', error as Error);
            }
            setGlobalError(handleApiError(error));
            // 註冊失敗後重置 Turnstile Widget
            resetTurnstileWidgetUponError();
        } finally {
            setIsLoading(false);
        }
    };
    
    return (
      <MotionWrapper variantsType="container" className="rounded-xl overflow-hidden">
        <Card className="w-fit min-w-100 max-w-lg mx-auto shadow-2xl backdrop-blur-sm bg-card/95">
          <CardHeader className="px-10">
            <MotionWrapper className="flex flex-1 flex-col items-start gap-1">
              <CardTitle className="text-3xl font-extrabold tracking-tight">
                註冊
              </CardTitle>
              <CardDescription className="whitespace-nowrap text-muted-foreground/80 tracking-wide">
                加入 Yeet，開啟你的 Yeet 之旅！
              </CardDescription>
            </MotionWrapper>
            <CardAction>
              <MotionWrapper>
                <Logo
                  src={logoImageSrc}
                  avatarClassName="size-20 drop-shadow-md"
                />
              </MotionWrapper>
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
                        className="text-xs font-bold uppercase tracking-widest text-muted-foreground"
                      >
                        使用者名稱
                      </FieldLabel>
                      <Input
                        {...register("username")}
                        id="username"
                        autoComplete="off"
                        placeholder="請輸入使用者名稱"
                        aria-invalid={errors.username && "true"}
                        maxLength={100}
                        className="bg-muted/30 focus:bg-background transition-colors duration-300"
                        required
                      />
                      <FieldError>{errors.username?.message}</FieldError>
                    </Field>
                  </MotionWrapper>
                  <MotionWrapper>
                    <Field>
                      <FieldLabel
                        htmlFor="email"
                        required
                        className="text-xs font-bold uppercase tracking-widest text-muted-foreground"
                      >
                        電子郵件
                      </FieldLabel>
                      <Input
                        {...register("email")}
                        id="email"
                        autoComplete="off"
                        placeholder="請輸入電子郵件"
                        aria-invalid={errors.email && "true"}
                        maxLength={100}
                        className="bg-muted/30 focus:bg-background transition-colors duration-300"
                        required
                      />
                      <FieldError>{errors.email?.message}</FieldError>
                    </Field>
                  </MotionWrapper>
                  <MotionWrapper>
                    <Field data-invalid={errors.verificationCode && "true"}>
                      <FieldLabel
                        htmlFor="verificationCode"
                        required
                        className="text-xs font-bold uppercase tracking-widest text-muted-foreground"
                      >
                        信箱驗證碼
                      </FieldLabel>
                      <div className="relative">
                        <Input
                          {...register("verificationCode")}
                          id="verificationCode"
                          autoComplete="off"
                          placeholder="請輸入驗證碼"
                          aria-invalid={errors.verificationCode && "true"}
                          maxLength={10}
                          className="pr-32.5 bg-muted/30 focus:bg-background transition-colors duration-300"
                          required
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="absolute right-1 top-1/2 -translate-y-1/2 h-7 px-3 text-xs font-medium text-primary hover:text-primary/80 hover:bg-primary/10"
                          disabled={isResendDisabled || isSendingCode}
                          onClick={handleSendVerificationCode}
                        >
                          {isSendingCode ? "發送中..." : countdown > 0 ? `重寄 (${countdown}s)` : "寄送驗證碼"}
                        </Button>
                      </div>
                      <FieldError>{errors.verificationCode?.message}</FieldError>
                    </Field>
                  </MotionWrapper>
                  <MotionWrapper>
                    <Field data-invalid={errors.password && "true"}>
                      <FieldLabel
                        htmlFor="password"
                        required
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
                        maxLength={20}
                        className="bg-muted/30 focus:bg-background transition-colors duration-300"
                        required
                      />
                      <FieldError>{errors.password?.message}</FieldError>
                    </Field>
                  </MotionWrapper>
                  <MotionWrapper>
                    <Field data-invalid={errors.confirmPassword && "true"}>
                      <FieldLabel
                        htmlFor="confirmPassword"
                        required
                        className="text-xs font-bold uppercase tracking-widest text-muted-foreground"
                      >
                        確認密碼
                      </FieldLabel>
                      <Input
                        {...register("confirmPassword")}
                        id="confirmPassword"
                        type="password"
                        autoComplete="off"
                        placeholder="請再次輸入密碼"
                        aria-invalid={errors.confirmPassword && "true"}
                        maxLength={20}
                        className="bg-muted/30 focus:bg-background transition-colors duration-300"
                        required
                      />
                      <FieldError>{errors.confirmPassword?.message}</FieldError>
                    </Field>
                  </MotionWrapper>
                </FieldGroup>
              </FieldSet>
              <MotionWrapper className="mt-8">
                <Button
                  type="submit"
                  className="w-full font-bold text-base h-11 bg-primary hover:shadow-lg hover:shadow-primary/20 transition-all active:scale-[0.98]"
                  disabled={isLoading || !isTurnstileReady}
                >
                  <LoadingWrapper isLoading={isLoading}>註冊</LoadingWrapper>
                </Button>
              </MotionWrapper>
            </form>
          </CardContent>
          <CardFooter className="justify-center px-10">
            <MotionWrapper className="flex items-center gap-1 text-sm">
              <span>已經有帳號了？</span>
              <Button
                variant="link"
                size="sm"
                className="h-auto p-0 font-bold hover:no-underline"
                onClick={() => onSwitchTab("login")}
              >
                登入！
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

export default Register;
