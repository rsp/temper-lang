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

package buffy.lang.diagnostic;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.logging.Level;

/**
 * A diagnostic message meant for a Buffy developer.
 */
public final class Diagnostic {
  public final Level level;
  public final String msg;
  public final ImmutableList<SourcePosition> pos;

  /**
   * @param level The severity of the message.
   * @param msg Developer readable message text.
   * @param pos Positions if any related to the message.
   */
  public Diagnostic(Level level, String msg, ImmutableList<SourcePosition> pos) {
    this.level = Preconditions.checkNotNull(level);
    this.msg = Preconditions.checkNotNull(msg);
    this.pos = Preconditions.checkNotNull(pos);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Diagnostic that = (Diagnostic) o;
    return Objects.equals(level, that.level)
            && Objects.equals(msg, that.msg)
            && Objects.equals(pos, that.pos);
  }

  @Override
  public int hashCode() {

    return Objects.hash(level, msg, pos);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(level);
    for (SourcePosition p : this.pos) {
      sb.append(':').append(p);
    }
    sb.append(": ").append(msg);
    return sb.toString();
  }
}
