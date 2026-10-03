package com.fitsync.web.timezone;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method argument of type {@code Optional<ZoneId>} that is filled from the
 * {@code X-Timezone} request header. The value is empty when the header is absent or invalid.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestTimezone {
}
