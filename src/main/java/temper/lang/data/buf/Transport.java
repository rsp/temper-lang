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

import temper.lang.basic.CodeUnitType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Abstracts away buffer copies and reads.
 */
abstract class Transport<ELEMENT, SLICE, MUT_STORAGE, IMU_STORAGE> {
  abstract MUT_STORAGE ensureCapacity(MUT_STORAGE storage, int n);

  abstract int lengthOfImu(IMU_STORAGE storage);

  abstract int lengthOfMut(MUT_STORAGE storage);

  abstract void truncate(MUT_STORAGE storage, int length);

  abstract int moveFromImu(IMU_STORAGE source, int si, MUT_STORAGE destination, int di, int n);

  abstract int moveFromMut(MUT_STORAGE source, int si, MUT_STORAGE destination, int di, int n);

  abstract IMU_STORAGE freeze(MUT_STORAGE storage, int left, int right);

  abstract ELEMENT readFromImu(IMU_STORAGE storage, int i);

  abstract ELEMENT readFromMut(MUT_STORAGE storage, int i);

  abstract int bulkReadFromImu(IMU_STORAGE source, int si, SLICE dest, int di, int n);

  abstract int bulkReadFromMut(MUT_STORAGE source, int si, SLICE dest, int di, int n);

  abstract boolean write(MUT_STORAGE storage, int i, ELEMENT element);

  abstract int insert(MUT_STORAGE storage, int i, SLICE slice, int left, int right);

  abstract MUT_STORAGE createMutStorage();
}

final class ReferenceTransport<T>
    extends Transport<T, List<T>, ArrayList<T>, ImmutableList<T>> {

  @Override
  ArrayList<T> ensureCapacity(ArrayList<T> storage, int n) {
    storage.ensureCapacity(n);
    return storage;
  }

  @Override
  int lengthOfImu(ImmutableList<T> storage) {
    return storage.size();
  }

  @Override
  int lengthOfMut(ArrayList<T> storage) {
    return storage.size();
  }

  void truncate(ArrayList<T> storage, int length) {
    storage.subList(length, storage.size()).clear();
  }

  @Override
  int moveFromImu(ImmutableList<T> source, int si, ArrayList<T> destination, int di, int n) {
    destination.addAll(di, source.subList(si, (si + n)));
    return n;
  }

  @Override
  int moveFromMut(ArrayList<T> source, int si, ArrayList<T> destination, int di, int n) {
    destination.addAll(di, source.subList(si, (si + n)));
    return n;
  }

  @Override
  ImmutableList<T> freeze(ArrayList<T> storage, int left, int right) {
    return ImmutableList.copyOf(storage.subList(left, right));
  }

  @Override
  T readFromImu(ImmutableList<T> ts, int i) {
    return ts.get(i);
  }

  @Override
  T readFromMut(ArrayList<T> ts, int i) {
    return ts.get(i);
  }

  @Override
  int bulkReadFromImu(ImmutableList<T> source, int si, List<T> dest, int di, int n) {
    return bulkRead(source, si, dest, di, n);
  }

  @Override
  int bulkReadFromMut(ArrayList<T> source, int si, List<T> dest, int di, int n) {
    return bulkRead(source, si, dest, di, n);
  }

  private int bulkRead(List<T> source, int si, List<T> dest, int di, int n) {
    n = Math.min(n, source.size() - si);
    int len = dest.size();
    if (len > di) {
      dest.subList(di, Math.min(di + n, len)).clear();
    }
    dest.addAll(di, source.subList(si, si + n));
    return n;
  }

  @Override
  boolean write(ArrayList<T> ts, int i, T t) {
    ts.add(i, t);
    return true;
  }

  @Override
  int insert(ArrayList<T> ts, int i, List<T> slice, int left, int right) {
    int len = ts.size();
    List<T> elements;
    if (left == 0 && right == slice.size()) {
      elements = slice;
    } else {
      elements = slice.subList(left, right);
    }
    ts.addAll(i, elements);
    return ts.size() - len;
  }

  ArrayList<T> createMutStorage() {
    return new ArrayList<>();
  }
}

abstract class ValueTransport<ELEMENT, SLICE, MUT_STORAGE, IMU_STORAGE>
extends Transport<ELEMENT, SLICE, MUT_STORAGE, IMU_STORAGE> {
  final CodeUnitType codeUnitType;

  ValueTransport(CodeUnitType codeUnitType) {
    this.codeUnitType = codeUnitType;
  }
}

class ByteData {
  protected byte[] arr;
  protected int limit;

  ByteData(byte[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  byte read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, byte[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableByteData extends ByteData {
  WritableByteData(byte[] bytes, int limit) {
    super(bytes, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      byte[] narr = new byte[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, ByteData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, ByteBuffer source) {
    ByteBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, byte x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, byte[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class ByteTransport extends ValueTransport<Byte, byte[], WritableByteData, ByteData> {
  private static final byte[] EMPTY = new byte[0];

  ByteTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableByteData ensureCapacity(WritableByteData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(ByteData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableByteData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableByteData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(ByteData source, int si, WritableByteData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableByteData source, int si, WritableByteData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  ByteData freeze(WritableByteData data, int left, int right) {
    int n = right - left;
    byte[] bytes = new byte[n];
    data.copyTo(left, bytes, 0, n);
    return new ByteData(bytes, n);
  }

  @Override
  Byte readFromImu(ByteData data, int i) {
    return data.read(i);
  }

  @Override
  Byte readFromMut(WritableByteData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(ByteData source, int si, byte[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableByteData source, int si, byte[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableByteData data, int i, Byte x) {
    data.set(i, x.byteValue());
    return true;
  }

  @Override
  int insert(WritableByteData data, int destIndex, byte[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableByteData createMutStorage() {
    return new WritableByteData(EMPTY, 0);
  }
}

class CharData {
  protected char[] arr;
  protected int limit;

  CharData(char[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  char read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, char[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableCharData extends CharData {
  WritableCharData(char[] chars, int limit) {
    super(chars, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      char[] narr = new char[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, CharData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, CharBuffer source) {
    CharBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, char x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, char[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class CharTransport
  extends ValueTransport<Character, char[], WritableCharData, CharData> {
  private static final char[] EMPTY = new char[0];

  CharTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  int lengthOfImu(CharData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableCharData storage) {
    return storage.length();
  }

  @Override
  WritableCharData ensureCapacity(WritableCharData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  void truncate(WritableCharData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(CharData source, int si, WritableCharData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableCharData source, int si, WritableCharData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  CharData freeze(WritableCharData data, int left, int right) {
    int n = right - left;
    char[] chars = new char[n];
    data.copyTo(left, chars, 0, n);
    return new CharData(chars, n);
  }

  @Override
  Character readFromImu(CharData data, int i) {
    return data.read(i);
  }

  @Override
  Character readFromMut(WritableCharData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(CharData source, int si, char[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableCharData source, int si, char[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableCharData data, int i, Character x) {
    data.set(i, x.charValue());
    return true;
  }

  @Override
  int insert(WritableCharData data, int destIndex, char[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableCharData createMutStorage() {
    return new WritableCharData(EMPTY, 0);
  }
}

class ShortData {
  protected short[] arr;
  protected int limit;

  ShortData(short[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  short read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, short[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableShortData extends ShortData {
  WritableShortData(short[] shorts, int limit) {
    super(shorts, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      short[] narr = new short[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, ShortData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, ShortBuffer source) {
    ShortBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, short x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, short[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class ShortTransport
  extends ValueTransport<Short, short[], WritableShortData, ShortData> {
  private static final short[] EMPTY = new short[0];

  ShortTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableShortData ensureCapacity(WritableShortData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(ShortData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableShortData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableShortData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(ShortData source, int si, WritableShortData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableShortData source, int si, WritableShortData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  ShortData freeze(WritableShortData data, int left, int right) {
    int n = right - left;
    short[] shorts = new short[n];
    data.copyTo(left, shorts, 0, n);
    return new ShortData(shorts, n);
  }

  @Override
  Short readFromImu(ShortData data, int i) {
    return data.read(i);
  }

  @Override
  Short readFromMut(WritableShortData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(ShortData source, int si, short[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableShortData source, int si, short[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableShortData data, int i, Short x) {
    data.set(i, x.shortValue());
    return true;
  }

  @Override
  int insert(WritableShortData data, int destIndex, short[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableShortData createMutStorage() {
    return new WritableShortData(EMPTY, 0);
  }
}

class IntData {
  protected int[] arr;
  protected int limit;

  IntData(int[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  int read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, int[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableIntData extends IntData {
  WritableIntData(int[] ints, int limit) {
    super(ints, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      int[] narr = new int[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, IntData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, IntBuffer source) {
    IntBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, int x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, int[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class IntTransport
  extends ValueTransport<Integer, int[], WritableIntData, IntData> {
  private static final int[] EMPTY = new int[0];

  IntTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableIntData ensureCapacity(WritableIntData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(IntData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableIntData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableIntData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(IntData source, int si, WritableIntData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableIntData source, int si, WritableIntData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  IntData freeze(WritableIntData data, int left, int right) {
    int n = right - left;
    int[] ints = new int[n];
    data.copyTo(left, ints, 0, n);
    return new IntData(ints, n);
  }

  @Override
  Integer readFromImu(IntData data, int i) {
    return data.read(i);
  }

  @Override
  Integer readFromMut(WritableIntData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(IntData source, int si, int[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableIntData source, int si, int[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableIntData data, int i, Integer x) {
    data.set(i, x.intValue());
    return true;
  }

  @Override
  int insert(WritableIntData data, int destIndex, int[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableIntData createMutStorage() {
    return new WritableIntData(EMPTY, 0);
  }
}

class LongData {
  protected long[] arr;
  protected int limit;

  LongData(long[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  long read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, long[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableLongData extends LongData {
  WritableLongData(long[] longs, int limit) {
    super(longs, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      long[] narr = new long[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, LongData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, LongBuffer source) {
    LongBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, long x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, long[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class LongTransport
  extends ValueTransport<Long, long[], WritableLongData, LongData> {
  private static final long[] EMPTY = new long[0];

  LongTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableLongData ensureCapacity(WritableLongData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(LongData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableLongData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableLongData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(LongData source, int si, WritableLongData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableLongData source, int si, WritableLongData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  LongData freeze(WritableLongData data, int left, int right) {
    int n = right - left;
    long[] longs = new long[n];
    data.copyTo(left, longs, 0, n);
    return new LongData(longs, n);
  }

  @Override
  Long readFromImu(LongData data, int i) {
    return data.read(i);
  }

  @Override
  Long readFromMut(WritableLongData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(LongData source, int si, long[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableLongData source, int si, long[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableLongData data, int i, Long x) {
    data.set(i, x.longValue());
    return true;
  }

  @Override
  int insert(WritableLongData data, int destIndex, long[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableLongData createMutStorage() {
    return new WritableLongData(EMPTY, 0);
  }
}

class FloatData {
  protected float[] arr;
  protected int limit;

  FloatData(float[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  float read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, float[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableFloatData extends FloatData {
  WritableFloatData(float[] floats, int limit) {
    super(floats, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      float[] narr = new float[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, FloatData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, FloatBuffer source) {
    FloatBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, float x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, float[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class FloatTransport
  extends ValueTransport<Float, float[], WritableFloatData, FloatData> {
  private static final float[] EMPTY = new float[0];

  FloatTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableFloatData ensureCapacity(WritableFloatData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(FloatData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableFloatData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableFloatData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(FloatData source, int si, WritableFloatData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableFloatData source, int si, WritableFloatData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  FloatData freeze(WritableFloatData data, int left, int right) {
    int n = right - left;
    float[] floats = new float[n];
    data.copyTo(left, floats, 0, n);
    return new FloatData(floats, n);
  }

  @Override
  Float readFromImu(FloatData data, int i) {
    return data.read(i);
  }

  @Override
  Float readFromMut(WritableFloatData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(FloatData source, int si, float[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableFloatData source, int si, float[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableFloatData data, int i, Float x) {
    data.set(i, x.floatValue());
    return true;
  }

  @Override
  int insert(WritableFloatData data, int destIndex, float[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableFloatData createMutStorage() {
    return new WritableFloatData(EMPTY, 0);
  }
}

class DoubleData {
  protected double[] arr;
  protected int limit;

  DoubleData(double[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  double read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, double[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableDoubleData extends DoubleData {
  WritableDoubleData(double[] doubles, int limit) {
    super(doubles, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      double[] narr = new double[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, DoubleData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, DoubleBuffer source) {
    DoubleBuffer bb = source.duplicate();
    int n = bb.capacity();
    bb.get(arr, 0, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void set(int i, double x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, double[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class DoubleTransport
  extends ValueTransport<Double, double[], WritableDoubleData, DoubleData> {
  private static final double[] EMPTY = new double[0];

  DoubleTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableDoubleData ensureCapacity(WritableDoubleData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(DoubleData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableDoubleData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableDoubleData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(DoubleData source, int si, WritableDoubleData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableDoubleData source, int si, WritableDoubleData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  DoubleData freeze(WritableDoubleData data, int left, int right) {
    int n = right - left;
    double[] doubles = new double[n];
    data.copyTo(left, doubles, 0, n);
    return new DoubleData(doubles, n);
  }

  @Override
  Double readFromImu(DoubleData data, int i) {
    return data.read(i);
  }

  @Override
  Double readFromMut(WritableDoubleData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(DoubleData source, int si, double[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableDoubleData source, int si, double[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableDoubleData data, int i, Double x) {
    data.set(i, x.doubleValue());
    return true;
  }

  @Override
  int insert(WritableDoubleData data, int destIndex, double[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableDoubleData createMutStorage() {
    return new WritableDoubleData(EMPTY, 0);
  }
}

class BooleanData {
  protected boolean[] arr;
  protected int limit;

  BooleanData(boolean[] arr, int limit) {
    this.arr = arr;
    this.limit = limit;
  }

  boolean read(int i) {
    Preconditions.checkArgument(i < limit);
    return arr[i];
  }

  int length() {
    return limit;
  }

  void copyTo(int srcIndex, boolean[] dest, int destIndex, int n) {
    System.arraycopy(arr, srcIndex, dest, destIndex, n);
  }
}

final class WritableBooleanData extends BooleanData {
  WritableBooleanData(boolean[] booleans, int limit) {
    super(booleans, limit);
  }

  void ensureCapacity(int cap) {
    int ocap = arr.length;
    if (cap > ocap) {
      boolean[] narr = new boolean[Math.max(cap, ocap << 1)];
      System.arraycopy(arr, 0, narr, 0, limit);
      this.arr = narr;
    }
  }

  void copyFrom(int destIndex, BooleanData source, int sourceIndex, int n) {
    System.arraycopy(source.arr, sourceIndex, arr, destIndex, n);
    this.limit = Math.max(this.limit, destIndex + n);
  }

  void copyFrom(int destIndex, ByteBuffer source) {
    ByteBuffer bb = source.duplicate();
    int limit = bb.limit();
    int j = destIndex;
    for (int i = source.position(); i < limit; ++i) {
      byte b = source.get(i);
      arr[j++] = ((b >>> 7) & 1) != 0;
      arr[j++] = ((b >>> 6) & 1) != 0;
      arr[j++] = ((b >>> 5) & 1) != 0;
      arr[j++] = ((b >>> 4) & 1) != 0;
      arr[j++] = ((b >>> 3) & 1) != 0;
      arr[j++] = ((b >>> 2) & 1) != 0;
      arr[j++] = ((b >>> 1) & 1) != 0;
      arr[j++] = (b & 1) != 0;
    }

    this.limit = Math.max(this.limit, j);
  }

  void set(int i, boolean x) {
    if (i == limit) {
      Preconditions.checkState(limit < arr.length);
      ++limit;
    } else {
      Preconditions.checkArgument(i < limit);
    }
    arr[i] = x;
  }

  void setLimit(int limit) {
    Preconditions.checkArgument(limit >= 0 && limit <= arr.length);
    this.limit = limit;
  }

  void insert(int destIndex, boolean[] source, int left, int right) {
    Preconditions.checkArgument(source != arr && destIndex <= limit);
    int n = right - left;
    if (n == 0) {
      return;
    }
    if (destIndex != limit) {
      System.arraycopy(arr, destIndex, arr, destIndex + n, limit - destIndex);
    }
    System.arraycopy(source, left, arr, destIndex, n);
    limit += n;
  }
}

final class BooleanTransport
  extends ValueTransport<Boolean, boolean[], WritableBooleanData, BooleanData> {
  private static final boolean[] EMPTY = new boolean[0];

  BooleanTransport(CodeUnitType codeUnitType) {
    super(codeUnitType);
  }

  @Override
  WritableBooleanData ensureCapacity(WritableBooleanData data, int n) {
    data.ensureCapacity(n);
    return data;
  }

  @Override
  int lengthOfImu(BooleanData storage) {
    return storage.length();
  }

  @Override
  int lengthOfMut(WritableBooleanData storage) {
    return storage.length();
  }

  @Override
  void truncate(WritableBooleanData storage, int length) {
    storage.setLimit(length);
  }

  @Override
  int moveFromImu(BooleanData source, int si, WritableBooleanData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  int moveFromMut(WritableBooleanData source, int si, WritableBooleanData destination, int di, int n) {
    destination.copyFrom(di, source, si, n);
    return n;
  }

  @Override
  BooleanData freeze(WritableBooleanData data, int left, int right) {
    int n = right - left;
    boolean[] booleans = new boolean[n];
    data.copyTo(left, booleans, 0, n);
    return new BooleanData(booleans, n);
  }

  @Override
  Boolean readFromImu(BooleanData data, int i) {
    return data.read(i);
  }

  @Override
  Boolean readFromMut(WritableBooleanData data, int i) {
    return data.read(i);
  }

  @Override
  int bulkReadFromImu(BooleanData source, int si, boolean[] dest, int di, int n) {
    n = Math.min(source.limit - si, n);
    source.copyTo(si, dest, di, n);
    return n;
  }

  @Override
  int bulkReadFromMut(WritableBooleanData source, int si, boolean[] dest, int di, int n) {
    return bulkReadFromImu(source, si, dest, di, n);
  }

  @Override
  boolean write(WritableBooleanData data, int i, Boolean x) {
    data.set(i, x.booleanValue());
    return true;
  }

  @Override
  int insert(WritableBooleanData data, int destIndex, boolean[] source, int left, int right) {
    data.insert(destIndex, source, left, right);
    return right - left;
  }

  @Override
  WritableBooleanData createMutStorage() {
    return new WritableBooleanData(EMPTY, 0);
  }
}
