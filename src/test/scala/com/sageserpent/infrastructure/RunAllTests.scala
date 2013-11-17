package com.sageserpent.infrastructure

import org.junit.runner.JUnitCore

object RunAllTests extends App {
  System.out.println("Starting tests....")
  new JUnitCore().run(AllTests.getClass())
  System.out.println(".... finished tests.")
}
