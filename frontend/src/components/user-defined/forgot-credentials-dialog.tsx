import { Mail, ShieldCheck } from "lucide-react";
import { useEffect,useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { Button } from "@/components/animate-ui/components/buttons/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/animate-ui/components/radix/dialog";
import { Input } from "@/components/ui/input";
import {
  InputOTP,
  InputOTPGroup,
  InputOTPSlot,
} from "@/components/ui/input-otp";
import { LoadingWrapper } from "@/components/user-defined/loading-wrapper";
import { ApiError } from "@/types/api-error";
import { handleApiError } from "@/utils/api-error-handler";
import apiClient from "@/utils/api-util";
import { logger } from "@/utils/logger";

interface ForgotCredentialsDialogProps {
  trigger: React.ReactNode;
}

type ViewState = "INPUT" | "OTP";

// 重送冷卻秒數
const RESEND_COOLDOWN_SECONDS = 60;

/**
 * 忘記密碼/憑證找回彈窗組件
 *
 * 引導使用者進行密碼重設身分驗證。
 * 輸入 Email → 呼叫後端發送 OTP 至信箱 →
 * 輸入 6 位數 OTP → 驗證成功後取得 Reset Token，重導向重設密碼頁面
 *
 * @param trigger Dialog 的觸發元素
 * @returns 互動式的忘記密碼 Modal
 */
export const ForgotCredentialsDialog = ({
  trigger,
}: ForgotCredentialsDialogProps) => {
  const navigate = useNavigate();
  const [viewState, setViewState] = useState<ViewState>("INPUT");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [otpError, setOtpError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  const otpRef = useRef<HTMLInputElement>(null);

  // 冷卻計時器
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((prev) => prev - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!email || isLoading) return;

    setIsLoading(true);
    try {
      await apiClient.forgotPassword(email);
      setCooldown(RESEND_COOLDOWN_SECONDS);
      setViewState("OTP");
    } catch {
      // 不論成功或失敗一律進入 OTP 畫面，避免洩漏此 email 是否存在
      setCooldown(RESEND_COOLDOWN_SECONDS);
      setViewState("OTP");
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    if (isResending || cooldown > 0) return;

    setIsResending(true);
    setOtp("");
    setOtpError(null);
    try {
      await apiClient.forgotPassword(email);
      setCooldown(RESEND_COOLDOWN_SECONDS);
      toast.success("驗證碼已重新發送，請查收信件");
      setTimeout(() => {
        otpRef.current?.focus();
      }, 0);
    } catch (error) {
      if (!ApiError.isApiError(error)) {
        logger.error('[ForgotPassword] 錯誤', error as Error);
      }
      const { userMessage } = handleApiError(error);
      toast.error(userMessage);
    } finally {
      setIsResending(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (otp.length < 6 || isLoading) return;

    setIsLoading(true);
    try {
      const response = await apiClient.verifyOtp(email, otp);
      const resetToken = response.data.resetToken;
      navigate(`/reset-password?token=${resetToken}`);
    } catch (error) {
      if (!ApiError.isApiError(error)) {
        logger.error('[VerifyOtp] 錯誤', error as Error);
      }
      const { userMessage } = handleApiError(error);
      setOtpError(userMessage);
      setOtp("");
      setTimeout(() => {
        otpRef.current?.focus();
      }, 0);
    } finally {
      setIsLoading(false);
    }
  };

  const handleClosing = (open: boolean) => {
    if (!open) {
      // 延遲重置，確保關閉動畫完成後再切換內容，避免視覺閃爍
      setTimeout(() => {
        setViewState("INPUT");
        setEmail("");
        setOtp("");
        setOtpError(null);
        setIsLoading(false);
        setIsResending(false);
        setCooldown(0);
      }, 200);
    }
  };

  const isDesktopNarrow = viewState !== "INPUT";

  return (
    <Dialog onOpenChange={(open) => handleClosing(open)}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent
        className={isDesktopNarrow ? "sm:max-w-md px-12 pb-6" : "sm:max-w-md"}
        onPointerDownOutside={(e) => {
          if (viewState === "OTP") e.preventDefault();
        }}
        onEscapeKeyDown={(e) => {
          if (viewState === "OTP") e.preventDefault();
        }}
      >
        {viewState === "INPUT" && (
          <>
            <DialogHeader className="sm:text-center">
              <DialogTitle className="text-xl mb-1 flex items-center justify-center gap-2">
                <Mail className="size-5 text-primary" />
                忘記密碼？
              </DialogTitle>
              <DialogDescription className="text-center">
                請輸入您的 Email，我們將發送驗證碼信件給您。
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={handleSend} className="grid gap-4 mt-2">
              <div className="grid gap-2">
                <Input
                  type="email"
                  placeholder="name@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                  disabled={isLoading}
                />
              </div>
              <DialogFooter className="flex flex-col sm:flex-col gap-2">
                <Button
                  type="submit"
                  className="w-full font-bold"
                  disabled={isLoading}
                >
                  <LoadingWrapper isLoading={isLoading}>送出</LoadingWrapper>
                </Button>
                <DialogClose asChild>
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full"
                    disabled={isLoading}
                  >
                    取消
                  </Button>
                </DialogClose>
              </DialogFooter>
            </form>
          </>
        )}

        {viewState === "OTP" && (
          <>
            <DialogHeader className="sm:text-center">
              <DialogTitle className="text-xl mb-1 flex items-center justify-center gap-2">
                <ShieldCheck className="size-5 text-primary" />
                輸入驗證碼
              </DialogTitle>
              <DialogDescription className="text-center text-balance">
                已寄出驗證碼至 <span className="font-medium text-foreground">{email}</span>，請在下方輸入。
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={handleVerifyOtp} className="grid gap-6 pt-4">
              <div className="flex flex-col items-center gap-4">
                <InputOTP
                  ref={otpRef}
                  maxLength={6}
                  value={otp}
                  onChange={(value) => {
                    setOtp(value);
                    if (otpError) setOtpError(null);
                  }}
                  aria-invalid={!!otpError}
                  autoFocus
                >
                  <InputOTPGroup>
                    <InputOTPSlot index={0} />
                    <InputOTPSlot index={1} />
                    <InputOTPSlot index={2} />
                    <InputOTPSlot index={3} />
                    <InputOTPSlot index={4} />
                    <InputOTPSlot index={5} />
                  </InputOTPGroup>
                </InputOTP>
                {otpError && (
                  <p className="text-sm font-medium text-destructive">
                    {otpError}
                  </p>
                )}
                {/* 重送驗證碼 */}
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-muted-foreground text-xs h-auto py-1"
                  disabled={cooldown > 0 || isResending}
                  onClick={handleResend}
                >
                  <LoadingWrapper isLoading={isResending}>
                    {cooldown > 0 ? `重新發送 (${cooldown}s)` : "重新發送驗證碼"}
                  </LoadingWrapper>
                </Button>
              </div>
              <DialogFooter className="flex flex-col sm:flex-col gap-2">
                <Button
                  type="submit"
                  className="w-full font-bold"
                  disabled={isLoading || otp.length < 6}
                >
                  <LoadingWrapper isLoading={isLoading}>驗證</LoadingWrapper>
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  className="w-full text-muted-foreground"
                  disabled={isLoading || isResending}
                  onClick={() => setViewState("INPUT")}
                >
                  返回修改 Email
                </Button>
              </DialogFooter>
            </form>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
};