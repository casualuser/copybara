package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.EnvironmentException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.transform.FileMove.MoveElement;
import com.google.copybara.util.console.Console;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class FileMoveTest {

  private FileMove.Yaml yaml;
  private OptionsBuilder options;
  private Path workdir;
  private Console console;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    workdir = fs.getPath("/");
    Files.createDirectories(workdir);
    yaml = new FileMove.Yaml();
    options = new OptionsBuilder();
    console = options.general.console();
  }

  @Test
  public void testSimpleMove() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(
        createMove("one.before", "folder/one.after"),
        createMove("folder2/two.before", "two.after")));
    FileMove mover = yaml.withOptions(options.build());
    Files.write(workdir.resolve("one.before"), new byte[]{});
    Files.createDirectories(workdir.resolve("folder2"));
    Files.write(workdir.resolve("folder2/two.before"), new byte[]{});
    mover.transform(workdir, console);

    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFiles("folder/one.after", "two.after")
        .containsNoMoreFiles();
  }

  @Test
  public void testDoesntExist() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(
        createMove("blablabla", "other")));
    FileMove mover = yaml.withOptions(options.build());
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Error moving 'blablabla'. It doesn't exist");
    mover.transform(workdir, console);
  }

  @Test
  public void testEmpty() throws ConfigValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.<MoveElement>of());
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'paths' attribute is required");
    yaml.withOptions(options.build());
  }

  @Test
  public void testAbsolute() throws ConfigValidationException, EnvironmentException, IOException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'/blablabla' is not a relative path");
    yaml.setPaths(ImmutableList.of(createMove("/blablabla", "other")));
  }

  @Test
  public void testDotDot() throws ConfigValidationException, EnvironmentException, IOException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("'../blablabla' is not a relative path");
    yaml.setPaths(ImmutableList.of(createMove("../blablabla", "other")));
  }

  @Test
  public void testDestinationExist() throws ValidationException, EnvironmentException, IOException {
    yaml.setPaths(ImmutableList.of(createMove("one", "two")));
    Files.write(workdir.resolve("one"), new byte[]{});
    Files.write(workdir.resolve("two"), new byte[]{});
    FileMove mover = yaml.withOptions(options.build());
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot move '/two' because it already exists in the workdir");
    mover.transform(workdir, console);
  }

  @Test
  public void testDestinationExistDirectory() throws ValidationException, EnvironmentException,
      IOException {
    yaml.setPaths(ImmutableList.of(createMove("one", "folder/two")));
    Files.createDirectories(workdir.resolve("folder"));
    Files.write(workdir.resolve("one"), new byte[]{});
    FileMove mover = yaml.withOptions(options.build());
    mover.transform(workdir, console);

    assertAbout(FileSubjects.path())
        .that(workdir)
        .containsFiles("folder/two")
        .containsNoMoreFiles();
  }

  private MoveElement createMove(String before, String after) throws ConfigValidationException {
    MoveElement result = new MoveElement();
    result.setBefore(before);
    result.setAfter(after);
    return result;
  }
}