package com.donohoedigital.config;

import junit.framework.*;
import org.apache.logging.log4j.*;

import java.net.*;

/**
 * @author Doug Donohoe
 */
public class MatchingResourcesTest extends TestCase
{
    Logger logger = LogManager.getLogger(MatchingResourcesTest.class);

    // resource that exists exactly once on the classpath
    private static final String SINGLE = "classpath*:com/donohoedigital/config/ConfigUtils.class";

    // resource that exists in many jars on the classpath (one per jar)
    private static final String MULTIPLE = "classpath*:META-INF/MANIFEST.MF";

    public void testFindResources()
    {
        MatchingResources mr = new MatchingResources(SINGLE);
        URL[] match = mr.getAllMatchesURL();
        assertEquals(1, match.length);

        logger.info("URL: {}", match[0]);

        URL[] none = new MatchingResources("classpath*:com/donohoedigital/config/NoSuchFile.class").getAllMatchesURL();
        assertEquals(0, none.length);
    }

    public void testFindResource()
    {
        URL thiz = new MatchingResources(SINGLE).getSingleRequiredResourceURL();
        assertNotNull(thiz);

        // test required
        try
        {
            new MatchingResources("classpath*:com/donohoedigital/config/NoSuchFile.class").getSingleRequiredResourceURL();
            fail("should have thrown exception");
        }
        catch (Exception ae)
        {
            logger.debug("Expected exception: {}", ae.getMessage());
        }

        // test multiple matches
        try
        {
            new MatchingResources(MULTIPLE).getSingleResourceURL();
            fail("should have thrown exception");
        }
        catch (Exception ae)
        {
            logger.debug("Expected exception: {}", ae.getMessage());
        }
    }

    public void testToString()
    {
        MatchingResources mr = new MatchingResources(MULTIPLE);
        assertTrue(mr.getAllMatchesURL().length > 0);

        logger.info("URLs:\n{}", mr);
    }
}
