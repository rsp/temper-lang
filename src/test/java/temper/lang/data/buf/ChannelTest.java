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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class ChannelTest {

  @Test
  public void testWriteOneReadOneRefs() throws Exception {
    for (int capacity = 2; capacity <= 6; ++capacity) {
      runChannelAbcsTest(
          (Channel<String, List<String>>.Wbuf out) -> {
            for (char c = 'A'; c <= 'Z'; ++c) {
              out.append(new String(new char[]{c}));
              out.commit(out.end());
            }
            out.close();
            return null;
          },
          (Channel<String, List<String>>.Rbuf inp) -> {
            StringBuilder sb = new StringBuilder();
            Icur<String, List<String>> cur = inp.start();
            while (true) {
              Optional<String> str = cur.read();
              if (str.isPresent()) {
                sb.append(str.get());
                Optional<? extends Icur<String, List<String>>> next = cur.advance();
                if (next.isPresent()) {
                  cur = next.get();
                  inp.commit(cur);
                } else {
                  break;
                }
              } else {
                break;
              }
            }
            inp.close();
            return sb.toString();
          },
          BufferBuilder.<String>builderForReferences()
              .buildChannel(capacity),
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }
  }

  @Test
  public void testWriteOneReadOneChars() throws Exception {
    for (int capacity = 2; capacity <= 6; ++capacity) {
      runChannelAbcsTest(
          (Channel<Character, char[]>.Wbuf out) -> {
            for (char c = 'A'; c <= 'Z'; ++c) {
              out.append(c);
              out.commit(out.end());
            }
            out.close();
            return null;
          },
          (Channel<Character, char[]>.Rbuf inp) -> {
            StringBuilder sb = new StringBuilder();
            Icur<Character, char[]> cur = inp.start();
            while (true) {
              Optional<Character> str = cur.read();
              if (str.isPresent()) {
                sb.append(str.get().charValue());
                Optional<? extends Icur<Character, char[]>> next = cur.advance();
                if (next.isPresent()) {
                  cur = next.get();
                  inp.commit(cur);
                } else {
                  break;
                }
              } else {
                break;
              }
            }
            inp.close();
            return sb.toString();
          },
          BufferBuilder.builderForChars().buildChannel(capacity),
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }
  }

  @Test
  public void testWriteOneReadOneLongs() throws Exception {
    for (int capacity = 2; capacity <= 6; ++capacity) {
      runChannelAbcsTest(
          (Channel<Long, long[]>.Wbuf out) -> {
            for (long l = -10L; l <= 10L; ++l) {
              out.append(l);
              out.commit(out.end());
            }
            out.close();
            return null;
          },
          (Channel<Long, long[]>.Rbuf inp) -> {
            List<Long> longs = new ArrayList<>();
            Icur<Long, long[]> cur = inp.start();
            while (true) {
              Optional<Long> l = cur.read();
              if (l.isPresent()) {
                longs.add(l.get());
                Optional<? extends Icur<Long, long[]>> next = cur.advance();
                if (next.isPresent()) {
                  cur = next.get();
                  inp.commit(cur);
                } else {
                  break;
                }
              } else {
                break;
              }
            }
            inp.close();
            return longs;
          },
          BufferBuilder.builderForLongs().buildChannel(capacity),
          ImmutableList.of(
              -10L, -9L, -8L, -7L, -6L, -5L, -4L, -3L, -2L, -1L,
              0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
    }
  }

  @Test
  public void testWriteManyReadManyRefs() throws Exception {
    for (int capacity = 7; capacity <= 13; capacity += 2) {
      runChannelAbcsTest(
          (Channel<String, List<String>>.Wbuf out) -> {
            int nFromFirst = out.appendSlice(
                ImmutableList.of("_", "A", "B", "C", "D"),
                1, 5);
            Assert.assertEquals(4, nFromFirst);
            out.append("E");
            out.commit(out.end());

            final class Chunk {
              private final ImmutableList<String> data;
              private int left;
              private final int right;

              private Chunk(ImmutableList<String> data, int left, int right) {
                this.data = data;
                this.left = left;
                this.right = right;
              }

              private void drain() {
                while (left < right) {
                  int n = out.appendSlice(data, left, right);
                  Assert.assertTrue(n > 0);
                  left += n;
                  Assert.assertTrue(left <= right);
                  out.commit(out.end());
                }
              }
            }

            new Chunk(
                ImmutableList.of("E", "F", "G", "H", "I", "J", "K", "L", "M", "N"),
                1, 9)
                .drain();

            new Chunk(ImmutableList.of("N", "O", "P", "Q"),0, 4)
                .drain();
            new Chunk(ImmutableList.of("R", "S", "T", "U"),0, 4)
                .drain();
            new Chunk(ImmutableList.of("_", "V", "W", "X", "Y", "Z", "Z"), 1, 6)
                .drain();

            out.close();
            return null;
          },
          (Channel<String, List<String>>.Rbuf inp) -> {
            StringBuilder sb = new StringBuilder();
            @SuppressWarnings("MismatchedReadAndWriteOfArray")  // Written via strList.
            String[] strs = new String[5];
            List<String> strList = Arrays.asList(strs);
            Icur<String, List<String>> cur = inp.start();
            while (true) {
              int n = cur.readInto(strList, 1, 4);
              if (n == 0) {
                break;
              }
              for (int i = 0; i < n; ++i) {
                sb.append(strs[i + 1]);
              }
              Optional<? extends Icur<String, List<String>>> next = cur.advance(n);
              if (next.isPresent()) {
                cur = next.get();
                inp.commit(cur);
              } else {
                break;
              }
            }
            inp.close();
            return sb.toString();
          },
          BufferBuilder.<String>builderForReferences()
              .buildChannel(capacity),
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }
  }


  private <X, T, SLICE>
  void runChannelAbcsTest(
      Function<Channel<T, SLICE>.Wbuf, Void> writer,
      Function<Channel<T, SLICE>.Rbuf, X> reader,
      Channel<T, SLICE> c,
      X wanted)
  throws InterruptedException {
    final class ReadRunner implements Runnable {
      private X result;

      @Override
      public void run() {
        result = reader.apply(c.rbuf);
      }
    }
    ReadRunner readRunner = new ReadRunner();

    final class Uce implements Thread.UncaughtExceptionHandler {
      private final List<Throwable> raised = new ArrayList<>();

      @Override
      public void uncaughtException(Thread t, Throwable e) {
        raised.add(e);
      }
    }
    Uce uce = new Uce();

    Thread writerThread = new Thread(() -> writer.apply(c.wbuf));
    Thread readerThread = new Thread(readRunner);

    writerThread.setUncaughtExceptionHandler(uce);
    readerThread.setUncaughtExceptionHandler(uce);

    writerThread.start();
    readerThread.start();

    writerThread.join(1000);
    readerThread.join(1000);

    boolean neededInterrupt = false;
    if (writerThread.isAlive()) {
      System.err.print("Writer deadlocked\n\t");
      System.err.println(Joiner.on("\n\t").join(writerThread.getStackTrace()));
      System.err.println("Reader alive=" + readerThread.isAlive());
      writerThread.interrupt();
      neededInterrupt = true;
    }
    if (readerThread.isAlive()) {
      System.err.print("Reader deadlocked\n\t");
      System.err.println(Joiner.on("\n\t").join(readerThread.getStackTrace()));
      readerThread.interrupt();
      neededInterrupt = true;
    }

    for (Throwable th : uce.raised) {
      th.printStackTrace();
    }

    Assert.assertTrue(c.isClosed());
    Assert.assertEquals(wanted, readRunner.result);
    Assert.assertEquals(ImmutableList.of(), uce.raised);
    Assert.assertFalse(neededInterrupt);
  }


}
