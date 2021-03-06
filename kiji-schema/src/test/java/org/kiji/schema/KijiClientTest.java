/**
 * (c) Copyright 2012 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.schema;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kiji.schema.platform.SchemaPlatformBridge;
import org.kiji.schema.util.TestingFileUtils;

/**
 * Base class for tests that interact with kiji as a client.
 * Provides MetaTable and KijiSchemaTable access.
 */
public class KijiClientTest {
  private static final Logger LOG = LoggerFactory.getLogger(KijiClientTest.class);

  static {
    SchemaPlatformBridge.get().initializeHadoopResources();
  }

  // JUnit requires public, checkstyle disagrees:
  // CSOFF: VisibilityModifierCheck
  /** Test method name (eg. "testFeatureX"). */
  @Rule
  public final TestName mTestName = new TestName();
  // CSON: VisibilityModifierCheck

  /** Counter for fake HBase instances. */
  private final AtomicLong mFakeHBaseInstanceCounter = new AtomicLong();

  /** Test identifier, eg. "org_package_ClassName_testMethodName". */
  private String mTestId;

  /** Kiji instances opened during test, and that must be released and cleaned up after. */
  private List<Kiji> mKijis = Lists.newArrayList();

  /** Local temporary directory, automatically cleaned up after. */
  private File mLocalTempDir = null;

  /** Default test Kiji instance. */
  private Kiji mKiji = null;

  /** The configuration object for this kiji instance. */
  private Configuration mConf;

  /**
   * Initializes the in-memory kiji for testing.
   *
   * @throws Exception on error.
   */
  @Before
  public final void setupKijiTest() throws Exception {
    try {
      doSetupKijiTest();
    } catch (Exception exn) {
      // Make exceptions from setup method visible:
      exn.printStackTrace();
      throw exn;
    }
  }

  private void doSetupKijiTest() throws Exception {
    mTestId =
        String.format("%s_%s", getClass().getName().replace('.', '_'), mTestName.getMethodName());
    mLocalTempDir = TestingFileUtils.createTempDir(mTestId, "temp-dir");
    mConf = HBaseConfiguration.create();
    mConf.set("fs.defaultFS", "file://" + mLocalTempDir);
    mConf.set("fs.default.name", "file://" + mLocalTempDir);
    mConf.set("mapred.job.tracker", "local");
    mKiji = null;  // lazily initialized
  }

  /**
   * Opens a new unique test Kiji instance, creating it if necessary.
   *
   * Each call to this method returns a fresh new Kiji instance.
   * All generated Kiji instances are automatically cleaned up by KijiClientTest.
   *
   * @return a fresh new Kiji instance.
   * @throws Exception on error.
   */
  public Kiji createTestKiji() throws Exception {
    Preconditions.checkNotNull(mConf);
    final long fakeHBaseCounter = mFakeHBaseInstanceCounter.getAndIncrement();
    final String instanceName =
        String.format("%s_%s", getClass().getSimpleName(), mTestName.getMethodName());
    final String hbaseAddress =
        String.format(".fake.%s-%d", instanceName, fakeHBaseCounter);
    final KijiURI uri =
        KijiURI.newBuilder(String.format("kiji://%s/%s", hbaseAddress, instanceName)).build();
    KijiInstaller.get().install(uri, mConf);
    final Kiji kiji = Kiji.Factory.open(uri, mConf);

    mKijis.add(kiji);
    return kiji;
  }

  /**
   * Closes the in-memory kiji instance.
   * @throws Exception If there is an error.
   */
  @After
  public final void teardownKijiTest() throws Exception {
    LOG.debug("Tearing down {}", mTestId);
    for (Kiji kiji : mKijis) {
      kiji.release();
      KijiInstaller.get().uninstall(kiji.getURI(), mConf);
    }
    mKijis = null;
    mKiji = null;
    mConf = null;
    FileUtils.deleteDirectory(mLocalTempDir);
    mLocalTempDir = null;
    mTestId = null;

    // Force a garbage collection, to trigger finalization of resources and spot
    // resources that were not released or closed.
    System.gc();
    System.runFinalization();
  }

  /**
   * Gets the default Kiji instance to use for testing.
   *
   * @return the default Kiji instance to use for testing.
   *     Automatically released by KijiClientTest.
   * @throws IOException on I/O error.  Should be Exception, but breaks too many tests for now.
   */
  public synchronized Kiji getKiji() throws IOException {
    if (null == mKiji) {
      try {
        mKiji = createTestKiji();
      } catch (IOException ioe) {
        throw ioe;
      } catch (Exception exn) {
        // TODO: Remove wrapping:
        throw new IOException(exn);
      }
    }
    return mKiji;
  }

  /** @return a valid identifier for the current test. */
  public String getTestId() {
    return mTestId;
  }

  /** @return a local temporary directory. */
  public File getLocalTempDir() {
    return mLocalTempDir;
  }

  /**
   * @return a test Hadoop configuration, with:
   *     <li> a default FS
   *     <li> a job tracker
   */
  public Configuration getConf() {
    return mConf;
  }
}
