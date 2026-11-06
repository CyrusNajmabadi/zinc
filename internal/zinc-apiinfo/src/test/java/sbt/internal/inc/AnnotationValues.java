/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */
package sbt.internal.inc;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AnnotationValues {
    public enum Choice { FIRST, SECOND }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Nested {
        String z() default "last";
        String a() default "first";
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Values {
        String[] strings() default {};
        String text() default "\\\"\n})=";
        char character() default '\'';
        Class<?> type() default String.class;
        Choice choice() default Choice.FIRST;
        Nested nested() default @Nested;
        int[] ints() default {1, 2};
        byte b() default 1;
        short s() default 1;
        int i() default 1;
        long l() default 1L;
        float f() default 1.0f;
        double d() default 1.0;
        boolean flag() default true;
    }

    @Values(strings = {"a,b"})
    public static class Single {}

    @Values(strings = {"a", "b"})
    public static class Multiple {}
}
