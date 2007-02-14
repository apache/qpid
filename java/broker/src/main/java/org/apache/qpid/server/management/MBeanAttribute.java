package org.apache.qpid.server.management;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for MBean attributes. This should be used with getter or setter
 * methods of attributes.
 * @author  Bhupendra Bhardwaj
 * @version 0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface MBeanAttribute
{
    String name();
    String description();
}
