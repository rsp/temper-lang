## Design Goals

### Libraries should interoperate well with many host languages.

Nodes in distributed systems like web applications often use ad-hoc
methods to parse and unparse message bodies.  The message body formats
are often well-understood and well-documented, but not all languages
that people write systems in have good library support for parsing,
unparsing, and composing message bodies.  When failures result, they
are too often security relevant.

The first high-level goal is to provide a way to write libraries for
simple, well-understood, but tricky transformations at the borders
between nodes in distributed systems.  These libraries should be
accessible from most "host" languages that people use to write
distributed systems.

Initially, interop with (C, Java, Python, C#, Go, and Rust) should
allow us to work with code on most of the major multi-language VMs and
allow a single library to serve many clients.

_Design decision_: Code will not run in a dedicated VM or be tied to a
single existing VM.  Instead the primary deployment strategy will be
**code-generation** alongside small runtime-support libraries.

Most of the targeted host languages have garbage collection, but not all.

_Design decision_: Memory allocations will be primarily **scoped allocations**,
possibly with scoped reference-counted pointers.

_Design decision_: **Auto-expanding buffers** will be the primary way to
allocate space for an output of unknown unpredictable size.

Host languages are imperative more than functional languages.

_Design decision_: Idiomatic code will be **primarily imperative** so that
there is an analogous construct for most constructs in the target language.

Host languages differ in how they represent character/byte content
internally and this determines the efficient way to chunk such
content.  Similarly, parsing inputs requires understanding its input
representation.  Some languages are specified either as strings of
codepoints, many as UTF_16 code units, and some like URIs as sequences
of octets.

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

_Design decision_: **Types specify code-unit granularity** when they
represent binary or textual content.  Parameterizing string and buffer
types should suffice.


### Suitable for writing code that can be checked by humans

Checking the correctness of security-relevant code is hard.
It's harder when the language makes it hard to write reviewable code.

The second high-level goal is to favor language features that enable
case-based reasoing by reviewers, and eschew features that almost-always
do what one intends but come with caveats that require lots of work to
check.

Most host languages use a combination of exceptions, return value
checking, and option types to communicate failure of an operation.

_Design decision_: Statement level constructs, as in Icon, may succeed or
fail, and **success or failure drives branching in flow-control constructs**.
For example, the primary iteration (see decision re imperative above)
will continue as long as the body succeeds in making progress towards
a goal.

_Design decision_: **Failing branches' side effects will be invisible**
except for out-of-band concerns like debug logs.  All operations will
*fail purely*; though imperative, an operation that fails to produce a
result cannot modify any inputs or shared state.

This will have consequences for the kinds of allowable aliasing and
possible interleavings of operations that might fail or succeed
independently.

When recovering from a failing branch, mutations that might be visible
to later attempts must be rolled back.

_Design decision_: We are not going to rely upon or implement
transactional memory for N host languages.  Mutable types must
define **snapshot and rollback** operators.  For example, all buffers
(see above) could be append-only, cannot themselves contain mutable
values, and do not allow random-access instead providing access by
scoped cursors.  Rolling back a buffer means truncating it to its length
when the ultimately failing operation started.

This should hopefully allow crafting algorithms as searches over a
problem space, letting reviewers focus on concerns like coverage, and
correct detection, instead confirming that failing paths correctly
restore state needed by later attempts.  (Reasoning simoultaneously about
a failing branch and branches attempted later often requires non-local
reasoning which is a burden on reviewers, especially when they have to
consider what later branches might rely upon in the future.)

_Design decision_: This will **provide neither file\-system nor shell** access
via builtin libraries.  If we need to revisit this, we may provide a way to
*commit* a buffer that contains a series of shell and/or file mutation
operations, so that rolling back past the commit point results in a
system-wide panic.

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

### Monotonically decreasing dynamism

Many programming languages have a load or compile step, and reflective or
introspective operators that relate runtime strings to programming language
symbols.

It is easier to check a program for correctness if a reviewer knows that
attacker-controlled strings cannot reach these reflective or introspective
operators.

Rejected approaches:

  *  Some systems have relied on *taintedness* to protect sensitive operators
     from crafted strings; strings carry an *evil bit* which sensitive operators
     check, and a concatenation of a tained string is itself tainted.
     Tainting systems have a poor history of providing strong guarantees.
  *  Trademarks per "Protection in Programming Languages" by Morris Jr. are better
     but reflection/introspection also complicate code elimination and require
     speccing meta-objects so I'd rather satisfy reflective/introspective use
     cases without reflection/introspection.

Implementing mini-declarative languages (above) typically requires macros
unless you plan on parsing at runtime which is itself prone to abuse by
attacker-controlled string.

_Design decision_: **Reify lifecycle stage** of a program.  Operator definitions
and uses can be tagged with a range of lifecycle stages.  Program elements that
execute at later stages are available as parse trees to earlier stages.
The last stage, *Run*, has no reflective or introspective access and is the only
stage that happens after a program has been converted to generated code in a host
language.

Tenatively, lifecycle stages include:

1.  *Gather* Dependencies
1.  *Syntax* to Parse Tree
1.  *Expand* Macros
1.  *Type* Check Code
1.  *Control* Access
1.  *Interface* Implemented Checks
1.  *Bundle* Types and Declarations into Namespaces
1.  *Generate* Host Language Code
1.  *Run*

This means that introspection, meta-circularity, macroing can all
happen before untrusted input reaches a system.  We assume all inputs
that reach the system are trustworthy -- we trust everything
*Gather*ed and treat software supply-chain security as out-of-band.

One common reason that APIs relax access control is to allow test code
privileged access to better interrogate production code.  Debugging
machinery often provide access that violate access controls so that
a developer running locally can better interrogate a running system.

_Design decision_: Treat **tests as attacks that abuses debug
access with love**.  Explicitly distinguish test code from production
code and provide test code with more operators which ignore internal
access controls.

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

    \`\`\`bff
    code
    \`\`\`

    </pre>
*  named URLs like `[name]: example/url/that/ends/with/file.bff.md`.

The fetcher resolves URLs whose path ends in `.bff.md` against the containing
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

| Lang id | Role                                                 |
| ------- | ---------------------------------------------------- |
| `bff`   | Production Code                                      |
| `bft`   | Privileged Test Code                                 |
| `bfe`   | Example code.  A mini unprivileged test that we leave in generated docs but test to make sure the docs make sense. |

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
See `bfe\`\`\`...\`\`\`` blocks above.  A block can export the same  might be exported
multiple times with different restrictions, and during different ranges of
stages.

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

_Design decision_: **Classify types as *pass-by-value* (pv), *outlive*
(ol), *scoped* (sc), *scoped-ptr* (sp)** based on whether they can
be copied from one stack scoped location to another, whether they predate
and outlive a call from the external library, and whether they must be stack
allocated.

  *  Pass-by-value values can leak to globals and pass around
     promisciously since they involve no deallocation step, and
     snapshotting involves byte copy.
  *  Scoped values must only be reachable from the scope by **one name
     at a time**.  They must not escape from a scope to an outer scope
     -- e.g., they cannot leak to globals.
  *  Outlive values must not be mutable by user code, so user code can
     ignore outlive values since they are host language code's
     responsibility.  They must not leak to globals so that the host
     environment can deallocate them without affecting later library
     calls into user code.
  *  Scoped pointer values can pass as part of a collection to a
     narrower scope.  The pointed to value might change via the
     pointer.  The pointer itself does not survive a scope, and its
     scope must be narrower than its referent.

_Design decision_: **Classify types as *immutable* (imu), *mutable* (mu)** based
on whether they can change.  Immutable types need not support snapshot, rollback.
Only *scoped* types can be *mutable*.

_Design decision_: **Pass-by-value types include**
`boolean`, `int`, `long`, `double`, `char[t in cu]` (`char` takes code unit type).
Collections of *pbv* types are not themselves *pbv*.  All *pbv* types are *imu*.

_Design decision_: **Outlive types include** *baglist* and *bag* to interoperate
with loosely typed host languages like JavaScript which pass around bags of
properties and untyped arrays.

_Design decision_: **Scoped-ptr types include** `ptr[t]`.
If `t` is *sc* then the pointed to value must be available when the pointer is
created, and `ptr[t]` is *imu*.  If `t` is *pv* then `ptr[t]` is *mu*.  The value
pointed to can change.

_Design decision_: **Scoped types include** all collection types and user defined types.

| Type              | Constraints      | Attributes  |
| ----------------- | ---------------- | ----------- |
| boolean           |                  | imu, pbv    |
| int, long, double |                  | imu, pbv    |
| char[t]           | t in code-units  | imm, pbv    |
| string[t]         | t in code-units  | imm, sc     |
| ibuffer[t]        | t in pbv         | imm, sc     |
| obuffer[t]        | t in pbv         | mu, sc      |
| list[t]           |                  | mu, sc      |
| set[t]            |                  | mu, sc      |
| sptr[t]           | t in pbv, sp, sc | mu, sp      |

TODO: Function parameters contain explicit type constraints.  f(x T0, y T1) where x :< y means
x is allocated in the same or narrower scope than y.  Allows x to be added to the collection y.

TODO:
ordered types simplify memory management, and prevent the need for rollback to be idempotent


_Design decision_: Types available at *Runtime* are:
  * `boolean` is in imm, pbv, ext
  * `int`, `long`, `double` are imm, pbv, ext
  * `char[t in cu]` is in imm, pbv, ext
  * `string[t in cu]` is in imm, ext
  * `ibuffer[t in pbv]` where `t` <: the ibuffer type is in com, is in ext when `t` is in ext
  * `obuffer[t in pbv]` where `t` <: the obuffer type is in com, is in ext when `t` is in ext
  * `list[t in pbv]` is in ext when t is in ext where `t` <= the list type
  * `set[t in imm]` is in ext when t is in ext
  * `map[k in imm][v in pbv]` where `v` <: the map type, is in ext when each of (k, v) are in ext
  * `iset[t in pbv]` where `v` <: the set type, is in ext when t is in ext
  * `imap[k in imm][v in pbv]` where `k` <= the map type and `v` <: the map type,
     is in ext when exach of (k, v) are in ext
  * `ptr[t]`. `ptr[t]` orders the same as `t`.
  * `extlist`, `extmap` is in imm, ext.
     They allow access to loosely typed, possibly circular external values that precede
     and outlive any call.
  * interface types are >:= their greatest type parameter.
  * class types are >:= max[...members, ...parameter] with possible exception of `rec` fields
    that must be const initialized at create time so cannot be cyclic.

TODO: Dispatch based on type tag, endpoint.  An endpoint is either an opaque symbol or a textual names.
Symbols can be allocated prior to the *Type* stage.  External APIs must be name based.

TODO: An allocator can be created prior to the *Type* stage that takes a state vector description
and produces zeroed regions of memory with enough room for the state vector and which tags each
vector with a type tag.  These correspond to classes.

TODO: An interface type can be created prior to the *Type* stage.  It relates endpoints to method types.

TODO: Interfaces.

TODO: Type parameters via erasure.  Declarations determine variance.
Specialization for builtin types via host language at construct time.
This means that variance violations due to type mismatch between parameters
might not be caught.

TODO: Type unions and intersections.

### Operators
A major use case of for this language is processing left-to-right over a buffer and
accumulating output on another buffer.


### Function Call Dispatch


