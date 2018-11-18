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

import com.google.common.base.Preconditions;

/**
 * A way of finding element boundaries in a series of octets and interpreting
 * the bits between those boundaries.
 */
public enum CodeUnitType {
  BIT(1, 1, Boolean.TYPE),
  BYTE(8, 8, Byte.TYPE),
  UTF8(8, 32, Integer.TYPE),
  UTF16(16, 16, Character.TYPE),
  UTF32(32, 32, Integer.TYPE),
  INT32(32, 32, Integer.TYPE),
  FLOAT32(32, 32, Float.TYPE),
  INT64(64, 64, Long.TYPE),
  FLOAT64(64, 64, Double.TYPE),
  ;

  public final int minBitWidth;
  public final int maxBitWidth;
  public final Class<?> primType;

  CodeUnitType(int minBitWidth, int maxBitWidth, Class<?> primType) {
    Preconditions.checkArgument(minBitWidth > 0 && minBitWidth <= maxBitWidth);
    this.minBitWidth = minBitWidth;
    this.maxBitWidth = maxBitWidth;
    this.primType = Preconditions.checkNotNull(primType);
  }

  public boolean isOctetAligned() {
    return 0 == ((minBitWidth | maxBitWidth) & 7);
  }

  public boolean isFixedWidth() {
    return minBitWidth == maxBitWidth;
  }
}
