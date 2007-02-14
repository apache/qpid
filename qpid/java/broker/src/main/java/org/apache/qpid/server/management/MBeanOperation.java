package org.apache.qpid.server.management;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.management.MBeanOperationInfo;

/**
 * Annotation for MBean operations.
 * @author  Bhupendra Bhardwaj
 * @version 0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface MBeanOperation
{
    String name();
    String description();
    int impact() default MBeanOperationInfo.INFO;
}
