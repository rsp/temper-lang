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

/** A tri-state truth value where fail indicates question is not well phrased. */
public enum TBool {
  /** */
  FALSE,
  /** */
  TRUE,
  /** */
  FAIL,
  ;

  private static final TBool[] INVERSES;

  static {
    INVERSES = values();
    INVERSES[FALSE.ordinal()] = TRUE;
    INVERSES[TRUE.ordinal()] = FALSE;
    // FAIL breaks excluded middle.
  }

  /**
   * !FAIL -> FAIL.
   */
  public TBool not() {
    return INVERSES[ordinal()];
  }

  public static TBool of(boolean b) {
    return b ? TRUE : FALSE;
  }

}
