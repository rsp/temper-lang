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

import buffy.lang.diagnostic.Diagnostic;
import buffy.lang.diagnostic.SourcePosition;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;

/**
 * A fetcher that resolves {@code file:} URLs using a FileSystem.
 */
final class FileSystemFetcher implements Fetcher {
  private final FileSystem fs;

  public FileSystemFetcher() {
    this(FileSystems.getDefault());
  }

  public FileSystemFetcher(FileSystem fs) {
    this.fs = Preconditions.checkNotNull(fs);
  }

  public Result fetch(URI uri) {
    if (!uri.isAbsolute() || !"file".equals(uri.getScheme())) {
      return newErrorResult(uri, "Expected absolute file path");
    }
    // TODO: look up roots if the path starts with C| or similar.
    String[] parts = uri.getPath().split("(?<!^)/");
    String first = parts[0];
    String[] rest = Arrays.copyOfRange(parts, 1, parts.length);
    Path p = fs.getPath(first, rest);

    byte[] bytes;
    java.nio.file.attribute.FileTime timestamp;
    Path canonPath;
    try {
      bytes = Files.readAllBytes(p);
      timestamp = Files.getLastModifiedTime(p);
      canonPath = p.toRealPath();
    } catch (IOException ex) {
      return newErrorResult(uri, "Failed to read " + p + ": " + ex.getMessage());
    }

    ByteString hash;
    String algo = "SHA-512";
    try {
      MessageDigest md = MessageDigest.getInstance(algo);
      md.update(bytes);
      hash = ByteString.copyFrom(md.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new AssertionError(ex);
    }

    String content;
    try {
      content = new String(bytes, "UTF-8");
    } catch (IOException ex) {
      return newErrorResult(uri, "File " + p + " was not well-formed UTF-8: " + ex.getMessage());
    }
    return new Result(
            canonPath.toUri(),
            Optional.of(
                    new Source(content,
                            new Metadata(
                                    timestamp.toMillis(),
                                    new Hash(hash, algo)))),
            ImmutableList.of());
  }

  private Result newErrorResult(URI uri, String message) {
    return new Result(
            uri, Optional.empty(),
            ImmutableList.of(new Diagnostic(
                    Level.SEVERE, message, ImmutableList.of(new SourcePosition(uri, 0, 0)))));
  }
}
