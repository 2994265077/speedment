/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.speedment.runtime.compute;

import static com.speedment.runtime.compute.TestUtil.strings;
import com.speedment.runtime.compute.expression.ExpressionType;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Per Minborg
 */
public final class ToByteTest extends AbstractToTest<ToByte<String>> {

    public ToByteTest() {
        super(ExpressionType.BYTE);
    }

    @Override
    ToByte<String> create() {
        return s -> (byte) s.length();
    }

    @Test
    public void testApplyAsInt() {
        strings().forEach(s -> {
            final long actual = mapper.apply(s);
            final long expected = instance.applyAsByte(s);
            assertEquals(expected, actual);
        });
    }

    @Test
    public void testMapToDouble() {
        strings().forEach(s -> {
            final double expected = mapper.apply(s).doubleValue() + 1.0;
            final ToDouble<String> toDouble = instance.mapToDouble(l -> l + 1);
            final double actual = toDouble.applyAsDouble(s);
            assertEquals(expected, actual, EPSILON);
        });
    }

    @Test
    public void testMap() {
        strings().forEach(s -> {
            final double expected = mapper.apply(s).doubleValue() + 1.0;
            final ToByte<String> to = instance.map(l -> (byte) (l + 1));
            final double actual = to.applyAsByte(s);
            assertEquals(expected, actual, EPSILON);
        });
    }

    @Test
    public void testCompose() {
        strings().forEach(s -> {
            final ToByte<String> composed = instance.compose(str -> str + "A");
            assertEquals((long) mapper.apply(s + "A"), composed.applyAsByte(s));
        });
    }

}