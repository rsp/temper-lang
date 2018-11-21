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
import temper.lang.diagnostic.SourcePosition;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.vladsch.flexmark.ast.Document;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlBlockBase;
import com.vladsch.flexmark.ast.HtmlInnerBlock;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ast.VisitHandler;
import com.vladsch.flexmark.ast.Visitor;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A markdown file including any information about fenced temper code blocks.
 */
public final class MarkdownFile {
  /**
   * The file source.
   */
  public final Source source;
  /**
   * Any named URLs parsed from the file.
   */
  public final ImmutableMap<String, URLReference> namedUrls;
  /**
   * Compilation units in lexical order.
   */
  public final ImmutableList<CompilationUnit> compUnits;
  /**
   * Sections
   */
  public final ImmutableList<Section> sections;

  private MarkdownFile(
      Source source, Map<? extends String, ? extends URLReference> namedUrls,
      Iterable<? extends CompilationUnit> compUnits,
      Iterable<? extends Section> sections) {
    this.source = Preconditions.checkNotNull(source);
    this.namedUrls = ImmutableMap.copyOf(namedUrls);
    this.compUnits = ImmutableList.copyOf(compUnits);
    this.sections = ImmutableList.copyOf(sections);
  }

  public static MarkdownFile of(Source source, DiagnosticSink dsink) {
    Parser p = Parser.builder().build();
    HtmlIdentifier.Assigner assigner = new HtmlIdentifier.Assigner();
    Document root = p.parse(source.contents);

    Map<String, URLReference> namedUrls = new LinkedHashMap<>();
    ImmutableList.Builder<CompilationUnit> compUnits = ImmutableList.builder();
    ImmutableList.Builder<Section> sections = ImmutableList.builder();

    AnalysisPass ap = new AnalysisPass(source, dsink, assigner, namedUrls);
    ap.process(root);
    CodePass cp = new CodePass(source, dsink, ap.nodeToHtmlId, ap.contextAfterHtml, compUnits);
    cp.process(root);

    MarkdownFile f = new MarkdownFile(source, namedUrls, compUnits.build(), cp.sections);
    int cuIndex = 0;
    for (CompilationUnit cu : f.compUnits) {
      cu.setContainingFile(f, cuIndex);
      ++cuIndex;
    }
    return f;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MarkdownFile that = (MarkdownFile) o;
    return Objects.equals(source, that.source)
        && Objects.equals(namedUrls, that.namedUrls)
        && Objects.equals(compUnits, that.compUnits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, namedUrls, compUnits);
  }

  @Override
  public String toString() {
    return "MarkdownFile{" + "source=" + source
        + ", namedUrls=" + namedUrls
        + ", compUnits=" + compUnits
        + '}';
  }


  private static final Pattern EXPLICIT_FRAGMENT_ID_PATTERN = Pattern.compile(
      "[{]#((?:[^}\\\\]|[\\\\].)*)[}]",
      Pattern.DOTALL);

  private static final Pattern AMBIGUOUS_CODE_BLOCK_PATTERN = Pattern.compile(
      "^\\s*((?:```|~~~)\\w*)"
  );

  private static boolean isEscaped(BasedSequence mdText, int index) {
    int nSlashes = 0;
    for (int i = index; --index >= 0; ++nSlashes) {
      if (mdText.charAt(i) != '\\') {
        break;
      }
    }
    return 0 != (nSlashes & 1);
  }


  /**
   * Extracts side tables of explicit and explicit fragment identifiers and named URLs,
   * and the gross structure of inline HTML.
   */
  private static final class AnalysisPass {
    private final Source source;
    private final DiagnosticSink dsink;
    private final HtmlIdentifier.Assigner assigner;
    private final Map<String, URLReference> namedUrls;
    private final Multimap<Node, HtmlIdentifier> nodeToHtmlId = ArrayListMultimap.create();
    private final List<Heading> unassigned = new ArrayList<>();
    private final List<HtmlBlockBase> htmlBlocks = new ArrayList<>();
    private final Map<HtmlBlockBase, Element> contextAfterHtml = new IdentityHashMap<>();

    AnalysisPass(
        Source source, DiagnosticSink dsink, HtmlIdentifier.Assigner assigner,
        Map<String, URLReference> namedUrls) {
      this.source = source;
      this.dsink = dsink;
      this.assigner = assigner;
      this.namedUrls = namedUrls;
    }

    void process(Node node) {
      new NodeVisitor(
          // Find references like
          //   [name]: url
          new VisitHandler<>(
              Reference.class,
              new Visitor<Reference>() {
                @Override
                public void visit(Reference node) {
                  BasedSequence nameText = node.getReference();
                  BasedSequence urlText = node.getUrl();
                  SourcePosition pos = new SourcePosition(
                      source.canonicalUrl,
                      node.getStartOffset(), node.getEndOffset());
                  URI relUri;
                  try {
                    relUri = new URI(urlText.unescape());
                  } catch (URISyntaxException ex) {
                    dsink.report(new Diagnostic(
                        Level.SEVERE,
                        "Malformed URL " + urlText.unescape() + ": " + ex,
                        ImmutableList.of(pos)));
                    relUri = URI.create("about:invalid");
                  }
                  URI absUrl = source.canonicalUrl.resolve(relUri).normalize();
                  String name = nameText.unescape().trim().toLowerCase(Locale.ROOT);
                  URLReference old = namedUrls.put(
                      name,
                      new URLReference(
                          absUrl,
                          Optional.of(name),
                          Optional.of(pos)));
                  if (old != null && !old.url.equals(absUrl)) {
                    ImmutableList.Builder<SourcePosition> positions =
                        ImmutableList.builder();
                    if (old.declarationSite.isPresent()) {
                      positions.add(old.declarationSite.get());
                    }
                    positions.add(pos);
                    dsink.report(new Diagnostic(
                        Level.WARNING,
                        "URL reference [" + nameText + "] redefined from " + old.url
                            + " to " + absUrl,
                        positions.build()));
                  }
                }
              }),
          new VisitHandler<>(
              Heading.class,
              new Visitor<Heading>() {
                @Override
                public void visit(Heading node) {
                  for (BasedSequence segment : node.getSegments()) {
                    Matcher m = EXPLICIT_FRAGMENT_ID_PATTERN.matcher(segment);
                    while (m.find()) {
                      if (isEscaped(segment, m.start())) {
                        continue;
                      }
                      BasedSequence idSeq = segment.subSequence(
                          m.start(1), m.end(1));
                      String id = idSeq.unescape().trim().toLowerCase(Locale.ROOT);
                      if (!id.isEmpty()) {
                        Optional<HtmlIdentifier> assignedId = assigner.unassigned(id);
                        if (assignedId.isPresent()) {
                          nodeToHtmlId.put(node, assignedId.get());
                        } else {
                          dsink.report(new Diagnostic(
                              Level.SEVERE, "Ambiguous explicit id `" + id + "`",
                              ImmutableList.of(new SourcePosition(
                                  source.canonicalUrl,
                                  idSeq.getStartOffset(), idSeq.getEndOffset()))));
                        }
                      }
                    }
                  }
                  if (nodeToHtmlId.get(node).isEmpty()) {
                    unassigned.add(node);
                  }
                }
              }),
          new VisitHandler<>(
              HtmlBlock.class,
              new Visitor<HtmlBlock>() {
                @Override
                public void visit(HtmlBlock node) {
                  htmlBlocks.add(node);
                }
              }),
          new VisitHandler<>(
              HtmlInnerBlock.class,
              new Visitor<HtmlInnerBlock>() {
                @Override
                public void visit(HtmlInnerBlock node) {
                  htmlBlocks.add(node);
                }
              })

      ).visit(node);

      // Make sure we have assigned a fragment identifier for each heading.
      for (Heading heading : unassigned) {
        HtmlIdentifier id = assigner.assignIdentifier(heading.getText().unescape());
        nodeToHtmlId.put(heading, id);
      }
      unassigned.clear();

      // Figure out, where on the HTML element stack, each HTML block starts and ends.
      int nHtmlBlocks = htmlBlocks.size();
      if (nHtmlBlocks != 0) {
        StringBuilder allHtml = new StringBuilder();
        String idSeparatorPrefix =
            nonceNotInHtmls(htmlBlocks, (HtmlBlockBase x) -> x.getChars()) + "_";
        for (int i = 0; i < nHtmlBlocks; ++i) {
          allHtml.append(htmlBlocks.get(i).getChars());
          allHtml.append("<wbr id=\"").append(idSeparatorPrefix).append(i).append("\" />");
        }
        org.jsoup.nodes.Document htmlDoc = Jsoup.parseBodyFragment(allHtml.toString());
        for (int i = 0; i < nHtmlBlocks; ++i) {
          String id = idSeparatorPrefix + i;
          org.jsoup.nodes.Element el = htmlDoc.getElementById(id);
          HtmlBlockBase htmlBlock = htmlBlocks.get(i);
          if (el != null) {
            contextAfterHtml.put(htmlBlock, el);
          } else {
            dsink.report(new Diagnostic(
                Level.SEVERE,
                "HTML block does not end in PCDATA context: " + htmlBlock.getChars(),
                ImmutableList.of(new SourcePosition(
                    source.canonicalUrl,
                    htmlBlock.getStartOffset(), htmlBlock.getEndOffset()))));
          }
        }
      }
    }

    private final SecureRandom rnd = new SecureRandom();

    private <T> String nonceNotInHtmls(Iterable<T> xs, Function<T, BasedSequence> f) {
      StringBuilder sb = new StringBuilder();
      for (T x : xs) {
        BasedSequence s = Preconditions.checkNotNull(f.apply(x));
        sb.append(s).append(s.unescape());
      }
      String toAvoid = sb.toString();
      byte[] bytes = new byte[24];
      while (true) {
        rnd.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        if (!toAvoid.contains(nonce)) {
          return nonce;
        }
      }
    }
  }

  /**
   * Auto-assign implicit fragments and find fenced code blocks.
   */
  private static final class CodePass {
    private final Source source;
    private final DiagnosticSink dsink;
    private final ImmutableListMultimap<Node, HtmlIdentifier> nodeToHtmlId;
    private final Map<HtmlBlockBase, Element> contextAfterHtml;
    private final ImmutableList.Builder<CompilationUnit> compUnits;
    private final List<Section> sections = new ArrayList<>();

    CodePass(Source source, DiagnosticSink dsink,
             Multimap<Node, HtmlIdentifier> nodeToHtmlId,
             Map<HtmlBlockBase, Element> contextAfterHtml,
             ImmutableList.Builder<CompilationUnit> compUnits) {
      this.source = source;
      this.dsink = dsink;
      this.nodeToHtmlId = ImmutableListMultimap.copyOf(nodeToHtmlId);
      this.contextAfterHtml = contextAfterHtml;
      this.compUnits = compUnits;
    }

    void process(Node node) {
      Visitor<HtmlBlockBase> htmlBlockVisitor = new Visitor<HtmlBlockBase>() {
        Element lastHtmlMarker;

        @Override
        public void visit(HtmlBlockBase node) {
          Element htmlMarker = contextAfterHtml.get(node);
          if (htmlMarker == null) {
            // Error reported by previous pass.
            return;
          }
          List<String> sectionText = new ArrayList<>();
          List<HtmlIdentifier> ids = new ArrayList<>();
          Set<HtmlIdentifier> closed = new LinkedHashSet<>();

          boolean found = walkReverseDepthFirstUntilLastMarker(
              true, true, htmlMarker, sectionText, ids, closed);
          HtmlIdentifier markerId = new HtmlIdentifier(htmlMarker.attr("id").trim());
          Preconditions.checkState(found == (lastHtmlMarker != null));
          lastHtmlMarker = htmlMarker;

          sectionText = Lists.reverse(sectionText);

          Set<HtmlIdentifier> opened = new HashSet<>(ids);
          closed.add(markerId);
          opened.add(markerId);

          ids.removeAll(closed);
          closed.removeAll(opened);

          if (!sectionText.isEmpty() || !ids.isEmpty() || !closed.isEmpty()) {
            sections.add(new Section(
                new SourcePosition(
                    source.canonicalUrl, node.getStartOffset(), node.getEndOffset()),
                Joiner.on(' ').join(sectionText).trim(),
                -1,
                Lists.reverse(ids),
                Lists.reverse(new ArrayList<>(closed))));
          }
        }

        private boolean walkReverseDepthFirstUntilLastMarker(
            boolean asc, boolean behindMark,
            org.jsoup.nodes.Node n, List<String> sectionText, List<HtmlIdentifier> ids,
            Set<HtmlIdentifier> closed) {
          if (n == lastHtmlMarker) {
            return true;
          }
          if (n instanceof Element) {
            Element el = (Element) n;

            String id = el.attr("id");
            if (id != null) {
              id = id.trim();
              if (id.isEmpty()) {
                id = null;
              }
            }

            if (behindMark && id != null) {
              closed.add(new HtmlIdentifier(id));
            }

            if (asc) {
              // Ascending
              for (org.jsoup.nodes.Node sibling = el;
                   (sibling = sibling.previousSibling()) != null; ) {
                if (walkReverseDepthFirstUntilLastMarker(
                    false, behindMark, sibling, sectionText, ids, closed)) {
                  return true;
                }
              }
            } else {
              // Descending
              List<org.jsoup.nodes.Node> children = el.childNodes();
              for (int i = children.size(); --i >= 0; ) {
                if (walkReverseDepthFirstUntilLastMarker(
                    false, behindMark, children.get(i), sectionText, ids, closed)) {
                  return true;
                }
              }
            }

            if (!behindMark && id != null) {
              ids.add(new HtmlIdentifier(id));
            }

            if (asc) {
              if (el.hasParent()) {
                return walkReverseDepthFirstUntilLastMarker(
                    true, false, el.parent(), sectionText, ids, closed);
              }
            }
          } else if (n instanceof TextNode) {
            String text = ((TextNode) n).text().trim();
            if (!text.isEmpty()) {
              sectionText.add(text);
            }
          }
          return false;
        }
      };

      new NodeVisitor(
          new VisitHandler<>(
              Heading.class,
              new Visitor<Heading>() {
                @Override
                public void visit(Heading node) {
                  ImmutableList<HtmlIdentifier> ids = nodeToHtmlId.get(node);
                  int offset = node.getStartOffset();
                  String text = node.getText().trim().unescape();
                  sections.add(new Section(
                      new SourcePosition(source.canonicalUrl, offset, offset),
                      text, node.getLevel(), ids, ImmutableList.of()));
                }
              }),
          new VisitHandler<>(HtmlBlock.class, htmlBlockVisitor),
          new VisitHandler<>(HtmlInnerBlock.class, htmlBlockVisitor),
          new VisitHandler<>(
              FencedCodeBlock.class,
              new Visitor<FencedCodeBlock>() {
                int lastSectionUsed = -1;

                @Override
                public void visit(FencedCodeBlock node) {
                  String fenceInfo = node.getInfo().unescape();
                  // Split the language identifier from other info.
                  String fenceId = fenceInfo.split("[\r\n \t]+")[0];
                  Optional<CodeBlockVariant> v = CodeBlockVariant.forFenceId(fenceId);
                  if (v.isPresent()) {
                    SourcePosition pos = new SourcePosition(
                        source.canonicalUrl,
                        node.getInfo().getEndOffset(),
                        node.getClosingFence().getStartOffset());
                    Optional<HtmlIdentifier> htmlId = Optional.empty();
                    Set<HtmlIdentifier> closed = new HashSet<>();
                    for (int i = sections.size(); --i > lastSectionUsed; ) {
                      Section s = sections.get(i);
                      closed.addAll(s.closedFragments);
                      if (!s.openFragments.isEmpty()) {
                        for (int j = s.openFragments.size(); --j >= 0; ) {
                          HtmlIdentifier idj = s.openFragments.get(j);
                          if (!closed.contains(idj)) {
                            htmlId = Optional.of(idj);
                            break;
                          }
                        }
                        lastSectionUsed = Math.max(lastSectionUsed, i);
                        break;
                      }
                    }
                    compUnits.add(new CompilationUnit(v.get(), pos, htmlId));
                  }
                }
              }),
          new VisitHandler<>(
              IndentedCodeBlock.class,
              new Visitor<IndentedCodeBlock>() {
                @Override
                public void visit(IndentedCodeBlock node) {
                  Matcher m = AMBIGUOUS_CODE_BLOCK_PATTERN.matcher(node.getChars());
                  if (m.find()) {
                    dsink.report(new Diagnostic(
                        Level.WARNING,
                        "Ambiguous indented code block starts with " + m.group(1),
                        ImmutableList.of(new SourcePosition(
                            source.canonicalUrl, node.getStartOffset(),
                            node.getEndOffset()))));
                  }
                }
              })
      ).visit(node);

    }
  }

  /**
   * An identifiable section.
   */
  public static final class Section {
    /**
     * Location of the section declaration.
     */
    public final SourcePosition location;
    /**
     * Header text
     */
    public final String text;
    /**
     * Level 1 - 6 like {@code <h1>}-{@code <h6>}.
     */
    public final int level;
    /**
     * Any fragment identifiers that link to the section.
     */
    public final ImmutableList<HtmlIdentifier> openFragments;
    /**
     * Any fragments no longer in scope by after entering this section.
     */
    public final ImmutableList<HtmlIdentifier> closedFragments;

    Section(SourcePosition location, String text, int level,
            Iterable<? extends HtmlIdentifier> openFragments,
            Iterable<? extends HtmlIdentifier> closedFragments) {
      this.location = Preconditions.checkNotNull(location);
      this.text = Preconditions.checkNotNull(text);
      this.level = level;
      this.openFragments = ImmutableList.copyOf(openFragments);
      this.closedFragments = ImmutableList.copyOf(closedFragments);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Section section = (Section) o;
      return level == section.level
          && Objects.equals(location, section.location)
          && Objects.equals(text, section.text)
          && Objects.equals(openFragments, section.openFragments)
          && Objects.equals(closedFragments, section.closedFragments);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Section{");
      sb.append("location=").append(location);
      sb.append(", text='").append(text).append('\'');
      sb.append(", level=").append(level);
      if (!openFragments.isEmpty() || !closedFragments.isEmpty()) {
        sb.append(", fragments");
        if (!openFragments.isEmpty()) {
          sb.append(" += ").append(openFragments);
        }
        if (!closedFragments.isEmpty()) {
          sb.append(openFragments.isEmpty() ? " -= " : " - ").append(closedFragments);
        }
      }
      sb.append('}');
      return sb.toString();
    }

    @Override
    public int hashCode() {
      return Objects.hash(location, text, level, openFragments, closedFragments);
    }
  }
}
