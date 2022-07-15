package com.github.ppfeiler.jprops.util;

import com.github.ppfeiler.jprops.annotation.Property;
import com.github.ppfeiler.jprops.annotation.PropertySource;
import lombok.Data;

@Data
@PropertySource("test-data")
public class TestDataProperties {
    private String hello;
    private String world;

    @Property("message")
    private String greeting;
}
