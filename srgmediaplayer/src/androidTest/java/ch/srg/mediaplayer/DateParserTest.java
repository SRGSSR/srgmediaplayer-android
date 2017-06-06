package ch.srg.mediaplayer;

import android.app.Application;
import android.test.ApplicationTestCase;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

import ch.srg.mediaplayer.utils.DateParser;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class DateParserTest {
	@Test
	public void test8601() {
		DateParser parser = new DateParser(DateParser.DATE_FORMAT_ISO_8601);
		Assert.assertEquals(new Date(1496126175000L), parser.parseDate("2017-05-30T08:36:15.079+02:00"));
		Assert.assertEquals(new Date(1496126175000L), parser.parseDate("2017-05-30T08:36:15+02:00"));
		Assert.assertEquals(new Date(1496126175000L), parser.parseDate("2017-05-30T06:36:15Z"));
	}
}