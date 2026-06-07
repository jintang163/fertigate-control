package com.fertigate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fertigate.entity.SysRole;

@Data
public class SysRoleDTO {

    private UUID id;
    private String roleCode;
    private String roleName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<String> permissionCodes;
    private Set<String> permissionNames;

    public static SysRoleDTO fromEntity(SysRole role) {
        SysRoleDTO dto = new SysRoleDTO();
        dto.setId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setDescription(role.getDescription());
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        if (role.getPermissions() != null) {
            dto.setPermissionCodes(role.getPermissions().stream()
                    .map(perm -> perm.getPermissionCode())
                    .collect(Collectors.toSet()));
            dto.setPermissionNames(role.getPermissions().stream()
                    .map(perm -> perm.getPermissionName())
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}
