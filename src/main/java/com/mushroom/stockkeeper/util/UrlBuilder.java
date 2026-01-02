package com.mushroom.stockkeeper.util;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component("urlBuilder")
public class UrlBuilder {

    public String replaceParam(String key, Object value) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam(key, value)
                .toUriString();
    }

    public String replaceParam(String key, Object value, String key2, Object value2) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam(key, value)
                .replaceQueryParam(key2, value2)
                .toUriString();
    }
}
