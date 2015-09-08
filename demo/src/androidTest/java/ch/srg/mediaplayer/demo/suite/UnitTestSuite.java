package ch.srg.mediaplayer.demo.suite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import ch.srg.mediaplayer.demo.SegmentsVideoTests;
import ch.srg.mediaplayer.demo.VideoPlayTests;

/**
 * Runs all unit tests.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({VideoPlayTests.class, StatisticsPlayTests.class, SegmentsVideoTests.class})
public class UnitTestSuite {}
