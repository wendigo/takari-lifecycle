package io.takari.maven.plugins.compile;

import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.javac.CompilerJavacLauncher;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;

public class AnnotationProcessingTest extends AbstractCompileTest {

  public AnnotationProcessingTest(String compilerId) {
    super(compilerId);
  }

  @Parameters(name = "{0}")
  public static Iterable<Object[]> compilers() {
    List<Object[]> compilers = new ArrayList<Object[]>();
    compilers.add(new Object[] {"javac"});
    compilers.add(new Object[] {"forked-javac"});
    compilers.add(new Object[] {"jdt"});
    return compilers;
  }

  private File procCompile(String projectName, Proc proc, Xpp3Dom... parameters) throws Exception, IOException {
    File basedir = resources.getBasedir(projectName);
    return procCompile(basedir, proc, parameters);
  }

  private File procCompile(File basedir, Proc proc, Xpp3Dom... parameters) throws Exception, IOException {
    File processor = compileAnnotationProcessor();
    return processAnnotations(basedir, proc, processor, parameters);
  }

  private File processAnnotations(File basedir, Proc proc, File processor, Xpp3Dom... parameters) throws Exception {
    MavenProject project = mojos.readMavenProject(basedir);
    processAnnotations(project, processor, proc, parameters);
    return basedir;
  }

  protected void processAnnotations(MavenProject project, File processor, Proc proc, Xpp3Dom... parameters) throws Exception {
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = mojos.newMojoExecution();

    addDependency(project, "processor", new File(processor, "target/classes"));

    Xpp3Dom configuration = execution.getConfiguration();

    if (proc != null) {
      configuration.addChild(newParameter("proc", proc.name()));
    }
    if (parameters != null) {
      for (Xpp3Dom parameter : parameters) {
        configuration.addChild(parameter);
      }
    }

    mojos.executeMojo(session, project, execution);
  }

  private File compileAnnotationProcessor() throws Exception, IOException {
    File processor = compile("compile-proc/processor");
    cp(processor, "src/main/resources/META-INF/services/javax.annotation.processing.Processor", "target/classes/META-INF/services/javax.annotation.processing.Processor");
    return processor;
  }


  @Test
  public void testProc_only() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.only);
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"), "proc/GeneratedSource.java", "proc/AnotherGeneratedSource.java");
  }

  @Test
  public void testProc_none() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.none);
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
  }

  @Test
  public void testProc_proc() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_incrementalProcessorChange() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");
    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");

    rm(processor, "target/classes/META-INF/services/javax.annotation.processing.Processor");
    mojos.flushClasspathCaches();
    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_dummyOutput() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc, newProcessors("processor.Processor_dummyOutput"));
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
  }

  @Test
  public void testProcTypeReference() throws Exception {
    File basedir = procCompile("compile-proc/proc-type-reference", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/GeneratedSourceSubclass.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_createResource() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc, newProcessors("processor.ProcessorCreateResource"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java");
  }

  @Test
  public void testProc_annotationProcessors() throws Exception {
    Xpp3Dom processors = newProcessors("processor.Processor");
    File basedir = procCompile("compile-proc/proc", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testProc_processorErrorMessage() throws Exception {
    Xpp3Dom processors = newProcessors("processor.ErrorMessageProcessor");
    File basedir = resources.getBasedir("compile-proc/proc");
    try {
      procCompile(basedir, Proc.only, processors);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(new File(basedir, "target"), new String[0]);

    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("jdt", "ERROR Source.java [6:14] test error message"); // TODO why 14?
    expected.setSnippets("javac", "ERROR Source.java [6:8] test error message");
    mojos.assertMessage(basedir, "src/main/java/proc/Source.java", expected);

    Collection<String> pomMessages = mojos.getBuildContextLog().getMessages(new File(basedir, "pom.xml"));
    Assert.assertEquals(3, pomMessages.size());
    // TODO assert actual messages are as expected
  }

  @Test
  public void testProc_messages() throws Exception {
    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("javac", "ERROR BrokenSource.java [2:29]", "cannot find symbol");
    expected.setSnippets("jdt", "ERROR BrokenSource.java [2:29]", "cannot be resolved to a type");

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");

    String[] outputs;
    if (CompilerJdt.ID.equals(compilerId)) {
      outputs = new String[] {"classes/proc/Source.class" //
          , "generated-sources/annotations/proc/BrokenSource.java"};
    } else {
      // TODO investigate why javac does not generate classes/proc/Source.class
      outputs = new String[] {"generated-sources/annotations/proc/BrokenSource.java"};
    }

    Xpp3Dom processors = newProcessors("processor.BrokenProcessor");
    try {
      processAnnotations(basedir, Proc.proc, processor, processors);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(new File(basedir, "target"), outputs);
    assertProcMessage(basedir, "target/generated-sources/annotations/proc/BrokenSource.java", expected);

    // no change rebuild should produce the same messages
    try {
      processAnnotations(basedir, Proc.proc, processor, processors);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertCarriedOverOutputs(new File(basedir, "target"), outputs);
    assertProcMessage(basedir, "target/generated-sources/annotations/proc/BrokenSource.java", expected);
  }

  @Test
  public void testProc_lenientProcOnly_processorException() throws Exception {
    Assume.assumeTrue("only javac 7 and jdt support lenientProcOnly=true", !isJava8orBetter || CompilerJdt.ID.equals(compilerId));

    File basedir = resources.getBasedir("compile-proc/proc");

    try {
      procCompile(basedir, Proc.only, newProcessors("processor.ThrowExceptionProcessor"), newParameter("lenientProcOnly", "true"));
    } catch (Exception expected) {
      if (!CompilerJavacLauncher.ID.equals(compilerId)) {
        Assert.assertTrue(expected.getMessage().contains("throw exception processor"));
      } else {
        // TODO forked compiler logs exceptions to stderr, figure out how to test it
      }
    }
  }

  private void assertProcMessage(File basedir, String path, ErrorMessage expected) throws Exception {
    // javac reports the same compilation error twice when Proc.proc
    Set<String> messages = new HashSet<String>(mojos.getBuildContextLog().getMessages(new File(basedir, path)));
    Assert.assertEquals(messages.toString(), 1, messages.size());
    String message = messages.iterator().next();
    Assert.assertTrue(expected.isMatch(message));
  }

  @Test
  public void testProc_processorOptions() throws Exception {
    Xpp3Dom processors = newProcessors("processor.ProcessorWithOptions");

    Xpp3Dom options = new Xpp3Dom("annotationProcessorOptions");
    options.addChild(newParameter("optionA", "valueA"));
    options.addChild(newParameter("optionB", "valueB"));
    procCompile("compile-proc/proc", Proc.proc, processors, options);
  }

  @Test
  public void testProc_staleGeneratedSourcesCleanup() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");

    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");

    // remove annotation
    cp(basedir, "src/main/java/proc/Source.java-remove-annotation", "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  @Ignore("edge case, need to fix eventually, but not a show-stopper")
  public void testProc_incrementalDeleteLastAnnotatedSource() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");

    Xpp3Dom processors = newProcessors("processor.Processor");

    // initial compilation
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // no-change rebuild
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertCarriedOverOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // remove annotated class
    rm(basedir, "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testIncrementalDelete() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-delete");

    Xpp3Dom processors = newProcessors("processor.Processor");

    // initial compilation
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Keep.class", //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // no-change rebuild
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertCarriedOverOutputs(new File(basedir, "target"), //
        "classes/proc/Keep.class", //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // remove annotated source
    rm(basedir, "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/Source.class", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testConvertGeneratedSourceToHandwritten() throws Exception {
    // this test demonstrates the following scenario
    // 1. annotation processor generates java source and the generated source is compiled by the compiler
    // 2. annotation is removed from original source and the generated source is moved to a dependency
    // assert original generatedSource.java and generatedSource.class are deleted

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-move");
    File moduleA = new File(basedir, "module-a");
    File moduleB = new File(basedir, "module-b");

    Xpp3Dom processors = newProcessors("processor.Processor");

    mojos.compile(moduleB);
    MavenProject projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "target/classes"));
    processAnnotations(projectA, processor, Proc.proc, processors);
    mojos.assertBuildOutputs(new File(moduleA, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    mojos.flushClasspathCaches();

    // move generated source to module-b/src/main/java
    cp(moduleB, "src/main/java/proc/GeneratedSource.java-moved", "src/main/java/proc/GeneratedSource.java");
    cp(moduleA, "src/main/java/modulea/ModuleA.java-new", "src/main/java/modulea/ModuleA.java");
    cp(moduleA, "src/main/java/proc/Source.java-remove-annotation", "src/main/java/proc/Source.java");
    mojos.compile(moduleB);
    mojos.assertBuildOutputs(moduleB, "target/classes/proc/GeneratedSource.class");
    processAnnotations(projectA, processor, Proc.proc, processors);
    mojos.assertBuildOutputs(new File(moduleA, "target"), //
        "classes/proc/Source.class", "classes/modulea/ModuleA.class");
    mojos.assertDeletedOutputs(new File(moduleA, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  private Xpp3Dom newProcessors(String... processors) {
    Xpp3Dom annotationProcessors = new Xpp3Dom("annotationProcessors");
    for (String processor : processors) {
      annotationProcessors.addChild(newParameter("processor", processor));
    }
    return annotationProcessors;
  }

  @Test
  public void testLenientProcOnly_lenient() throws Exception {
    Assume.assumeTrue("only javac 7 and jdt support lenientProcOnly=true", !isJava8orBetter || CompilerJdt.ID.equals(compilerId));

    Xpp3Dom processors = newProcessors("processor.Processor");

    File basedir = resources.getBasedir("compile-proc/missing-type");
    File generatedSources = new File(basedir, "target/generated-sources/annotations");

    procCompile("compile-proc/missing-type", Proc.only, processors, newParameter("lenientProcOnly", "true"));
    mojos.assertBuildOutputs(generatedSources, "proc/GeneratedSource.java");
    mojos.assertMessages(basedir, "src/main/java/warn/Source.java", new String[0]);

    procCompile("compile-proc/missing-type", Proc.only, processors, newParameter("lenientProcOnly", "true"), newParameter("showWarnings", "true"));
    mojos.assertBuildOutputs(generatedSources, "proc/GeneratedSource.java");
    ErrorMessage warning = new ErrorMessage(compilerId);
    warning.setSnippets("jdt", "WARNING", "missing cannot be resolved to a type");
    warning.setSnippets("javac", "WARNING", "package missing does not exist");
    mojos.assertMessage(basedir, "src/main/java/warn/Source.java", warning);
  }

  @Test
  public void testLenientProcOnly_strict() throws Exception {
    Assume.assumeTrue("only javac 8+ and jdt support lenientProcOnly=false", isJava8orBetter || CompilerJdt.ID.equals(compilerId));

    Xpp3Dom processors = newProcessors("processor.Processor");
    File basedir = resources.getBasedir("compile-proc/missing-type");
    try {
      procCompile("compile-proc/missing-type", Proc.only, processors, newParameter("lenientProcOnly", "false"));
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    ErrorMessage error = new ErrorMessage(compilerId);
    error.setSnippets("jdt", "ERROR", "missing cannot be resolved to a type");
    error.setSnippets("javac", "ERROR", "package missing does not exist");
    mojos.assertMessage(basedir, "src/main/java/warn/Source.java", error);
  }

  @Test
  public void testRequireProc() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/require-proc");
    try {
      processAnnotations(basedir, null, processor);
      Assert.fail();
    } catch (IllegalArgumentException expected) {
      // TODO assert message
    }

    processAnnotations(basedir, null, null);
  }

  @Test
  public void testRecompile() throws Exception {

    /**
     * <pre>
     *    Source.java -> Source.class
     *      \-> GeneratedSource.java -> GeneratedSource.class
     *                                      ^
     *                                Client.java -> Client.class 
     * </pre>
     */

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-recompile");

    Xpp3Dom processors = newProcessors("processor.ProcessorSiblingBody");
    Xpp3Dom options = new Xpp3Dom("annotationProcessorOptions");
    options.addChild(newParameter("basedir", new File(basedir, "src/main/java").getCanonicalPath()));

    processAnnotations(basedir, Proc.proc, processor, processors, options);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/Client.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    cp(basedir, "src/main/java/proc/GeneratedSource.body-changed", "src/main/java/proc/GeneratedSource.body");
    touch(basedir, "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, processors, options);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/Client.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testProp_processorLastRound() throws Exception {
    Xpp3Dom processors = newProcessors("processor.ProcessorLastRound");
    File basedir = procCompile("compile-proc/proc", Proc.only, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/types.lst");

    assertFileContents("proc.Source\n", basedir, "target/classes/types.lst");
  }

  @Test
  public void testIncremental_proc_only() throws Exception {
    // the point of this test is to assert that changes to annotations trigger affected sources reprocessing when proc=only
    // note that proc=only is not incremental, sources are processed in all-or-nothing manner

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-proconly");
    File generatedSources = new File(basedir, "target/generated-sources/annotations");

    Xpp3Dom processors = newProcessors("processor.Processor");

    compile(basedir, processor, "compile-only");
    processAnnotations(basedir, Proc.only, processor, processors);
    mojos.assertBuildOutputs(generatedSources, "proc/GeneratedConcrete.java", "proc/GeneratedAbstract.java", "proc/GeneratedAnother.java");

    cp(basedir, "src/main/java/proc/Abstract.java-remove-annotation", "src/main/java/proc/Abstract.java");
    compile(basedir, processor, "compile-only");
    processAnnotations(basedir, Proc.only, processor, processors);
    mojos.assertDeletedOutputs(generatedSources, "proc/GeneratedConcrete.java", "proc/GeneratedAbstract.java");
    if (CompilerJdt.ID.equals(compilerId)) {
      mojos.assertCarriedOverOutputs(generatedSources, "proc/GeneratedAnother.java");
    } else {
      mojos.assertBuildOutputs(generatedSources, "proc/GeneratedAnother.java");
    }
  }

  private void compile(File basedir, File processor, String executionId) throws Exception {
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = mojos.newMojoExecution();
    MojoExecution cloned = new MojoExecution(execution.getMojoDescriptor(), executionId, null);
    cloned.setConfiguration(execution.getConfiguration());
    execution.getConfiguration().addChild(newParameter("proc", Proc.none.name()));
    addDependency(project, "processor", new File(processor, "target/classes"));
    mojos.executeMojo(session, project, cloned);
  }

  @Test
  public void testMutliround_procOnly() throws Exception {
    File basedir = procCompile("compile-proc/multiround", Proc.only);
    File generatedSources = new File(basedir, "target/generated-sources/annotations");

    mojos.assertMessages(basedir, "src/main/java/multiround/Source.java", new String[] {});
    mojos.assertBuildOutputs(generatedSources, "multiround/GeneratedSource.java", "multiround/AnotherGeneratedSource.java");
  }

  @Test
  public void testMultiround_processorLastRound() throws Exception {
    // processor.ProcessorLastRound creates well-known resource during last round
    // the point of this test is to assert this works during incremental build
    // when compiler may be invoked several times to compile indirectly affected sources

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/multiround-type-reference");

    processAnnotations(basedir, Proc.procEX, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/AnotherSource.class", //
        "classes/types.lst");

    cp(basedir, "src/main/java/proc/Source.java-changed", "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.procEX, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/AnotherSource.class", //
        "classes/types.lst");
  }

  @Test
  public void testLastRound_typeIndex() throws Exception {
    Xpp3Dom processors = newProcessors("processor.ProcessorLastRound_typeIndex");
    File basedir = procCompile("compile-proc/multiround-type-index", Proc.procEX, processors);
    File target = new File(basedir, "target");
    mojos.assertBuildOutputs(target, //
        "generated-sources/annotations/generated/TypeIndex.java", //
        "generated-sources/annotations/generated/TypeIndex2.java", //
        "classes/generated/TypeIndex.class", //
        "classes/generated/TypeIndex2.class", //
        "classes/typeindex/Annotated.class");
  }

  @Test
  public void testReprocess() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/reprocess");
    File target = new File(basedir, "target");

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProessorValue"));
    mojos.assertBuildOutputs(target, //
        "classes/reprocess/Annotated.class", //
        "classes/reprocess/Annotated.value", //
        "classes/reprocess/SimpleA.class", //
        "classes/reprocess/SimpleB.class");

    Assert.assertEquals("1", FileUtils.fileRead(new File(target, "classes/reprocess/Annotated.value")));

    cp(basedir, "src/main/java/reprocess/SimpleA.java-changed", "src/main/java/reprocess/SimpleA.java");
    touch(new File(basedir, "src/main/java/reprocess/Annotated.java"));

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProessorValue"));
    mojos.assertBuildOutputs(target, //
        "classes/reprocess/Annotated.class", //
        "classes/reprocess/Annotated.value", //
        "classes/reprocess/SimpleA.class", //
        "classes/reprocess/SimpleB.class");

    Assert.assertEquals("10", FileUtils.fileRead(new File(target, "classes/reprocess/Annotated.value")));
  }
}
