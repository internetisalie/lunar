---
id: "TYPE-12-DESIGN"
title: "12: Design — one wiring hub per receiver and method name"
type: "design"
parent_id: "TYPE-12"
folders:
  - "[[features/type/12-member-demand-clique/requirements|requirements]]"
---

# TYPE-12 Design — one wiring hub per receiver and method name

## 1. What changes, in one paragraph

Every colon call site keeps its own member node (the "site member"), and that node is still the one
the call's `CALL` demand hangs from. What changes is how the site member reaches the receiver's class
member. Today each site member is wired to the class member **bidirectionally**, and the transitive
closure makes every site member of one receiver mutually reachable: an n-way clique. After this
change, the **first** site member for a given `(receiver node, method name)` pair is that pair's
**hub**. Only the hub is ever wired bidirectionally into a class member; every later site member is
attached **downstream of the hub by one forward edge**. The hub reaches the class member exactly as
the single site member always did, so for a receiver with one call site nothing changes at all. One
consequence of removing the clique is that diagnostics the clique used to report once per site, by
anchoring on a site member acting as a *value*, would be reported only at the hub's site; the graph
replicates those to every later site of the same hub, so the anchored diagnostic multiset is
unchanged. Everything else — the visitor's other member minting, `checkTypes`, the BUG-473 memo and
hoist, the inference surface — is untouched. Equivalence is gated by golden files recorded from
today's engine before the change (§4.6, §5.5).

## 2. Constraints examined rather than assumed

| constraint | fixed or chosen | why |
| :-- | :-- | :-- |
| The reverse member edge in `checkTableCompatibility` stays | **fixed** | It carries the declaration's signature to the call (BUG-419). Dropping it stays red under this design as mutation M-C (risks-and-gaps DR-03). |
| A per-site member node stays | **chosen, on measurement** | Its `read` is that site's own `CALL` demand. The variants that deleted it (A1, A2; §10) lost per-site diagnostics (DR-02). |
| The anchored diagnostic multiset is preserved exactly, including HYPOTHESIS entries the clique produced at each site | **chosen** | TYPE-12-03 and the BUG-419 lesson: a performance change must not move what the engine says. The entries are user-observable — `LuaTypeHypothesisAnnotator` hangs the annotate-it intention on each. Removing the per-site duplicates is a separate behavioural decision, recorded in risks-and-gaps as future work, not taken here. |
| Deduplication key is the receiver **node identity**, not the receiver's class | **chosen** | The class is unknown when the visitor mints; it is discovered by `checkTypes`. Node identity is what `LuaScope.lookup` already shares between references of one binding (`LuaScope.kt:36`). Consequence: `self:m()` across methods is not deduplicated (DR-05). |
| Dot-access members (`b.name`, `visitIndexExpr`) are out of scope | **chosen** | They form the same clique (DR-05 measures it) but they are also assignment targets (`declaresMember`), so the hub rule's premise — a site member has no writes of its own — does not hold there without a separate argument. |
| No permanent dual-path differential | **chosen** | BUG-473 shipped `LuaTypePairWriteDifferential` because its hoist observed the same graph two ways. Here the two representations are two different graphs; keeping one alive for a test means keeping the clique-minting code in production. Equivalence is carried instead by golden files recorded from today's engine (§4.6), the pinned anchored fixtures (the requirements cases on single fixtures), and the one-time corpus-wide differential DR-04. |
| The corpus `.baseline` files are **not** the equivalence gate | **fixed** | They hold per-inspection counts of non-HYPOTHESIS findings. Every diagnostic §5.3 replicates is HYPOTHESIS (Appendix A, DR-08), and both type inspections skip HYPOTHESIS (`LuaTypeAssignabilityInspection.kt:32,38`, `LuaReturnTypeMismatchInspection.kt:30,36`), so a broken replication leaves every baseline byte-identical. They stay a gate for TYPE-12-05 only. |
| Golden files are **permanent** and re-recordable, not a one-off | **chosen** | A one-off comparison would have to be rebuilt for the next engine change. A permanent golden makes every future inference change visible as a reviewable diff; the cost is one `-PrecordTypeGolden` run when a change is intended. The record switch copies the existing `lunar.corpus.record` pattern (`build.gradle.kts:334-336` on `main` at `a90adac1`, read at `LuaCorpusSweepTest.kt:98`) because it already solves the same problem, not because it exists. |
| Member files are configured **alone**, not inside their project | **chosen** | A file configured alone has no cross-file globals to resolve, so its golden cannot inherit the build-order artifact DR-04 found. Cost: cross-file inference is outside the standing gate (requirements, "what is guaranteed, and what is not"). |
| Member-file goldens are skipped in CI | **chosen** | CI could fetch the corpus (`tooling/corpus/fetch-corpus.py`) and run them. It does not, for cost: fetching the pinned third-party projects on every push buys nothing the builder's ordinary suite, which has the corpus and is this plan's gate, does not already give. It is the trade-off `build.gradle.kts:300-305` records for `ParseOracleTest`, made the same way. |
| A golden's per-element deep rendering is stored as a hash | **chosen** | The deep rendering of one element can run to kilobytes (class tables with every member). Range, PSI class and `displayName()` stay readable; equality of the deep rendering is carried by a 16-hex-digit SHA-256 prefix. On a mismatch the test writes the full actual golden to `build/type-golden/actual/` for diagnosis. |
| A golden's rendering keeps the engine's own orders (member order, union arm order); nothing is sorted or normalised beyond the line sort of §5.5 | **chosen** | Sorting members in the rendering would hide a nondeterministic member order — the engine defect DR-09 found, BUG-498 (`materializeClass`, `LuaTypeManagerImpl.kt:344`) — and every later defect of the same kind. The fixture is changed instead (next row). |
| No shape fixture declares a `---@class`/`---@alias` name that a bundled stub declares | **chosen** | A shape is configured with the bundled stubs attached, so a class name shared with a stub merges two declarations and inherits BUG-498. The fixture that did (`fileUnion`, `---@class File`, against e.g. `runtime/standard/lua-5.4/io.lua:29`) is replaced by `pipeUnion`, the same text with the 4-character name `Pipe`, so every offset is unchanged (Appendix C). A test enforces the rule (§5.6). The stub-collision shape is **excluded pending BUG-498** (Appendix C, "Excluded shape"). |
| A recording is written under `build/`, and copied back from there | **chosen** | `gce-builder.sh` `cmd_sync` runs `rsync -az --delete --filter=':- .gitignore'` from whichever worktree is syncing into the one shared checkout (`gce-builder.sh:141-144`). It deletes an untracked `src/test/resources/types-golden/` on the builder and spares the ignored root `/build/` (`.gitignore:2`; DR-11 Part A and step 3). `gce-builder.sh` takes no lock, so "record and copy back under one lock hold" has nothing to hold. The recording goes to a path the sync does not delete instead (§5.5). |
| Wall-clock is not an assertion | **fixed** (BUG-474) | The growth gate is the closure size, a deterministic count. Timings are recorded as evidence only. |

## 3. Current state and prior art

### Current state

- `LuaTypesVisitor.visitFuncCall` (`LuaTypesVisitor.kt:864`), the method branch at `:922-931`:
  `graph.variable(methodExpr)` per site, a `LuaGraphType.Table(localMembers = mapOf(methodName to
  memberNode))` per site, `graph.addEdge(calleeNode, graph.use(methodExpr, tableConstraint))`,
  `calleeNode = memberNode`.
- `LuaTypeGraph.checkTableCompatibility` (`LuaTypeGraph.kt:844`), the bidirectional wiring at
  `:858-859` (`addEdge(valueNode, useNode)`; `addEdge(useNode, valueNode)`).
- `LuaTypeGraph.checkNodePairs` (`LuaTypeGraph.kt:420`), which calls `checkCompatibility` once per
  admitted `(valueNode, useNode)` pair at `:452-460`, with `certain` (`:431`) and
  `unknownProvenance` (`:438`) computed over the visited node's up-set.
- `LuaTypeGraph.addError` (`LuaTypeGraph.kt:265`) de-duplicates on `(element, message)` and drops
  file-wide anchors; `reportIncompatible` (`:301`) anchors on the use element unless it is
  file-wide, else on the value element.
- `LuaTypesSnapshot` (`LuaTypes.kt:55`) holds `elementNodes` and `globalNodes` privately and exposes
  `getValueType` (`:75`), `getGlobalType` (`:77`), `getFileReturnType` (`:65`) and `getErrors`
  (`:112`).

### Prior art in this repo

| component | relation |
| :-- | :-- |
| `LuaTypesVisitor.globalNodes` / `globalNode` (`LuaTypesVisitor.kt:36-41`) — one node per global name, `getOrPut` | **pattern reused**: the hub map is the same per-visitor memo, keyed on `(receiver, name)` instead of a name |
| `LuaTypesVisitor.visitFuncDecl` member minting (`:848`, `declaresMember = true`) | **not changed** — a declaration has writes |
| `LuaTypesVisitor.visitIndexExpr` member minting (`:1198`) | **not changed** — out of scope (§2) |
| `RootMemo` / `LuaTypeGraph.revision` (BUG-473 Phase 1), `checkNodePairs` write hoist and `LuaTypePairWriteDifferential` (Phase 2) | **kept unmodified**; their gates stay green (requirements case 8) |
| `LuaTypeGraph.compatMemoSize` (`:940`), `rootResolutionCount` (`:949`) — `@TestOnly` meters | **pattern reused** for the new `closureSize()` meter |
| Corpus `.baseline` ratchet (`CorpusBaseline`, `CorpusMetrics.kt:106`; `lunar.corpus.record`) | **not extended**: counts cannot carry anchors or types (§2). Its record-switch pattern is reused by the golden gate |
| `CorpusManifest.CORPUS_DIR` (`CorpusManifest.kt:39`) | **reused** to locate member files |
| `MemberEnumerationGoldenTest` (COMP-09, `src/test/kotlin/net/internetisalie/lunar/definitions/`, golden `src/test/resources/comp09/member-enumeration.golden`) | **pattern reused, not extended**: the same "record today's output before the change, assert equal after" gate, over a different surface (`LuaTypeManager` member enumeration across library roots, not a snapshot's errors and element types). Not extended because its fixture is a library-root project and its rows are per-door member lists; a snapshot golden needs per-file errors and per-element types. Its re-record path is by hand from printed output; this gate uses a record switch instead, so a recording is byte-exact and cannot be mis-pasted |

No existing component deduplicates call-site member demands (searched: `getOrPut` in
`lang/psi/types/`, `IdentityHashMap` in `src/main`, every `graph.variable(` call in the visitor). No
existing test enumerates a snapshot's elements: `elementNodes` is private (`LuaTypes.kt:58`) and is
read only by `getValueType` (`:75`); a search of `src/test/kotlin` for `elementNodes` and `.golden`
finds only `MemberEnumerationGoldenTest`.

## 4. Components

### 4.1 `net.internetisalie.lunar.lang.psi.types.LuaMemberDemandHubs` (new, `internal`)

- **Responsibility**: the graph-side record of which site members hang off which hub, and at which
  PSI anchors each hub's sites sit.
- **Threading**: owned by one `LuaTypeGraph`, which is built and checked on one thread inside the
  `forFile` cached-value computation (read action). No synchronisation.
- **Retention**: holds `TypeNode`s and `PsiElement`s already held by the same graph's `_nodes`; one
  object group, one lifetime. No `Project`/`Editor`/`PsiFile`/`VirtualFile` reference.
- **Key API** (complete):

  ```kotlin
  internal class LuaMemberDemandHubs {
      private val hubOfSite = java.util.IdentityHashMap<TypeNode, VariableNode>()
      private val siteAnchors = java.util.IdentityHashMap<TypeNode, MutableList<PsiElement>>()

      /** Records [siteMember] as a later site of [hub]. Never called for the hub itself. */
      fun attachSite(hub: VariableNode, siteMember: VariableNode) { hubOfSite[siteMember] = hub }

      /** Appends [anchor] to [hub]'s site anchors. The hub's own anchor is recorded first. */
      fun recordSiteAnchor(hub: VariableNode, anchor: PsiElement) {
          siteAnchors.getOrPut(hub) { mutableListOf() }.add(anchor)
      }

      /** The node `checkTableCompatibility` wires in place of [memberNode]. */
      fun wiringTargetFor(memberNode: VariableNode): VariableNode = hubOfSite[memberNode] ?: memberNode

      /** Anchors of every site of [node] except its first; empty unless [node] is a hub with ≥ 2 sites. */
      fun replicaAnchorsFor(node: TypeNode): List<PsiElement> {
          val anchors = siteAnchors[node] ?: return emptyList()
          return if (anchors.size < 2) emptyList() else anchors.subList(1, anchors.size).toList()
      }
  }
  ```

  `IdentityHashMap` is explicit rather than relied on: `VariableElement` (`LuaTypeNodes.kt:127`) does
  not override `equals`, but the maps must stay identity-keyed even if a future node type does.

### 4.2 `LuaTypesVisitor` (edited)

- New private members:

  ```kotlin
  private data class MethodDemandKey(val receiver: TypeNode, val methodName: String)

  private val methodDemandHubs: MutableMap<MethodDemandKey, VariableNode> = mutableMapOf()

  private fun methodSiteMember(receiver: TypeNode, methodExpr: LuaMethodExpr, methodName: String): VariableNode
  ```

  `MethodDemandKey.receiver` compares by identity in practice: every `TypeNode` implementation is a
  plain class (`ValueElement` `LuaTypeNodes.kt:95`, `UseElement` `:120`, `VariableElement` `:127`,
  `LazyValueElement`), so data-class equality falls through to `Object.equals`.
- The method branch at `:924-931` becomes:

  ```kotlin
  if (methodExpr != null) {
      val methodName = methodExpr.nameRef?.text
      if (methodName != null) {
          calleeNode = methodSiteMember(calleeNode, methodExpr, methodName)
      }
  }
  ```

  `methodExpr` is already typed `LuaMethodExpr` (`LuaNameAndArgs.getMethodExpr()`,
  `src/main/gen/.../LuaNameAndArgs.java:14`).
- `methodSiteMember` implements §5.1 exactly. Nothing else in the visitor changes.

### 4.3 `LuaTypeGraph` (edited)

- New property: `internal val memberDemandHubs = LuaMemberDemandHubs()`.
- `checkTableCompatibility`: the two `addEdge` calls at `:858-859` take the hub-redirected target
  (§5.2). The `else` branch (missing field) is unchanged.
- `checkNodePairs`: the `checkCompatibility(...)` call at `:452-460` is replaced by one call to a new
  private `checkAdmittedPair`, which brackets it with the replication of §5.3. To stay inside the
  contract's ≤ 3-argument cap the per-visit flags and the pair travel as the private classes below:

  ```kotlin
  private class VisitFacts(val certain: Boolean, val unknownProvenance: Boolean)

  private class AdmittedPair(val valueNode: ValueNode, val useNode: UseNode, val valueWrite: LuaGraphType)

  private fun checkAdmittedPair(pair: AdmittedPair, facts: VisitFacts)

  private fun replicateHubAnchoredErrors(pair: AdmittedPair, firstNewError: Int)
  ```

  `checkNodePairs` computes `VisitFacts(certain, unknownProvenance)` once per visit where it computes
  `certain` and `unknownProvenance` today, and its logic-line count does not grow (the multi-line
  `checkCompatibility` call becomes one line).
- New `@TestOnly` meter, next to `compatMemoSize`:

  ```kotlin
  @org.jetbrains.annotations.TestOnly
  internal fun closureSize(): Int = edgeCount()
  ```

### 4.4 `LuaTypesSnapshot` (edited)

`@TestOnly` accessors, each placed immediately after `override fun getErrors()` (`LuaTypes.kt:112`):

```kotlin
@org.jetbrains.annotations.TestOnly
internal fun closureSize(): Int = graph.closureSize()

@org.jetbrains.annotations.TestOnly
internal fun mappedElements(): Set<PsiElement> = elementNodes.keys

@org.jetbrains.annotations.TestOnly
internal fun declaredGlobalNames(): Set<String> = globalNodes.keys
```

`mappedElements` and `declaredGlobalNames` land in Phase 0's commit (the golden harness needs
them on today's engine); `closureSize` lands with the meter in §4.3. Neither enumeration changes
behaviour: both return the snapshot's existing key sets.

### 4.5 Tests (new and edited)

- New `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaMemberDemandHubTest.kt`
  (`BaseDocumentTest`), holding the requirements cases that assert one fixture's errors, types or
  closure. Fixture text comes from `TypeGoldenFixtures` (§4.6).
- `LuaAnnotatedClassDiagnosticsTest`, `LuaTypeGraphRootResolutionBudgetTest`,
  `LuaTypePairWriteDifferentialTest`, `LuaAnnotatedFixtureSweepTest` — **unmodified**; they are gates
  (case 8). If any of them needs editing, the change is wrong.

### 4.6 The golden gate (new, test sources)

All in package `net.internetisalie.lunar.lang.types`, directory
`src/test/kotlin/net/internetisalie/lunar/lang/types/`.

- **`TypeGoldenFixtures.kt`** — `internal object TypeGoldenFixtures`, the exact text of design
  Appendix C, and:

  ```kotlin
  internal data class GoldenSource(val goldenPath: String, val fileName: String, val text: String)

  fun shapes(): List<GoldenSource>        // Appendix C's list, in its order, then the annotated resource files
  fun memberFiles(): List<GoldenSource>   // §5.5 "member files", in that order
  fun annotated(n: Int): String
  fun paramN(n: Int): String
  ```

- **`TypeGolden.kt`** — `internal object TypeGolden`:

  ```kotlin
  /** Null when [source] matches its golden (or was just recorded); else a one-line description. */
  fun mismatch(fixture: CodeInsightTestFixture, source: GoldenSource): String?

  internal fun render(snapshot: LuaTypesSnapshot, sourceSha256: String): String

  /** Every name a `---@class` or `---@alias` tag in [text] declares (§5.6). */
  internal fun declaredTypeNames(text: String): Set<String>

  /** [declaredTypeNames] over every `.lua` file under `src/main/resources/runtime` (§5.6). */
  internal fun stubTypeNames(): Set<String>
  ```

  Private helpers: `renderFor(fixture, source): String`; `errorLines(snapshot)`,
  `elementLines(snapshot)`, `globalLines(snapshot)` (each returns that section's sorted
  `List<String>`, so `render` only assembles); `renderType(type: LuaGraphType, depth: Int)`,
  `renderLua(snapshot: LuaTypesSnapshot, type: LuaGraphType)`, `hash16(text: String)`,
  `escape(text: String)`, `firstDifference(expected: String, actual: String): String`. §5.5 specifies
  each.
- **`LuaTypeGoldenShapesTest.kt`** — `class LuaTypeGoldenShapesTest : BaseDocumentTest()`, two tests:

  ```kotlin
  @Test
  fun everyShapeMatchesItsGolden() {
      val failures = TypeGoldenFixtures.shapes().mapNotNull { TypeGolden.mismatch(myFixture, it) }
      assertTrue(failures.isEmpty(), failures.joinToString("\n"))
  }

  @Test
  fun noShapeDeclaresAStubTypeName() {
      val stubNames = TypeGolden.stubTypeNames()
      val collisions =
          TypeGoldenFixtures.shapes().flatMap { source ->
              TypeGolden.declaredTypeNames(source.text).filter { it in stubNames }.map { "${source.goldenPath}: $it" }
          }
      assertTrue(stubNames.isNotEmpty(), "no stub type names found under src/main/resources/runtime")
      assertTrue(collisions.isEmpty(), collisions.joinToString("\n"))
  }
  ```

  The `stubNames.isNotEmpty()` assertion keeps the second test from passing vacuously when the
  runtime path is wrong.

- **`LuaTypeGoldenMemberFilesTest.kt`** — the same shape over `TypeGoldenFixtures.memberFiles()`,
  method `everyMemberFileMatchesItsGolden`.
- Names: no class or method name contains `Corpus` (`build.gradle.kts:313-317` on `main` excludes `*Corpus*`
  without `-PwithCorpus`, and anything inside that filter shares the sweeps' JVM, BUG-418).
- **Golden files**: `src/test/resources/types-golden/shapes/<name>.golden` and
  `src/test/resources/types-golden/member-files/<name>.golden`, format §5.5.
- **`build.gradle.kts`** (two additions):
  1. In the `test` task, next to the `recordCorpusBaseline` block (`:334-336` on `main`):

     ```kotlin
     if (project.hasProperty("recordTypeGolden")) {
         systemProperty("lunar.typegolden.record", "true")
     }
     ```

  2. In the `excludeExternalFixtureTests` filter (`:296-307`), one more line with a reason comment:
     `excludeTestsMatching("*LuaTypeGoldenMemberFilesTest")`.

## 5. Algorithms

### 5.1 Minting at a colon call site — `methodSiteMember(receiver, methodExpr, methodName)`

Input: the receiver's node (`calleeNodeRef`, already resolved by `firstNode`), the `LuaMethodExpr`,
and its name text. Output: the node the call's `CALL` demand will hang from. Steps, **in this
order** (node creation order feeds `checkTypes`' iteration order, and this is the order DR-02/DR-04
measured):

1. `key = MethodDemandKey(receiver, methodName)`; `hub = methodDemandHubs[key]` (may be null).
2. `siteMember = graph.variable(methodExpr)`.
3. If `hub == null`: `methodDemandHubs[key] = siteMember` (this site member **is** the hub).
   Else: `graph.memberDemandHubs.attachSite(hub, siteMember)`.
4. `graph.memberDemandHubs.recordSiteAnchor(hub ?: siteMember, methodExpr)`.
5. `tableConstraint = LuaGraphType.Table(localMembers = mapOf(methodName to siteMember))` — the
   **site member**, never the hub (§7, edge case "per-site call shapes").
6. `graph.addEdge(receiver, graph.use(methodExpr, tableConstraint))` — one use node per site, as today.
7. If `hub != null`: `graph.addEdge(hub, siteMember)` — a forward edge only.
8. Return `siteMember`.

For the first site of a key, steps 2, 5, 6 are exactly today's code in today's order, and steps 3–4
only write side tables: a receiver with a single call site per method is represented identically.

### 5.2 Wiring a member pair — `checkTableCompatibility`

For each `(key, useNode)` in `use.getMembers()` with a matching `valueNode`:

1. `target = memberDemandHubs.wiringTargetFor(useNode)`.
2. `addEdge(valueNode, target)`; `addEdge(target, valueNode)`.

`target` is `useNode` itself for every member node that is not a later site of a hub — every
declaration member, every index-expression member, every first site — so those pairs are wired
exactly as today. A later site is never wired to a class member directly; it reaches the class
member's whole up-set through the hub's forward edge (§5.1 step 7) and transitive closure.

### 5.3 Replicating a hub-anchored diagnostic — `checkAdmittedPair` / `replicateHubAnchoredErrors`

`checkAdmittedPair(pair, facts)`:

1. `firstNewError = _errors.size`.
2. `checkCompatibility(pair.valueWrite, pair.useNode.read, pair.valueNode.element,
   pair.useNode.element, certain = facts.certain, declaredDemand = pair.useNode.declaredDemand,
   unknownProvenance = facts.unknownProvenance)` — the existing call, arguments unchanged.
3. `replicateHubAnchoredErrors(pair, firstNewError)`.

`replicateHubAnchoredErrors(pair, firstNewError)`:

1. `replicas = memberDemandHubs.replicaAnchorsFor(pair.valueNode)`; if empty, return.
2. `hubElement = pair.valueNode.element`. If `pair.useNode.element === hubElement`, return — an
   error anchored there is anchored on the **use**, and a use belongs to one site.
3. `anchoredAtHub = _errors.subList(firstNewError, _errors.size).filter { it.element === hubElement }`
   (materialised before step 4 appends).
4. For each `error` in `anchoredAtHub`, for each `site` in `replicas`:
   `addError(error.copy(element = site))`.

Why exactly this rule: in today's clique every site member `m_j` is in the class member's up-set, so
it is the **value** of a pair against every use reachable from the class member. When that use's
element is file-wide — the synthetic use `LuaGraphType.memberNodeFor` mints on
`graph.firstNodeElement()` — `reportIncompatible` falls back to the value element, i.e. to
`methodExpr_j`, once per site. Under the hub only the hub is such a value, so only its anchor
would carry the error. Step 4 restores the others. `addError`'s `(element, message)` de-duplication
makes replication idempotent across fixed-point iterations. Without step 4 the anchored lists lose
entries at later sites' `:method` anchors while every inferred type is unchanged (mutation A6NF;
DR-06, DR-11 step 8). That every replication comes from a file-wide use is DR-08's finding; that the
use is `memberNodeFor`'s is from reading (DR-08).

`checkAdmittedPair` only brackets an existing call, so pair admission, the `checkedPairs` set, the
write hoist and the differential at `:448-451` are unchanged.

**The call's arguments are unchanged; the flags' inputs are not.** `certain` and
`unknownProvenance` (`LuaTypeGraph.kt:431,438`) are computed over the visited node's up-set, and
`VariableNode : ValueNode`, so member nodes in an up-set count as values. A later site's up-set
changes from the class member's whole component (every other site member included) to
`{hub} ∪ upSet(hub)`. What that can change, and why it is accepted without proof that no fixture
reaches it, is risks-and-gaps Risk 1.6. The shapes built to probe it (`nilMember`, `unknownMember`,
`declaredFieldNil`, Appendix C) stay in the golden set as a regression net.

### 5.4 Complexity

Let n be the call sites of one method on one receiver. Today the class member's strongly connected
component holds all n site members, so closure size grows Θ(n²) (DR-01). With the hub, the
component holds one hub; each later site member's up-set is the hub's up-set plus the hub, and its
down-set is its own `CALL` demand, so closure is Θ(n) (DR-02). Replication adds at most (sites − 1) `addError` calls per hub-anchored
error; `addError`'s existing linear de-duplication scan is unchanged (risks-and-gaps Risk 1.4).

### 5.5 The golden format and its rendering — `TypeGolden`

**`mismatch(fixture, source)`**:

1. `actual = renderFor(fixture, source)`.
2. Write `actual` to `File(System.getProperty("user.dir"), "build/type-golden/actual/${source.goldenPath}")`
   (create parent directories).
3. `golden = File(System.getProperty("user.dir"), "src/test/resources/types-golden/${source.goldenPath}")`.
4. If `System.getProperty("lunar.typegolden.record") == "true"`: `recorded = File(System.getProperty("user.dir"),
   "build/type-golden/recorded/${source.goldenPath}")`; create its parent directories, write `actual`
   to `recorded`, print `[type-golden] recorded <goldenPath>`, return `null`. Record mode never writes
   under `src/` (§2, "A recording is written under `build/`").
5. If `!golden.isFile`: return `"no golden <goldenPath>; record with -PrecordTypeGolden"`.
6. `expected = golden.readText()`. If `expected == actual`, return `null`; else return
   `"golden <goldenPath>: " + firstDifference(expected, actual)`.

**`renderFor(fixture, source)`**: inside `EdtTestUtil.runInEdtAndWait<RuntimeException> { … }`
(precedent `LuaTypeGraphRootResolutionBudgetTest.annotatedClassFixtureStaysWithinItsRootResolutionBudget`,
`LuaTypeGraphRootResolutionBudgetTest.kt:47` on `main` at `a90adac1`): `file = fixture.configureByText(
source.fileName, source.text)`; `snapshot = LuaTypesSnapshot.forFile(file) as LuaTypesSnapshot`;
`rendered = render(snapshot, sha256Hex(source.text))`, where `rendered` is a `var rendered: String? = null`
declared before the call and `sha256Hex` is the lowercase hex of the SHA-256 of
`source.text.toByteArray(Charsets.UTF_8)` (`java.security.MessageDigest.getInstance("SHA-256")`).
After the call, return `checkNotNull(rendered)`.

**`render(snapshot, sourceSha256)`** — UTF-8, `\n` line ends, one trailing `\n`, in this order:

```
# lunar type golden v1
# source-sha256 <sourceSha256>
[errors]
<one line per getErrors() entry, sorted>
[elements]
<one line per mappedElements() entry, sorted>
[globals]
<one line per declaredGlobalNames() entry, sorted>
[fileReturn]
<escape(getFileReturnType().displayName())>|<hash16(renderType(getFileReturnType(), 2))>
```

"Sorted" is Kotlin `List<String>.sorted()` on the finished lines. Duplicate lines are kept (a
multiset). Line grammars, with `r = element.textRange`:

| section | line |
| :-- | :-- |
| errors | `<severity>\|<escape(message)>\|(<r.startOffset>,<r.endOffset>)\|<element.javaClass.simpleName>` |
| elements | `(<r.startOffset>,<r.endOffset>)\|<element.javaClass.simpleName>\|<escape(t.displayName())>\|<hash16(renderType(t, 2) + " :: " + renderLua(snapshot, t))>` with `t = snapshot.getValueType(element)` |
| globals | `<name>\|<escape(t.displayName())>\|<hash16(renderType(t, 2))>` with `t = snapshot.getGlobalType(name)` |

- `escape(s) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")`. It does not
  escape `|`: messages and display names contain it (`Pipe | nil is not assignable to { ... }`).
  Each line has exactly one such free-text field (the message, or the display name), so a line is
  read by taking its fixed fields from the ends: errors — severity first, PSI class last, range
  second-to-last, the message everything between. Elements — range and PSI class first, hash last,
  the display name everything between. Globals — name first, hash last. "Drop the PSI class" means
  drop the text from the last `|` on. Splitting on every `|` cuts the message.
- `hash16(s)` = the first 16 characters of the lowercase hex SHA-256 of `s.toByteArray(Charsets.UTF_8)`.
- `firstDifference(expected, actual)`: split both on `\n`; at the first index `i` where the lines
  differ (or one list ends), return `"line ${i + 1}: expected '<expected line or <eof>>' actual
  '<actual line or <eof>>'"`.

`renderType(type, depth)` — the renderer DR-02/DR-04 compared with, verbatim:

```kotlin
private fun renderType(type: LuaGraphType, depth: Int): String {
    if (depth <= 0) return type.displayName()
    return when (type) {
        is LuaGraphType.Table ->
            "${type.className ?: "{}"}[" +
                type.getMembers().entries.joinToString(",") { (k, v) ->
                    "$k:w=${renderType(v.write, depth - 1)}/r=${renderType(v.read, depth - 1)}"
                } + "]^" + type.superTypes.joinToString(",") { it.displayName() } + "#" + type.metamethods
        is LuaGraphType.Function ->
            "fun(" + type.params.joinToString(",") {
                "${it.name}:${renderType(it.node.write, depth - 1)}/${renderType(it.node.read, depth - 1)}"
            } + ")->" + type.returns.joinToString(",") { renderType(it.write, depth - 1) } + "!${type.declaredSignature}"
        is LuaGraphType.Union -> type.types.joinToString("|") { renderType(it, depth - 1) }
        is LuaGraphType.Array -> "${renderType(type.elementType, depth - 1)}[]"
        else -> type.displayName()
    }
}
```

`renderLua(snapshot, type)` — the Layer-1 rendering DR-04 compared with:

```kotlin
private fun renderLua(snapshot: LuaTypesSnapshot, type: LuaGraphType): String =
    try {
        val luaType = snapshot.graphTypeToLuaType(type)
        luaType.name + "{" +
            luaType.getMembers().entries.joinToString(",") { (k, m) -> "$k:${m.type.name}@${m.sourceElement?.textRange}" } +
            "}"
    } catch (e: RuntimeException) {
        "EXC:" + e.javaClass.simpleName
    }
```

Grounding for every member used: `LuaGraphType.Table.className`/`superTypes`/`metamethods`
(`LuaGraphType.kt:57-79`), `Function.params`/`returns`/`declaredSignature` (`:29-47`),
`Function.Parameter.node`/`name` (`:49-51`), `Union.types` (`:82-83`), `Array.elementType`
(`:91-92`), `getMembers()` (`:200`), `displayName()` (`:174`); `LuaType.name`/`getMembers()`
(`LuaType.kt:11-16`), `LuaTypeMember.type`/`sourceElement` (`LuaType.kt:21-26`);
`graphTypeToLuaType` (`LuaTypes.kt:114`).

**Member files**, in this order (`goldenPath`, file under `test/corpus/`):

| goldenPath | corpus file | why this file |
| :-- | :-- | :-- |
| `member-files/luacheck-cache.golden` | `luacheck/src/luacheck/cache.lua` | the one corpus shape DR-04 found a variant changing (`fh:read("*a")`, line 120, offset 3375) |
| `member-files/zerobrane-commands.golden` | `zerobrane/src/editor/commands.lua` | many later sites on one receiver (`ide:GetDocument` ×12, `ide:Print` ×10) |
| `member-files/luarocks-zip.golden` | `luarocks/src/luarocks/tools/zip.lua` | `zh:write` ×43, `zh:read` ×29 across functions |
| `member-files/penlight-Date.golden` | `penlight/lua/pl/Date.lua` | `self:set` repeated inside one method |

`memberFiles()` builds each `GoldenSource` with `fileName` = the file's base name and `text` =
`File(System.getProperty("user.dir"), "${CorpusManifest.CORPUS_DIR}/<corpus file>").readText()`,
after `check(file.isFile) { "corpus not fetched: <path> (tooling/corpus/fetch-corpus.py)" }`. The
`# source-sha256` header turns a re-pinned corpus into an explicit line-2 mismatch rather than a
silent type diff.

**Recording and pulling back.** `gce-builder` rsync is one-directional, so a recording on the VM
must be copied back before it can be committed. The recording lands in `build/type-golden/recorded/`
on the builder, which no session's sync deletes (§2), so the copy back may be a separate command:

```bash
tooling/gce-builder/gce-builder.sh run "test --rerun -PrecordTypeGolden --tests '*LuaTypeGolden*'" > record.log 2>&1
rsync -az builder:/home/builder/lunar/build/type-golden/recorded/ src/test/resources/types-golden/
mkdir -p build/type-golden/record-xml
scp builder:/home/builder/lunar/build/test-results/test/TEST-net.internetisalie.lunar.lang.types.LuaTypeGoldenShapesTest.xml build/type-golden/record-xml/
scp builder:/home/builder/lunar/build/test-results/test/TEST-net.internetisalie.lunar.lang.types.LuaTypeGoldenMemberFilesTest.xml build/type-golden/record-xml/
grep -ho '\[type-golden\] recorded [^<]*' build/type-golden/record-xml/*.xml | sort
```

The paths are `REMOTE_USER`/`REMOTE_DIR` of the libvirt backend: `builder` and `lunar`
(`tooling/gce-builder/config.sh:51,68,75`). The `[type-golden] recorded` lines are test stdout, which
the Gradle console does not show (`build.gradle.kts` configures no `testLogging`); they are in the
result XMLs' `<system-out>` (risks-and-gaps DR-11 step 2). A recording is accepted only if all of
these hold:

1. `record.log` contains `BUILD SUCCESSFUL`, and the `grep` above prints exactly one
   `[type-golden] recorded <goldenPath>` line for every `GoldenSource` in `shapes()` and
   `memberFiles()`, and no other line.
2. `rsync` exits 0. A missing `recorded/` directory makes it exit non-zero, so a recording deleted
   by a concurrent `clean` fails here rather than producing an empty commit.
3. `git status --short -uall src/test/resources/types-golden` lists exactly the `goldenPath`s of
   those sources and nothing else. `-uall` is required: without it git collapses the untracked
   directory to the single line `?? src/test/resources/types-golden/`. A file left in `recorded/` by an earlier recording of a source that no
   longer exists shows up here as an extra path, and is deleted before committing.
4. The verification run without the switch (implementation plan Phase 0) is green. It renders
   every source again in a fresh JVM against the files just pulled back. A stale or partial copy
   fails there.

Exposure that remains: a `./gradlew clean` run by another session in the shared checkout, between
the recording and the copy back, deletes `build/`. Check 2 catches that.

### 5.6 Stub-name isolation — `declaredTypeNames` / `stubTypeNames`

```kotlin
private val TYPE_TAG = Regex("""---\s*@(?:class|alias)\s+(?:\([^)]*\)\s*)?([A-Za-z_][A-Za-z0-9_.]*)""")

internal fun declaredTypeNames(text: String): Set<String> =
    TYPE_TAG.findAll(text).map { it.groupValues[1] }.toSet()

internal fun stubTypeNames(): Set<String> =
    File(System.getProperty("user.dir"), "src/main/resources/runtime")
        .walkTopDown()
        .filter { it.isFile && it.extension == "lua" }
        .flatMap { declaredTypeNames(it.readText()).asSequence() }
        .toSet()
```

The optional `\([^)]*\)` admits the `---@class (exact) Name` form. The name sets this regex yields
over the stubs and the shapes, and their empty intersection, are in DR-11 step 1. With `File` in
place of `Pipe` the intersection is `{File}`: case 11's mutation.

The rule covers shapes only. Member files are pinned third-party text and cannot be renamed; their
one overlap, which this regex cannot see, is risks-and-gaps Risk 1.5's residual (`penlight/lua/pl/Date.lua`).

## 6. Data flow

### Example 1: `b:setName(...)` three times on one `---@class Builder` local

Fixture is requirements case 2. Site 1 (`:setName` at 89–97) mints `m1`, which becomes the hub;
`use1 = Table{setName: m1}` hangs off `b`. Sites 2 and 3 mint `m2`, `m3`, attach them to `m1`,
hang `use2 = Table{setName: m2}` and `use3 = Table{setName: m3}` off `b`, and add `m1 → m2`,
`m1 → m3`. In `checkTypes`, `b`'s up-set holds the class table; the pairs `(class, use_k)` each wire
the class member `C.setName` to `wiringTargetFor(m_k)` = `m1`, bidirectionally — the first wiring adds
the edges and the other two are no-ops on `OrderedSet.add`. `m2`/`m3` see `C.setName`'s up-set
through `m1`; their own `CALL` demands are checked against `fun(n)` and `fun()` in their own visits.
The pair `(m1, synthetic declared use)` reports `fun() | fun(n) is not assignable to fun()` at
`m1`'s anchor (89–97), and §5.3 copies it to 105–113 and 121–129 — the three entries today's clique
produces.

### Example 2: a receiver with one call site per method

`local fh = io.open(...)` followed by `fh:read()` and `fh:close()`: two keys, one site each. Each site
member is its own hub with one anchor, `replicaAnchorsFor` is empty, `wiringTargetFor` is the
identity. The graph is node-for-node and edge-for-edge today's.

## 7. Edge cases

| case | behaviour |
| :-- | :-- |
| Per-site call shapes differ (`fh:read()` then `fh:read("*a")`) | Each `use_k` names its own `m_k`, whose `read` is its own `CALL` demand, so `isStructurallyCompatible` checks each site's shape. Naming the hub in every `Table` instead was measured to add a spurious HYPOTHESIS on the corpus (`luacheck/cache.lua:120`) and on requirements case 5 (DR-04, DR-06). |
| Two locals bound to the same class (`local a = P; local c = P`) | Two keys, two hubs, both wired to `P.go`; the clique is between hubs, size = number of receivers. Measured equal to today (DR-02 shape `twoReceivers`). |
| `self:m()` in many methods | Each method's `self` is a distinct node → one site per key → unchanged from today, clique included (DR-05). |
| A narrowed receiver (`if type(x) == "table" then x:m() end`) | Narrowing declares a fresh `graph.variable` for the name in the branch scope (`injectNarrowedBinding`, `LuaTypesVisitor.kt:464-480`) → its own key. Correct granularity: it is a different receiver node. From reading; no measured shape covers it. |
| Receiver reassigned (`local b = A; b:m(); b = B; b:m()`) | One binding node, one key, one hub; the hub is wired to both `A.m` and `B.m` exactly as `m1` and `m2` both are today. |
| Unresolved, `nil` or function receiver | No class member to wire; the per-site use still reports at each `methodExpr` (measured equal, DR-02 shapes `nilReceiver`, `functionReceiver`). The flags' inputs change for later sites (§5.3). |
| Multi-site receiver with a small or no class component | Closure can **rise** slightly: the hub's forward edges are added while there is little or no clique to remove (DR-02 lists the measured shapes; errors and types equal on each). Case 1's gate is on the one-receiver annotated fixture and is unaffected. |
| Declaration-typed callee (BUG-397 path) and `require`/`setmetatable` early returns | Return before the method branch; untouched. |
| Generic-template branch (`calleeNode is ValueNode && calleeNode.write is Function`) | A site member has no up-set at visit time, so its `write` is `Undefined` and the branch is not taken — today and after (DR-02 shape `generic`). |
| `inProgressSnapshot` built mid-visit (TYPE-10 re-entry) | Reads the partial graph; the side tables only ever grow with it. |

## 8. Integration points

No `plugin.xml` change, no extension point, no settings key, no index, no stub change. The engine
change is internal to `net.internetisalie.lunar.lang.psi.types`, reached only through
`LuaTypesSnapshot.forFile`. The build script gains the `recordTypeGolden` property and one
`excludeExternalFixtureTests` line (§4.6).

## 9. Requirement coverage

| Requirement | Priority | Implemented by |
| :-- | :-- | :-- |
| TYPE-12-01 | M | §4.1, §4.2, §5.1, §5.2 |
| TYPE-12-02 | M | §5.1, §5.2, §5.4; meter §4.3/§4.4 |
| TYPE-12-03 | M | §5.3 (and §5.1 step 5 for the per-site shape); gate §4.6, §5.5 |
| TYPE-12-04 | M | §5.1, §5.2; gate §4.6, §5.5 |
| TYPE-12-05 | M | §2 row 1, §4.5 |
| TYPE-12-06 | S | §5.4; evidence in risks-and-gaps DR-02 |
| TYPE-12-07 | C | none — explicitly not done (requirements) |

## 10. Alternatives considered

Each named variant was implemented as a throwaway variant and measured against today's engine
(V1 by BUG-473; the others in risks-and-gaps DR-02 and DR-04, where their results live). The last
two rows were not prototyped.

| variant | what it did | verdict |
| :-- | :-- | :-- |
| V1 (BUG-473) | drop the reverse member edge everywhere | rejected: M-C; deletes the `---@param` violation |
| V3 | drop the reverse edge only for call-site members | rejected: changes inferred types and diagnostics |
| A1 | one node per `(receiver, name)`; later sites reuse it and mint nothing | rejected: loses per-site diagnostics |
| A2 | one member per `(receiver, name)`, per-site use nodes | rejected: loses per-site diagnostics |
| A3 | a separate hub node, per-site members downstream, `Table` names the hub | rejected: loses the value-anchored per-site HYPOTHESIS entries |
| A4 | A3 + §5.3 replication | rejected: `Table` naming the hub makes every site's structural check use site 1's call shape (§7) |
| A5 | A4, but `Table` names the site member and wiring is redirected to the hub | equal everywhere measured, but the extra hub node made the `self:m()`-across-methods shape **worse** (DR-02) |
| **A6 (chosen)** | A5 with the first site member serving as the hub | equal everywhere measured; single-site receivers are node-for-node unchanged |
| SCC collapse (union-find over mutually reachable nodes) | — | not prototyped: `resolveRead` is first-wins over insertion order (`LuaTypeNodes.kt:247-253`, BUG-395 comment), so merging nodes whose down-sets differ in order changes `read`; a far wider blast radius than the call-site minting this feature is about |
| Gate on the corpus `.baseline` files | — | rejected: cannot see HYPOTHESIS entries or types (§2) |

## 11. Open questions

_None._

## Appendix C — golden shapes (`TypeGoldenFixtures`)

The Kotlin below is the exact fixture text. Every entry except `pipeUnion` and the three DR-10
shapes (`nilMember`, `unknownMember`, `declaredFieldNil`) is the probe's own text, which DR-02 ran
(without the probe's appended salt comment). `pipeUnion` is the probe's `fileUnion` with every `File`
replaced by `Pipe`. Both names have 4 characters, so every offset in it equals `fileUnion`'s.

```kotlin
fun annotated(n: Int): String {
    val calls = (0 until n).joinToString("\n") { "b:setName(\"a$it\")" }
    return "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(n) end\n\nlocal b = Builder\n$calls"
}

fun paramN(n: Int): String {
    val calls = (0 until n).joinToString("\n") { if (it % 2 == 0) "b:setName(\"a$it\")" else "b:setName($it)" }
    return "---@class Builder\nlocal Builder = {}\n---@param n string\n---@return Builder\n" +
        "function Builder:setName(n) return self end\nlocal b = Builder\n$calls"
}

fun selfN(n: Int): String {
    val methods = (0 until n).joinToString("\n") { "function C:f$it() self:inc($it) end" }
    return "---@class C\nlocal C = {}\n---@param x number\nfunction C:inc(x) end\n$methods"
}

fun dotN(n: Int): String {
    val reads = (0 until n).joinToString("\n") { "print(b.name)" }
    return "---@class D\n---@field name string\nlocal D = {}\nlocal b = D\n$reads"
}

val PARAM =
    "---@class Builder\nlocal Builder = {}\n---@param n string\n---@return Builder\n" +
        "function Builder:setName(n) return self end\nlocal b = Builder\nb:setName(42)"

val NAMED_SHAPES: List<Pair<String, String>> =
    listOf(
        "selfCalls" to
            "---@class C\nlocal C = {}\n---@param n number\nfunction C:inc(n) end\n" +
            "function C:a() self:inc(1) self:inc('x') end\nfunction C:b() self:inc(2) self:inc() end\n" +
            "function C:c() self:inc(3, 4) end\n",
        "nilReceiver" to "local x = nil\nx:foo()\nx:foo(1)\nx:bar()\n",
        "functionReceiver" to "local f = function() end\nf:foo(1)\nf:foo(2)\n",
        "unannotated" to
            "local o = {}\nfunction o:f(a) return a end\nlocal r1 = o:f(1)\nlocal r2 = o:f('s')\nlocal r3 = o:f(true)\nprint(r1 .. r2)\n",
        "callBeforeDecl" to
            "local o = {}\nlocal function use() return o:g(1), o:g('a') end\nfunction o:g(v) return v end\nlocal z = use()\n",
        "inheritance" to
            "---@class Base\nlocal Base = {}\n---@param s string\nfunction Base:say(s) end\n" +
            "---@class D : Base\nlocal D = {}\nlocal d = D\nd:say('x')\nd:say(1)\nd:say()\n",
        "fieldFunction" to
            "---@class K\n---@field run fun(self: K, n: number): string\nlocal K = {}\nlocal k = K\n" +
            "local s = k:run(1)\nlocal t = k:run('x')\nprint(s .. t)\n",
        "chain" to
            "---@class Bd\nlocal Bd = {}\n---@param n string\n---@return Bd\nfunction Bd:set(n) return self end\n" +
            "local b = Bd\nlocal r = b:set('x')\nr:set(1)\nb:set('y'):set(2)\nlocal q = b:set('z')\n",
        "twoReceivers" to
            "---@class P\nlocal P = {}\n---@param n number\nfunction P:go(n) end\nlocal a = P\nlocal c = P\n" +
            "a:go(1)\nc:go('x')\na:go('y')\nc:go(2)\n",
        "generic" to
            "---@class G\nlocal G = {}\n---@generic T\n---@param x T\n---@return T\nfunction G:id(x) return x end\n" +
            "local g = G\nlocal n = g:id(1)\nlocal s = g:id('s')\n",
        "arity" to
            "---@class Ar\nlocal Ar = {}\n---@param a number\n---@param b number\nfunction Ar:two(a, b) end\n" +
            "local x = Ar\nx:two(1)\nx:two(1, 2)\nx:two(1, 2, 3)\nx:two(1, 'b')\n",
        "unionReceiver" to
            "---@class U1\nlocal U1 = {}\nfunction U1:m(a) end\n---@class U2\nlocal U2 = {}\nfunction U2:m(a, b) end\n" +
            "local u = U1\nif cond then u = U2 end\nu:m(1)\nu:m(1, 2)\nu:m()\n",
        "pipeUnion" to
            "---@class Pipe\nlocal Pipe = {}\n---@param fmt? string\n---@return string\nfunction Pipe:read(fmt) end\n" +
            "function Pipe:close() end\n---@return Pipe|nil\nlocal function open(p) end\nlocal fh = open('x')\n" +
            "if not fh then return end\nif fh:read() ~= 'v' then fh:close() return end\nlocal s = fh:read('*a')\nfh:close()\n",
        "fileUnionStdlib" to
            "local fh = io.open('x', 'rb')\nif not fh then return end\nif fh:read() ~= 'v' then fh:close() return end\n" +
            "if fh:read() ~= 'w' then fh:close() return end\nlocal s = fh:read('*a')\nfh:close()\n",
        "builderFixture" to
            "---@class LB\n---@field name string\nlocal LB = {}\n---@param name string\n---@return LB\n" +
            "function LB:setName(name)\n  self.name = name\n  return self\nend\nlocal builder = LB\n" +
            (0 until 12).joinToString("\n") { "builder:setName(\"f$it\")" } + "\n",
        "nilMember" to "local o = {}\no.m = nil\no:m(1)\no:m(2)\no:m(3)\n",
        "unknownMember" to "local o = {}\no.m = wx.thing\no:m(1)\no:m('a')\no:m(true)\n",
        "declaredFieldNil" to
            "---@class K\n---@field m fun(self: K, n: string)\nlocal K = {}\nK.m = nil\nlocal k = K\n" +
            "k:m(1)\nk:m('a')\nk:m(2)\n",
    )
```

`shapes()` returns, in order, with `fileName = "test.lua"` and `goldenPath = "shapes/<name>.golden"`:
`param` (`PARAM`); every `NAMED_SHAPES` entry under its name; `annotated1`, `annotated3`,
`annotated20` (`annotated(1)`, `annotated(3)`, `annotated(20)`); `paramN20` (`paramN(20)`);
`selfN20` (`selfN(20)`); `dotN20` (`dotN(20)`); then `annotated-builder` and `annotated-shapes`,
whose text is `File(System.getProperty("user.dir"), "src/test/resources/corpus/annotated/<builder|shapes>.lua").readText()`
and whose `fileName` is that file's base name.

**Excluded shape: the stub-name collision (pending BUG-498).** The probe's `fileUnion` (the
`pipeUnion` text with `File`) is not in `shapes()`: its rendering is not deterministic across JVMs
(DR-09; cause BUG-498).

**What re-enables it:** BUG-498 reaching `done`, with its fix making `materializeClass`'s
declaration order deterministic. Then, in one commit:

1. Add `"fileUnion"` to `NAMED_SHAPES` with that text.
2. In `noShapeDeclaresAStubTypeName` (§4.6), change `TypeGoldenFixtures.shapes().flatMap` to
   `TypeGoldenFixtures.shapes().filterNot { it.goldenPath == "shapes/fileUnion.golden" }.flatMap`.
   The guard keeps holding for every other shape.
3. Record it per §5.5.
4. Run `tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaTypeGolden*'"` as four
   separate invocations, each a fresh test JVM, and require all four green.

The determinism evidence is BUG-498's own fresh-JVM test. Step 4 confirms it on this golden and
does not replace it: under the defect, some JVMs agreed with the recording (DR-09), so agreeing JVMs
are not proof on their own.
