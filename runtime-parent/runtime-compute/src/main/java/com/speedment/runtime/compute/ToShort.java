package com.speedment.runtime.compute;

import com.speedment.common.function.BooleanUnaryOperator;
import com.speedment.common.function.ShortToDoubleFunction;
import com.speedment.common.function.ShortUnaryOperator;
import com.speedment.common.function.ToShortFunction;
import com.speedment.runtime.compute.expression.Expression;
import com.speedment.runtime.compute.expression.ExpressionType;
import com.speedment.runtime.compute.expression.Expressions;
import com.speedment.runtime.compute.internal.expression.CastUtil;
import com.speedment.runtime.compute.internal.expression.ComposedUtil;
import com.speedment.runtime.compute.internal.expression.MapperUtil;
import com.speedment.runtime.compute.trait.*;

import java.util.function.Function;

/**
 * Expression that given an entity returns a {@code short} value. This
 * expression can be implemented using a lambda, or it can be a result of
 * another operation. It has additional methods for operating on it.
 *
 * @param <T> type to extract from
 *
 * @see ToShortFunction
 *
 * @author Emil Forslund
 * @since 3.1.0
 */
@FunctionalInterface
public interface ToShort<T>
extends Expression<T>,
        ToShortFunction<T>,
        HasAsDouble<T>,
        HasAsInt<T>,
        HasAsLong<T>,
        HasAbs<ToShort<T>>,
        HasSign<ToByte<T>>,
        HasSqrt<ToDouble<T>>,
        HasNegate<ToShort<T>>,
        HasPow<T>,
        HasPlus<T, ToInt<T>, ToInt<T>, ToLong<T>>,
        HasMinus<T, ToInt<T>, ToInt<T>, ToLong<T>>,
        HasMultiply<T, ToInt<T>, ToInt<T>, ToLong<T>>,
        HasDivide<T>,
        HasMap<T, ShortUnaryOperator, ToShort<T>>,
        HasHash<T>,
        HasCompare<T> {

    @Override
    short applyAsShort(T object);

    @Override
    default ExpressionType expressionType() {
        return ExpressionType.SHORT;
    }

    @Override
    default ToDouble<T> asDouble() {
        return CastUtil.castToDouble(this);
    }

    @Override
    default ToInt<T> asInt() {
        return CastUtil.castToInt(this);
    }

    @Override
    default ToLong<T> asLong() {
        return CastUtil.castToLong(this);
    }

    default ToDouble<T> mapToDouble(ShortToDoubleFunction operator) {
        return MapperUtil.mapToDouble(this, operator);
    }

    @Override
    default ToShort<T> map(ShortUnaryOperator operator) {
        return MapperUtil.map(this, operator);
    }

    @Override
    default ToShort<T> abs() {
        return Expressions.abs(this);
    }

    @Override
    default ToByte<T> sign() {
        return Expressions.sign(this);
    }

    @Override
    default ToDouble<T> sqrt() {
        return Expressions.sqrt(this);
    }

    @Override
    default ToDouble<T> pow(int power) {
        return Expressions.pow(this, power);
    }

    @Override
    default ToDouble<T> pow(double power) {
        return Expressions.pow(this, power);
    }

    @Override
    default ToDouble<T> pow(ToInt<T> power) {
        return Expressions.pow(this, power);
    }

    @Override
    default ToDouble<T> pow(ToDouble<T> power) {
        return Expressions.pow(this, power);
    }

    @Override
    default ToInt<T> plus(byte other) {
        return Expressions.plus(this, other);
    }

    @Override
    default ToInt<T> plus(ToByte<T> other) {
        return Expressions.plus(this.asInt(), other);
    }

    @Override
    default ToInt<T> plus(int other) {
        return Expressions.plus(this.asInt(), other);
    }

    @Override
    default ToInt<T> plus(ToInt<T> other) {
        return Expressions.plus(this.asInt(), other);
    }

    @Override
    default ToLong<T> plus(long other) {
        return Expressions.plus(this, other);
    }

    @Override
    default ToLong<T> plus(ToLong<T> other) {
        return Expressions.plus(this.asLong(), other);
    }

    @Override
    default ToDouble<T> plus(double other) {
        return Expressions.plus(this.asDouble(), other);
    }

    @Override
    default ToDouble<T> plus(ToDouble<T> other) {
        return Expressions.plus(this.asDouble(), other);
    }

    @Override
    default ToInt<T> minus(byte other) {
        return Expressions.minus(this, other);
    }

    @Override
    default ToInt<T> minus(ToByte<T> other) {
        return Expressions.minus(this.asInt(), other);
    }

    @Override
    default ToInt<T> minus(int other) {
        return Expressions.minus(this.asInt(), other);
    }

    @Override
    default ToInt<T> minus(ToInt<T> other) {
        return Expressions.minus(this.asInt(), other);
    }

    @Override
    default ToLong<T> minus(long other) {
        return Expressions.minus(this, other);
    }

    @Override
    default ToLong<T> minus(ToLong<T> other) {
        return Expressions.minus(this.asLong(), other);
    }

    @Override
    default ToDouble<T> minus(double other) {
        return Expressions.minus(this.asDouble(), other);
    }

    @Override
    default ToDouble<T> minus(ToDouble<T> other) {
        return Expressions.minus(this.asDouble(), other);
    }

    @Override
    default ToInt<T> multiply(byte other) {
        return Expressions.multiply(this, other);
    }

    @Override
    default ToInt<T> multiply(ToByte<T> other) {
        return Expressions.multiply(this.asInt(), other);
    }

    @Override
    default ToInt<T> multiply(int other) {
        return Expressions.multiply(this.asInt(), other);
    }

    @Override
    default ToInt<T> multiply(ToInt<T> other) {
        return Expressions.multiply(this.asInt(), other);
    }

    @Override
    default ToLong<T> multiply(long other) {
        return Expressions.multiply(this, other);
    }

    @Override
    default ToLong<T> multiply(ToLong<T> other) {
        return Expressions.multiply(this.asLong(), other);
    }

    @Override
    default ToDouble<T> multiply(double other) {
        return Expressions.multiply(this.asDouble(), other);
    }

    @Override
    default ToDouble<T> multiply(ToDouble<T> other) {
        return Expressions.multiply(this.asDouble(), other);
    }

    @Override
    default ToDouble<T> divide(int divisor) {
        return Expressions.divide(this, divisor);
    }

    @Override
    default ToDouble<T> divide(ToInt<T> divisor) {
        return Expressions.divide(this, divisor);
    }

    @Override
    default ToDouble<T> divide(long divisor) {
        return Expressions.divide(this, divisor);
    }

    @Override
    default ToDouble<T> divide(ToLong<T> divisor) {
        return Expressions.divide(this, divisor);
    }

    @Override
    default ToDouble<T> divide(double divisor) {
        return Expressions.divide(this, divisor);
    }

    @Override
    default ToDouble<T> divide(ToDouble<T> divisor) {
        return Expressions.divide(this, divisor);
    }

    @Override
    default ToShort<T> negate() {
        return Expressions.negate(this);
    }

    @Override
    default long hash(T object) {
        return applyAsShort(object);
    }

    @Override
    default int compare(T first, T second) {
        return Short.compare(
            applyAsShort(first),
            applyAsShort(second)
        );
    }

    @SuppressWarnings("unchecked")
    default <V> ToShort<V> compose(Function<? super V, ? extends T> before) {
        return ComposedUtil.compose((Function<V, T>) before, this);
    }
}