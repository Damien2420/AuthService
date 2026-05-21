package com.login.main.controller;

import com.login.main.common.error.AppException;
import com.login.main.dto.request.UpdateNicknameRequest;
import com.login.main.dto.request.UpdatePasswordRequest;
import com.login.main.dto.response.CustomApiResponse;
import com.login.main.dto.response.UserEmailInfoDTO;
import com.login.main.dto.response.UserProfileResponse;
import com.login.main.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 使用者個人資料控制器
 *
 * 提供查詢與更新目前登入使用者個人資料的 API 端點，所有端點均需 JWT 驗證。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "使用者管理", description = "使用者個人資料查詢與更新")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@Validated
public class UserController {

    private final UserService userService;

    /**
     * 以 Email 查詢使用者基本資訊
     *
     * 供邀請成員等跨服務流程使用，僅回傳已驗證且未封鎖的使用者。
     * @param email 電子郵件（需符合格式）
     * @return 包含 UserEmailInfoDTO 的成功回應
     */
    @Operation(summary = "以 Email 查詢使用者", description = "根據 Email 查詢使用者的 userId 與基本資訊，僅回傳已驗證且未封鎖的帳號")
    @ApiResponse(responseCode = "200", description = "查詢成功", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "401", description = "未登入或 Token 無效", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "使用者不存在或未驗證", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @GetMapping("/search")
    public ResponseEntity<CustomApiResponse<UserEmailInfoDTO>> searchByEmail(
            @RequestParam @Email(message = "Email 格式不正確") String email) {

        log.info("以 Email 查詢使用者 - email: {}", email);
        UserEmailInfoDTO result = userService.searchByEmail(email).orThrow(AppException::new);
        return ResponseEntity.ok(CustomApiResponse.success(result, "查詢成功"));
    }

    /**
     * 取得目前登入使用者的個人資料
     *
     * 從 JWT 取出使用者身分，查詢並回傳個人資料及登入提供者清單。
     * @param userDetails Spring Security 注入的目前使用者資訊
     * @return 包含 UserProfileResponse 的成功回應
     */
    @Operation(summary = "取得個人資料", description = "取得目前登入使用者的帳號資訊與登入提供者清單")
    @ApiResponse(responseCode = "200", description = "查詢成功", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "401", description = "未登入或 Token 無效", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<CustomApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();
        log.info("查詢個人資料 - 使用者: {}", username);

        UserProfileResponse profile = userService.getProfile(username).orThrow(AppException::new);
        return ResponseEntity.ok(CustomApiResponse.success(profile, "查詢成功"));
    }

    /**
     * 更新目前登入使用者的暱稱
     *
     * @param userDetails Spring Security 注入的目前使用者資訊
     * @param request     包含新暱稱的請求 DTO
     * @return 成功回應
     */
    @Operation(summary = "更新暱稱", description = "更新目前登入使用者的顯示暱稱")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "400", description = "輸入格式錯誤", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "401", description = "未登入或 Token 無效", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PatchMapping("/me")
    public ResponseEntity<CustomApiResponse<Void>> updateNickname(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateNicknameRequest request) {

        String username = userDetails.getUsername();
        log.info("更新暱稱 - 使用者: {}", username);

        userService.updateNickname(username, request.getNickname()).orThrow(AppException::new);
        return ResponseEntity.ok(CustomApiResponse.success(null, "暱稱已更新"));
    }

    /**
     * 更新目前登入使用者的密碼
     *
     * 已有本地密碼的使用者需提供 currentPassword 驗證身分；
     * OAuth2 使用者首次設定密碼時可省略 currentPassword。
     * @param userDetails Spring Security 注入的目前使用者資訊
     * @param request     包含 currentPassword（選填）與 newPassword 的請求 DTO
     * @return 成功回應
     */
    @Operation(summary = "更新密碼", description = "更新密碼。OAuth2 使用者首次設定密碼時不需提供 currentPassword")
    @ApiResponse(responseCode = "200", description = "密碼已更新")
    @ApiResponse(responseCode = "400", description = "目前密碼錯誤或格式不符", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @ApiResponse(responseCode = "401", description = "未登入或 Token 無效", content = @Content(schema = @Schema(implementation = CustomApiResponse.class)))
    @PatchMapping("/me/password")
    public ResponseEntity<CustomApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdatePasswordRequest request) {

        String username = userDetails.getUsername();
        log.info("更新密碼 - 使用者: {}", username);

        userService.updatePassword(username, request.getCurrentPassword(), request.getNewPassword())
                .orThrow(AppException::new);
        return ResponseEntity.ok(CustomApiResponse.success(null, "密碼已更新"));
    }
}
