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

public class BufferBuilder<T, SLICE> {

  /** Factory for buffers over arbitrary values. */
  public static <T> BufferBuilder<T, List<T>> builderForReferences() {
    return builderForReferences(ImmutableList.<T>of());
  }

  /** Factory for buffers over arbitrary values. */
  public static <T> BufferBuilder<T, List<T>> builderForReferences(
      Iterable<? extends T> initial) {
    ReferenceTransport<T> rt = new ReferenceTransport<>();
    ArrayList<T> storage = rt.createMutStorage();
    if (initial instanceof Collection<?>) {
      storage.addAll((Collection<? extends T>) initial);
    } else {
      for (T el : initial) {
        storage.add(el);
      }
    }
    return new BufferBuilder<>(rt, storage);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Boolean, boolean[]> builderForBits() {
    return builderForValuesInternal(null, CodeUnitType.BIT, boolean.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Boolean, boolean[]> builderForBits(ByteBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.BIT, boolean.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Byte, byte[]> builderForBytes() {
    return builderForValuesInternal(null, CodeUnitType.BYTE, byte.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Byte, byte[]> builderForBytes(ByteBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.BYTE, byte.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Character, char[]> builderForChars() {
    return builderForValuesInternal(null, CodeUnitType.UTF16, Character.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Character, char[]> builderForChars(CharBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.UTF16, Character.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Short, short[]> builderForShorts() {
    return builderForValuesInternal(null, CodeUnitType.UTF16, Short.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Short, short[]> builderForShorts(ShortBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.UTF16, Short.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Integer, int[]> builderForInts() {
    return builderForValuesInternal(null, CodeUnitType.INT32, Integer.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Integer, int[]> builderForInts(IntBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.INT32, Integer.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Integer, int[]> builderForUints() {
    return builderForValuesInternal(null, CodeUnitType.UTF32, Integer.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Integer, int[]> builderForUints(IntBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.UTF32, Integer.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Long, long[]> builderForLongs() {
    return builderForValuesInternal(null, CodeUnitType.INT64, Long.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Long, long[]> builderForLongs(LongBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.INT64, Long.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Float, float[]> builderForFloats() {
    return builderForValuesInternal(null, CodeUnitType.FLOAT32, Float.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Float, float[]> builderForFloats(FloatBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.FLOAT32, Float.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Double, double[]> builderForDoubles() {
    return builderForValuesInternal(null, CodeUnitType.FLOAT64, Double.class);
  }

  /** Factory for buffers over pass-by-value types. */
  public static BufferBuilder<Double, double[]> builderForDoubles(DoubleBuffer data) {
    return builderForValuesInternal(data, CodeUnitType.FLOAT64, Double.class);
  }

  public static <T, SLICE> BufferBuilder<T, SLICE> builderForValues(
      CodeUnitType codeUnitType, Class<? extends T> primType) {
    return builderForValuesInternal(null, codeUnitType, primType);
  }

  public static <T, SLICE> BufferBuilder<T, SLICE> builderForValues(
      Buffer buffer, CodeUnitType codeUnitType, Class<? extends T> primType) {
    return builderForValuesInternal(buffer, codeUnitType, primType);
  }

  private static <T, SLICE> BufferBuilder<T, SLICE> builderForValuesInternal(
      @Nullable Buffer buffer, CodeUnitType codeUnitType, Class<? extends T> primType) {
    if (primType == byte.class) {
      Preconditions.checkArgument(
          (buffer == null || buffer instanceof ByteBuffer)
          && codeUnitType.minBitWidth == 8);
      ByteTransport transport = new ByteTransport(codeUnitType);
      WritableByteData initialData = transport.createMutStorage();
      if (buffer != null) {
        ByteBuffer bb = (ByteBuffer) buffer;
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == char.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.UTF16);
      CharTransport transport = new CharTransport(codeUnitType);
      WritableCharData initialData = transport.createMutStorage();
      if (buffer != null) {
        CharBuffer bb = buffer instanceof CharBuffer
            ? ((CharBuffer) buffer)
            : ((ByteBuffer) buffer).asCharBuffer();
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == short.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.UTF16);
      ShortTransport transport = new ShortTransport(codeUnitType);
      WritableShortData initialData = transport.createMutStorage();
      if (buffer != null) {
        ShortBuffer bb = buffer instanceof ShortBuffer
            ? ((ShortBuffer) buffer)
            : ((ByteBuffer) buffer).asShortBuffer();
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == int.class) {
      Preconditions.checkArgument(
          codeUnitType == CodeUnitType.UTF32
          || codeUnitType == CodeUnitType.INT32);
      IntTransport transport = new IntTransport(codeUnitType);
      WritableIntData initialData = transport.createMutStorage();
      if (buffer != null) {
        IntBuffer bb = buffer instanceof IntBuffer
            ? ((IntBuffer) buffer)
            : ((ByteBuffer) buffer).asIntBuffer();
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == float.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.FLOAT32);
      FloatTransport transport = new FloatTransport(codeUnitType);
      WritableFloatData initialData = transport.createMutStorage();
      if (buffer != null) {
        FloatBuffer bb = buffer instanceof FloatBuffer
            ? ((FloatBuffer) buffer)
            : ((ByteBuffer) buffer).asFloatBuffer();
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == long.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.INT64);
      LongTransport transport = new LongTransport(codeUnitType);
      WritableLongData initialData = transport.createMutStorage();
      if (buffer != null) {
        LongBuffer bb = buffer instanceof LongBuffer
            ? ((LongBuffer) buffer)
            : ((ByteBuffer) buffer).asLongBuffer();
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == double.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.FLOAT64);
      DoubleTransport transport = new DoubleTransport(codeUnitType);
      WritableDoubleData initialData = transport.createMutStorage();
      if (buffer != null) {
        DoubleBuffer bb = buffer instanceof DoubleBuffer
            ? ((DoubleBuffer) buffer)
            : ((ByteBuffer) buffer).asDoubleBuffer();
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else if (primType == boolean.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.BIT);
      BooleanTransport transport = new BooleanTransport(codeUnitType);
      WritableBooleanData initialData = transport.createMutStorage();
      if (buffer != null) {
        ByteBuffer bb = (ByteBuffer) buffer;
        initialData.ensureCapacity(bb.remaining());
        initialData.copyFrom(0, bb);
      }
      @SuppressWarnings("unchecked")
      BufferBuilder<T, SLICE> newBuf =
        (BufferBuilder<T, SLICE>) new BufferBuilder<>(transport, initialData);
      return newBuf;
    } else {
      throw new AssertionError(
          "Cannot create buffer over " + primType + " from " + buffer);
    }
  }

  static final class BuilderState<ELEMENT, SLICE, MUT_STORAGE, IMU_STORAGE> {
    private final Transport<ELEMENT, SLICE, MUT_STORAGE, IMU_STORAGE> transport;
    private final MUT_STORAGE storage;

    BuilderState(Transport<ELEMENT, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
                 MUT_STORAGE storage) {
      this.transport = transport;
      this.storage = storage;
    }

    Ibuf<ELEMENT, SLICE> buildReadOnlyBuf() {
      return new Robuf<ELEMENT, SLICE>(
          transport, transport.freeze(storage, 0, transport.lengthOfMut(storage)));
    }

    Iobuf<ELEMENT, SLICE> buildReadWriteBuf() {
      return new Iobuf<ELEMENT, SLICE>(transport, storage);
    }

    Channel<ELEMENT, SLICE> buildChannel() {
      return new Channel<ELEMENT, SLICE>(transport, storage);
    }
  }

  private final BuilderState<T, SLICE, ?, ?> builderState;

  private <MUT, IMU> BufferBuilder(Transport<T, SLICE, MUT, IMU> transport, MUT storage) {
    this.builderState = new BuilderState<>(transport, storage);
  }

  public Ibuf<T, SLICE> buildReadOnlyBuf() {
    return builderState.buildReadOnlyBuf();
  }

  public Iobuf<T, SLICE> buildReadWriteBuf() {
    return builderState.buildReadWriteBuf();
  }

  public Channel<T, SLICE> buildChannel() {
    return builderState.buildChannel();
  }
}
