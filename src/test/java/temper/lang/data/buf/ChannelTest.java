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
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
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
              .buildChannel(capacity));
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
          BufferBuilder.builderForChars().buildChannel(capacity));
    }
  }


  private <T, SLICE>
  void runChannelAbcsTest(
      Function<Channel<T, SLICE>.Wbuf, Void> writer,
      Function<Channel<T, SLICE>.Rbuf, String> reader,
      Channel<T, SLICE> c)
  throws InterruptedException {
    final class ReadRunner implements Runnable {
      private String result;

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
    Assert.assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", readRunner.result);
    Assert.assertEquals(ImmutableList.of(), uce.raised);
    Assert.assertFalse(neededInterrupt);
  }
}
