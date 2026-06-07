package com.fertigate.service;

import com.fertigate.dto.OperationLogDTO;
import com.fertigate.entity.OperationLog;
import com.fertigate.entity.SysUser;
import com.fertigate.repository.OperationLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;
    private final AuthService authService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OperationLog createLog(String operation, OperationLog.OperationType type, String description,
                                  String targetType, String targetId, String oldValue, String newValue,
                                  Boolean success, String errorMessage, Long executionTime) {
        OperationLog log = new OperationLog();

        SysUser currentUser = authService.getCurrentUser().orElse(null);
        if (currentUser != null) {
            log.setUserId(currentUser.getId());
            log.setUsername(currentUser.getUsername());
            log.setRealName(currentUser.getRealName());
        } else {
            log.setUsername("anonymous");
            log.setRealName("匿名用户");
        }

        log.setOperation(operation);
        log.setOperationType(type.getCode());
        log.setDescription(description);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setIpAddress(getClientIp());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setRequestUri(request.getRequestURI());
        log.setRequestMethod(request.getMethod());
        log.setSuccess(success != null ? success : true);
        log.setErrorMessage(errorMessage);
        log.setExecutionTime(executionTime);

        return operationLogRepository.save(log);
    }

    public Page<OperationLogDTO> getLogs(String username, String operationType, String targetType,
                                         Boolean success, LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();

        return operationLogRepository.findByConditions(username, operationType, targetType, success, start, end, pageable)
                .map(OperationLogDTO::fromEntity);
    }

    public OperationLogDTO getLogById(UUID id) {
        return operationLogRepository.findById(id)
                .map(OperationLogDTO::fromEntity)
                .orElseThrow(() -> new RuntimeException("日志不存在"));
    }

    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();

        Map<String, Object> stats = new HashMap<>();

        long totalCount = operationLogRepository.count();
        stats.put("totalCount", totalCount);

        List<Object[]> typeCounts = operationLogRepository.countByOperationType(start, end);
        Map<String, Long> typeStats = new HashMap<>();
        for (Object[] row : typeCounts) {
            typeStats.put((String) row[0], (Long) row[1]);
        }
        stats.put("operationTypeStats", typeStats);

        List<Object[]> userCounts = operationLogRepository.countByUser(start, end);
        List<Map<String, Object>> userStats = userCounts.stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("username", row[0]);
                    map.put("count", row[1]);
                    return map;
                })
                .collect(Collectors.toList());
        stats.put("userStats", userStats);

        return stats;
    }

    @Transactional
    public void deleteOldLogs(LocalDateTime beforeTime) {
        operationLogRepository.findAll().stream()
                .filter(log -> log.getCreatedAt().isBefore(beforeTime))
                .forEach(operationLogRepository::delete);
    }

    public String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    private String getClientIp() {
        try {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
