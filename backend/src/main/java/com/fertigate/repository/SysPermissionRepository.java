package com.fertigate.repository;

import com.fertigate.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, UUID> {

    Optional<SysPermission> findByPermissionCode(String permissionCode);

    boolean existsByPermissionCode(String permissionCode);

    List<SysPermission> findByParentIdOrderBySortOrder(UUID parentId);
}
