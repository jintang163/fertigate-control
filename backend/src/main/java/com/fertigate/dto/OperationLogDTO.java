package com.fertigate.dto;

import com.fertigate.entity.OperationLog;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OperationLogDTO {

    private UUID id;
    private UUID userId;
    private String username;
    private String realName;
    private String operation;
    private String operationType;
    private String operationTypeDesc;
    private String description;
    private String targetType;
    private String targetId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private String requestUri;
    private String requestMethod;
    private Boolean success;
    private String errorMessage;
    private Long executionTime;
    private LocalDateTime createdAt;

    public static OperationLogDTO fromEntity(OperationLog log) {
        OperationLogDTO dto = new OperationLogDTO();
        dto.setId(log.getId());
        dto.setUserId(log.getUserId());
        dto.setUsername(log.getUsername());
        dto.setRealName(log.getRealName());
        dto.setOperation(log.getOperation());
        dto.setOperationType(log.getOperationType());
        try {
            dto.setOperationTypeDesc(OperationLog.OperationType.valueOf(log.getOperationType().toUpperCase()).getDescription());
        } catch (Exception e) {
            dto.setOperationTypeDesc(log.getOperationType());
        }
        dto.setDescription(log.getDescription());
        dto.setTargetType(log.getTargetType());
        dto.setTargetId(log.getTargetId());
        dto.setOldValue(log.getOldValue());
        dto.setNewValue(log.getNewValue());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setRequestUri(log.getRequestUri());
        dto.setRequestMethod(log.getRequestMethod());
        dto.setSuccess(log.getSuccess());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setExecutionTime(log.getExecutionTime());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
