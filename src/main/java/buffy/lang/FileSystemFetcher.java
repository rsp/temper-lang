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
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.MalformedInputException;
import java.nio.file.*;
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
    String[] parts = uri.getPath().split("(?<!^)/");
    String first = parts[0];
    String[] rest = Arrays.copyOfRange(parts, 1, parts.length);
    if ("\\".equals(fs.getSeparator()) && first.startsWith("/") && first.endsWith(":")) {
      // "/C:" -> "C:"
      first = first.substring(1);
    }
    Path path = fs.getPath(first, rest);

    byte[] bytes;
    java.nio.file.attribute.FileTime timestamp;
    Path canonPath;
    try {
      bytes = Files.readAllBytes(path);
      timestamp = Files.getLastModifiedTime(path);
      canonPath = path.toRealPath();
    } catch (IOException ex) {
      String suffix = (ex instanceof FileNotFoundException || ex instanceof NoSuchFileException)
              ? "" : ": " + ex;
      return newErrorResult(uri, "Failed to read " + path + suffix);
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
      content = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    } catch (IOException ex) {
      String suffix = ex instanceof MalformedInputException ? "" : ": " + ex.toString();
      return newErrorResult(uri, "File " + path + " was not well-formed UTF-8" + suffix);
    }

    return new Result(
            canonPath.toUri(),
            Optional.of(
                    new Source(
                            content,
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
