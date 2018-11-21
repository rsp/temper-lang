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

public class IobufTest {

  static final Object A = new Object() { @Override public String toString() { return "A"; } };
  static final Object B = new Object() { @Override public String toString() { return "B"; } };
  static final Object C = new Object() { @Override public String toString() { return "C"; } };
  static final Object D = new Object() { @Override public String toString() { return "D"; } };

  @Test
  public void testRefBufInitialized() {
    Iobuf<Object, List<Object>> buf = BufferBuilder.builderForReferences(ImmutableList.of(A, B, C))
        .buildReadWriteBuf();
    testRefAbcBuf(buf);
  }

  @Test
  public void testRefBufWritten() {
    Iobuf<Object, List<Object>> buf = BufferBuilder.builderForReferences()
        .buildReadWriteBuf();
    buf.append(A);
    buf.appendSlice(ImmutableList.of(A, B, C, D),1, 3);
    testRefAbcBuf(buf);
  }

  private void testRefAbcBuf(Iobuf<Object, List<Object>> buf) {
    Iocur<Object, List<Object>> start = buf.start();
    Iocur<Object, List<Object>> end = buf.end();
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

    Optional<? extends Iocur<Object, List<Object>>> curPlus1Opt = start.advance();
    assertTrue(curPlus1Opt.isPresent());
    Iocur<Object, List<Object>> curPlus1 = curPlus1Opt.get();

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
  public void testCharBufInitialized() {
    Iobuf<Character, char[]> buf = BufferBuilder
        .builderForChars(CharBuffer.wrap(new char[]{'A', 'B', 'C'}))
        .buildReadWriteBuf();
    testCharAbcBuf(buf);
  }

  @Test
  public void testCharBufWritten() {
    Iobuf<Character, char[]> buf = BufferBuilder.builderForChars().buildReadWriteBuf();
    int newCap = buf.end().needCapacity(5);
    assertEquals(5, newCap);
    int nWritten = buf.appendSlice(new char[] { '0', 'A', 'B', 'C', 'D' }, 1, 3);
    assertEquals(2, nWritten);
    buf.append('C');
    testCharAbcBuf(buf);
  }

  private void testCharAbcBuf(Iobuf<Character, char[]> buf) {
    Iocur<Character, char[]> start = buf.start();
    Iocur<Character, char[]> end = buf.end();

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

    Optional<? extends Iocur<Character, char[]>> curPlus1Opt = start.advance();
    assertTrue(curPlus1Opt.isPresent());
    Iocur<Character, char[]> curPlus1 = curPlus1Opt.get();

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
