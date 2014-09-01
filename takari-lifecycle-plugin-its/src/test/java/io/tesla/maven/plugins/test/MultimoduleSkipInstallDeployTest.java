package io.tesla.maven.plugins.test;

import io.takari.maven.testing.it.VerifierResult;
import io.takari.maven.testing.it.VerifierRuntime.VerifierRuntimeBuilder;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class MultimoduleSkipInstallDeployTest extends AbstractIntegrationTest {

  public MultimoduleSkipInstallDeployTest(VerifierRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("multimodule-skip-install-deploy");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    VerifierResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertErrorFreeLog();

    File group = new File(remoterepo, "io/takari/lifecycle/its/multimodule-skip-install-deploy");
    Assert.assertTrue(new File(group, "parent/1.0/parent-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "modulea/1.0/modulea-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "modulea/1.0/modulea-1.0.jar").canRead());

    Assert.assertFalse(new File(group, "moduleb/1.0/modulea-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "moduleb/1.0/modulea-1.0.jar").canRead());
  }

}
