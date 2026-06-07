package com.fertigate.controller;

import com.fertigate.annotation.OperationLog;
import com.fertigate.dto.*;
import com.fertigate.entity.OperationLog;
import com.fertigate.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @OperationLog(operation = "用户登录", type = OperationLog.OperationType.LOGIN, description = "用户登录系统", recordParams = false)
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @OperationLog(operation = "用户登出", type = OperationLog.OperationType.LOGOUT, description = "用户退出系统")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "退出成功"));
    }

    @GetMapping("/userinfo")
    public ResponseEntity<SysUserDTO> getUserInfo() {
        return authService.getCurrentUser()
                .map(user -> ResponseEntity.ok(SysUserDTO.fromEntity(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<String>> getPermissions() {
        return ResponseEntity.ok(authService.getCurrentUserPermissions());
    }
}
