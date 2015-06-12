package com.pelzer.util.daemon;

import com.pelzer.util.PropertyManager;
import junit.framework.TestCase;

public class DaemonManagerTest extends TestCase{
  public DaemonManagerTest(final String name){
    super(name);
  }

  public void testExpansion(){
    assertEquals(PropertyManager.getEnvironment(), DaemonManager.expandTokens("$ENV$"));

    final String startTokens[] = new String[]{"$ENV$", "$env$", "$SERVER_NAME$"};
    final String endTokens[] = DaemonManager.expandTokens(startTokens);
    assertEquals(PropertyManager.getEnvironment(), endTokens[0]);
    assertEquals(PropertyManager.getEnvironment().toLowerCase(), endTokens[1]);
    assertEquals(PropertyManager.getHostname(), endTokens[2]);
  }
}
