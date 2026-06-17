/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.bulkactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.lingala.zip4j.model.FileHeader;

class ActionThreadZipSecurityTest {

  @TempDir
  Path tempDir;

  @Test
  void fixAndExtractZipExtractsRegularFilesInsideDestination() throws Exception {
    Path zip = tempDir.resolve("regular.zip");
    Path outputDir = tempDir.resolve("output");

    createZip(zip,
              new ZipItem("folder/document.txt", "hello"),
              new ZipItem("root.txt", "world"));

    ActionThread.fixAndExtractZip(zip.toFile(), outputDir.toFile());

    assertEquals("hello", Files.readString(outputDir.resolve("folder/document.txt"), StandardCharsets.UTF_8));
    assertEquals("world", Files.readString(outputDir.resolve("root.txt"), StandardCharsets.UTF_8));
  }

  @Test
  void fixAndExtractZipRejectsDotDotTraversalEntries() throws Exception {
    Path zip = tempDir.resolve("traversal.zip");
    Path outputDir = tempDir.resolve("output");
    Path outsideFile = tempDir.resolve("evil.txt");

    createZip(zip, new ZipItem("../evil.txt", "owned"));

    assertThrows(SecurityException.class, () -> ActionThread.fixAndExtractZip(zip.toFile(), outputDir.toFile()));
    assertFalse(Files.exists(outsideFile), "Traversal entry must not be written outside the extraction directory");
  }

  @Test
  void fixAndExtractZipRejectsNestedDotDotTraversalEntries() throws Exception {
    Path zip = tempDir.resolve("nested-traversal.zip");
    Path outputDir = tempDir.resolve("output");

    createZip(zip, new ZipItem("safe/../../evil.txt", "owned"));

    assertThrows(SecurityException.class, () -> ActionThread.fixAndExtractZip(zip.toFile(), outputDir.toFile()));
    assertFalse(Files.exists(tempDir.resolve("evil.txt")));
  }

  @Test
  void validateZipEntryRejectsAbsoluteUnixPath() throws Exception {
    SecurityException exception = assertThrows(SecurityException.class,
        () -> invokeValidateZipEntry(tempDir.toFile(), "/etc/passwd"));

    assertTrue(exception.getMessage().contains("outside target directory"));
  }

  @Test
  void validateZipEntryRejectsNullByte() throws Exception {
    SecurityException exception = assertThrows(SecurityException.class,
        () -> invokeValidateZipEntry(tempDir.toFile(), "safe.txt\0evil"));

    assertTrue(exception.getMessage().contains("invalid name"));
  }

  @Test
  void validateZipEntryAcceptsNormalRelativePath() throws Exception {
    File target = invokeValidateZipEntry(tempDir.toFile(), "folder/file.txt");

    assertEquals(tempDir.resolve("folder/file.txt").normalize().toFile(), target);
  }

  @Test
  void isSymlinkDetectsUnixSymlinkAttributes() throws Exception {
    FileHeader header = new FileHeader();
    setExternalFileAttributes(header, unixModeAttributes(0120777));

    assertTrue(invokeIsSymlink(header));
  }

  @Test
  void isSymlinkDoesNotFlagRegularUnixFileAttributes() throws Exception {
    FileHeader header = new FileHeader();
    setExternalFileAttributes(header, unixModeAttributes(0100644));

    assertFalse(invokeIsSymlink(header));
  }

  @Test
  void ensureNoSymlinksRejectsExistingSymlinkInExtractionTree() throws Exception {
    assumeTrue(supportsSymlinks(), "Symbolic links are not supported in this environment");

    Path outputDir = tempDir.resolve("output");
    Files.createDirectories(outputDir.resolve("folder"));
    Files.writeString(tempDir.resolve("target.txt"), "secret", StandardCharsets.UTF_8);
    Files.createSymbolicLink(outputDir.resolve("folder/link.txt"), tempDir.resolve("target.txt"));

    assertThrows(SecurityException.class, () -> invokeEnsureNoSymlinks(outputDir.toFile()));
  }

  @Test
  void fixAndExtractZipDoesNotOverwriteExistingDestinationFile() throws Exception {
    Path zip = tempDir.resolve("duplicate.zip");
    Path outputDir = tempDir.resolve("output");
    Files.createDirectories(outputDir);
    Files.writeString(outputDir.resolve("file.txt"), "existing", StandardCharsets.UTF_8);

    createZip(zip, new ZipItem("file.txt", "from zip"));

    assertThrows(SecurityException.class, () -> ActionThread.fixAndExtractZip(zip.toFile(), outputDir.toFile()));
    assertEquals("existing", Files.readString(outputDir.resolve("file.txt"), StandardCharsets.UTF_8));
  }

  private static void createZip(Path zipPath, ZipItem... items) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
      for (ZipItem item : items) {
        ZipEntry entry = new ZipEntry(item.name());
        zos.putNextEntry(entry);
        zos.write(item.content().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }
  }

  private static File invokeValidateZipEntry(File destinationDir, String entryName) throws Exception {
    Method method = ActionThread.class.getDeclaredMethod("validateZipEntry", File.class, String.class);
    method.setAccessible(true);
    return invoke(method, null, destinationDir, entryName);
  }

  private static boolean invokeIsSymlink(FileHeader header) throws Exception {
    Method method = ActionThread.class.getDeclaredMethod("isSymlink", FileHeader.class);
    method.setAccessible(true);
    return invoke(method, null, header);
  }

  private static void invokeEnsureNoSymlinks(File root) throws Exception {
    Method method = ActionThread.class.getDeclaredMethod("ensureNoSymlinks", File.class);
    method.setAccessible(true);
    invoke(method, null, root);
  }

  @SuppressWarnings("unchecked")
  private static <T> T invoke(Method method, Object target, Object... args) throws Exception {
    try {
      return (T) method.invoke(target, args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw e;
    }
  }

  private static byte[] unixModeAttributes(int unixMode) {
    int externalAttributes = unixMode << 16;
    return new byte[] {
        (byte) (externalAttributes & 0xff),
        (byte) ((externalAttributes >>> 8) & 0xff),
        (byte) ((externalAttributes >>> 16) & 0xff),
        (byte) ((externalAttributes >>> 24) & 0xff)
    };
  }

  private static void setExternalFileAttributes(FileHeader header, byte[] attributes) throws Exception {
    Method setter = FileHeader.class.getMethod("setExternalFileAttributes", byte[].class);
    setter.invoke(header, (Object) attributes);
  }

  private boolean supportsSymlinks() {
    Path target = tempDir.resolve("symlink-target.txt");
    Path link = tempDir.resolve("symlink-test.txt");
    try {
      Files.writeString(target, "test", StandardCharsets.UTF_8);
      Files.createSymbolicLink(link, target);
      return Files.isSymbolicLink(link);
    } catch (UnsupportedOperationException | IOException | SecurityException e) {
      return false;
    } finally {
      deleteIfExists(link);
      deleteIfExists(target);
    }
  }

  private static void deleteIfExists(Path path) {
    try {
      if (path != null && Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
          Files.walk(path)
               .sorted(Comparator.reverseOrder())
               .forEach(ActionThreadZipSecurityTest::deleteIfExists);
        } else {
          Files.deleteIfExists(path);
        }
      }
    } catch (IOException ignored) {
      // best-effort cleanup for test helper only
    }
  }

  private static final class ZipItem {
    private final String name;

    private final String content;

    private ZipItem(String name, String content) {
      this.name = name;
      this.content = content;
    }

    private String name() {
      return name;
    }

    private String content() {
      return content;
    }
  }
}
