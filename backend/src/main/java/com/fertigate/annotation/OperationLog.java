package com.fertigate.annotation;

import com.fertigate.entity.OperationLog;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    String operation();

    OperationLog.OperationType type() default OperationLog.OperationType.OTHER;

    String description() default "";

    String targetType() default "";

    boolean recordParams() default true;

    boolean recordResult() default true;
}
