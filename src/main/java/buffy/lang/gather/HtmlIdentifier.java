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

package buffy.lang.gather;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashSet;
import java.util.Set;

/**
 * An HTML identifier which corresponds to a document fragment.
 */
public final class HtmlIdentifier {

  /** The text of the identifier. */
  public final String text;
  /**
   * True if this identifier was ambiguous with a previously seen identifier
   * so might be less stable w.r.t. edits.
   */
  public final boolean ambiguous;

  HtmlIdentifier(String text, boolean ambiguous) {
    this.text = Preconditions.checkNotNull(text);
    this.ambiguous = ambiguous;
  }

  /**
   * Auto assigns HTML identifiers based on header text.
   * This is stateful so that it can disambiguate headers with equivalent text
   * and should be applied to header texts in the order they appears lexically.
   */
  @NotThreadSafe
  public static class Assigner {

    private final Set<String> assigned = new HashSet<>();

    private static long TYPE_BITS =
            (1L << Character.COMBINING_SPACING_MARK)
                    | (1L << Character.NON_SPACING_MARK)
                    | (1L << Character.LOWERCASE_LETTER)
                    | (1L << Character.MODIFIER_LETTER)
                    | (1L << Character.OTHER_LETTER)
                    | (1L << Character.TITLECASE_LETTER)
                    | (1L << Character.UPPERCASE_LETTER)
                    | (1L << Character.DECIMAL_DIGIT_NUMBER)
                    | (1L << Character.LETTER_NUMBER)
                    | (1L << Character.OTHER_NUMBER);

    private static final String REPLACEMENTS;

    static {
      // Reverse engineered from GH flavored markdown.
      char[] chars = new char[256];
      chars[' '] = chars['-'] = '-';
      chars['_'] = '_';
      for (int i = '0'; i <= '9'; ++i) {
        chars[i] = (char) i;
      }
      for (int i = 'a'; i <= 'z'; ++i) {
        chars[i] = (char) i;
        chars[i & ~32] = (char) i;
      }
      chars[170] = (char) 170;
      chars[181] = (char) 181;
      chars[186] = (char) 186;
      // Does not include fractions and superscripts for whatever reason.
      for (int i = 192; i < 256; ++i) {
        if (Character.isLetterOrDigit(i)) {
          chars[i] = (char) i;
        }
      }
      chars[215] = (char) 215;
      chars[247] = (char) 247;
      REPLACEMENTS = new String(chars);
    }

    /** Autoassigns an identifier. */
    public HtmlIdentifier assignIdentifier(String decodedInnerText) {
      StringBuilder sb = new StringBuilder(decodedInnerText.trim());
      int pos = 0;
      for (int i = 0, n = sb.length(); i < n; ++i) {
        char ch = sb.charAt(i);
        char repl = 0;
        if (ch < 256) {
          repl = REPLACEMENTS.charAt(ch);
        } else {
          int cp = Character.codePointAt(sb, i);
          int type = Character.getType(cp);
          if (((1L << type) & TYPE_BITS) != 0) {
            if (Character.isSupplementaryCodePoint(cp)) {
              sb.setCharAt(pos, ch);
              ++pos;
              ++i;
              sb.setCharAt(pos, sb.charAt(i));
              ++pos;
              continue;
            }
            repl = ch;
          }
        }
        if (repl != 0) {
          sb.setCharAt(pos, repl);
          ++pos;
        }
      }
      sb.setLength(pos);
      String id = sb.toString();
      boolean ambiguous = !assigned.add(id);
      if (ambiguous) {
        int counter = 1;
        sb.append('-');
        int length = sb.length();
        do {
          sb.setLength(length);
          sb.append(counter);
          ++counter;
          Preconditions.checkState(counter >= 0);
          id = sb.toString();
        } while (!assigned.add(id));
      }
      return new HtmlIdentifier(id, ambiguous);
    }
  }
}
