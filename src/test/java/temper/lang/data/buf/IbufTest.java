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

package temper.lang.data.buf;

import com.google.common.collect.ImmutableList;
import static org.junit.Assert.*;
import org.junit.Test;
import temper.lang.basic.PComparable;
import temper.lang.basic.TBool;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class IbufTest {

  private static final Object
      A = new Object() { @Override public String toString() { return "A"; } },
      B = new Object() { @Override public String toString() { return "B"; } },
      C = new Object() { @Override public String toString() { return "C"; } };

  @Test
  public void testRefBuf() {
    Ibuf<Object, List<Object>> buf = BufferBuilder.builderForReferences(ImmutableList.of(A, B, C))
        .buildReadOnlyBuf();
    Icur<Object, List<Object>> start = buf.start();
    Icur<Object, List<Object>> end = buf.end();

    assertEquals("2", start.countBetweenExceeds(end, 2), TBool.TRUE);
    assertEquals("3", start.countBetweenExceeds(end, 3), TBool.TRUE);
    assertEquals("4", start.countBetweenExceeds(end, 4), TBool.FALSE);

    // Read 3, 4, 5
    for (int nToRead = 3; nToRead <= 5; ++nToRead){
      List<Object> ls = new ArrayList<>();
      ls.add(null);
      ls.add(null);
      ls.add(null);
      int nRead = start.readInto(ls, 0, nToRead);
      assertEquals("[A, B, C], 3", ls.toString() + ", " + nRead);
    }

    // Read 2
    {
      List<Object> ls = new ArrayList<>();
      ls.add(null);
      ls.add(null);
      ls.add(null);
      int nRead = start.readInto(ls, 0, 2);
      assertEquals("[A, B, null], 2", ls.toString() + ", " + nRead);
    }

    Optional<? extends Icur<Object, List<Object>>> curPlus1Opt = start.advance();
    assertTrue(curPlus1Opt.isPresent());
    Icur<Object, List<Object>> curPlus1 = curPlus1Opt.get();

    assertEquals("0", start.countBetweenExceeds(curPlus1, 0), TBool.TRUE);
    assertEquals("1", start.countBetweenExceeds(curPlus1, 1), TBool.TRUE);
    assertEquals("2", start.countBetweenExceeds(curPlus1, 2), TBool.FALSE);

    // Read 2 from +1
    {
      List<Object> ls = new ArrayList<>();
      ls.add(null);
      ls.add(null);
      ls.add(null);
      int nRead = curPlus1.readInto(ls, 0, 2);
      assertEquals("[B, C, null], 2", ls.toString() + ", " + nRead);
    }

    // Read 2 from +1 into 1
    {
      List<Object> ls = new ArrayList<>();
      ls.add(null);
      ls.add(null);
      ls.add(null);
      int nRead = curPlus1.readInto(ls, 1, 2);
      assertEquals("[null, B, C], 2", ls.toString() + ", " + nRead);
    }

    // Read past end
    {
      List<Object> ls = new ArrayList<>();
      ls.add(null);
      ls.add(null);
      ls.add(null);
      ls.add(null);
      int nRead = end.readInto(ls, 0, 4);
      assertEquals("[null, null, null, null], 0", ls.toString() + ", " + nRead);
    }

    // Cherrypick
    assertEquals(Optional.of(A), start.read());
    assertEquals(Optional.of(B), curPlus1.read());
    assertEquals(Optional.empty(), end.read());

    // Cursor equality
    assertEquals(Optional.of(curPlus1), start.advance(1));
    assertEquals(Optional.of(end), start.advance(3));
    assertEquals(Optional.empty(), start.advance(4));

    // Cursor comparison
    assertEquals(PComparable.PComparison.EQUIVALENT, start.tcompareTo(start));
    assertEquals(PComparable.PComparison.EQUIVALENT, end.tcompareTo(end));
    assertEquals(PComparable.PComparison.LESS_THAN, start.tcompareTo(end));
    assertEquals(PComparable.PComparison.UNRELATED,
        start.tcompareTo(BufferBuilder.builderForReferences().buildReadOnlyBuf().start()));
  }


  @Test
  public void testCharBuf() {
    Ibuf<Character, char[]> buf = BufferBuilder
        .builderForChars(CharBuffer.wrap(new char[] { 'A', 'B', 'C' }))
        .buildReadOnlyBuf();
    Icur<Character, char[]> start = buf.start();
    Icur<Character, char[]> end = buf.end();

    assertEquals("2", start.countBetweenExceeds(end, 2), TBool.TRUE);
    assertEquals("3", start.countBetweenExceeds(end, 3), TBool.TRUE);
    assertEquals("4", start.countBetweenExceeds(end, 4), TBool.FALSE);

    // Read 3, 4, 5
    for (int nToRead = 3; nToRead <= 5; ++nToRead){
      char[] ls = new char[] { '?', '?', '?' };
      int nRead = start.readInto(ls, 0, nToRead);
      assertEquals("[A, B, C], 3", Arrays.toString(ls) + ", " + nRead);
    }

    // Read 2
    {
      char[] ls = new char[] { '?', '?', '?' };
      int nRead = start.readInto(ls, 0, 2);
      assertEquals("[A, B, ?], 2", Arrays.toString(ls) + ", " + nRead);
    }

    Optional<? extends Icur<Character, char[]>> curPlus1Opt = start.advance();
    assertTrue(curPlus1Opt.isPresent());
    Icur<Character, char[]> curPlus1 = curPlus1Opt.get();

    assertEquals("0", start.countBetweenExceeds(curPlus1, 0), TBool.TRUE);
    assertEquals("1", start.countBetweenExceeds(curPlus1, 1), TBool.TRUE);
    assertEquals("2", start.countBetweenExceeds(curPlus1, 2), TBool.FALSE);

    // Read 2 from +1
    {
      char[] ls = new char[] { '?', '?', '?' };
      int nRead = curPlus1.readInto(ls, 0, 2);
      assertEquals("[B, C, ?], 2", Arrays.toString(ls) + ", " + nRead);
    }

    // Read 2 from +1 into 1
    {
      char[] ls = new char[] { '?', '?', '?' };
      int nRead = curPlus1.readInto(ls, 1, 2);
      assertEquals("[?, B, C], 2", Arrays.toString(ls) + ", " + nRead);
    }

    // Read past end
    {
      char[] ls = new char[] { '?', '?', '?', '?' };
      int nRead = end.readInto(ls, 0, 4);
      assertEquals("[?, ?, ?, ?], 0", Arrays.toString(ls) + ", " + nRead);
    }

    // Cherrypick
    assertEquals(Optional.of('A'), start.read());
    assertEquals(Optional.of('B'), curPlus1.read());
    assertEquals(Optional.empty(), end.read());

    // Cursor equality
    assertEquals(Optional.of(curPlus1), start.advance(1));
    assertEquals(Optional.of(end), start.advance(3));
    assertEquals(Optional.empty(), start.advance(4));

    // Cursor comparison
    assertEquals(PComparable.PComparison.EQUIVALENT, start.tcompareTo(start));
    assertEquals(PComparable.PComparison.EQUIVALENT, end.tcompareTo(end));
    assertEquals(PComparable.PComparison.LESS_THAN, start.tcompareTo(end));
    assertEquals(PComparable.PComparison.UNRELATED,
        start.tcompareTo(BufferBuilder.builderForChars().buildReadOnlyBuf().start()));
  }
}
