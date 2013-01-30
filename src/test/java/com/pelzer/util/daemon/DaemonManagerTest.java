package com.pelzer.util.daemon;

import junit.framework.TestCase;
import com.pelzer.util.PropertyManager;
import com.pelzer.util.daemon.DaemonManager.ManagerThread;

public class DaemonManagerTest extends TestCase {
  public DaemonManagerTest(final String name) {
    super(name);
  }
  
  public void testExpansion() {
    final ManagerThread thread = new ManagerThread();
    assertEquals(PropertyManager.getEnvironment(), thread.expandTokens("$ENV$"));
    
    final String startTokens[] = new String[] { "$ENV$", "$env$", "$SERVER_NAME$" };
    final String endTokens[] = thread.expandTokens(startTokens);
    assertEquals(PropertyManager.getEnvironment(), endTokens[0]);
    assertEquals(PropertyManager.getEnvironment().toLowerCase(), endTokens[1]);
    assertEquals(PropertyManager.getHostname(), endTokens[2]);
  }
}