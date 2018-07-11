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

package temper.lang.gather;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HtmlIdentifierAssignerTest {

  /**
   * Derived from https://gist.github.com/mikesamuel/0c13f9105e13348fceb5aadcf6a1903d
   * by running
   * <pre>
   * const els = document.getElementsByTagName('article')[0].querySelectorAll('*[id]');
   * const pairs = [];
   * for (let el of els) {
   *   pairs.push([el.id.replace(/^user-content-/, ''), el.parentElement.innerText]);
   * }
   * function uhex4(c) {
   *   let cc = c.charCodeAt(0);
   *   let hex = cc.toString(16);
   *   return "\\u" + ("0000".substring(hex.length) + hex);
   * }
   * JSON.stringify(pairs, null, 2).replace(/[^ \n,\[\]\"0-z\\\-]/g, uhex4)
   * </pre>
   */
  private static final String[][] TESTCASES = {
          {
                  "ambiguous",
                  "Ambiguous",
          },
          {
                  "ambiguous-1",
                  "Ambiguous"
          },
          {
                  "ambiguous-2",
                  "Ambiguous"
          },
          {
                  "nfc-\u212b---\u00c5",
                  "NFC \u212b - \u00c5"
          },
          {
                  "nfkc-\u00c4\ufb03n---\u00c4ffin",
                  "NFKC \u00c4\ufb03n - \u00c4ffin"
          },
          {
                  "nfd-\u212b---a\u030a",
                  "NFD \u212b - A\u030a"
          },
          {
                  "nfkd-\u00c4\ufb03n---a\u0308ffin",
                  "NFKD \u00c4\ufb03n - A\u0308ffin"
          },
          {
                  "html-character-reference-in-header---abcd",
                  "HTML character reference in header - abcd"
          },
          {
                  "123-starts-with-numerals",
                  "123 starts with numerals"
          },
          {
                  "mixed-case-roman-numerals---\u2160-\u2161-\u2162-\u2163-\u2170-\u2171-\u2172-\u2173",
                  "Mixed case Roman numerals - \u2160 \u2161 \u2162 \u2163 \u2170 \u2171 \u2172 \u2173"
          },
          {
                  "extra-syntax--custom-id",
                  "Extra syntax \u0023 \u007b\u0023custom id\u007d"
          },
          {
                  "ascii-----0123456789abcdefghijklmnopqrstuvwxyz_abcdefghijklmnopqrstuvwxyz"
                  + "\u00aa\u00b5\u00ba\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7"
                  + "\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf"
                  + "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7"
                  + "\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df"
                  + "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7"
                  + "\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef"
                  + "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7"
                  + "\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff",

                  "ASCII -  \u0021\"\u0023\u0024\u0025\u0026\u0027"
                  + "\u0028\u0029\u002a\u002b,-\u002e\u002f0123456789"
                  + ":;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
                  + "abcdefghijklmnopqrstuvwxyz"
                  + "\u007b\u007c\u007d\u007e\u007f"
                  + "\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087"
                  + "\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f"
                  + "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097"
                  + "\u0098\u0099\u009a\u009b\u009c\u009d\u009e\u009f"
                  + "\u00a0\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7"
                  + "\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af"
                  + "\u00b0\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7"
                  + "\u00b8\u00b9\u00ba\u00bb\u00bc\u00bd\u00be\u00bf"
                  + "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7"
                  + "\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf"
                  + "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7"
                  + "\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df"
                  + "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7"
                  + "\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef"
                  + "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7"
                  + "\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff",
          },
          {
                  "non-ascii---zwnj-punctuation",
                  "Non-ASCII - ZWNJ=\u200c Punctuation=\u2042"
          },
          {
                  "lower-ascii---_____________-___",
                  "Lower ASCII - _!_\"_#_$_%_&_'_(_)_*_+_,_-_._/_",
          },
          {
                  "tab__",
                  "Tab_\t_",
          },
          {
                  "extra-spaces",
                  "  Extra Spaces  ",
          },
          {
                  "-dashes-",
                  "-dashes-",
          },
  };

  @Test
  public void testCornerCasesInSeries() {
    HtmlIdentifier.Assigner assigner = new HtmlIdentifier.Assigner();
    for (String[] pair : TESTCASES) {
      String want = pair[0];
      String htmlDecodedText = pair[1];
      String got = assigner.assignIdentifier(htmlDecodedText).text;
      assertEquals(htmlDecodedText, want, got);
    }
  }
}
