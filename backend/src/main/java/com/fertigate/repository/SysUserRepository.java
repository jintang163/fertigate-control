package com.fertigate.repository;

import com.fertigate.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, UUID> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM SysUser u JOIN u.roles r WHERE r.roleCode = :roleCode")
    List<SysUser> findByRoleCode(@Param("roleCode") String roleCode);

    @Query("SELECT DISTINCT p.permissionCode FROM SysUser u JOIN u.roles r JOIN r.permissions p WHERE u.id = :userId")
    List<String> findPermissionCodesByUserId(@Param("userId") UUID userId);
}
