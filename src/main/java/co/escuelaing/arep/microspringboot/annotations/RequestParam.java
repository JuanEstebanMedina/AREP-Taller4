package co.escuelaing.arep.microspringboot.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
/**
 *
 * @author juan.medina-r
 */
public @interface RequestParam {
    public String value() default "Default";

    public String defaultValue() default "Default";
}
