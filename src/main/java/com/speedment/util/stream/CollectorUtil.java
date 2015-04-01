/**
 *
 * Copyright (c) 2006-2015, Speedment, Inc. All Rights Reserved.
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
package com.speedment.util.stream;

import static com.speedment.codegen.Formatting.lcfirst;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import static java.util.stream.Collector.Characteristics.CONCURRENT;
import static java.util.stream.Collectors.joining;
import java.util.stream.Stream;

/**
 *
 * @author pemi
 */
public class CollectorUtil {

    private static final String NULL_TEXT = " must not be null";
    
    public static <T> Collector<T, ?, String> toJson(Class<T> entityType) {
        return new Parser<>(CollectorUtil::toJson, l -> "[" + l.stream().collect(joining(", ")) + "]");
    }
    
    @SuppressWarnings("unchecked")
    public static <T> String toJson(T entity) {
        try {
            final Method m = entity.getClass().getMethod("toJSON");
            return (String) m.invoke(entity);
        } catch (NoSuchMethodException 
            | SecurityException 
            | IllegalAccessException 
            | IllegalArgumentException 
            | InvocationTargetException ex) {
            
            Logger.getLogger(CollectorUtil.class.getName()).log(Level.SEVERE, 
                "Could not parse entity to JSON. Make sure '" + 
                entity + "' is generated by Speedment.", ex
            );
            
            return null;
        }
    }

    private static class Parser<T> implements Collector<T, List<String>, String> {

        private final Function<T, String> converter;
        private final Function<List<String>, String> merger;
        
        public Parser(Function<T, String> converter, Function<List<String>, String> merger) {
            this.converter = converter;
            this.merger = merger;
        }
      
        @Override
        public Supplier<List<String>> supplier() {
            return () -> Collections.synchronizedList(new ArrayList<>());
        }

        @Override
        public BiConsumer<List<String>, T> accumulator() {
            return (l, t) -> {
                synchronized(l) {
                    l.add(converter.apply(t));
                }
             };
        }

        @Override
        public BinaryOperator<List<String>> combiner() {
            return (l1, l2) -> {
                synchronized(l1) {
                    l1.addAll(l2); 
                    return l1;
                }
            };
        }

        @Override
        public Function<List<String>, String> finisher() {
            return merger::apply;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(CONCURRENT);
        }
    }

    @SafeVarargs
    @SuppressWarnings({"unchecked", "varargs"})
    public static <T> T of(Supplier<T> supplier, Consumer<T> modifier, Consumer<T>... additionalModifiers) {
        Objects.requireNonNull(supplier, "supplier" + NULL_TEXT);
        Objects.requireNonNull(modifier, "modifier" + NULL_TEXT);
        final T result = supplier.get();
        modifier.accept(result);
        Stream.of(additionalModifiers).forEach((Consumer<T> c) -> {
            c.accept(result);
        });
        return result;
    }

    public static <I, T> T of(Supplier<I> supplier, Consumer<I> modifier, Function<I, T> finisher) {
        Objects.requireNonNull(supplier, "supplier" + NULL_TEXT);
        Objects.requireNonNull(modifier, "modifier" + NULL_TEXT);
        Objects.requireNonNull(finisher, "finisher" + NULL_TEXT);
        final I intermediateResult = supplier.get();
        modifier.accept(intermediateResult);
        return finisher.apply(intermediateResult);
    }

    public static <T> Collector<T, Set<T>, Set<T>> toUnmodifiableSet() {
        return Collector.of(HashSet::new, Set::add, (left, right) -> {
            left.addAll(right);
            return left;
        }, Collections::unmodifiableSet, Collector.Characteristics.UNORDERED);
    }
    
    @SafeVarargs
    @SuppressWarnings({"unchecked", "varargs"})
    public static <T> Set<T> unmodifiableSetOf(T... items) {
        return Stream.of(items).collect(toUnmodifiableSet());
    }

}
