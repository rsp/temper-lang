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

import temper.lang.diagnostic.SourcePosition;
import com.google.common.base.Preconditions;

import java.util.Objects;
import java.util.Optional;

/**
 * Corresponds to a fenced temper code block.
 * A markdown file is not the unit of compilation.
 */
public final class CompilationUnit {
  /**
   * The variety of code block.
   */
  public final CodeBlockVariant codeBlockVariant;

  /**
   * The range of characters in the {@link Source} that .
   */
  public final SourcePosition codePosition;

  /**
   * A fragment identifier that identifies the code block within the file.
   *
   * <p>This is computed by looking back for
   * <ol>
   *   <li>a {@code a surrounding <detail>} element with an {@code id} attribute</li>
   *   <li>an <a href="https://github.github.com/gfm/#atx-heading">ATX heading</a>
   * </ol>
   *
   * <p>In any case, traversal stops when a relevant fenced code block is seen lexically
   * between the current code block and the markdown element that specifies the ID.
   *
   * <p>A "relevant" fenced code block is one whose {@linkplain CodeBlockVariant#fenceId fence id}
   * {@linkplain CodeBlockVariant#forFenceId corresponds} to a {@link CodeBlockVariant}.
   *
   * <p>To compute a fragment identifier:
   * <ul>
   *   <li>If available, the fragment identifier is the decode value of the HTML {@code id}
   *       attribute.
   *   <li>Else, if there is an explicit fragment identifiers in <code>{#fragmentIdentifier}</code>
   *       we use it per https://talk.commonmark.org/t/anchors-in-markdown/247
   *   <li>Else we fall back to a variant of
   *       http://pandoc.org/MANUAL.html#extension-auto_identifiers
   *       that normalizes the text, excludes <code>{...}</code> blocks and which may add a suffix
   *       to disambiguate.
   *       <br>This is also discussed at
   *       <a href="https://talk.commonmark.org/t/feature-request-automatically-generated-ids-for-headers/115">
   *         "Automatically generated ids for headers"</a>.
   *       <br>It is a goal to track Github markdown auto-identifier conventions where possible.
   *       https://github.com/gjtorikian/commonmarker#creating-a-custom-renderer seems to be the
   *       Markdown renderer used by GH but also seems to leave ID generation to custom passes.
   *   </li>
   * </ul>
   */
  public final Optional<HtmlIdentifier> fragmentIdentifier;

  private Optional<MarkdownFile> containingFile = Optional.empty();
  private int indexInContainingFile = -1;

  /** Constructor. */
  public CompilationUnit(
      CodeBlockVariant codeBlockVariant, SourcePosition codePosition,
      Optional<HtmlIdentifier> fragmentIdentifier) {
    this.codeBlockVariant = Preconditions.checkNotNull(codeBlockVariant);
    this.codePosition = Preconditions.checkNotNull(codePosition);
    this.fragmentIdentifier = Preconditions.checkNotNull(fragmentIdentifier);
  }

  /**
   * The markdown file that contains this code block.
   */
  public Optional<MarkdownFile> getContainingFile() {
    return containingFile;
  }

  /**
   * The index of this block among the blocks in {@link #getContainingFile}.
   * @return -1 iff {@link #getContainingFile} is absent.
   */
  public int getIndexInContainingFile() {
    return indexInContainingFile;
  }

  void setContainingFile(MarkdownFile file, int index) {
    Preconditions.checkState(!this.containingFile.isPresent());
    Preconditions.checkState(file.compUnits.get(index) == this);
    Preconditions.checkState(this.codePosition.source.equals(file.source.canonicalUrl));
    this.containingFile = Optional.of(file);
    this.indexInContainingFile = index;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CompilationUnit that = (CompilationUnit) o;
    return codeBlockVariant == that.codeBlockVariant
        && Objects.equals(codePosition, that.codePosition)
        && Objects.equals(fragmentIdentifier, that.fragmentIdentifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(codeBlockVariant, codePosition, fragmentIdentifier);
  }

  @Override
  public String toString() {
    return "{CompilationUnit " + this.codePosition
        + (this.fragmentIdentifier.isPresent() ? "#" + this.fragmentIdentifier.get().text : "")
        + "}";
  }
}
