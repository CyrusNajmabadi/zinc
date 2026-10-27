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
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class AnnotationRenderingFixture {
  public enum Mode {
    FIRST,
    SECOND
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface Nested {
    String a();

    String b();
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface Values {
    String text();

    String[] strings();

    int[] numbers();

    Class<?> type();

    Mode mode();

    Nested nested();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Hidden {
    String value();
  }

  @Hidden("hidden")
  static class HiddenFixture {}

  private static <T extends Annotation> T proxy(
      Class<T> type, Map<String, Object> values, String text) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (p, m, args) -> {
              if (m.getName().equals("annotationType")) return type;
              if (m.getName().equals("toString")) return text;
              if (values.containsKey(m.getName())) return values.get(m.getName());
              throw new AssertionError(m);
            }));
  }

  private static String render(Annotation value) {
    return ClassToAPI$.MODULE$.annotation(value).arguments()[0].value();
  }

  private static void require(boolean value, String reason) {
    if (!value) throw new AssertionError(reason);
  }

  public static void verify() {
    Map<String, Object> nested = new HashMap<>();
    nested.put("a", "one");
    nested.put("b", "two");
    Nested nestedA = proxy(Nested.class, nested, "@Nested(a=one,b=two)");
    Nested nestedB = proxy(Nested.class, nested, "@Nested(b=two,a=one)");
    Map<String, Object> a = new HashMap<>();
    a.put("text", "comma, quotes\" and \\ slash");
    a.put("strings", new String[] {"a, b", "c"});
    a.put("numbers", new int[] {1, 2});
    a.put("type", String.class);
    a.put("mode", Mode.FIRST);
    a.put("nested", nestedA);
    Map<String, Object> b = new HashMap<>(a);
    b.put("nested", nestedB);
    Values first = proxy(Values.class, a, "@Values(text=first,nested=@Nested(a=one,b=two))");
    Values reordered = proxy(Values.class, b, "@Values(nested=@Nested(b=two,a=one),text=first)");
    require(render(first).equals(render(reordered)), "annotation member order changed API value");
    for (String field : a.keySet()) {
      Map<String, Object> different = new HashMap<>(a);
      switch (field) {
        case "text":
          different.put(field, "changed");
          break;
        case "strings":
          different.put(field, new String[] {"a", "b, c"});
          break;
        case "numbers":
          different.put(field, new int[] {2, 1});
          break;
        case "type":
          different.put(field, Integer.class);
          break;
        case "mode":
          different.put(field, Mode.SECOND);
          break;
        case "nested":
          Map<String, Object> n = new HashMap<>(nested);
          n.put("b", "changed");
          different.put(field, proxy(Nested.class, n, "nested"));
          break;
      }
      require(
          !render(first).equals(render(proxy(Values.class, different, "same-display"))),
          "lost member " + field);
    }
    require(
        render(HiddenFixture.class.getAnnotation(Hidden.class)).contains("hidden"),
        "nonpublic annotation inaccessible");
  }
}
