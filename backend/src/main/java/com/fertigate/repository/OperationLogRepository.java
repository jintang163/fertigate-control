package com.fertigate.repository;

import com.fertigate.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, UUID> {

    Page<OperationLog> findByUsername(String username, Pageable pageable);

    Page<OperationLog> findByOperationType(String operationType, Pageable pageable);

    Page<OperationLog> findByTargetType(String targetType, Pageable pageable);

    Page<OperationLog> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT o FROM OperationLog o WHERE o.createdAt BETWEEN :startTime AND :endTime")
    Page<OperationLog> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       Pageable pageable);

    @Query("SELECT o FROM OperationLog o WHERE " +
           "(:username IS NULL OR o.username LIKE %:username%) AND " +
           "(:operationType IS NULL OR o.operationType = :operationType) AND " +
           "(:targetType IS NULL OR o.targetType = :targetType) AND " +
           "(:success IS NULL OR o.success = :success) AND " +
           "o.createdAt BETWEEN :startTime AND :endTime")
    Page<OperationLog> findByConditions(@Param("username") String username,
                                        @Param("operationType") String operationType,
                                        @Param("targetType") String targetType,
                                        @Param("success") Boolean success,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        Pageable pageable);

    @Query("SELECT o.operationType, COUNT(o) FROM OperationLog o " +
           "WHERE o.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY o.operationType")
    List<Object[]> countByOperationType(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    @Query("SELECT o.username, COUNT(o) FROM OperationLog o " +
           "WHERE o.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY o.username ORDER BY COUNT(o) DESC LIMIT 10")
    List<Object[]> countByUser(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);
}
