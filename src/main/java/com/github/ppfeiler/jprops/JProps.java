package com.github.ppfeiler.jprops;

import com.github.ppfeiler.jprops.annotation.Property;
import com.github.ppfeiler.jprops.annotation.PropertySource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.configuration2.CombinedConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.MergeCombiner;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

@Slf4j
public class JProps {
    private static final Class<PropertiesConfiguration> DEFAULT_CONFIGURATION_FILE_TYPE = PropertiesConfiguration.class;
    private static final MergeCombiner DEFAULT_COMBINER = new MergeCombiner();
    private static final String DEFAULT_CONFIG_FILE_NAME = "application";
    private static final String CONFIG_FILE_PREFIX = "application-";
    private static final String PROPERTIES_FILE_EXTENSION = ".properties";
    private static final char LIST_DELIMITER = ',';

    private final Configuration config;

    public JProps() {
        this(new JPropsConfiguration());
    }

    public JProps(final JPropsConfiguration configuration) {
        List<String> activeProfiles = getActiveProfiles(configuration);
        config = loadProperties(activeProfiles);
    }

    private List<String> getActiveProfiles(final JPropsConfiguration config) {
        List<String> activeProfiles = new ArrayList<>();
        if (config.isLoadProvileFromEnvironment()) {
            final String profilesFromEnv = Optional.ofNullable(System.getProperty(config.getProvileEnvironment()))
                    .orElse("");
            activeProfiles.addAll(Arrays.asList(profilesFromEnv.split(",")));
        }

        log.debug("Using profiles: {}", activeProfiles);
        return activeProfiles;
    }

    public String getString(final String key) {
        return config.getString(key);
    }

    public <T> T getObject(final Class<T> clazz) {
        if (!clazz.isAnnotationPresent(PropertySource.class)) {
            throw new IllegalArgumentException("Class must be annotated with @PropertySource");
        }
        return getObject(clazz.getAnnotation(PropertySource.class).value(), clazz);
    }

    public <T> T getObject(final String keyPrefix,
                           final Class<T> clazz) {
        try {
            final Configuration subset = config.subset(keyPrefix);
            final T newInstance = clazz.getConstructor().newInstance();
            for (final Iterator<String> it = subset.getKeys(); it.hasNext(); ) {
                final String attr = it.next();
                try {
                    final String fieldName = getFieldName(clazz, attr);
                    BeanUtils.setProperty(newInstance, fieldName, determineValue(subset, attr, clazz.getDeclaredField(fieldName).getType()));
                } catch (Exception e) {
                    log.warn("Could not set field: {}", attr);
                }
            }
            return newInstance;
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String getFieldName(final Class<?> clazz,
                                final String attr) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(attr).getName();
        } catch (final NoSuchFieldException e) {
            // dont to anything, just try to find a field annotated with @Property(key = attr)
        }

        for (final Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Property.class)
                    && field.getAnnotation(Property.class).value().equals(attr)) {
                return field.getName();
            }
        }

        throw new NoSuchFieldException(attr);
    }

    private <T> Object determineValue(final Configuration conf, final String attribute, final Class<T> type) throws Exception {
        // this is obviously limited.
        if (type.equals(List.class)) {
            return conf.getList(attribute);
        } else if (type.equals(Set.class)) {
            return new HashSet<>(conf.getList(attribute));
        }
        return conf.get(type, attribute);
    }

    private Configuration loadProperties(final List<String> profiles) {
        // There are different combiners that can be used: OverrideCombiner, MergeCombiner, UnionCombiner
        final CombinedConfiguration configuration = new CombinedConfiguration(DEFAULT_COMBINER);

        // Not very intuitive: First load the specific properties files and then add the default one
        final List<String> configurationsToLoad = //
                profiles.stream() //
                        .filter(profile -> !StringUtils.isBlank(profile)) //
                        .map(profile -> CONFIG_FILE_PREFIX + profile) //
                        .collect(collectingAndThen( //
                                toList(), //
                                confs -> { // this code runs AFTER the toList. So the "application" is added at the end
                                    confs.add(DEFAULT_CONFIG_FILE_NAME);
                                    return confs;
                                }));

        // for the sake of readability do a separate stream... but it could be attatched to the collect from above
        configurationsToLoad.stream() //
                .map(this::propertiesFileBuilder) //
                .map(this::safeGetConfiguration) //
                .filter(Objects::nonNull) //
                .forEach(configuration::addConfiguration);

        // useful when using docker where you may want to set environment variables in the docker-compose or docker run
        System.getenv().forEach((k, v) -> {
            if (configuration.containsKey(k)) {
                log.debug("Overriding property {} with value {}", k, v);
            }
            configuration.setProperty(k, v);
        });

        return configuration;
    }

    private PropertiesConfiguration safeGetConfiguration(final FileBasedConfigurationBuilder<PropertiesConfiguration> configurationBuilder) {
        try {
            return configurationBuilder.getConfiguration();
        } catch (final ConfigurationException e) {
            log.warn("Could not load configuration file: {} - skip it", configurationBuilder.getFileHandler().getFileName());
            return null;
        }
    }

    private FileBasedConfigurationBuilder<PropertiesConfiguration> propertiesFileBuilder(final String config) {
        // here you can choose between PropertiesConfiguration.class, YAMLConfiguration.class or XMLConfiguration.class!
        final var propertiesParams = new Parameters()//
                .fileBased() //
                .setFile(new File(config + PROPERTIES_FILE_EXTENSION)) //
                .setListDelimiterHandler(new DefaultListDelimiterHandler(LIST_DELIMITER));
        return new FileBasedConfigurationBuilder<>(DEFAULT_CONFIGURATION_FILE_TYPE).configure(propertiesParams);
    }
}
