package com.fertigate.controller;

import com.fertigate.annotation.OperationLog;
import com.fertigate.dto.*;
import com.fertigate.entity.OperationLog;
import com.fertigate.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<List<SysUserDTO>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SysUserDTO> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(authService.getUserById(id));
    }

    @PostMapping
    @OperationLog(operation = "创建用户", type = OperationLog.OperationType.CREATE, targetType = "user")
    public ResponseEntity<SysUserDTO> createUser(@Valid @RequestBody CreateUserDTO dto) {
        return ResponseEntity.ok(authService.createUser(dto));
    }

    @PutMapping("/{id}")
    @OperationLog(operation = "更新用户", type = OperationLog.OperationType.UPDATE, targetType = "user")
    public ResponseEntity<SysUserDTO> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserDTO dto) {
        return ResponseEntity.ok(authService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    @OperationLog(operation = "删除用户", type = OperationLog.OperationType.DELETE, targetType = "user")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        authService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reset-password")
    @OperationLog(operation = "重置用户密码", type = OperationLog.OperationType.UPDATE, targetType = "user")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("密码长度至少6位");
        }
        authService.resetPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("message", "密码重置成功"));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<SysRoleDTO>> getAllRoles() {
        return ResponseEntity.ok(authService.getAllRoles());
    }
}
