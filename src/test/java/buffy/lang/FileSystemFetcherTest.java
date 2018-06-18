// Copyright 2018 Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the License);
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an AS IS BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package buffy.lang;

import com.google.common.base.Charsets;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.Optional;

public class FileSystemFetcherTest {

  private FileSystem fs;
  private FileSystemFetcher fetcher;

  void unix() {
    fs = Jimfs.newFileSystem(Configuration.unix());
    fetcher = new FileSystemFetcher(fs);
  }

  void win() {
    fs = Jimfs.newFileSystem(Configuration.windows());
    fetcher = new FileSystemFetcher(fs);
  }

  @After
  public void tearDown() throws IOException {
    fetcher = null;
    fs.close();
    fs = null;
  }

  @Test
  public final void testNoSuchFile() {
    unix();
    URI uri = URI.create("file:///foo/bar.md");
    Fetcher.Result result = fetcher.fetch(uri);
    assertEquals(uri, result.canonicalURI);
    assertEquals(Optional.empty(), result.source);
    assertEquals("[SEVERE:/foo/bar.md: Failed to read /foo/bar.md]", result.diagnostics.toString());
  }

  @Test
  public final void testFileThatExists() throws IOException {
    unix();
    Files.createDirectory(fs.getPath("/foo"));
    Files.write(fs.getPath("/foo/bar.md"), "# Hello, World!".getBytes(Charsets.UTF_8));

    URI uri = URI.create("file:///foo/bar.md");
    Fetcher.Result result = fetcher.fetch(uri);
    assertEquals("[]", result.diagnostics.toString());
    assertTrue(result.source.isPresent());
    assertEquals("# Hello, World!", result.source.get().contents);
    assertTrue(
            "URL is canonical: " + result.canonicalURI,
            result.canonicalURI.getPath().endsWith("/foo/bar.md")
                    && result.canonicalURI.getScheme().equals("jimfs"));

    // Check canonicalization
    URI complicatedUri = URI.create("file:///foo/./baz/../bar.md");
    Fetcher.Result otherResult = fetcher.fetch(uri);
    assertEquals(result, otherResult);
  }

  @Test
  public final void testMalformedFile() throws IOException {
    unix();
    Files.createDirectory(fs.getPath("/foo"));
    Files.write(fs.getPath("/foo/bar.md"), new byte[] { (byte) 0x80 });

    URI uri = URI.create("file:///foo/bar.md");
    Fetcher.Result result = fetcher.fetch(uri);
    assertEquals(
            "[SEVERE:/foo/bar.md: File /foo/bar.md was not well-formed UTF-8]",
            result.diagnostics.toString());
    assertFalse(result.source.isPresent());
  }

  @Test
  public final void testWinFile() throws IOException {
    win();
    Files.createDirectory(fs.getPath("C:\\foo"));
    Files.write(fs.getPath("C:\\foo\\bar.md"), "# Hello, World!".getBytes(Charsets.UTF_8));

    URI uri = URI.create("file:///C:/foo/bar.md");
    Fetcher.Result result = fetcher.fetch(uri);
    assertEquals("[]", result.diagnostics.toString());
    assertTrue(result.source.isPresent());
    assertEquals("# Hello, World!", result.source.get().contents);
    assertTrue(
            "URL is canonical: " + result.canonicalURI,
            result.canonicalURI.getPath().endsWith("/foo/bar.md")
                    && result.canonicalURI.getScheme().equals("jimfs"));
  }
}
