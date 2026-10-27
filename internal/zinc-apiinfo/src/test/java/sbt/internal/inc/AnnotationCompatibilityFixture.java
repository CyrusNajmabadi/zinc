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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

public final class AnnotationCompatibilityFixture {
  private static void compile(Path directory, String name, String source) throws IOException {
    Path file = directory.resolve(name + ".java");
    Files.write(file, source.getBytes(StandardCharsets.UTF_8));
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-cp",
                directory.toString(),
                "-d",
                directory.toString(),
                file.toString());
    if (result != 0) throw new AssertionError("Compilation failed for " + name);
  }

  private static void check(Annotation annotation) {
    String first = ClassToAPI$.MODULE$.annotation(annotation).arguments()[0].value();
    String second = ClassToAPI$.MODULE$.annotation(annotation).arguments()[0].value();
    if (!first.equals(second)) throw new AssertionError("Unstable annotation " + annotation);
  }

  public static void verify() throws Exception {
    try {
      Class<?> counterCell = Class.forName("java.util.concurrent.ConcurrentHashMap$CounterCell");
      for (Annotation annotation : counterCell.getDeclaredAnnotations()) check(annotation);
    } catch (ClassNotFoundException absentOnThisJdk) {
      // This OpenJDK fixture exercises a runtime annotation in a non-exported package.
    }
    for (String kind : new String[] {"missing-class", "missing-enum", "mismatch", "incomplete"}) {
      Path directory = Files.createTempDirectory("zinc-annotation-");
      try {
        String prefix =
            "import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface A { ";
        switch (kind) {
          case "missing-class":
            compile(directory, "Missing", "public class Missing {}");
            compile(directory, "A", prefix + "Class<?> value(); }");
            compile(directory, "T", "@A(Missing.class) public class T {}");
            Files.delete(directory.resolve("Missing.class"));
            break;
          case "missing-enum":
            compile(directory, "E", "public enum E { OLD }");
            compile(directory, "A", prefix + "E value(); }");
            compile(directory, "T", "@A(E.OLD) public class T {}");
            compile(directory, "E", "public enum E { NEW }");
            break;
          case "mismatch":
            compile(directory, "A", prefix + "int value(); }");
            compile(directory, "T", "@A(1) public class T {}");
            compile(directory, "A", prefix + "String value(); }");
            break;
          case "incomplete":
            compile(directory, "A", prefix + "int value(); }");
            compile(directory, "T", "@A(1) public class T {}");
            compile(directory, "A", prefix + "int value(); String added(); }");
            break;
        }
        try (URLClassLoader loader =
            new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
          Annotation annotation = loader.loadClass("T").getDeclaredAnnotations()[0];
          annotation.toString();
          check(annotation);
        }
      } finally {
        try (Stream<Path> files = Files.walk(directory)) {
          files
              .sorted(Comparator.reverseOrder())
              .forEach(
                  path -> {
                    try {
                      Files.delete(path);
                    } catch (IOException error) {
                      throw new UncheckedIOException(error);
                    }
                  });
        }
      }
    }
  }
}
