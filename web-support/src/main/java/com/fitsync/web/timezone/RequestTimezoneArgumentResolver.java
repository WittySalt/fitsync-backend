package com.fitsync.web.timezone;

import java.time.ZoneId;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@code @RequestTimezone Optional<ZoneId>} arguments from the X-Timezone header. */
public class RequestTimezoneArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestTimezone.class)
                && Optional.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Optional<ZoneId> zone = ClientZoneHeader.parse(webRequest.getHeader(ClientZoneHeader.NAME));
        return zone;
    }
}
