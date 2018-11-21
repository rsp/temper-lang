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

import temper.lang.diagnostic.Diagnostic;
import temper.lang.diagnostic.DiagnosticSink;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import org.junit.Test;

import static temper.lang.gather.CodeBlockVariant.EXAMPLE;
import static temper.lang.gather.CodeBlockVariant.PRODUCTION;
import static temper.lang.gather.CodeBlockVariant.TEST;
import static org.junit.Assert.assertEquals;

public final class MarkdownFileTest {

  private final Metadata testMetadata = new Metadata(
      0,
      new Hash(ByteString.copyFrom(new byte[]{(byte) 0, (byte) 1, (byte) 2}),
          "test-hash"));

  private final URI testUrl = URI.create("file:///d/foo.md");

  private MarkdownFile assertMarkdownFile(
      String[] inputLines,
      Map<String, URLReference> namedUrls,
      List<CodeDetails> codes,
      List<SectionDetails> secs,
      String... messages) {
    class CapturingDiagnosticSink implements DiagnosticSink {
      List<Diagnostic> diagnostics = new ArrayList<>();

      @Override
      public void report(Diagnostic d) {
        diagnostics.add(Preconditions.checkNotNull(d));
      }
    }

    CapturingDiagnosticSink sink = new CapturingDiagnosticSink();
    String input = Joiner.on('\n').join(inputLines);
    Source source = new Source(
        testUrl,
        input,
        testMetadata);
    MarkdownFile got = MarkdownFile.of(source, sink);
    assertEquals(namedUrls, got.namedUrls);
    List<CodeDetails> gotCodes = got.compUnits.stream()
        .map(x -> new CodeDetails(input, x))
        .collect(Collectors.toList());
    if (!codes.equals(gotCodes)) {
      assertEquals(
          Joiner.on('\n').join(codes),
          Joiner.on('\n').join(gotCodes));
      assertEquals(codes, gotCodes);
    }
    List<SectionDetails> gotSections = got.sections.stream()
        .map(SectionDetails::new)
        .collect(Collectors.toList());
    if (!secs.equals(gotSections)) {
      assertEquals(
          Joiner.on('\n').join(secs),
          Joiner.on('\n').join(gotSections));
      assertEquals(secs, gotSections);
    }
    assertEquals(
        Arrays.asList(messages),
        sink.diagnostics.stream().map(Diagnostic::toString)
            .collect(Collectors.toList()));

    for (int i = 0, n = got.compUnits.size(); i < n; ++i) {
      CompilationUnit cu = got.compUnits.get(i);
      assertEquals(Optional.of(got), cu.getContainingFile());
      assertEquals(i, cu.getIndexInContainingFile());
    }
    return got;
  }

  @Test
  public void testEmptyFile() {
    assertMarkdownFile(new String[]{}, ImmutableMap.of(), ImmutableList.of(), ImmutableList.of());
  }

  @Test
  public void testHeader() {
    assertMarkdownFile(
        new String[]{
            "# Hello World!"
        },
        ImmutableMap.of(), ImmutableList.of(),
        ImmutableList.of(
            new SectionDetails("Hello World!", "hello-world")));
  }

  @Test
  public void testHeaderWithExplicitId() {
    assertMarkdownFile(
        new String[]{
            "# Hello World! # {#foo}"
        },
        ImmutableMap.of(), ImmutableList.of(),
        ImmutableList.of(
            new SectionDetails("Hello World! # {#foo}", "foo")));
  }

  @Test
  public void testUrl() {
    MarkdownFile f = assertMarkdownFile(
        new String[]{
            "[foo foo]: ./foo/foo.md",
            "[ bar Url ]: ./bar.md#frag",
            "[ bra\\[kets]: https://brackets.org/foo/../b.md"
        },
        ImmutableMap.of(
            "foo foo", new URLReference(URI.create("file:/d/foo/foo.md")),
            "bar url", new URLReference(URI.create("file:/d/bar.md#frag")),
            "bra[kets", new URLReference(URI.create("https://brackets.org/b.md"))
        ),
        ImmutableList.of(),
        ImmutableList.of());
    assertEquals(
        "Optional[/d/foo.md:24-50]",
        f.namedUrls.get("bar url").declarationSite.toString());
  }

  @Test
  public void testOneCodeBlock() {
    String input = Joiner.on('\n').join(
        "<details id=\"bar\">",
        "  <summary>some code</summary>",
        "",
        "  ```tmpr",
        "  // Code",
        "  ```",
        "",
        "</details>");

    assertMarkdownFile(
        new String[]{input},
        ImmutableMap.of(),
        ImmutableList.of(
            new CodeDetails(PRODUCTION, "\n  // Code\n  ", "bar")),
        ImmutableList.of(
            new SectionDetails("some code", "bar"),
            new SectionDetails("", "-bar")));
  }

  @Test
  public void testAmbiguousCodeBlock() {
    assertMarkdownFile(
        new String[]{
            // 4 space characters indicate an indented code block, not a fenced code block.
            "    ```bff",
            "    // Code",
            "    ```",
        },
        ImmutableMap.of(),
        ImmutableList.of(),
        ImmutableList.of(),
        "WARNING:/d/foo.md:4-30: Ambiguous indented code block starts with ```bff");
  }

  @Test
  public void testHtmlNestingAndIdPrecedence() {
    String input = Joiner.on('\n').join(
        "# Header",
        "",
        "<section id=sec1>",
        "",
        "  ```tmpr",
        "  // Code in section with id",
        "  ```",
        "  ## subheader",
        "  ~~~~tmpt",
        "  // Code after sub header",
        "  ~~~~",
        "",
        "</section>",
        "",
        "# Header 2",
        "```tmpr",
        "// Code under header 2",
        "```",
        "",
        "```tmpe",
        "// More code that is pre-empted by the above code block",
        "```",
        "",
        "<details id=doesnotcontain>",
        "",
        "  Some text",
        "",
        "</details>",
        "",
        "```tmpe",
        "// not inside details#doesnotcontain",
        "```",

        "",
        "<details id=doesnotcontaineither>",
        "  A single HTML block",
        "</details>",
        "",
        "```tmpe",
        "// not inside details#doesnotcontaineither",
        "```",

        "## Yet another header",
        "```foo",
        "// Not in a language we care about",
        "```",
        "",
        "```tmpr",
        "// Not pre-empted by code in languages we don't care about",
        "```"
    );

    assertMarkdownFile(
        new String[]{input},
        ImmutableMap.of(),
        ImmutableList.of(
            new CodeDetails(
                PRODUCTION, "\n  // Code in section with id\n  ", "sec1"),
            new CodeDetails(TEST, "\n  // Code after sub header\n  ", "subheader"),
            new CodeDetails(PRODUCTION, "\n// Code under header 2\n", "header-2"),
            new CodeDetails(
                EXAMPLE,
                "\n// More code that is pre-empted by the above code block\n",
                (String) null),
            new CodeDetails(
                EXAMPLE,
                "\n// not inside details#doesnotcontain\n", (String) null),
            new CodeDetails(
                EXAMPLE,
                "\n// not inside details#doesnotcontaineither\n", (String) null),
            new CodeDetails(
                PRODUCTION,
                "\n// Not pre-empted by code in languages we don't care about\n",
                "yet-another-header")
        ),
        ImmutableList.of(
            new SectionDetails("Header", "header"),
            new SectionDetails("", "sec1"),
            new SectionDetails("subheader", "subheader"),
            new SectionDetails("", "-sec1"),
            new SectionDetails("Header 2", "header-2"),
            new SectionDetails("", "-doesnotcontain"),
            new SectionDetails("A single HTML block", "-doesnotcontaineither"),
            new SectionDetails("Yet another header", "yet-another-header")));
  }


  static class CodeDetails {
    final CodeBlockVariant variant;
    final String code;
    final @Nullable String id;

    CodeDetails(CodeBlockVariant variant, String code, @Nullable String id) {
      this.variant = variant;
      this.code = code;
      this.id = id;
    }

    CodeDetails(CodeBlockVariant variant, String code, @Nullable HtmlIdentifier id) {
      this(variant, code, id != null ? id.text : null);
    }

    CodeDetails(String input, CompilationUnit cu) {
      this(cu.codeBlockVariant, input.substring(cu.codePosition.start, cu.codePosition.end),
          cu.fragmentIdentifier.orElse(null));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CodeDetails that = (CodeDetails) o;
      return variant == that.variant
          && Objects.equals(code, that.code)
          && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(variant, code, id);
    }

    @Override
    public String toString() {
      return "new CodeDetails(CodeBlockVariant." + variant.name()
          + ", \"" + code.replace("\n", "\\n") + '"'
          + ", " + (id != null ? '"' + id + '"' : "null")
          + ')';
    }
  }

  static class SectionDetails {
    final String text;
    final String fragments;

    SectionDetails(MarkdownFile.Section section) {
      this(section.text, toFragments(section));
    }

    private static String toFragments(MarkdownFile.Section sec) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, n = sec.openFragments.size(); i < n; ++i) {
        if (i != 0) {
          sb.append(' ');
        }
        sb.append(sec.openFragments.get(i).text);
      }

      if (!sec.closedFragments.isEmpty()) {
        if (sb.length() != 0) {
          sb.append(' ');
        }
        sb.append('-');
        for (int i = 0, n = sec.closedFragments.size(); i < n; ++i) {
          if (i != 0) {
            sb.append(' ');
          }
          sb.append(sec.closedFragments.get(i).text);
        }
      }
      return sb.toString();
    }

    SectionDetails(String text, String fragments) {
      this.text = text;
      this.fragments = fragments;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SectionDetails that = (SectionDetails) o;
      return Objects.equals(text, that.text)
          && Objects.equals(fragments, that.fragments);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, fragments);
    }

    @Override
    public String toString() {
      return "new SectionDetails(\""
          + text.replace("\n", "\\n") + "\", \""
          + fragments.replace("\n", "\\n") + "\")";
    }
  }
}
