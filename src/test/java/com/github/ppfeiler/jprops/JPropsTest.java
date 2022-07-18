package com.github.ppfeiler.jprops;

import com.github.ppfeiler.jprops.util.TestDataProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JPropsTest {

    @Test
    void canReadPropertiesFromFile() {
        final JProps jprops = new JProps();
        assertEquals("world", jprops.getString("hello"));
        assertEquals("world", jprops.getString("test-data.hello"));
        assertEquals("hello", jprops.getString("test-data.world"));
    }

    @Test
    void canReadPropertiesFromClass() {
        final JProps jprops = new JProps();
        final TestDataProperties testDataProperties = jprops.getObject(TestDataProperties.class);
        assertEquals("world", testDataProperties.getHello());
        assertEquals("hello", testDataProperties.getWorld());
    }

    @Test
    void canReadPropertiesFromClassWithPropertyAnnotation() {
        final JProps jprops = new JProps();
        final TestDataProperties testDataProperties = jprops.getObject(TestDataProperties.class);
        assertEquals("Hello, World", testDataProperties.getGreeting());
    }
}