package com.fertigate.aspect;

import com.fertigate.annotation.OperationLog;
import com.fertigate.service.OperationLogService;
import com.fertigate.entity.OperationLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(Set.of(
            "password", "pwd", "secret", "token", "key", "credentials", "authorization"
    ));

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        Object result = null;

        String oldValue = null;
        String newValue = null;
        String targetId = null;

        try {
            if (operationLog.recordParams()) {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    try {
                        oldValue = maskSensitiveFields(args);
                    } catch (Exception e) {
                        oldValue = "参数序列化失败";
                    }
                }
                if (args != null) {
                    for (Object arg : args) {
                        if (arg instanceof UUID || arg instanceof String) {
                            targetId = String.valueOf(arg);
                            break;
                        }
                    }
                }
            }

            result = joinPoint.proceed();

            if (operationLog.recordResult() && result != null) {
                try {
                    newValue = objectMapper.writeValueAsString(result);
                } catch (Exception e) {
                    newValue = "结果序列化失败";
                }
            }

            return result;
        } catch (Throwable e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            operationLogService.createLog(
                    operationLog.operation(),
                    operationLog.type(),
                    operationLog.description(),
                    operationLog.targetType(),
                    targetId,
                    oldValue,
                    newValue,
                    success,
                    errorMessage,
                    executionTime
            );
        }
    }

    private String maskSensitiveFields(Object[] args) throws Exception {
        JsonNode rootNode = objectMapper.valueToTree(args);
        maskSensitiveFieldsRecursive(rootNode);
        return objectMapper.writeValueAsString(rootNode);
    }

    private void maskSensitiveFieldsRecursive(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            var fieldNames = objectNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (SENSITIVE_FIELDS.contains(fieldName.toLowerCase())) {
                    objectNode.put(fieldName, "******");
                } else {
                    maskSensitiveFieldsRecursive(objectNode.get(fieldName));
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                maskSensitiveFieldsRecursive(arrayNode.get(i));
            }
        }
    }
}
