package com.fertigate.aspect;

import com.fertigate.annotation.OperationLog;
import com.fertigate.service.OperationLogService;
import com.fertigate.entity.OperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

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
                        oldValue = objectMapper.writeValueAsString(args);
                    } catch (Exception e) {
                        oldValue = "参数序列化失败";
                    }
                }
                if (args != null) {
                    for (Object arg : args) {
                        if (arg instanceof java.util.UUID || arg instanceof String) {
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
    }
