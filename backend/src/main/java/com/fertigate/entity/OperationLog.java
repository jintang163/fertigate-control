package com.fertigate.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "sys_operation_logs")
public class OperationLog {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 50)
    private String username;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(length = 200)
    private String userAgent;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "success")
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "execution_time")
    private Long executionTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum OperationType {
        CREATE("create", "创建"),
        UPDATE("update", "更新"),
        DELETE("delete", "删除"),
        QUERY("query", "查询"),
        CONTROL("control", "控制"),
        EXPORT("export", "导出"),
        LOGIN("login", "登录"),
        LOGOUT("logout", "登出"),
        ACKNOWLEDGE("acknowledge", "确认"),
        OTHER("other", "其他");

        private final String code;
        private final String description;

        OperationType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}
