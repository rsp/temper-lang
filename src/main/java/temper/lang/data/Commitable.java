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

package temper.lang.data;

/**
 * A {@link Checkpointable} that can commit to not rolling back to snapshots.
 */
public interface Commitable<SNAPSHOT> extends Checkpointable<SNAPSHOT> {

  /**
   * Invalidates prior snapshots which may allow an observer to make sound
   * conclusions on about portions of its eventual state based on its current
   * state.
   *
   * <p>Attempts to restore to state prior to a commit warrant a system-level
   * panic.
   */
  void commit(SNAPSHOT snapshot);
}
