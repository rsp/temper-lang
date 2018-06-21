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

package buffy.lang.gather;

import com.google.common.collect.ImmutableMap;

import java.util.Optional;

public enum CodeBlockVariant {
  /** Buffy production code. */
  PRODUCTION("bff"),
  /** Privileged buffy test code. */
  TEST("bft"),
  /** Buffy example code that should pass. */
  EXAMPLE("bfe"),
  /** Buffy example code that should panic. */
  PANICS("bfp"),
  ;

  /**
   * The text that appears after the opening <code>```</code>.
   * As in
   * <pre>
   * ```fenceId
   * code goes here
   * ```
   * </pre>
   */
  public final String fenceId;

  /**
   * Constructor.
   * @param fenceId The text that appears after the opening <code>```</code>
   */
  CodeBlockVariant(String fenceId) {
    this.fenceId = fenceId;
  }

  private static final ImmutableMap<String, CodeBlockVariant> BY_FENCE_ID;

  static {
    ImmutableMap.Builder<String, CodeBlockVariant> b = ImmutableMap.builder();
    for (CodeBlockVariant v : values()) {
      b.put(v.fenceId, v);
    }
    BY_FENCE_ID = b.build();
  }

  /** The variant for the given fence block ID if any. */
  public static Optional<CodeBlockVariant> forFenceId(String fenceId) {
    return Optional.ofNullable(BY_FENCE_ID.get(fenceId));
  }

  @Override
  public String toString() {
    return fenceId;
  }
}
