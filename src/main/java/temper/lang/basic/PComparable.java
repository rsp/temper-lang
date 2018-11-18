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

package temper.lang.basic;

import java.util.Optional;

/**
 * Like a comparable but has a partial natural ordering.
 * Not all values may be meaningfully compared, like cursors that index
 * into different buffers.
 */
public interface PComparable<T> extends Comparable<T> {

  /** Like {#link #compareTo} but returns a {@link PComparison}. */
  public PComparison tcompareTo(T other);

  @Override
  public default int compareTo(T other) {
    return tcompareTo(other).intOption.get();
  }

  /** The result of a partial comparison. */
  public enum PComparison {
    LESS_THAN(Optional.of(-1)),
    EQUIVALENT(Optional.of(0)),
    GREATER_THAN(Optional.of(1)),
    UNRELATED(Optional.empty()),
    ;

    /** An equivalent Java {@link Comparable#compareTo} return value, if any. */
    public final Optional<Integer> intOption;

    PComparison(Optional<Integer> intOption) {
      this.intOption = intOption;
    }

    public static PComparison from(int cmp) {
      return cmp < 0
          ? LESS_THAN
          : cmp == 0
          ? EQUIVALENT
          : GREATER_THAN;
    }

    private static final PComparison[] NEGS;
    static {
      NEGS = values();
      NEGS[LESS_THAN.ordinal()] = GREATER_THAN;
      NEGS[GREATER_THAN.ordinal()] = LESS_THAN;
    }

    public PComparison neg() {
      return NEGS[ordinal()];
    }
  }
}
