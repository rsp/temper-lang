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

/**
 * Builtin buffer types.
 *
 * <p>There are a variety of buffer types available depending on
 * whether the buffer can be written (W) and if so, whether
 * <ul>
 *   <li>ability to write implies ability to read or</li>
 *   <li>writes are dead drops and commit transfers ownership of committed
 *       content freeing up space for new writes</li>
 * </ul>
 *
 * <table>
 *   <tr>
 *     <th>Writable</th>
 *     <th>Reads</th>
 *     <th>Type</th>
 *   </tr>
 *   <tr>
 *     <td>False</td>
 *     <td>.</td>
 *     <td><i>ibuf</i> extends <i>buf</i></td>
 *   </tr>
 *   <tr>
 *     <td>True</td>
 *     <td>Readable</td>
 *     <td><i>iobuf</i> extends <i>buf</i>, <i>obuf</i></td>
 *   </tr>
 *   <tr>
 *     <td>True</td>
 *     <td>Commit Transfers</td>
 *     <td><i>cbuf</i> extends <i>obuf</i></td>
 *   </tr>
 * </table>
 */
@javax.annotation.ParametersAreNonnullByDefault
package temper.lang.data.buf;
