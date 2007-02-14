package org.apache.qpid.server.management;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for MBean operation parameters.
 * @author  Bhupendra Bhardwaj
 * @version 0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MBeanOperationParameter {
    String name();
    String description();
}
