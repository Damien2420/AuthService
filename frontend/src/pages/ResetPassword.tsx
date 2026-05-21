import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { z } from "zod";

import { Button } from "@/components/animate-ui/components/buttons/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel, FieldSet } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ErrorAlert } from "@/components/user-defined/error-alert";
import { LoadingWrapper } from "@/components/user-defined/loading-wrapper";
import type { ErrorDetail } from "@/constants/error-mapping";
import { ApiError } from "@/types/api-error";
import { handleApiError } from "@/utils/api-error-handler";
import apiClient from "@/utils/api-util";
import { logger } from "@/utils/logger";
import { ValidationUtil } from "@/utils/validation-util";

// 定義表單驗證模式
const resetPasswordFormSchema = z
  .object({
    newPassword: z
      .string()
      .min(6, "密碼長度需在 6 到 20 個字元之間")
      .max(20, "密碼長度需在 6 到 20 個字元之間")
      .refine((val) => !ValidationUtil.hasNonAlphanumeric(val), {
        message: "密碼僅能包含數字與英文字母",
      }),
    confirmPassword: z
      .string()
      .min(1, "請再次輸入密碼"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "兩次輸入的密碼不一致",
    path: ["confirmPassword"],
  });

type ResetPasswordFormSchemaTypes = z.infer<typeof resetPasswordFormSchema>;

/**
 * 重設密碼頁面
 *
 * 使用者在完成 OTP 驗證後，透過 URL 中的 Reset Token 進入此頁面。
 * 表單使用 react-hook-form + Zod 進行驗證，送出後呼叫後端重設密碼 API。
 * 成功後所有現有 Session 失效，並重導向至首頁。
 *
 * @returns 重設密碼頁面
 */
const ResetPassword = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const resetToken = searchParams.get("token");

  const [isLoading, setIsLoading] = useState(false);
  const [globalError, setGlobalError] = useState<ErrorDetail | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormSchemaTypes>({
    resolver: zodResolver(resetPasswordFormSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
    defaultValues: {
      newPassword: "",
      confirmPassword: "",
    },
  });

  const onSubmit = async (data: ResetPasswordFormSchemaTypes) => {
    if (!resetToken) return;

    setIsLoading(true);
    setGlobalError(null);
    try {
      await apiClient.resetPassword(resetToken, data.newPassword);
      toast.success("密碼已重設，請使用新密碼重新登入");
      navigate("/");
    } catch (error) {
      if (!ApiError.isApiError(error)) {
        logger.error('[ResetPassword] 錯誤', error as Error);
      }
      const errorDetail = handleApiError(error);
      setGlobalError(errorDetail);
    } finally {
      setIsLoading(false);
    }
  };

  if (!resetToken) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <Card className="w-full max-w-lg">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">連結無效</CardTitle>
            <CardDescription>
              此重設密碼連結無效或已過期，請重新申請。
            </CardDescription>
          </CardHeader>
          <CardContent className="flex justify-center">
            <Button type="button" onClick={() => navigate("/")}>
              返回登入頁
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-lg">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">設定新密碼</CardTitle>
            <CardDescription>
              請輸入您的新密碼。密碼重設後，您會登出所有裝置。
            </CardDescription>
          </CardHeader>
          <CardContent>
            {globalError && (
              <div className="mb-4">
                <ErrorAlert
                  title={globalError.userMessage}
                  severity={globalError.severity}
                  action={globalError.actionMsg}
                  onClose={() => setGlobalError(null)}
                />
              </div>
            )}
            <form onSubmit={handleSubmit(onSubmit)}>
              <FieldSet>
                <FieldGroup>
                  <Field>
                    <FieldLabel>新密碼</FieldLabel>
                    <Input
                      type="password"
                      placeholder="請輸入新密碼（6-20 位英數字元）"
                      autoComplete="new-password"
                      disabled={isLoading}
                      {...register("newPassword")}
                    />
                    {errors.newPassword && (
                      <FieldError>{errors.newPassword.message}</FieldError>
                    )}
                  </Field>
                  <Field>
                    <FieldLabel>確認新密碼</FieldLabel>
                    <Input
                      type="password"
                      placeholder="請再次輸入新密碼"
                      autoComplete="new-password"
                      disabled={isLoading}
                      {...register("confirmPassword")}
                    />
                    {errors.confirmPassword && (
                      <FieldError>{errors.confirmPassword.message}</FieldError>
                    )}
                  </Field>
                </FieldGroup>
              </FieldSet>
              <Button
                type="submit"
                className="w-full mt-6 font-bold"
                disabled={isLoading}
              >
                <LoadingWrapper isLoading={isLoading}>確認重設密碼</LoadingWrapper>
              </Button>
            </form>
          </CardContent>
      </Card>
    </div>
  );
};

export default ResetPassword;