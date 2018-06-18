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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import java.net.URI;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Fetches source files.
 */
public interface Fetcher {
  /**
   * Given an absolute URI, tries to resolve it to source.
   * It is the caller's responsibility to resolve relative URIs to absolute using the containing source's absolute URL.
   */
  public Result fetch(URI uri);

  public static final class Result {
    public final URI canonicalURI;
    public final Optional<Source> source;
    public final ImmutableList<Diagnostic> diagnostics;

    Result(URI canonicalURI,
           Optional<Source> source,
           ImmutableList<Diagnostic> diagnostics) {
      this.canonicalURI = Preconditions.checkNotNull(canonicalURI);
      this.source = Preconditions.checkNotNull(source);
      this.diagnostics = Preconditions.checkNotNull(diagnostics);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Result result = (Result) o;
      return Objects.equals(canonicalURI, result.canonicalURI) &&
              Objects.equals(source, result.source) &&
              Objects.equals(diagnostics, result.diagnostics);
    }

    @Override
    public int hashCode() {
      return Objects.hash(canonicalURI, source, diagnostics);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Result{");
      sb.append("canonicalURI=").append(canonicalURI);
      if (source.isPresent()) {
        sb.append(", source='").append(source.get()).append('\'');
      }
      sb.append(", diagnostics=").append(diagnostics);
      sb.append('}');
      return sb.toString();
    }
  }

  public static final class Source {
    public final String contents;
    public final Metadata metadata;

    Source(String contents, Metadata metadata) {
      this.contents = Preconditions.checkNotNull(contents);
      this.metadata = Preconditions.checkNotNull(metadata);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Source source = (Source) o;
      return Objects.equals(contents, source.contents) &&
              Objects.equals(metadata, source.metadata);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contents, metadata);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Source{");
      String contentsAbbrev = contents.length() <= 16
              ? contents
              : contents.substring(0, 13) + "...";
      sb.append("contents='").append(contentsAbbrev).append('\'');
      sb.append(", metadata=").append(metadata);
      sb.append('}');
      return sb.toString();
    }
  }

  public static final class Metadata {
    public final long timestamp;
    public final Hash contentHash;

    Metadata(long timestamp, Hash contentHash) {
      this.timestamp = timestamp;
      this.contentHash = Preconditions.checkNotNull(contentHash);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Metadata metadata = (Metadata) o;
      return timestamp == metadata.timestamp &&
              Objects.equals(contentHash, metadata.contentHash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(timestamp, contentHash);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Metadata{");
      sb.append("timestamp=").append(timestamp);
      sb.append(", contentHash=").append(contentHash);
      sb.append('}');
      return sb.toString();
    }
  }

  public static final class Hash {
    public final ByteString bytes;
    public final String algo;

    Hash(ByteString bytes, String algo) {
      this.bytes = Preconditions.checkNotNull(bytes);
      this.algo = Preconditions.checkNotNull(algo);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Hash hash = (Hash) o;
      return Objects.equals(bytes, hash.bytes) &&
              Objects.equals(algo, hash.algo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(bytes, algo);
    }

    @Override
    public String toString() {
      return algo + ":" + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
  }
}
