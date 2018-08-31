# Temper Language

*Temper* - to lessen the force or effect of something;
to heat and then cool in order to make hard.

Also, a convenient language for robust programs.

## Design Goals

### Dynamic language features with fewer drawbacks

_Goal_: to provide the benefits of dynamic programming while producing
static systems.

A language is highly dynamic when a runtime value can substitute for
many syntactic elements.

For example, in JavaScript

```js
x.foo;  // static access to property foo
a = 'foo', x[a];  // runtime value 'foo' substitutes for property name

import * as ns from './module';  // static load of external module
b = './module';
const ns = import(b);  // runtime value substitutes for module name

z = (1 + 1);  // static specification of arithmetic expression
c = 'z = (1 + 1);', eval(c);  // runtime value substitutes for inline code
```

Many claim that dynamism makes developers more productive.  This seems
true, at least during the early stages of a project.

Because runtime values can substitute for source code written by
trusted developers, an attacker who controls some inputs has more
opportunities to cause the program to act as if the attacker wrote
portions of the program.  Dynamic systems are prone to unexpected
changes in behavior when exposed to crafted inputs, and these changes
in behavior are too often security relevant.

```js
x = {};
a = 'constructor', b = 'sideEffect()';  // Attacker controlled
x[a][a](b)();

c = '../../../../attacker-controlled-file';
import(c);

d = 'just.about.anything()';
eval(d);
```

Commonly used dynamic languages allow user-code to do the following:

| Use case | Dynamic element |
| -------- | --------------- |
| Lazy loading | Module name |
| Load module named in config file | Module name |
| Load module based on naming convention | Module name |
| Find service supplier among locally installed modules | Module name |
| Serialize a user type | RTTI, reflect over type members |
| Deserialize a user type | RTTI, reflect over type members |
| Bridge user types to external systems like DB | Reflect over type members |
| Domain specific languages, syntax extensions | Some of (function, type, module) declaration |
| Membranes | Object identity, message interception, message redispatch |
| Duck typing | Type introspection |
| Monkeypatching, polyfilling | Mutable builtins, or replacable builtins and message interception & redispatch |
| Value provenance & declassification | Module identity |
| Relative resource resolution | Module metadata |
| Code-referencing log messages | Module metadata, call stack |
| Ad-hoc queries: pure expressions with free variables bound via a data bundle. | Runtime function declaration or library support |
| Dependency Injection | Actual parameter lists |
| Partial classes | Type members |

This language aims to provide enough powers to programmers to enable these
use cases when the values that substitute for code from trusted developers
also come from trusted sources.

### Produce analyzable systems

_Goal_: make it easy to write code that is easy for humans to review.

_Goal_: produce code artifacts that are amenable to automated static analysis.

Checking the correctness of security-relevant code is hard.  It's
harder when the language makes it hard to write reviewable code.

The second high-level goal is to favor language features that enable
case-based reasoning by reviewers, and eschew features that
almost-always do what one intends but come with caveats that require
lots of work to check.

It is also a goal to produce code artifacts that are amenable to sound
static analysis. There are highly regarded static analyzers for
JavaScript, Python, and other dynamic languages but most widely-used
ones are unsound.  They are useful for catching common developer
errors but less so for testing system-level security properties.

### Interoperate with many host languages.

_Goal_: wide reuse of common functions over untrusted inputs.

Another high-level goal is to provide a way to write libraries for
simple, well-understood, but tricky-to-implement transformations at
the borders between nodes in distributed systems.  These libraries
should be accessible from most "host" languages that people use to
write distributed systems so that we can incrementally move tricky
handling of untrusted inputs out of confusable dynamic language code.

Initially, interop with (C, C#, Go, Java, JavaScript, Python, and
Rust) should allow us to work with code on most of the major
multi-language VMs and allow a single library to serve many clients.

### Manage string complexity

_Goal_: first-class support for common string encodings.

A language is a set of string, but some are sets of strings of Unicode
scalar values, some of UTF-16 code-unit strings, and some of octet
strings.

Much code ignores these differences.  This may not matter when a
non-malicious human authored the string.  Getting this right when
dealing with crafted inputs is a common source of implementation
complexity and subtle bugs.

Host languages differ in how they represent character/byte content
internally and this determines the efficient way to chunk and process
such content.  Similarly, parsing inputs requires understanding its
input representation.

| Host Lang  | Natural Representations |
| ---------- | ----------------------- |
| C & C++    | octets, chars as UTF-8 more often than not |
| Go         | octets, chars as UTF-8  |
| Java       | octets, chars as UTF-16 |
| JavaScript | UTF-16 + TypedArray as various |
| Python     | octets, chars as UTF-16 (rarely UTF-32 instead) |

| Input Lang | SourceCharacter |
| ---------- | --------------- |
| CSS, HTML  | Unicode scalar  |
| JS, Java   | UTF-16          |
| URL        | octets          |
| JSON       | UTF-8 by caveat |


### Accommodate casual developers

_Goal_: **casual developers** should be able to effectively use code
written by experts based on a partial mental model of the language.

It's easier to explain this by example than to formally define.  A
Java library developer who understands the details of generic type
variance can put `<T extends Bound>` and `<T extends Super>` in
the right places in their library code.

Many Java developers never define a type parameter except perhaps via
copy/paste, but when they use libraries that do, type variance bleeds
through; the library user must sprinkle wildcard bounds
`<? extends MyClass>` through their code or else use raw types which
are a source of unsoundness.

One strategy might be to allow expert developers to pick defaults for
casual developers.  Applying this to variance in Java, if the API
developer could say, "consider any variant binding for this type
parameter to be one of (covariant, contravariant) unless specified
otherwise" the concept of variance would bleed through to the casual
developer far less often, and the casual developer could more often
achieve goals based on a mental model of the language that doesn't
include type parameter variance without recourse to unsafe features
like raw types.



## Design Choices

### Monotonically decreasing dynamism

_Decision_: The language will be a multi-staged programming language.

Staged programming languages allow some user code to run during the
compilation or loading process to specify code that can run at a later
stage.

Staged programming languages provide an opportunity to bring
domain-specific languages into the shipped binary, so that static
analysis does not require bridging embedded-language-specific analyzers.

Similar to macros, staged programming allows replacing some sources of
dynamism with functions that desugar to parse trees.

Our security story is:

1. The last stage is runtime.
1. Compilation units proceed through stages independently, but
   all units transition to the runtime stage at the same time.
1. Assume that no untrusted inputs reach the system before the
   runtime stage.  The language allows unidirectional suspicion via
   loading in a constrained environment.
   Source code supply chain security is out of band.
1. No dynamic operators are available during the runtime stage.
1. Therefore, no untrusted input can reach a source of dynamism.
1. Caveat: user code can create powerful reflective APIs via
   code generation during an early stage.  We hope to mitigate
   this risk by using multiple stages so that a module would
   have to opt-into a code generating library during an early
   stage to have such a high-level of power.
   If dynamism decreases monotonically, we hope human reviewers
   can afford to be less suspicious of code that runs later,
   allowing a system that does most work in later stages to
   be efficiently reviewed.

Rejected approaches:

  *  Some systems have relied on *taintedness* to protect sensitive operators
     from crafted strings; strings carry an *evil bit* which sensitive operators
     check, and a concatenation of a tained string is itself tainted.
     Tainting systems have a poor history of providing strong guarantees.
  *  Trademarks per "Protection in Programming Languages" by Morris Jr. allow
     user-code to pre-approve values for use in a particular context.
     This trades-off integrity/confidentiality for availability, though
     trademarks can correspond to types to catch errors early.

Dynamic powers include the powers to

*  declare a compilation unit (*cu*)
*  load code into a compilation unit (*lc*)
*  mutate a compilation units token stream (*tk*)
*  convert a token into a symbol (*sy*)
*  add/modify a member of a state vector (*mv*)
*  import symbols into a compilation unit (*im*)
*  add/modify a declaration to a scope (*ms*)
*  add/modify instructions in a block (*mi*)
*  add a local declaration to a block scope (*ld*)
*  enumerate declarations in a scope and members of a state vector (*en*)
*  traverse the AST (*tr*)
*  modify metadata tables (*md*)
*  abort processing (*ab*)

The lifecycle of a compilation unit proceeds thus:

1.  _G_ather Code: An out-of-band module gatherer fetches the source
    code and specifies a canonical reference for the module.
1.  _P_arse to Tree: Imports in prologue gathered; module lexed to a
    token stream, possibly using syntax extensions defined in imported
    module; tokens lifted to AST.
1.  Rewrite _T_ree: Imported user code gets to manipulate the parse
    tree.  Imports at this stage may provide non-hermetic macros.
1.  _T_ype Check Code: Types inferred, digest of values that reached
    functions checked against actual types.  All member accesses
    resolved to symbols.
1.  _E_xpand Macros: Imported user code gets to manipulate the parse
    tree.  Imports at this stage must be hermetic -- may create new
    temporary symbols but may not add references to symbols in scope
    not provided to the macro as inputs.
1.  _C_heck Access: User code can veto imports, and member references.
    Digest of prior accesses checked.
1.  _X_late Host Language Code: Produces an AST for each target
    language, produce target language artifacts including debug
    tables.  There are no initial plans to expose this AST to user
    code.
1.  _R_un Program: The program may receive untrusted inputs.

| &darr; Stage / &rarr; Power | **cu** | **lc** | **tk** | **mv** | **sy** | **im** | **ms** | **mi** | **ld** | **en** | **tr** | **md** | **ab** |
| --------------------------- | ------ | ------ | ------ | ------ | ------ | ------ | ------ | ------ | ------ | ------ | ------ | ------ | ------ |
| **Gather**                  | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      |
| **Parse**                   |        |        | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      |
| **Tree**                    |        |        |        | X      | X      | X      | X      | X      | X      | X      | X      | X      | X      |
| **Type**                    |        |        |        |        | X      | X      | X      | X      | X      | X      | X      | X      | X      |
| **Expand**                  |        |        |        |        |        | X      | X      | X      | X      | X      | X      | X      | X      |
| **Check**                   |        |        |        |        |        |        |        |        |        | X      | X      | X      | X      |
| **Xlate**                   |        |        |        |        |        |        |        |        |        | X      | X      | X      | X      |
| **Run**                     |        |        |        |        |        |        |        |        |        |        |        |        | X      |

Test code has elevated privileges, so test running happens separately
from the lifecycle above.

#### Glossary

*Block*: a tuple of (an optional outer (parameter) scope, an inner
scope, a set of declarations, and a sequence of statement AST nodes)

*Scope*: a map from names to declarations and some non-normative metadata.
Prior to the end of the syntax phase, names may be strings.
Afterwards they are symbol values.

*Declaration*: a symbol, and optionally a type reference, and
optionally a block that gates access.
Each declaration becomes immutable before its creating module enters
the *Type* stage.

*Type*: a state vector, and some non-normative metadata.
Each type's state vector becomes immutable before its creating module
enters the *Type* stage.

*Module*: a set of imports, a scope that contains exported symbols,
and a block.  A module is always either before a particular stage, in
processing for a particular stage, or just after processing a
particular stage.

*State Vector*: an ordered series of declarations scoped to an
instance of a struct type.

*AST Node*: either (node type, block, children) or (node type, leaf
data).  An AST node becomes immutable before its creating module
enters the *Type* stage.

### Memory Management

The set of host languages include languages that don't use garbage
collection.  To interoperate well with C++ code, temper should not
require a garbage collector.

_Decision_: Manage memory via scoped allocation and/or reference counting.

See cycle avoidance below.
Early stages will run in a host environment that does have GC, so
before the code generation stages, we need not prove absence of
cycles early.

Requiring the consistent time and order of deallocation side effects
that reference counting provides across all target languages would
burden target languages with efficient GC.

_Descision_: Make no guarantees about the order or time of
**deallocation side effects**.  Most deallocation side effects in real
systems relate to resource exhaustion.  Core APIS should scope
resource use, or simplify clear hand-off of responsibility to
release when a resource isn't bounded to a scope as when it
moves over a channel or deallocation awaits Promise resolution.


### Typing

There is no goal that requires a program be fully statically
typed from the first stage through the last.

In a staged language, some stages may define types used by later
stages, so we might dodge some chicken-egg issues by not resolving
type references until later.

Types may be mutable during early stages, so statically typing
during those stages might not provide meaningful guarantees.

It is a goal to interoperate well with target languages that are
statically typed, and to produce statically analyzable output,
so having static types during the code generation stage would
be very helpful.
It is also a goal to interoperate with target languages that
are unsound around type parameters since they deal with
generics via erasure.

The early stages run code that figures out how to build the project.
Widely used build systems are not statically typed.  Bazel/skylark,
buck, gradle, grunt, make, mvn, rake all have dynamically typed
configuration languages.  Arguably in XML-based BUILD configurations
like Ant, Maven, and MSBuild, elements serve similar goals to static
type declarations and Maven does do consistency checks when loading
the POM (project object model).  Scripts that wrap these build systems
are usually written in untyped shell scripts or mini-languages built
on them like `./configure`, though gradually-typed TypeScript is
seeing some adoption in this niche.

Many reliable systems seem to use types in inner loops that run in
production but eschew them in bootstrapping code that runs early in
the project lifecycle.  These dynamically typed systems scale when
they effectively bound side effects to produce static artifacts, like
build dependency graphs, that drive complex tools written in static
languages.
Absent a compelling reason, Temper should not buck the trend by
requiring types for code that only runs during and mainly serves to
build the project.

_Decision_: Temper will aim for **eventual type consistency**.
Assuming inputs from the host environment are well typed, a program in
the *Run* stage will preserve type safety.  Before the type checking
stage, types are advisory, and the language will optimistically assume
that types pass.  The runtime will make a best effort to catch type
errors eventually, by keeping a digest of values that bind to typed
symbols, so the type checking stage can recheck values against types
once they are fully resolved and immutable.  This may be incomplete
since there is no way to get a tight type bound on the type parameter
for an empty container.

_Decision_: Type mismatches do not lead to undefined behavior.
Any attempt to use a value as a type that it is clearly not aborts
processing.

Reference counting leaks in the presence of reference cycles.
Ephemerons are not a perfect solution.

_Decision_: **Types form a partial order** so that a state vector
element cannot transitively refer to itself.  A state vector element
may be "recursive" in which case it can refer to its own type, but
must be immutable post construction and initialized prior to any use
of `this` by the constructor.  This allows acyclic object graphs,
without the risk of cycles.  Adjacency matrices can serve for cyclic
data structures with some loss of ergonomics but no loss of
generality.

It is a goal to interoperate with untyped languages like JavaScript
where objects function as *bags of properties*.  These bags are often
untrusted, for example, when decoded from an untrusted string of JSON.
It is a goal to offload processing of untrusted inputs from these
dynamic languages.

_Decision_: Temper will provide two **bag types** to interoperate with
dynamic languages that correspond to bags of key/value pairs and bag
of ordered elements.  User code may not create or mutate these meaning
that memory management is entirely up to the host language.

Some host languages dispatch object member access based on two factors:

*  The static type which determines the available overloads
*  The runtime type which determines the override of the chosen overloads.

For example, Java has a notion of overload specificity for the first.
C++ involves implicit coercion and parameter default values in the
first and `virtual` for the second.

Some host languages, JavaScript, for example, do not allow overloaded
member declarations, so idiomatic JavaScript uses predicates over
`arguments.length` and runtime types to accomodate multiple different
calling conventions via the same method name.

No target host language does true double dispatch.

Temper cannot rely on the static type for dispatch and maintain
identical method call semantics during loosely typed early stages and
strongly typed later stages.

Cover methods provide the appearance of overloading -- providing a
single method with a variable number of arguments that inspects the
arguments and then calls out to a helper with a more specific
signature.  An optimization could relink calls from the cover method
to signature specific variants, but bottom-typey values like *null*
used as actual parameters might defeat this requiring some cover
methods to actually appear in compiled output.

_Descision_: Temper will provide **no overloading**.
Nor will member availability depend on the static type.
A common *uncovering* optimization pass will help code-generating
backends efficiently dispatch based on actual argument signature.

Overriding provides no complications as long as all instances of
subtyped types carry runtime-type information.

Union types can make dispatch ambiguous.  If we want to resolve
the token `foo` in `x.foo` to a symbol based on the static type of
`foo`.

For tagged unions we can generate a cover function over all members of
the union that define a member named `foo`.

_Decision_: No untagged union types.

Smalltalk's *doesNotUnderstand* turns member access failure
into a message, and JavaScript proxy handlers can provide the
same.  To support this level of dynamism, cover functions would
need to be able to explicitly dispatch to *doesNotUnderstand*
traps.

Type parameters help humans reason about the correctness of
code, but are a source of unsoundness.  Java's type system
is unsound around both type parameters and exception checking
because both are compiler fictions that do not exist at the
VM level.  Interoperability with untyped languages also means
that a program cannot take inputs that bind arbitrary type
parameters and both efficiently/exhaustively check them.
Unsound type systems are a source of false confidence.

_Descision_: Provide generic types by **erasure**.
Focus on making sure type expectation mismatches are not
a source of undefined behavior.

Generics, even with erasure catch more bugs than they cause,
analysis tools can ignore them, and we can assist
generic-skeptical human code review by providing a way to
format code with generic parameters elided.

We have discussed typing without discussing types which
we will discuss after a look at control flow.

### Control Flow

Pure functional code is easy to reason about.  It is a goal to
interoperate with many host languages.  Few (none) of these languages
are either functional or pure.

_Decision_: Code will not run in a dedicated VM or assume a single
existing VM.  Instead the primary deployment strategy will be
**code-generation** alongside small runtime-support libraries.

_Decision_: Idiomatic code will be **primarily imperative** so that
there is an analogous construct for most constructs in the target
language.

When writing security-critical code, a major source of bugs is
branches that start to construct an output, realize that the
current strategy will not work, but then fail to clean up after
themselves before handing control over to an alternate strategy.

_Decision_: Statement level constructs, as in Icon, may succeed
or fail, and **success or failure drives branching in flow-control
constructs**.  For example, the primary iteration (see decision re
imperative above) will continue as long as the body succeeds in making
progress towards a goal.

_Decision_: **Failing branches' side effects will be
invisible** except for out-of-band concerns like debug logs.  All
operations will *fail purely*; though imperative, an operation that
fails to produce a result cannot modify any inputs or shared state.

Hopefully this will make it easy to express computations in terms
of searches over a problem space that accumulate an output.

_Decision_: **Auto-expanding buffers** will be the primary way to
allocate space for an output of unknown unpredictable size.

Instead of writing a program that interacts with the file system,
we might accumulate a series of changes which the host environment
can replay.

_Decision_: Temper will **provide neither direct file\-system
nor shell** access via builtin libraries.

_Decision_: We are not going to rely upon or implement transactional
memory for all host languages.  Mutable types must define **snapshot
and rollback** operations.  For example, buffers could be append-only,
and do not allow random-access instead providing access by scoped
cursors.  Rolling back a buffer means truncating it to its length when
the ultimately failing operation started.

Sometimes it is best not to wait to produce an entire output before
causing some change:

*  Part of an HTTP response might reach the client and start
   rendering while the server works on the rest.
*  An actor might dispatch some messages to other actors and
   expect to receive some messages in return.

_Decision_: Temper will provide **committable output buffers**.
Committing an output buffer means that any attempt to modify
content before the commit point is an unrecoverable failure.

Committing a buffer backed by a flushable stream satisfies
the HTTP response use case.

Committing a write-only buffer could tranfer content from the producer
side of a queue to the consumer side, or dispatch events to a pub-sub
model handling the actor model use case.

It is a goal for Temper to be a good language for dealing with
untrusted input strings.  Analytic grammars often use
Kleene-operators which are analogous to flow-control constructs.

| Grammar Operator  | Syntax              | Semanics                |
| ----------------- | ------------------- | ----------------------- |
| `/` - Alternation | `code0 || code1`    | Result of `code0` if it succeeds, otherwise result of `code1`. |
| `?` - Maybe       | `? code`            | Syntactic sugar for `code || {}` |
| Concatenation     | `code0 ; code1`     | Performs `code0` then `code1`.  Succeeds if both succeed. |
| `+` - Repetition  | `+ code`            | Performs `code` while successful and *progressing*. Succeeds if the first iteration does. |
| `*` - Repetition  | `* code`            | Syntactic sugar for `{ + code } || {}` |

_Descision_: **Prefix Kleene operators** will provide common flow
control.  These are prefix like the ABNF used in RFCs in part to
avoid confusion between infix/postfix operators.

_Decision_: **Types specify code-unit granularity** when they
represent binary or textual content.  Parameterizing string and buffer
types should suffice.

The repetition operator refers to a concept of *progress*.  Assuming search
progressively consumes an input left and right to construct an output,
we define progress in terms of `>` between a snapshot of the input cursor
before entering the loop body and a snapshot of the input cursor after
exiting the loop body.  In this view, an infinite concatenation of empty
strings is itself the empty string.  While arbitrary, this view is well
defined and promotes halting.

Analytic parser generators do a lot of work to avoid or handle
left-recursion.  Search algorithms that might do the equivalent of
LR need to take similar steps.

_Decision_: A variant of *Grow the seed* will handle LR-like problems
in search computations that consume an input left-to-right and
building an output left-to-right.

#### Grow the seed via post-processing of embedded markers

1.  LR is "imminent" when a production (identified by a symbol)
    is about to reenter with the same input buffer at the same cursor
    as an outstanding use.
    This probably requires keeping a set of (symbol, cursor) pairs but
    stack introspection would suffice.
1.  On detecting an imminent LR, we push a *start-LR* marker onto the
    output.
1.  Treat LR uses as failing so that control proceeds to non-LR
    alternatives.
1.  Do the below while progressing.
    1.  Push a *grow-LR* marker onto the output.
    1.  Execute the production but on an *LR* use, do not recurse and
        instead emit a *LR*
    1.  Fail unless there was exactly one *LR* use since the beginning
        of the loop.
1.  Push an *end-LR* marker onto the output.

This provides enough structure for a post-processing pass to
reconstruct the output by shifting content between *grow-LR* and *LR*
before the corresponding *start-LR* marker in reverse order and
eliding all the markers.

### Type Lifecycle

As noted before, mutable builtin types must report snapshot and
recover operations, and user types will need defensive copying.

During interpreation in early stages, journaling will help, but in
compiled code for runtime, we need to be able to eliminate most values
in scope from the set that need copying or snapshotting in inner
loops.

Most mutations to an object happen shortly after creation at which
point the object enters a steady state.

The same design choices that make a hashmap-like structure efficient
for rollback often make it leass efficient for heavy random reads.

_Descision_: the lifecycle of objects will be explicit in the type
system.  Objects may go one-way from mutable to immutable via
a *constify* operation that returns a read-only version.  Freezing
is deep.

For nominal types, a name suffix will encode information about the
lifecycle stage.

| Nominal type | Lifecycle stage | Relationship       |
| ------------ | --------------- | ------------------ |
| *T*          | Unknown         | Abstract base type |
| *T\_b*       | Mutable builer  | Subtype of *T*     |
| *T\_f*       | Frozen          | Subtype of *T*     |

*T* defines a `constify` method (() &rarr; *Tf*) which, when a *T* is a
*T\_f*, is a simple narrowing cast.

Snapshot & rollback become expensive if we have to reason about all
the mutable state that could have changed in other scopes.

_Decision_: Post type checks, **only frozen values may escape** the
scope in which they were created to a broader scope except as an
output of a function call.

The set of values that might roll back due to a failing operation is
then the set of values reachable via symbols on the stack, and
any mutable global state.

### Types

_Decision_: Separate types from the variable namespace by convention.
Builtin type names start with a capital letter, and by convention
variable names start with a lowercase letter.  (This will not limit
user-code identifiers to character sets with case-distinctions.)

Some common interfaces:

| Interface             | Description                                       |
| --------------------- | ------------------------------------------------- |
| *Const*               | Types that are immutable                          |
| *Mut*[T <: Const]     | Mutable type that constifies to *T*               |
| *Commitable*          | May promise not to rollback                       |
| *Snapshot*[T <: Mut]  | The result of snapshoting a *T*                   |
| *Hashable*            | Reducable to a hash code.                         |
| *Comparable*[T]       | Supports (<, =, >) style comparison against T     |
| *Series*[T]           | Supports fetching start and end cursors.          |
| *Cursor*[T]           | A position in a particular *Series*. *Comparable* |

Temper will encourage using buffers with cursors to process input and
accumulate output.

| Type     | Can Read | Can Append | Can Commit | Monotonic Length | Description                                |
| -------- | -------- | ---------- | ---------- | ---------------- | ------------------------------------------ |
| Ibuf[T]  | Y        | N          | N          | Y                | A readable input buffer                    |
| Obuf[T]  | N        | Y          | Y          | Y                | A commitable dead-drop.  Writes may block. |
| Iobuf[T] | Y        | Y          | N          | Y                | Constifies to an Ibuf[T]                   |
| Istr[T]  | Y        | N          | N          | N                | A readable stream that may block.          |

Key-value maps can build complex relations via maps whose values are maps.
Weak maps provide a place to store associated information without
pinning keys in memory.  Unfortunately, when information relates to
two or more values, weak maps can only be fully weak with respect to one.

_Decision_: Support maps via a variadic relation type.

A `Rel[[T0, T1], T2]` defines a 3-column relation that is efficient to index
by T0, T1, and (T0, T1).  `Map[T0, T1]` is equivalent to `Rel[[T0], T1]`.

Type parameters may actually bind to:

*  A single type: `T`
*  A code unit kind (see string goal): `char.UTF8`.  This may extend to other values.
*  Zero or more actual bindings: `[P0, P1, P2]`

Formal type parameters may have bounds:

*  A type bound: `T <: SuperType` or `T >: SubType`
*  One of a group of values: `CU in char`
*  A variadic bound: `[*B]`


## Staging execution

Other staged languages (TODO Meta-ML, Scala, Julia) have a code type
and operators to inline code.  Julia uses dynamic compilation of
function variants to create efficient variants of nary functions for
specific *n*.

It is a goal for a compiled bundle to be statically analyzable.
Julia-style just-in-time staging conflicts with that in two ways: it
prevents analyses that must make whole program assumptions, and it
requires a bundle to include the runtime compiler and loader so
complete analyses must account for these powerful meta operators.

It is a goal to allow casual and novice users to ignore parts of
the language that are primarily of interest to library authors.
If library usage documentation has to recommend explicit use of
an inline operator then we have failed to meet this goal re
staging.

_Decision_: A Temper phrase's semantics depend on whether it
is *satisfied*.  A reference may *expire* at the end of a
particular stage.  **Code substitution happens implicitly** when a
satisfied use is about to expire.

For example, depending on a function's signature, a call to that
function may become satisfied when the function reference and some
actual arguments are themselves satisfied.

_Decision_: **Reify stage**.  A program element may refer
to a stage.  An import or export may be available only during
certain stages.  An exporter may specify default stages for
imports.

Declarations, imports, and exports may all have stage annotations
that bound when they are available and uses become satisfied.

This should allow library code to manage satisfaction and expiration.

Implicit inlining has its own set of risks:
*  Macro hygiene: TODO quote.  The set of symbols manipulable
   by a macro is at most the set of symbols available from that
   macros scope, and those symbols mentioned by inputs to the
   macro.
*  Inlining uses of `private` symbols may not survive when
   inlined across trust boundaries.
*  The kinds of things that violate R.D. Tennent's Correspondence
   Principle also complicate inlining and outlining refactorings.

   TODO: quote

   This occurs often around `this` and `super` where a name
   that cannot be alpha-renamed has a meaning tied to the scope
   of a declaration.

_Decision_: Allow Tennent's violations and hygiene violations
because desugaring transforms often requires them, but only before
identifier-to-endpoint resolution and disallow after.

_Decision_: The internal code representation must be trivially
mobile.  Temper will define any concepts like `this` and `super` whose
meaning depends on the scope and which cannot be &alpha;-transformed
as syntactic sugar.  (This is in contrast to languages like Java where
`this` and `private` (but not `super`) are first-class concepts in the
language runtime.)

_Decision_: Define access control via object capability discipline
-- ability to reference is sufficient proof of access.  `private`
endpoints are not enumerable by middle and late stage operations.  It
must be possible to emit a code fragment that uses a `private`
endpoint but which is opaque to middle and late stages so that the
endpoint does not leak in a way that allows replay attacks.

<details>
  <summary>

Pseudocode for staging a function call: `subject.method(arg0, arg1, ..., argn)`.

  </summary>

*  If `subject` is satisfied and reduces to a value with a specific type:
   *  Let *Tsubject* be the runtime type of `subject`.
   *  Let *Smethod* be the signature of `.method` in *Tsubject*.
   *  If *Smethod* is available for calling for substitution in the current stage:
       *  Let actuals = a new empty list
       *  Let *locals* = a new empty list
       *  Let *satisfied* = true
       *  For each *actual* parameter expression in `(arg0, arg1, ..., argn)`:
          *  Let *formal* = the formal parameter in *Smethod* corresponding to *actual*.
          *  Let *local* = a new endpoint with the same descriptive name as *formal*.
          *  Add to *locals* a local declaration that initializes *local* to *actual*.
          *  Let *Ractual* = a thunk for the reduced form of *actual*.
          *  If *formal* expects code:
             *  Add a code wrapper with {*Ractual*,*local*} to *actuals*.
          *  Else:
             *  Let *actualValue*, *actualAvailable* = apply *Ractual*.
          *  If *actualAvailable*:
             *  Add *actualValue* to *actuals*.
          *  Else:
             *  Set *satisfied* = false.
             *  Break.
       *  If *satisfied*:
          *  Let *result*, *passed* = the result of applying `subject.method` to *actuals*.
          *  If *passed*:
             *  Substitute a block expression with *locals* and the body *result* for the call.

</details>

Each regular stage does a root-to-leaf walk trying to simplify nodes that are
simplifiable but which will not be next stage.

TODO: imports import from a stage of a module which means we need to keep old-stage versions of modules around.

TODO: for imports to be able to operate at lex stage, there needs to be a fixed-syntax prologue.

TODO: decision, prologue grammar.

TODO: stage specifier grammar as prefix operator.









## Testing

One common reason that APIs relax access control is to allow test code
privileged access to better interrogate production code.  Debugging
machinery often provide access that violate access controls so that
a developer running locally can better interrogate a running system.

_Design decision_: Treat **tests as attacks that abuses debug
access with love**.  Explicitly distinguish test code from production
code and provide test code with more operators which ignore internal
access controls.









This will have consequences for the kinds of allowable aliasing and
possible interleavings of operations that might fail or succeed
independently.

When recovering from a failing branch, mutations that might be visible
to later attempts must be rolled back.




This should hopefully allow crafting algorithms as searches over a
problem space, letting reviewers focus on concerns like coverage, and
correct detection, instead confirming that failing paths correctly
restore state needed by later attempts.  (Reasoning simoultaneously about
a failing branch and branches attempted later often requires non-local
reasoning which is a burden on reviewers, especially when they have to
consider what later branches might rely upon in the future.)


### Single source of truth for functions

Many operations at distributed node boundaries are dual: parsing reverses
unparsing.

Keeping a parser and unparser in sync is hard.

The third high-level goal is to allow a single-source of truth for both sides
of a dual operation.  For example, specifying parsing and unparsing in terms
of an annotated grammar that works both generatively and analytically.

_Design decision_: We need to enable **mini-declarative languages** from
which we can derive multiple different library functions.  For
example, parsing and stringifying a URL and ideally composing a URL
from trusted and untrusted content should be derivable from a
declarative grammar, possibly with annotations or hints.

### Single source of truth for documentation

It would be nice to have a single source of truth for documentation.
Documentation extracted from source files can supplement user documentation
like that in markdown files, but cannot substitute for it.

_Design decision_: **Source files are UTF-8 encoded markdown files.**
During the *Gather* stage, a fetcher fetches markdown content on behalf
of the gatherer.
The gatherer parses the markdown looking for two kinds of constructs:
*  Fenced code blocks like
    <pre>

    ```tmpr
    code
    ```

    </pre>
*  named URLs like `[name]: example/url/that/ends/with/file.tmpr.md`.

The fetcher resolves URLs whose path ends in `.tmpr.md` against the containing
source file's URL to get an absolute URL and recursively fetches and gathers
any not previously fetched.

_Design decision_: The language specification **does not specify how a fetcher
matches content**.  Specifically, it does not determine whether the fetcher
actually reaches over the network.
The fetcher should not send the fragment to external services, but may use it
to perform resource integrity checks.  Because of this, if two URLS are the
same except for the fragment, the fetcher may respond with different content,
for example to halt the build process early due to an integrity check failure,
but absent such concerns should notify the gatherer that they are the same.

_Design decision_: **A code blocks language id** determines how the gatherer
treats it.

| Lang id | Role                                                            |
| ------- | --------------------------------------------------------------- |
| `tmpr`  | Production Code                                                 |
| `tmpt`  | Privileged Test Code                                            |
| `tmpe`  | Example code.  A mini unprivileged test left in generated docs. |
| `tmpp`  | Example code.  Instead of passing it should panic.              |

### Kernelizability

`public`, `private` and similar mechanisms allow checking what can link to what
and lets modules distinguish APIs meant for internal consumption from those not.

These typically depend on some overarching module naming scheme.

_Design decision_: Provide some access control checks.  Maybe have each module
export a public key and during the *Control* stage ask the linked module whether
the linker can access the API.

### Dependencies and staging

Per macros and the program lifecycle above, after the *Gather* stage,
chunks of code need to collaborate with other chunks.  We need some way to import
symbols and export symbols.

_Design decision_: **Imports must all appear in a syntactically rigid
prologue at the top of an extracted code block**, so that the *Syntax*
stage can find any custom parsers.

Per our mini-declarative language requirements above, we need a way to
specify that some tokens are specially handled.

_Design decision_: During stage *Syntax*, **`@Syntax f {{{ ... }}}`
hands off lexing and parsing to `f`**. `f` will receive the input
after `{{{` and a callback for nested parsrs, and if it succeeds will
produce a parse tree and advance the input to before the `}}}`.

Custom parsers will need to be functions that are more fully developed
than parsed code, so may need to have already reached the `@Type` stage.

_Design decision_: **Code blocks advance through stages independently**
and the graph of `import`s will define which blocks advance through which
stages.  Specifically `import @Syntax f from @Type [foo]` means
"The current code block cannot proceed to the *Syntax* stage until the
module identified by the URL reference `[foo]` finishes the *Type* stages,
and during the current modules *Syntax* stage, the symbol `f` will
bind to a symbol of the same name that `[foo]` exports."

We are using `@Stage` above to specify when something happens (invocation
of a custom parser on a delimited block in a nested language), when something
is ready to import, and when a symbol is available.
An import might need to be availabe during multiple stages as might an export.

_Design decision_: **The \@ syntax below specifies a contiguous range of stages**.
The language will use it widely to specify both availability and use time.
Preceding a call, it specifies when the call happens.  Preceding a function definition,
it determines when the function can apply.  The actual application time of a
function call is the intersection of the declared stages and the applied stages, and
the availability of any symbols used to reach the function.

```bnf
Stages ::== "@" StageRange;
StageRange ::== StageName
              / LeftStageBound "," RightStageBound;
LeftStageBound ::== LeftStageBracket StageName?;
RightStageBound ::== StageName? RightStageBracket;
LeftStageBracket ::== "["   // inclusive
                    / "(";  // exclusive
RightStageBracket ::== "]"  // inclusive
                     / ")"; // exclusive
StageName ::== Identifier;
```

This range syntax lets authors control which language elements convert into host
language code and which solely serve to bootstrap the input to host language code
generators.

It might be nice to be able to refer to stage ranges.  `@local` won't
work as well when `local` is a local variable name whose value is a
stage range.  There is a chicken-egg problem in having stage ranges
depend on symbols that have their own ranges of availability, but this
might be resolvable if `local` were imported into local scope.

_Design decision_: `Syntax`, `Type` and other **stage names are
builtin, globally visible constant stage range values**.  `Syntax` is
intentionally capital so that it does not intrude on the local
variable namespace

_Design decision_: In local variable declarations, the initializer
runs immediately prior to the symbol becoming available.  This
suggests that **each code block is either in a stage,
or between stages after completing a stage**.

_Design decision_: **Declarations happen between stages**, so that the
declared is available over its whole range.  **Uses happen as late as
possible**, based on the intersection of the used values availability
and any explicit hint, so that function call arguments are as
thoroughly processed as possible.

This suggest the following flow for processing stage *n*
(where *n* > *Gather* since *Gather* is responsible for bootstrapping
 and doesn't involve running user code):

1.  Wait until all dependencies of symbols needed from *n* on have
    reached the stage on the right of the import.  If multiple stages
    become live at the same time, process them in the order in which
    they were gathered.
1.  Create bindings that become available at stage *n*.
1.  Initialize bindings that become avaliable at stage *n*.
1.  Check stage *n* specific preconditions.
1.  Process stage *n* by walking parse tree and applying calls /
    dereferencing symbols that become unavailable after stage *n*.
1.  Check stage *n* specific postconditions.
1.  Mark stage *n* finished.
1.  Snapshot state at end of *n* if any other code blocks might have
    unapplied uses of symbols imported from the current module.
1.  Eliminate bindings that become unavailable after stage *n*.
1.  Reschedule as potentially ready for stage (*n*+1) unless *n* is *Generate*.

We need to make it clear between stages when imported symbols becomes
available for resolution.  There are multiple code blocks within a file
and it would be convenient for blocks within the same file to be able
to share symbols, but each block needs to be able to proceed independently.
We could require that blocks within a file proceed in forward or
reverse order.  I'd rather not since that seems like it would complicate
processing, and moving code between files.

We may also want to export symbols to a specific set of files and then
double check \@*Check* that no exports violate access restrictions
(see discussion of `private` above).
An author can restrict error-prone APIs to importers that are known
to use it correctly and which might provide generally safe wrappers
so do not themselves warrant access restrictions.

_Design decision_: **Exports must appear in the prologue.**

_Design decision_: **Exports may export to explicit importers, or to
the containing file.**  This makes it much easier to write example code.
See <tt>tmpe\`\`\`...\`\`\`</tt> blocks above.  A block can export the same
might be exported multiple times with different restrictions, and
during different ranges of stages.

```bnf
Export ::== "export" ExportBindings ("to" ExportReceivers)? ExpertRestrictions?;
ExportBindings ::== StageRange? ExportBinding ("," ExportBindings)?;
ExportBinding ::== Expression "as" Identifier
                 / Identifier;
ExportReceivers ::== "."    // export implicitly to all blocks in same file
                   / "*"    // make available for explicit import by other blocks
ExportRestrictions ::== "if" StageRange? Expression;  // Unless otherwise specified evaluated @Check.
```

_Design decision_: **Production code blocks must not import from test or example code.**
Production code blocks do not implicitly imports from test or example code blocks.

Restrictions on implicit imports (`to .`) are only evaluated if the symbol is ever used.

The set of exports for all code blocks in a file is the set of exports available when
implementing that file.  Markdown provides a way to refer to fragments within a file.
`(#fragment)`.

_Design decision_: The gatherer will collect `# ... {#id}` and associate the *id* with the
syntactically next fenced code block.

The last unresolved detail for import syntax is how to refer to a block of code.

_Design decision_: **Markdown <code>\[id\]</code> and <code>\(#id\)</code> refer to export bindings**.
It is a fatal error if `[id]` was not gathered during the gather phrase, or
if the identifier in `(#id)` is not associated with an importable code block.

_Design decision_: **There is no wildcard syntax for import bindings**.   If a code block or file
has many bindings which clients might want to use as a group, it can collect them in a namespace
and export the namespace.

```bnf
Prologue ::== PrologueElement*;
PrologueElement ::== Export
                   / Import;

Import ::== "import" ImportBindings "from" ExportBindingsReference;

ImportBindings ::== StageRange? Identifier ("as" Identifier)?;

ExportBindingsReference ::== StringLiteral;
// The StringLiteral contentns must match
MarkdownReference ::== "[" NoSpace MarkdownIdentifier NoSpace "]"   // References file
                     / "(#" NoSpace MarkdownIdentifier NoSpace ")"; // References block in same file
```


### End Game
When all code blocks are ready for the *Generate* stage the code blocks should be good inputs
to host language code generators.

_Design decision_: The `export`/`import` message links language elements internally, and
is not involved in deciding how host language code interacts with language elements at
*Run* time.

_Design decision_: We will provide a way via the code generator interface to **partition
the set of code blocks** into multiple independent generated libraries that
link to one another.  This requires stability of generated code identifiers
from one generator run to another, so generators might have to produce a side table
of generated names that can feed back into subsequent runs.

Consider a code block that is ready for *Generate* that has a structure like the
following JavaScript pseudocode:

```js
({
  "namespace": {
    "incr": (x) => x + 1
  }
  [Symbol.for('Externs')]: [
    ["namespace", "incr"]
  ]
})
```

This might indicate that host language code should be able to use some syntax like
`namespace.incr` or `namespace::incr` to invoke `(x) => x + 1`.

_Design decision_: Code generators should be free to adjust identifier names
to match host language style conventions.

_Design decision_: Priovide builtin functions that only apply `@Bundle` that
declare namespaces and attaching both external and internal properties.
Property values are constant values or declarations.

Processing the *Generate* stage consists of:

1.  Get the set of namespaces and properties defined from the `@Bundle` builtins.
1.  Error out if there are any duplicate properties.
1.  Double check that all types in the signatures of externally visible declarations
    are themselves externally visible via some name.
1.  Take a partition map describing which namespaces go in which output libraries.
1.  Insert accessors for any cross-library references that are unreachable
    via external APIs.
1.  Generate code for each library.

### Typing, Aliasing, and Composition

Method and operator dispatch should work the same before and after
type checking and compilation.  Host languages like Java provide
multi-phase dispatch based on a distinction between the static type of
a message receiver and the runtime type.  C++ has another level of
complexity based on whether methods are `virtual`.

_Design decision_: **There is no way to subtype a concrete type.** It
is not a goal to support object-oriented decomposition of application
domain objects using deeply layered abstract classes.  Those are
available in most host languages, so we could revisit this.

_Design decision_: **Provide interfaces** as bundles of interface elements
that do not play a role in method dispatch.

Some host languages like JavaScript treat objects as bags of properties.
Others (C++, Go, Java) define types with a fixed-at-compile-time static vector with a
known memory footprint.

_Design decision_: All types that user code can create or mutate will have a
**well defined memory footprint**.  Separate **immutable bag types**
that user code cannot create will allow host language code to pass in bags.

It would be nice if rollback of side effects did not need to be idempotent.
If there are *n* values that might have to rolled back together, there are
potentially (*n* * *n-1*)/2 aliases that we might have to check to avoid
multiply rolling back.
If there's a way to compare two snapshots, we could avoid that with
*n* potentially more complex tests.

Memory management requires that we bound all heap-allocation either to a scope or
by reference counting.



----

This is not an official Google product.
