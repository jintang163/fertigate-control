package com.fertigate.repository;

import com.fertigate.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, UUID> {

    Optional<SysRole> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);
}
