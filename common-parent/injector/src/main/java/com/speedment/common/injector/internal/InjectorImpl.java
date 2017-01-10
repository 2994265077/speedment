/**
 *
 * Copyright (c) 2006-2016, Speedment, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.speedment.common.injector.internal;

import com.speedment.common.injector.InjectBundle;
import com.speedment.common.injector.Injector;
import com.speedment.common.injector.State;
import com.speedment.common.injector.annotation.Config;
import com.speedment.common.injector.annotation.Inject;
import com.speedment.common.injector.annotation.InjectKey;
import com.speedment.common.injector.annotation.WithState;
import com.speedment.common.injector.exception.NoDefaultConstructorException;
import com.speedment.common.injector.internal.dependency.DependencyGraph;
import com.speedment.common.injector.internal.dependency.DependencyNode;
import com.speedment.common.injector.internal.dependency.Execution;
import com.speedment.common.injector.internal.dependency.impl.DependencyGraphImpl;
import static com.speedment.common.injector.internal.util.ReflectionUtil.traverseAncestors;
import static com.speedment.common.injector.internal.util.ReflectionUtil.traverseFields;
import com.speedment.common.logger.Level;
import com.speedment.common.logger.Logger;
import com.speedment.common.logger.LoggerManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;

/**
 * The default implementation of the {@link Injector} interface.
 *
 * @author Emil Forslund
 * @since  3.0.0
 */
public final class InjectorImpl implements Injector {

    private final static Logger LOGGER = 
        LoggerManager.getLogger(InjectorImpl.class);

    private final static State[] STATES = State.values();
    private final Set<Class<?>> injectables;
    private final List<Object> instances;
    private final Injector.Builder builder;

    private InjectorImpl(
            Set<Class<?>> injectables, 
            List<Object> instances, 
            Injector.Builder builder) {
        
        this.injectables = requireNonNull(injectables);
        this.instances   = requireNonNull(instances);
        this.builder     = requireNonNull(builder);
    }

    @Override
    public <T> Stream<T> stream(Class<T> type) {
        return findAll(type);
    }

    @Override
    public <T> T getOrThrow(Class<T> type) throws IllegalArgumentException {
        return find(type, true);
    }

    @Override
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(find(type, false));
    }

    @Override
    public Stream<Class<?>> injectables() {
        return injectables.stream();
    }

    @Override
    public <T> T inject(T instance) {
        injectFields(instance);
        return instance;
    }

    @Override
    public void stop() {
        final DependencyGraph graph = DependencyGraphImpl.create(injectables);

        final AtomicBoolean hasAnythingChanged = new AtomicBoolean();

        // Loop until all nodes have been started.
        Set<DependencyNode> unfinished;
        while (!(unfinished = graph.nodes()
            .filter(n -> n.getCurrentState() != State.STOPPED)
            .collect(toSet())).isEmpty()) {

            hasAnythingChanged.set(false);

            unfinished.forEach(n -> {

                // Check if all its dependencies have been satisfied.
                // TODO: Dependencies should be resolved in the opposite order 
                // when stopping.
                if (n.canBe(State.STOPPED)) {

                    printLine();

                    // Retreive the instance for that node
                    final Object instance = find(n.getRepresentedType(), true);

                    // Execute all the executions for the next step.
                    n.getExecutions().stream()
                        .filter(e -> e.getState() == State.STOPPED)
                        .map(Execution::getMethod)
                        .forEach(m -> {
                            final Object[] params = Stream.of(m.getParameters())
                                .map(p -> find(
                                    p.getType(), 
                                    p.getAnnotation(WithState.class) != null
                                ))
                                .toArray(Object[]::new);

                            m.setAccessible(true);

                            final String shortMethodName
                                = n.getRepresentedType().getSimpleName() + "#"
                                + m.getName() + "("
                                + Stream.of(m.getParameters())
                                .map(p -> p.getType()
                                    .getSimpleName().substring(0, 1)
                                )
                                .collect(joining(", ")) + ")";

                            LOGGER.debug(String.format(
                                "| -> %-76s |", shortMethodName));

                            try {
                                m.invoke(instance, params);
                            } catch (final IllegalAccessException 
                                         | IllegalArgumentException 
                                         | InvocationTargetException ex) {

                                throw new RuntimeException(ex);
                            }
                        });

                    // Update its state to the new state.
                    n.setState(State.STOPPED);
                    hasAnythingChanged.set(true);

                    LOGGER.debug(String.format(
                        "| %-66s %12s |",
                        n.getRepresentedType().getSimpleName(),
                        State.STOPPED.name()
                    ));
                }
            });

            if (!hasAnythingChanged.get()) {
                throw new IllegalStateException(
                    "Injector appears to be stuck in an infinite loop. The " + 
                    "following componenets have not been stopped: " + 
                    unfinished.stream()
                        .map(DependencyNode::getRepresentedType)
                        .map(Class::getSimpleName)
                        .collect(toSet())
                );
            }
        }
    }

    @Override
    public Injector.Builder newBuilder() {
        return builder;
    }
    
    private <T> Stream<T> findAll(Class<T> type) {
        return findAll(type, this, instances);
    }

    private <T> T find(Class<T> type, boolean required) {
        return findIn(type, this, instances, required);
    }
    
    private static <T> Stream<T> findAll(
            Class<T> type, 
            Injector injector, 
            List<Object> instances) {
        
        if (Injector.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            final T casted = (T) injector;
            return Stream.of(casted);
        }

        return instances.stream()
            .filter(inst -> type.isAssignableFrom(inst.getClass()))
            .map(type::cast);
    }

    private static <T> T findIn(
            Class<T> type, 
            Injector injector, 
            List<Object> instances, 
            boolean required) {
        
        final Optional<T> found = findAll(type, injector, instances)
            .findFirst(); // Order is important.

        if (required) {
            return found.orElseThrow(() -> 
                new IllegalArgumentException(
                    "Could not find any installed implementation of " + 
                    type.getName() + "."
                )
            );
        } else {
            return found.orElse(null);
        }
    }

    private static void printLine() {
        LOGGER.debug("+------------------------------------------------------" + 
            "---------------------------+");
    }

    private static String limit(String in, int length) {
        if (in.length() < length) {
            return in;
        } else {
            final int breakpoint = (length - 3) / 2;
            return in.substring(0, breakpoint) + 
                "..." + in.substring(length - breakpoint - 3);
        }
    }

    private <T> void injectFields(T instance) {
        requireNonNull(instance);
        
        traverseFields(instance.getClass())
            .filter(f -> f.isAnnotationPresent(Inject.class))
            .distinct()
            .forEachOrdered(field -> {
                final Object value;

                if (Injector.class.isAssignableFrom(field.getType())) {
                    value = this;
                } else {
                    value = find(
                        field.getType(), 
                        field.getAnnotation(WithState.class) != null
                    );
                }

                field.setAccessible(true);

                try {
                    field.set(instance, value);
                } catch (final IllegalAccessException ex) {
                    final String err = "Could not access field '" + 
                        field.getName() +
                        "' in class '" + value.getClass().getName() +
                        "' of type '" + field.getType() + "'.";
                    LOGGER.error(ex, err);
                    throw new RuntimeException(err, ex);
                }
            });
    }

    public static Injector.Builder builder() {
        return new Builder();
    }

    private final static class Builder implements Injector.Builder {

        private final Map<String, List<Class<?>>> injectables;
        private final Map<String, String> overriddenParams;
        private Path configFileLocation;

        private Builder() {
            this(Collections.emptySet());
        }

        private Builder(Set<Class<?>> injectables) {
            requireNonNull(injectables);
            
            this.injectables        = new LinkedHashMap<>();
            this.overriddenParams   = new HashMap<>();
            this.configFileLocation = Paths.get("settings.properties");

            injectables.forEach(this::put);
        }

        @Override
        public Builder put(Class<?> injectableType) {
            requireNonNull(injectableType);

            // Store the injectable under every superclass in the map, as well
            // as under every inherited InjectorKey value.
            traverseAncestors(injectableType)
                
                // only include classes that has an ancestor with the 
                // InjectorKey-annotation, or that are the original class.
                .filter(c -> c == injectableType || traverseAncestors(c)
                    .anyMatch(c2 -> c2.isAnnotationPresent(InjectKey.class))
                )
                
                .forEachOrdered(c -> {
                    // Store it under the class name itself
                    appendInjectable(c.getName(), injectableType, true);

                    // Include InjectorKey value
                    if (c.isAnnotationPresent(InjectKey.class)) {
                        final InjectKey key = c.getAnnotation(InjectKey.class);
                        appendInjectable(
                            key.value().getName(), 
                            injectableType, 
                            key.overwrite()
                        );
                    }
                });

            return this;
        }

        @Override
        public Builder put(String key, Class<?> injectableType) 
                throws NoDefaultConstructorException {
            
            requireNonNull(key);
            requireNonNull(injectableType);
            
            appendInjectable(key, injectableType, true);

            return this;
        }

        @Override
        public Builder putInBundle(Class<? extends InjectBundle> bundleClass) {
            try {
                final InjectBundle bundle = bundleClass.newInstance();
                bundle.injectables().forEach(this::put);
            } catch (IllegalAccessException | InstantiationException e) {
                throw new NoDefaultConstructorException(e);
            }
            return this;
        }

        @Override
        public Builder withConfigFileLocation(Path configFile) {
            this.configFileLocation = requireNonNull(configFile);
            return this;
        }

        @Override
        public Builder putParam(String key, String value) {
            overriddenParams.put(key, value);
            return this;
        }

        @Override
        public Injector build() 
        throws InstantiationException, NoDefaultConstructorException {
            
            // Load settings
            final File configFile = configFileLocation.toFile();
            final Properties properties = loadProperties(configFile);
            overriddenParams.forEach(properties::setProperty);

            final Set<Class<?>> injectablesSet = unmodifiableSet(
                injectables.values().stream()
                    .flatMap(List::stream)
                    .collect(toCollection(() -> new LinkedHashSet<>()))
            );

            final DependencyGraph graph = 
                DependencyGraphImpl.create(injectablesSet);
            
            final LinkedList<Object> instances = new LinkedList<>();

            LOGGER.debug("Creating " + injectablesSet.size() + 
                " injectable instances.");
            
            printLine();

            // Create an instance of every injectable type
            for (final Class<?> injectable : injectablesSet) {

                // If we are currently debugging, print out every created
                // instance and which configuration options are available for
                // it.
                if (LOGGER.getLevel().isEqualOrLowerThan(Level.DEBUG)) {
                    LOGGER.debug(String.format("| %-71s CREATED |", 
                        limit(injectable.getSimpleName(), 71)
                    ));

                    traverseFields(injectable)
                        .filter(f -> f.isAnnotationPresent(Config.class))
                        .map(f -> f.getAnnotation(Config.class))
                        .map(a -> String.format(
                            "|     %-48s %26s |", 
                            limit(a.name(), 48),
                            limit(properties.containsKey(a.name())
                                ? properties.get(a.name()).toString()
                                : a.value(), 26
                            )
                        ))
                        .forEachOrdered(LOGGER::debug);

                    printLine();
                }

                final Object instance = newInstance(injectable, properties);
                instances.addFirst(instance);
            }

            // Build the Injector
            final Injector injector = new InjectorImpl(
                injectablesSet,
                unmodifiableList(instances),
                this
            );

            // Set the auto-injected fields
            instances.forEach(instance -> traverseFields(instance.getClass())
                .filter(f -> f.isAnnotationPresent(Inject.class))
                .distinct()
                .forEachOrdered(field -> {
                    final Object value;

                    if (Inject.class.isAssignableFrom(field.getType())) {
                        value = injector;
                    } else {
                        value = findIn(
                            field.getType(),
                            injector,
                            instances, 
                            field.getAnnotation(WithState.class) != null
                        );
                    }

                    field.setAccessible(true);

                    try {
                        field.set(instance, value);
                    } catch (final IllegalAccessException ex) {
                        throw new RuntimeException(
                            "Could not access field '" + field.getName()
                                + "' in class '" + value.getClass().getName()
                                + "' of type '" + field.getType()
                                + "'.", ex
                        );
                    }
                })
            );

            final AtomicBoolean hasAnythingChanged = new AtomicBoolean();
            final AtomicInteger nextState = new AtomicInteger(0);

            // Loop until all nodes have been started.
            Set<DependencyNode> unfinished;

            // Go through every state up and including STARTED.
            while (nextState.get() <= State.STARTED.ordinal()) {

                // Get a set of the nodes that has not yet reached that state,
                // and operate upon it until it is empty
                while (!(unfinished = graph.nodes()
                    .filter(n -> n.getCurrentState().ordinal() < nextState.get())
                    .collect(toSet())).isEmpty()) {

                    hasAnythingChanged.set(false);

                    unfinished.forEach(n -> {
                        // Determine the next state of this node.
                        final State state = STATES[
                            n.getCurrentState().ordinal() + 1
                        ];

                        // Check if all its dependencies have been satisfied.
                        if (n.canBe(state)) {

                            printLine();

                            // Retreive the instance for that node
                            final Object instance = findIn(
                                n.getRepresentedType(), 
                                injector, 
                                instances, 
                                true
                            );

                            // Execute all the executions for the next step.
                            n.getExecutions().stream()
                                .filter(e -> e.getState() == state)
                                .map(Execution::getMethod)
                                .forEach(m -> {
                                    final Object[] params = 
                                        Stream.of(m.getParameters())
                                        .map(p -> findIn(
                                            p.getType(), 
                                            injector, 
                                            instances, 
                                            p.getAnnotation(WithState.class) 
                                                != null
                                        )).toArray(Object[]::new);

                                    m.setAccessible(true);

                                    // We might want to log exactly which steps we have
                                    // completed.
                                    if (LOGGER.getLevel().isEqualOrLowerThan(Level.DEBUG)) {
                                        final String shortMethodName
                                            = n.getRepresentedType().getSimpleName() + "#"
                                            + m.getName() + "("
                                            + Stream.of(m.getParameters())
                                            .map(p -> p.getType().getSimpleName().substring(0, 1))
                                            .collect(joining(", ")) + ")";

                                        LOGGER.debug(String.format(
                                            "| -> %-76s |", 
                                            limit(shortMethodName, 76)
                                        ));
                                    }

                                    try {
                                        m.invoke(instance, params);
                                    } catch (final IllegalAccessException 
                                                 | IllegalArgumentException 
                                                 | InvocationTargetException ex) {

                                        throw new RuntimeException(ex);
                                    }
                                });

                            // Update its state to the new state.
                            n.setState(state);
                            hasAnythingChanged.set(true);

                            LOGGER.debug(String.format(
                                "| %-66s %12s |",
                                limit(n.getRepresentedType().getSimpleName(), 66),
                                limit(state.name(), 12)
                            ));
                        }
                    });

                    // The set was not empty when we entered the 'while' clause, 
                    // and yet nothing has changed. This means that we are stuck
                    // in an infinite loop.
                    if (!hasAnythingChanged.get()) {
                        throw new IllegalStateException(
                            "Injector appears to be stuck in an infinite loop."
                        );
                    }
                }

                // Every node has reached the desired state. 
                // Begin working with the next state.
                nextState.incrementAndGet();
            }

            printLine();
            LOGGER.debug(String.format(
                "| %-79s |",
                "All " + instances.size() + " components have been configured!"
            ));
            printLine();

            return injector;
        }
        
        private void appendInjectable(String key, Class<?> clazz, boolean overwrite) {
            final List<Class<?>> list = Optional.ofNullable(
                injectables.remove(key)
            ).orElseGet(LinkedList::new);
            
            if (overwrite) {
                list.clear();
            }
            
            list.add(clazz);
            injectables.put(key, list);
        }
        
        private static Properties loadProperties(File configFile) {
            final Properties properties = new Properties();
            if (configFile.exists() && configFile.canRead()) {

                try (final InputStream in = new FileInputStream(configFile)) {
                    properties.load(in);
                } catch (final IOException ex) {
                    final String err = "Error loading default settings from "
                        + configFile.getAbsolutePath() + "-file.";
                    LOGGER.error(ex, err);
                    throw new RuntimeException(err, ex);
                }
            } else {
                LOGGER.info(
                    "No configuration file '"
                    + configFile.getAbsolutePath() + "' found."
                );
            }

            return properties;
        }

        private static <T> T newInstance(Class<T> type, Properties properties) 
        throws InstantiationException, NoDefaultConstructorException {
            try {
                final Constructor<T> constr = type.getDeclaredConstructor();
                constr.setAccessible(true);
                final T instance = constr.newInstance();

                traverseFields(type)
                    .filter(f -> f.isAnnotationPresent(Config.class))
                    .forEach(f -> {
                        final Config config = f.getAnnotation(Config.class);

                        final String serialized;
                        if (properties.containsKey(config.name())) {
                            serialized = properties.getProperty(config.name());
                        } else {
                            serialized = config.value();
                        }

                        f.setAccessible(true);

                        try {
                            if (boolean.class == f.getType() 
                            || Boolean.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Boolean.parseBoolean(serialized));
                            } else if (byte.class == f.getType() 
                            || Byte.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Byte.parseByte(serialized));
                            } else if (short.class == f.getType() 
                            || Short.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Short.parseShort(serialized));
                            } else if (int.class == f.getType() 
                            || Integer.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Integer.parseInt(serialized));
                            } else if (long.class == f.getType() 
                            || Long.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Long.parseLong(serialized));
                            } else if (float.class == f.getType() 
                            || Float.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Float.parseFloat(serialized));
                            } else if (double.class == f.getType() 
                            || Double.class.isAssignableFrom(f.getType())) {
                                f.set(instance, Double.parseDouble(serialized));
                            } else if (String.class.isAssignableFrom(f.getType())) {
                                f.set(instance, serialized);
                            } else if (char.class == f.getType() 
                            || Character.class.isAssignableFrom(f.getType())) {
                                if (serialized.length() == 1) {
                                    f.set(instance, serialized.charAt(0));
                                } else {
                                    throw new IllegalArgumentException(
                                        "Value '" + serialized
                                        + "' is to long to be parsed into a field of type '"
                                        + f.getType().getName() + "'."
                                    );
                                }
                            } else if (File.class.isAssignableFrom(f.getType())) {
                                f.set(instance, new File(serialized));
                            } else if (URL.class.isAssignableFrom(f.getType())) {
                                try {
                                    f.set(instance, new URL(serialized));
                                } catch (final MalformedURLException ex) {
                                    throw new IllegalArgumentException(
                                        "Specified URL '" + serialized + "' is malformed.", ex
                                    );
                                }
                            }
                        } catch (final IllegalAccessException | IllegalArgumentException ex) {
                            throw new RuntimeException(
                                "Failed to set config parameter '" + config.name()
                                + "' in class '" + type.getName() + "'.", ex
                            );
                        }
                    });

                return instance;

            } catch (final NoSuchMethodException ex) {
                throw new NoDefaultConstructorException(
                    "Could not find any default constructor for class '" + type.getName() + "'.", ex
                );

            } catch (final IllegalAccessException 
                         | IllegalArgumentException 
                         | InvocationTargetException ex) {

                throw new RuntimeException(ex);
            }
        }
    }
}
