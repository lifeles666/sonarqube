/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.performance.batch.suite;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.performance.PerfTestCase;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class HighlightingTest extends PerfTestCase {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = BatchPerfTestSuite.ORCHESTRATOR;

  @BeforeClass
  public static void setUp() throws IOException {
    // Execute a first analysis to prevent any side effects with cache of plugin JAR files
    orchestrator.executeBuild(newSonarRunner("-Xmx512m -server", "sonar.profile", "one-xoo-issue-per-line"));
  }

  @Before
  public void cleanDatabase() {
    orchestrator.resetData();
  }

  @Test
  public void computeSyntaxHighlightingOnBigFiles() throws IOException {

    File baseDir = temp.newFolder();
    File srcDir = new File(baseDir, "src");
    srcDir.mkdir();

    int nbFiles = 100;
    int chunkSize = 100000;
    for (int nb = 1; nb <= nbFiles; nb++) {
      File xooFile = new File(srcDir, "sample" + nb + ".xoo");
      File xoohighlightingFile = new File(srcDir, "sample" + nb + ".xoo.highlighting");
      FileUtils.write(xooFile, "Sample xoo\ncontent");
      StringBuilder sb = new StringBuilder(16 * chunkSize);
      for (int i = 0; i < chunkSize; i++) {
        sb.append(i).append(":").append(i + 1).append(":s\n");
      }
      FileUtils.write(xoohighlightingFile, sb.toString());
    }

    SonarRunner runner = SonarRunner.create()
      .setProperties(
        "sonar.projectKey", "highlighting",
        "sonar.projectName", "highlighting",
        "sonar.projectVersion", "1.0",
        "sonar.sources", "src",
        "sonar.showProfiling", "true")
      .setEnvironmentVariable("SONAR_RUNNER_OPTS", "-Xmx512m -server -XX:MaxPermSize=64m")
      .setRunnerVersion("2.4")
      .setProjectDir(baseDir);

    long start = System.currentTimeMillis();
    orchestrator.executeBuild(runner);
    long duration = System.currentTimeMillis() - start;
    assertDurationAround(duration, 43000L);
  }

}