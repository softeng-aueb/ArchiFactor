# Repair object-model queries broken by the "Unmap JPA Relationships" refactoring

You are working inside a Java with JPA project, in its root directory, immediately after an
automated refactoring called **Unmap JPA Relationships** was applied and committed. Your
single goal is to make the project's **existing test suite pass again** by repairing only
the queries the refactoring broke. You must not change test code, and you must not undo the
refactoring.

## What the refactoring did (and why some queries now break)

For each relationship listed in the manifest below, a JPA association field (for example a
`@ManyToOne`, `@OneToMany`, `@ManyToMany`, or `@OneToOne`) was turned into a `@Transient` field that
is lazy-loaded through a generated service. The relationship data is now kept differently:
- On the **owning side**, the association was replaced by a real persistent field:
  - a **foreign-key field** for to-one relationships (manifest: `newForeignKeyField`), or
  - an **`@ElementCollection` of ids** for many-to-many relationships (manifest:
    `newElementCollectionField`) where the collection stores the target entities' id values.
- On the **inverse side**, the field is purely `@Transient`. The data lives on the *target*
  entity (manifest: `targetForeignKeyField` / `targetElementCollectionField`).

The **database schema is unchanged**. The join tables and foreign-key columns still exist; the
refactoring only changed how Java maps to them. Two consequences follow, and they are the key
to this task:
1. **Native SQL is unaffected.** Any `createNativeQuery(...)` or `@Query(nativeQuery = true)`
   still works. Do not touch native queries.
2. **Only object-model queries that navigate an unmapped association break.** JPQL
   (`createQuery`, `createNamedQuery`, `@Query`, `@NamedQuery` / `@NamedQueries`), the Criteria
   API, Spring Data derived query methods, and Panache queries fail **at runtime** (not at
   compile time) when they reference a path or join through a field that is now `@Transient`,
   because that field no longer maps to a column.

A query is broken **only if** it navigates one of the unmapped associations, either by a path
expression (`alias.field...`) or a `JOIN alias.field`. Queries that touch only unchanged
fields are fine and must be left untouched.

### Not every failing test is a broken query

Unmapping also drops whatever lifecycle propagation the relationship declared. Where a manifest
entry carries a `droppedSemantics` block, that association used to cascade `PERSIST` / `MERGE` /
`REMOVE`, or delete orphans, and the ORM no longer does any of it. The database foreign-key
constraints are unchanged, so a parent delete can now fail or leave orphan rows, and an entity
wired to a not-yet-persisted parent can be saved with a null foreign key.

Those failures are **semantics losses, not broken queries, and they are out of scope for you.**
They look different from query failures: expect constraint-violation or referential-integrity
errors on delete/save, or a row that is unexpectedly still present, absent, or null after a
flush, rather than an error naming a field that no longer maps to a column. Repairing one would
mean adding an explicit delete or persist to application logic, which is a developer decision about
the new module boundary. Recognize them, leave them alone, and list them in your final report.

## Your inputs

- **The change manifest**: the authoritative description of exactly what changed, every
  unmapped relationship, the new field that replaced it, and the services + methods the
  refactoring generated. It is included verbatim at the end of this prompt.
- **The baseline commit `{{BASELINE_COMMIT}}`**: the commit that contains the deterministic
  refactoring. Run `git show {{BASELINE_COMMIT}}` to see precisely which fields and annotations
  changed, and `git diff {{BASELINE_COMMIT}}` to review your own (uncommitted) edits as you go.
- **The project's test suite**: figure out how this project runs its automated tests (check the
  README, the build files -`pom.xml`, `build.gradle`, `package.json`, `Makefile`- or any
  documented script), then run it to verify your work and iterate.

## How to find the broken queries

For each unmapped relationship in the manifest, take `fromEntity` + `fieldName` (the removed
association) and its replacement field, then search the codebase for object-model queries that
navigate that association:
- Grep for the association field name used as a JPQL path or join: `.<fieldName>`,
  `join <alias>.<fieldName>`, `JOIN FETCH <alias>.<fieldName>`.
- Grep for query carriers: `createQuery(`, `createNamedQuery(`, `@Query`, `@NamedQuery`,
  `@NamedQueries`, `CriteriaBuilder` / `CriteriaQuery`, Spring Data repository method names,
  Panache `find(`/`list(`/`stream(`.
- Classify each hit: **unaffected** (leave it), **Shape A**, or **Shape B** (below).

## The two repair shapes

### Shape A: in-place rewrite (the query only needs the target's id / key)

When a query navigates the association solely to reach the target entity's **primary key**,
rewrite the path or join to use the new field directly. No service call is needed.

To-one example: `Item.book` (`@ManyToOne`) was replaced by the FK field `bookno`
(manifest `newForeignKeyField.name`):

```
- SELECT i FROM Item i WHERE i.book.id = :id
+ SELECT i FROM Item i WHERE i.bookno = :id
```

Many-to-many example: `Book.authors` (`@ManyToMany`, owning) was replaced by the element
collection `authorIds` (manifest `newElementCollectionField.name`). An `@ElementCollection` of
ids can still be joined in JPQL; the joined value *is* the id:

```
- SELECT b FROM Book b JOIN b.authors a WHERE a.id = :id
+ SELECT b FROM Book b JOIN b.authorIds aid WHERE aid = :id
```

### Shape B: route the cross-entity need through the generated service

Use this when the query needs data that lives on the **other** entity: a **non-key property** of
the target (for example `i.book.title`), or relationship data that now lives on the
target / owning side (typical for inverse-side navigations). Such a query can no longer be a
single JPQL statement, since the association is `@Transient` and maps to no column, so there 
is no path to traverse.

This decomposition is the strategically intended outcome: the reason the association was
unmapped is to make each entity self-contained so the schemas, and later the services,
can be split apart, and a service lookup is exactly the seam that split will follow.

Split it into two steps, reusing the services the refactoring already generated:
1. Resolve the matching target ids by querying the **target** entity on the non-key property.
2. Query the source entity by its FK / element-collection field with `IN (:ids)`.

Add the lookup method to the **already-generated** service for the target entity. The manifest
lists each generated service interface, its implementation class, and the methods already
present. **Add** to these; do **not** create a new service, and do **not** duplicate a method
that already exists.

Example: `Item.book` → FK `bookno` the query wants items whose book title matches:

```
- SELECT i FROM Item i WHERE i.book.title LIKE :t
```

becomes, in the repository/service that owned the query:

```
List<Integer> bookIds = bookService.findBookIdsByTitleLike(t); // new BookService method
if (bookIds.isEmpty()) { return Collections.emptyList(); }     // guard the empty IN list
... "SELECT i FROM Item i WHERE i.bookno IN :ids" ...
   .setParameter("ids", bookIds) ...
```

and add to `BookService` / `BookServiceImpl` (matching the style of the existing generated
methods from the manifest `git show {{BASELINE_COMMIT}}`):

```
List<Integer> findBookIdsByTitleLike(String t);
// impl: SELECT b.id FROM Book b WHERE b.title LIKE :t
```

Direction note: inverse (non-owning) sides have no FK column of their own. The FK lives on the
target (manifest `targetForeignKeyField` / `targetElementCollectionField`). A query from the
inverse side usually becomes a query on the target by that FK, or a service lookup. Always let
the manifest tell you where the data now lives.

### Choosing between the shapes - follow where the data now lives

- If the value the query needs is a **local persistent field on the entity being queried**, i.e. its
  own foreign-key column (to-one), or its own `@ElementCollection` of ids (the many-to-many
  owning side), then use **Shape A**. You are reading the entity's own column, so a service call
  would add coupling, not remove it. This is why `WHERE i.bookno = :id` and `JOIN b.authorIds aid`
  stay as single queries.
- If the query needs data on the **other** entity, i.e. a non-key property, or relationship data that
  now lives on the target / owning side, then use **Shape B** and route it through the generated
  service. That service boundary is the seam the long-term schema (and service) split will follow.
- Do not force Shape B when the data is already local, and never re-create a removed association
  just to keep a query as one statement.

## Hard constraints

- **Never** edit test code, test resources, or assertions to make a test pass. The tests define
  correct behavior; if a test fails, the production query is still wrong.
- **Never** re-introduce a removed mapping: do not add back `@ManyToOne` / `@OneToMany` /
  `@ManyToMany` / `@OneToOne` / `@JoinColumn` / `@JoinTable` / `mappedBy`, and do not un-`@Transient` the
  unmapped fields. The refactoring is intentional.
- Touch **only** query code (JPQL strings, `@Query` / `@NamedQuery` values, Criteria builders,
  repository method declarations and their call sites) and the generated service
  interfaces/implementations listed in the manifest. Leave unrelated domain logic untouched.
- Choose the repair shape by **where the data now lives** (see "Choosing between the shapes"):
  read a local field in place (Shape A); route a genuine cross-entity need through the generated
  service (Shape B). Do not add a service hop for data the entity already owns.
- Do **not** try to restore dropped cascade or `orphanRemoval` behavior. When a failure traces to
  a relationship carrying a `droppedSemantics` block, do not add cascading deletes or persists to
  domain logic, do not re-add the mapping, and do not keep iterating on it. Report it and move on:
  a suite left failing **only** for this reason is a `PARTIAL` result, and that is the correct
  outcome, not a reason to keep trying.
- Do **not** run `git commit`, `git reset`, `git checkout`, `git rebase`, or `git stash`, or any
  other history-changing command. Leave every edit **uncommitted** for human review.
- If you exhaust reasonable attempts or reach a query you cannot safely fix, **stop and report
  it**.

## Process

1. Read the manifest below and run `git show {{BASELINE_COMMIT}}` to see exactly what changed.
2. Enumerate candidate queries by searching the codebase.
3. Classify each as unaffected / Shape A / Shape B.
4. Apply the fixes.
5. Run the project's test suite (discover how, as above), read the failures, and iterate until it
   passes.
6. Produce the final report described below.

## Final report

Your **final message** is captured verbatim as the run result, so make it the report and put
nothing after it. Begin with a single status line, then the summary:

```
RESULT: SUCCESS | PARTIAL | FAILED
```

Use `SUCCESS` if the whole suite passes, `PARTIAL` if some tests still fail, and `FAILED` if you
could not run the tests at all. After that line, summarise:
- each query you changed, with `file:line` and whether it was Shape A or Shape B;
- each service method you added (signature + which service);
- the final test outcome (passed, or which tests still fail);
- anything you could **not** fix, and why.

---

## Change manifest

```json
{{MANIFEST}}
```
