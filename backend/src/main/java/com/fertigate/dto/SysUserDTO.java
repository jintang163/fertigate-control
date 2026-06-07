package com.fertigate.dto;

import com.fertigate.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
public class SysUserDTO {

    private UUID id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Boolean enabled;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<String> roleCodes;
    private Set<String> roleNames;

    public static SysUserDTO fromEntity(SysUser user) {
        SysUserDTO dto = new SysUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setEnabled(user.getEnabled());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        if (user.getRoles() != null) {
            dto.setRoleCodes(user.getRoles().stream()
                    .map(role -> role.getRoleCode())
                    .collect(Collectors.toSet()));
            dto.setRoleNames(user.getRoles().stream()
                    .map(role -> role.getRoleName())
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}
