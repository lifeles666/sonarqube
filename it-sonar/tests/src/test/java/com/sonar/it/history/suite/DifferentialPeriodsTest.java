/*
 * Copyright (C) 2009-2012 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.it.history.suite;

import com.sonar.it.ItUtils;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.selenium.Selenese;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class DifferentialPeriodsTest {

  @ClassRule
  public static Orchestrator orchestrator = HistoryTestSuite.ORCHESTRATOR;

  @Before
  public void cleanUpAnalysisData() {
    orchestrator.getDatabase().truncateInspectionTables();
  }

  /**
   * SONAR-4700
   */
  @Test
  public void not_display_periods_selection_dropdown_on_first_analysis() {
    orchestrator.executeBuild(SonarRunner.create(ItUtils.locateProjectDir("shared/xoo-sample")).withoutDynamicAnalysis());

    orchestrator.executeSelenese(Selenese.builder().setHtmlTestsInClasspath("not-display-periods-selection-dropdown-on-first-analysis",
      "/selenium/history/differential-periods/not-display-periods-selection-dropdown-on-dashboard.html",
      "/selenium/history/differential-periods/not-display-periods-selection-dropdown-on-issues-drilldown.html"
    ).build());
  }

  /**
   * SONAR-4700
   */
  @Test
  public void display_periods_selection_dropdown_after_first_analysis() {
    SonarRunner scan = SonarRunner.create(ItUtils.locateProjectDir("shared/xoo-sample")).withoutDynamicAnalysis();
    orchestrator.executeBuilds(scan, scan);

    orchestrator.executeSelenese(Selenese.builder().setHtmlTestsInClasspath("display-periods-selection-dropdown-after-first-analysis",
      "/selenium/history/differential-periods/display-periods-selection-dropdown-on-dashboard.html",
      "/selenium/history/differential-periods/display-periods-selection-dropdown-on-issues-drilldown.html"
    ).build());
  }

}
