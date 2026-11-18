# Pyloros – SKILL.md

Wissenssammlung für die Zusammenarbeit von ChatGPT, Pyloros, ACP-Agenten und IntelliJ. Ein Skill pro Kapitel. Der erste Skill beschreibt die geplante Agentenkoordination; die weiteren Kapitel dokumentieren die neun über Pyloros abgerufenen Skills.

**Stand:** 2026-11-18. Geplante Funktionen sind ausdrücklich als geplant markiert.

## Skill: ACP-Agenten koordinieren (neu)

**Skill-ID:** `pyloros-acp-agent-orchestration`  
**Status:** Verfahrensanleitung; vorhandene Funktionen von geplanten Erweiterungen getrennt behandeln.

### Ziel
Nutze Pyloros als Ausführungsplattform und ChatGPT als fachliche Koordination. Verteile unabhängige Entwicklungsaufträge an mehrere ACP-Agenten, weise ihnen geeignete Modelle, Werkzeuge und Arbeitsbereiche zu, und prüfe ihre Ergebnisse nachvollziehbar. Lasse Nebenläufigkeit nicht zu unkontrollierten Repository-Änderungen führen.

### Bereits vorhandene Schnittstellen (Stand 2026-11-18)
- `acp_registry__list_installed_agents` und `acp_registry__get_agent_status`: Installationen und Aktivierungsstatus prüfen. Aktiviert heißt nicht notwendigerweise erfolgreich startbar.
- `<agent-prefix>run_task(prompt, cwd?, timeoutSeconds?)`: Auftrag synchron ausführen, bis Ergebnis oder Fehler vorliegt.
- `<agent-prefix>start_task(prompt, cwd?, timeoutSeconds?)`: Hintergrundauftrag starten; liefert eine Task-ID zurück.
- `<agent-prefix>get_task_status(taskId, includeEvents?, maxEvents?)`: Laufzustand/Ereignisse lesen.
- `<agent-prefix>get_task_result(taskId)`: Ergebnis einer abgeschlossenen Aufgabe abrufen.
- `<agent-prefix>cancel_task(taskId)`: Laufende Aufgabe abbrechen.
- `<agent-prefix>respond_to_permission(taskId, permissionId, approve)`: Ausstehende Berechtigung nach Prüfung beantworten.
- Pyloros startet separate ACP-Prozesse/Sitzungen und kann Aufgaben parallel laufen lassen. Tool-Sicht (`agentToolView`) ist heute je Agentenprovider konfiguriert; sie ist noch keine beliebige Allowlist pro Auftrag.

### Standardablauf
1. **Auftrag zerlegen:** Fasse eigenständige Aufgaben so, dass jeder Agent ein klares Ziel, Eingaben, erlaubte Änderungen und Abnahmekriterien hat. Koordiniere fachliche Abhängigkeiten explizit.
2. **Agent auswählen:** Rufe die Registry ab und prüfe die Funktionsfähigkeit des konkreten Providers. Verlasse dich nicht nur auf `enabled=true`. Antigravity ACP wurde am 2026-11-18 mit einer reinen Testanfrage erfolgreich ausgeführt; Gemini CLI war nach Nutzerangabe nicht einsatzbereit.
3. **Berechtigungen und Tools:** Verteile zunächst nur über die bereits konfigurierte Agent-Tool-View und klare Arbeitsanweisungen. **Ein Prompt, bestimmte Tools zu verwenden, ist keine technische Sicherheitsbegrenzung.** Verlange für harte Grenzen später eine serverseitig erzwungene Tool-Allowlist / eingeschränkte View pro Auftrag. Agent-interne Tools sind separat zu berücksichtigen.
4. **Arbeitsbereich bestimmen:** Leseaufträge dürfen dieselbe Working Copy verwenden. Für parallele unabhängige Schreibaufträge bevorzugt separate Git-Worktrees/Branches (oder eine gleichwertig isolierte Umgebung). Ohne Isolation nur bei explizit koordinierten, nicht überlappenden Änderungen.
5. **Asynchron starten:** Starte mehrere unabhängige Aufgaben jeweils mit `start_task` und notiere Agent, Task-ID, Auftrag, Arbeitsverzeichnis, Branch, gewünschtes Modell und erlaubte Werkzeuge. Warte nicht auf jede Einzelaufgabe, bevor du die nächste startest.
6. **Fortschritt verfolgen:** Prüfe Zustände über `get_task_status`; hole Ergebnisse erst nach erfolgreichem Abschluss mit `get_task_result`. Reagiere auf Fehler und Timeouts, ohne sie als Erfolg darzustellen.
7. **Freigaben behandeln:** Überprüfe Berechtigungsanfragen und hole erforderliche Nutzerfreigaben ein. Bestätige nicht ungefragt destructive Befehle, Sicherheitsänderungen, Geheimniszugriffe oder Deployment.
8. **Qualitätskontrolle:** Prüfe Diff, Tests, Architektur, Zusammenführbarkeit und fachliche Anforderungen; erledige Konfliktlösung nach Möglichkeit in einem getrennten Integrationsschritt.
9. **Abschluss:** Gib für jeden Agenten Ergebnis, Teststatus, Commit-/PR-Referenz und offene Risiken an. Keine Pushes, Merges, Releases oder Deployments ohne ausdrückliche Freigabe.

### Modellwahl (noch zu vervollständigen)
- Der ACP-Agent kann bei `session/new` Modelloptionen über `configOptions` anbieten; die konkrete Antwort des installierten Agenten muss geprüft werden.
- Geplante Erweiterung: verfügbare Modelle dynamisch auslesen, auf tatsächlich angebotene IDs begrenzen, `model` optional als Task-Parameter übergeben und **vor `session/prompt`** per ACP-Sitzungskonfiguration auswählen; Auswahl bestätigen.
- Kein Erfolg behaupten, wenn der Agent das Modell nicht bestätigt; ohne Unterstützung das voreingestellte Modell nutzen oder den Auftrag mit nachvollziehbarem Fehler ablehnen.
- Der Stand dieser Anleitung ist **kein Nachweis**, dass Pyloros bereits `model` in `run_task`/`start_task` unterstützt.

### Tool-Auswahl pro Auftrag (geplant)
- Technische Freigabe als geprüfte Liste konkreter Tools beziehungsweise eingeschränkte Session-View anbieten.
- Pyloros soll die Einschränkung serverseitig durchsetzen, nicht nur als Prompt-Anweisung.
- Capability Discovery, sichere Defaults, Berechtigungen, Validierung der Toolnamen und Ausschluss rekursiver ACP-Views vorsehen.
- Festhalten, ob ein Agent eigene lokale Werkzeuge außerhalb der Pyloros-MCP-View besitzt.

### Hintergrundbenachrichtigungen (geplant; Verfügbarkeit prüfen)
- Pyloros soll statusbasierte Ereignisse für `COMPLETED`, `FAILED`, `TIMEOUT` und `WAITING_FOR_PERMISSION` erzeugen, deduplizieren und nachvollziehbar speichern.
- ChatGPT-Sprachunterhaltungen können nicht automatisch durch beliebige MCP-Webhooks unterbrochen werden. Eine direkte ChatGPT-Benachrichtigung ist **nicht als bestehende Funktion anzunehmen**.
- Geeigneten Push-/Webhook-/Integrationsweg erst gegen aktuell unterstützte ChatGPT-Schnittstellen und Berechtigungen verifizieren; als Fallback Status-Polling bzw. eine ausdrücklich konfigurierte Automation nutzen.
- ChatGPT darf Ergebnisse erst nach tatsächlichem Abruf als geprüft melden. Ereignisse authentifizieren, signieren, minimieren und sensible Inhalte nicht unkontrolliert weitergeben.

### Parallelität und Isolation
- **Lesend + lesend:** gleicher Worktree möglich, sofern die Werkzeuge wirklich nicht schreiben.
- **Schreibend + schreibend:** unabhängige Worktrees/Branches bevorzugen; verwaiste Worktrees später kontrolliert aufräumen.
- **Lesend + schreibend:** je nach Anforderung an reproduzierbaren Lesestand Snapshot/Worktree einsetzen.
- Maximalzahl gleichzeitiger Tasks, Ressourcenbudget, Zeitlimits und Konfliktregeln explizit verwalten.

### Akzeptanzkriterien für die späteren Erweiterungen
- Modellliste stammt aus einer echten ACP-Antwort; gültiges Modell wird nachweislich gesetzt, ungültiges abgelehnt.
- Pro Auftrag können Tool-Berechtigungen eingegrenzt und technisch kontrolliert werden.
- Zwei unabhängige Hintergrundaufträge laufen parallel und ihre Task-IDs bleiben einzeln abfragbar.
- Bei Abschluss, Fehler und benötigter Freigabe entstehen nachvollziehbare, nicht duplizierte Ereignisse.
- Lesende und schreibende Aufgaben werden angemessen isoliert, ohne für reine Leseaufträge unnötig Worktrees zu erzeugen.
- Keine behaupteten ChatGPT-Push-Ereignisse ohne verifizierten Support.

---

## Skill: IntelliJ Extract Method for Intention-Revealing Legacy Code

**Skill-ID:** `intellij-extract-method-intention-revealing-legacy-code`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Use this skill when reading or improving legacy Java code and a block of code is hard to understand at a glance.

### Intent

Use IntelliJ's real Java refactoring engine, especially Extract Method, to turn opaque implementation steps into intention-revealing method names. The goal is not primarily reuse; the first goal is comprehension, safer change, and a smaller mental model.

### When to apply

Apply this when:

- A selected block requires mental decoding before its purpose is clear.
- A method mixes multiple abstraction levels.
- A future change would be easier if a concept had a name.
- You want to build a seam around legacy behavior before introducing tests or a test double.

Do not apply this mechanically based only on line count. Prefer extraction when the new method can be named by its domain intent.

### Workflow

1. Read the surrounding method and identify a cohesive block.
2. Choose a name that describes what the block means, not how it works.
3. Use IntelliJ PSI/refactoring APIs or the IDE Extract Method action, not string replacement.
4. Prefer dry-run/preview where available.
5. Apply the refactoring.
6. Build or run targeted tests.
7. If IntelliJ detects duplicate or partial duplicate code, consider replacing duplicates only when the intent really matches.

### Naming rules

Good names answer: "What is this step for?"

Prefer:

- `authenticateRequest`
- `mountMcpRoutes`
- `collectEligibleSkills`
- `persistUserSkill`

Avoid names that only restate mechanics:

- `doLoop`
- `processData`
- `handleStuff`
- `extractPart`

### Legacy-code safety

For code without tests, use Extract Method to create a seam first. After the seam exists, introduce a test or test double around that seam before doing larger structural changes.

### Validation

After applying:

- Run IntelliJ build or Gradle build.
- Check changed call sites.
- Keep pure refactoring commits separate from behavior changes.

### Agent behavior

When using this skill, report whether the change was performed through IntelliJ refactoring/PSI tooling or only through text editing. Never imply a semantic refactoring happened if only text was replaced.

---

## Skill: IntelliJ Preparatory Refactoring Before Feature Changes

**Skill-ID:** `intellij-preparatory-refactoring-before-feature-change`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Use this skill before implementing a feature, bugfix, or behavioral change in Java code when the current structure makes the change awkward or risky.

### Intent

Make the intended change easy before making the change. Use IntelliJ refactorings as small, behavior-preserving steps that reduce future change cost.

### When to apply

Apply this when:

- The feature would require editing too many places.
- A method or class mixes responsibilities.
- A parameter list, constructor, or return type blocks clean evolution.
- A dependency makes testing or local reasoning difficult.
- A move, rename, extraction, or signature change would make the feature smaller.

### Preferred IntelliJ operations

Use real IDE refactorings where possible:

- Extract Method to isolate intent.
- Rename to introduce domain language.
- Extract Variable or Extract Constant to make concepts explicit.
- Change Signature with delegate/overload when old call sites must remain stable.
- Move Method, Move Static Member, or Move Class when behavior belongs elsewhere.
- Extract Interface, Use Interface Where Possible, or Extract Superclass when type coupling blocks testing or evolution.
- Extract Parameter Object, Extract Method Object, or Wrap Return Value when API shape is the real problem.

### Workflow

1. State the desired feature or bugfix in one sentence.
2. Identify the smallest structural obstacle.
3. Perform one semantic IntelliJ refactoring.
4. Build or run targeted tests.
5. Repeat only while the next refactoring directly lowers the cost of the intended change.
6. Commit or report structural refactorings separately from behavior changes.

### Safety rules

- Do not combine large moves with behavior edits.
- Do not rename and change semantics in the same step.
- Prefer preview/usage analysis for Move, Rename, Change Signature, Pull Up, Push Down, and Safe Delete.
- Keep delegating overloads or delegates when callers need a migration path.

### Agent behavior

When using this skill, explain the preparatory purpose: what future change becomes easier after the refactoring. Avoid cosmetic refactorings without a stated change-cost benefit.

---

## Skill: IntelliJ Create From Usage for Outside-In API Design

**Skill-ID:** `intellij-create-from-usage-outside-in-design`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Use this skill when designing or reshaping Java code from the caller's point of view.

### Intent

Write the usage you want first, then let IntelliJ create the missing method, constructor, field, or constant. This is not a classic refactoring, but it belongs in the same clean-code workflow because it lets the caller express the desired API before the implementation exists.

### When to apply

Apply this when:

- The best method signature is clearer at the call site than inside the implementation class.
- You want to introduce a small domain operation before implementing it.
- You are doing outside-in TDD or API design.
- You want to replace inline logic with a named collaborator step.
- You are sketching a seam or abstraction before filling in details.

### Workflow

1. At the call site, write the desired call as if the API already existed.
2. Choose a name that expresses domain intent.
3. Use IntelliJ Alt+Enter / context action to create the missing method, constructor, field, or constant.
4. In the creation dialog, choose visibility, return type, parameters, abstract/static modifiers, and exceptions deliberately.
5. Keep the generated method body minimal until the caller reads well.
6. Implement the body or combine with Extract Method/Move Method afterwards.
7. Build or run tests.

### Examples

Prefer caller-first expressions like:

```java
Skill savedSkill = saveUserSkill(request);
mountTargetPlatformTools(router);
validateSkillPayload(arguments);
```

Then use Create Method to materialize the intended boundary.

### Safety rules

- Do not accept a generated signature blindly.
- Do not create broad utility methods with vague names.
- Keep generated methods close to the domain concept.
- If the call site becomes cleaner but implementation gets worse, follow up with Extract Method, Move Method, Extract Class, or Change Signature.

### Agent behavior

When using this skill, say explicitly that the operation is a context action / create-from-usage step, not a behavior-preserving refactoring in the strict Fowler sense.

---

## Skill: IntelliJ Legacy Seams for Testability

**Skill-ID:** `intellij-legacy-seams-for-testability`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Use this skill when legacy Java code is hard to test because it directly touches external systems, static helpers, framework globals, IO, time, network, database, or complex collaborators.

### Intent

Create a seam first, then test or substitute through that seam, then change behavior. The seam is the point where behavior can be varied without editing the legacy core again.

### When to apply

Apply this when:

- Code has no automated tests.
- A method directly calls external services, file system, database, clock, environment, HTTP, or static framework state.
- You need to introduce a test double.
- You want to isolate risky behavior before Extract Class, Move Method, Change Signature, or API changes.

### Preferred IntelliJ operations

- Extract Method to isolate the external interaction.
- Extract Interface to introduce a substitutable collaborator.
- Use Interface Where Possible to reduce concrete coupling.
- Change Signature to inject collaborator, clock, config, or context.
- Extract Parameter Object when several related dependencies travel together.
- Move Method or Extract Class when the seam deserves its own object.

### Workflow

1. Identify the hard-to-test dependency or behavior.
2. Extract the interaction into a named method or collaborator boundary.
3. Build immediately.
4. Add or update a focused test around the new seam if possible.
5. Introduce a test double, fake, stub, or mock only after the seam exists.
6. Perform the actual behavior change after the code is observable and safer.

### Safety rules

- Do not rewrite the external interaction and create the seam in one step.
- Prefer one seam at a time.
- If no tests exist, keep the first refactoring extremely small and build after it.
- Keep behavior-preserving changes separate from behavior changes.

### Agent behavior

When using this skill, identify the seam explicitly: what behavior becomes replaceable, injectable, or testable after the refactoring.

---

## Skill: IntelliJ Safe Structural Refactoring Steps

**Skill-ID:** `intellij-safe-structural-refactoring-steps`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Use this skill for larger Java structure changes in IntelliJ: moving code, changing APIs, extracting classes, reshaping hierarchies, or removing code.

### Intent

Use IntelliJ's semantic refactoring engine to make structural changes as small, previewed, build-validated operations. Treat each operation as a safe micro-step, not as a broad rewrite.

### When to apply

Apply this when:

- A class, method, or static member belongs in another type or package.
- A name is misleading and should be renamed project-wide.
- A class has too many responsibilities and needs Extract Class / Extract Delegate.
- An API needs Change Signature, Wrap Return Value, Parameter Object, Builder, or Factory.
- A hierarchy needs Pull Up, Push Down, Extract Interface, Extract Superclass, or Replace Inheritance with Delegation.
- Dead code should be removed with Safe Delete.

### Workflow

1. Choose exactly one structural goal.
2. Prefer IntelliJ semantic refactoring over text replacement.
3. Use preview/conflict analysis where available.
4. Apply the refactoring.
5. Build immediately.
6. Run targeted tests if available.
7. Only then continue with the next structural step.

### Important dialog options

Use migration-friendly options when available:

- Change Signature: delegate via overloading method.
- Extract Parameter Object: keep old method as delegate.
- Extract Interface / Extract Superclass: decide whether to use the new abstraction immediately.
- Move Method / Move Members: verify target class and required reference parameters.
- Extract Class / Extract Delegate: choose accessors and visibility changes deliberately.
- Safe Delete: inspect usages before removal.

### Commit discipline

Separate commits or reports for:

- rename-only changes,
- move-only changes,
- extraction-only changes,
- behavior changes.

This keeps diffs reviewable and rollback safe.

### Agent behavior

When using this skill, report the exact IntelliJ refactoring used, the files/classes affected, whether preview/build validation ran, and whether any behavior change was intentionally avoided.

---

## Skill: IntelliJ MCP Steroids Refactoring Execution Recipes

**Skill-ID:** `intellij-mcp-steroids-refactoring-execution-recipes`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Use this skill when an agent must execute IntelliJ Java refactorings from MCP Steroids Kotlin code, IntelliJ MCP Companion tools, IDE Index MCP tools, IDE actions, quick fixes, or PSI generation.

Critical rule: do not document a refactoring as verified until it has been practically executed in the current tool/IDE environment. Recipes derived from JetBrains source or IntelliJ OpenAPI are only recipes until a real probe has passed.

### Current live environment

Observed via MCP Steroid:

```text
IDE: IntelliJ IDEA 2025.3.2, build IU-253.30387.90
MCP Steroid plugin: com.jonnyzzz.mcp-steroid 0.95.0-b14969e1
Project: pyloros
Project path: C:/Projects/pyloros
```

Available MCP Steroid tools include:

```text
mcp-steroid__steroid_list_projects
mcp-steroid__steroid_fetch_resource
mcp-steroid__steroid_execute_code
mcp-steroid__steroid_apply_patch
mcp-steroid__steroid_execute_feedback
mcp-steroid__steroid_action_discovery
```

Earlier notes claiming that no direct MCP Steroids Kotlin executor is visible are obsolete and must not be repeated for this environment.

### Mandatory verification labels

Use only these labels:

```text
verified via MCP Steroid
attempted via MCP Steroid but failed
verified via IntelliJ MCP Companion
verified via IDE Index MCP
quick-fix discovered only
OpenAPI recipe only, not executed
PSI scaffolding fallback
text edit fallback
not tested
```

Never call a recipe verified unless a probe actually changed code correctly and validation/readback confirmed the result.

### Practical MCP Steroid findings

#### RenameProcessor on Java method

Status:

```text
attempted via MCP Steroid but failed
```

Probe file created through IntelliJ VFS/PSI:

```text
src/test/java/com/aresstack/pyloros/steroidprobe/SteroidRenameProbe.java
```

Probe content shape:

```java
package com.aresstack.pyloros.steroidprobe;

final class SteroidRenameProbe {
    String renderGreeting(String name) {
        return name;
    }

    String call() {
        return renderGreeting("x");
    }
}
```

Attempted MCP Steroid route:

```kotlin
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.RenameProcessor

val rel = "src/test/java/com/aresstack/pyloros/steroidprobe/SteroidRenameProbe.java"

val method = readAction {
    val vf = findProjectFile(rel) ?: error("probe file not found")
    val psi = PsiManager.getInstance(project).findFile(vf) as PsiJavaFile
    psi.classes[0].findMethodsByName("renderGreeting", false)[0]
}

writeIntentReadAction {
    RenameProcessor(project, method, "formatGreeting", true, true).run()
}
writeAction { PsiDocumentManager.getInstance(project).commitAllDocuments() }
```

Observed result:

```text
RENAME_STEROID_OK=false
```

The file still contained `renderGreeting`; it was not changed to `formatGreeting`.

Observed IDE failure:

```text
Slow operations are prohibited on EDT. See SlowOperations.assertSlowOperationsAreAllowed javadoc.
```

The stack trace occurred during `RenameProcessor.<init>` / `RenameProcessor.setNewName`, inside Java/Kotlin/Gradle resolve/index code called by IntelliJ while constructing/logging the refactoring processor.

Interpretation:

- MCP Steroid execution itself worked.
- Probe file creation through IntelliJ VFS worked.
- PSI resolution of the target method from the created file worked.
- `RenameProcessor` was instantiated from MCP Steroid code.
- The refactoring did not complete in IntelliJ IDEA 2025.3.2 with the tested `writeIntentReadAction` wrapping.
- Therefore this exact `RenameProcessor` route is not verified for this environment.

Do not document this route as successful until a later probe passes with a corrected threading/SlowOperations wrapper or another supported IDE invocation path.

#### JavaPsiFacade lookup of freshly-created probe class

Status:

```text
attempted via MCP Steroid but failed
```

First attempt created the Java file and then tried to resolve the class immediately through:

```kotlin
JavaPsiFacade.getInstance(project).findClass(
    "com.aresstack.pyloros.steroidprobe.SteroidRenameProbe",
    GlobalSearchScope.projectScope(project)
)
```

Observed result:

```text
Rename probe class not found
```

Interpretation:

- For newly-created probe files, do not immediately rely on FQN index lookup.
- Prefer direct PSI from the known VirtualFile/PsiJavaFile for the same execution pass, or wait for/synchronize indexes before using `JavaPsiFacade.findClass`.

### Existing non-Steroid verified routes

These were previously verified through other IntelliJ MCP tooling, not through MCP Steroid. Keep that distinction explicit.

#### Rename via IntelliJ MCP Companion

Status:

```text
verified via IntelliJ MCP Companion
```

Tool:

```text
intellij__rename_refactoring
```

Probe summary:

- A throwaway Java method `renderGreeting` was renamed to `formatGreeting`.
- Readback confirmed the method name changed.

Do not confuse this with the failed MCP Steroid `RenameProcessor` attempt above.

#### Move Java file/class between packages via IDE Index MCP

Status:

```text
verified via IDE Index MCP
```

Tool:

```text
intellij-index__ide_move_file
```

Probe summary:

- A throwaway Java class was moved between package directories.
- Readback confirmed the package declaration changed accordingly.

#### Safe Delete via IDE Index MCP

Status:

```text
verified via IDE Index MCP
```

Tool:

```text
intellij-index__ide_refactor_safe_delete
```

Probe summary:

- Temporary probe files with no external usages were removed successfully.

### OpenAPI recipes not yet verified via MCP Steroid

The following are recipes only. They must be practically executed with MCP Steroid before being promoted to verified.

#### Extract Method

Status:

```text
OpenAPI recipe only, not executed in current MCP Steroid session
```

Candidate API:

```java
com.intellij.refactoring.extractMethod.ExtractMethodProcessor
com.intellij.refactoring.extractMethod.ExtractMethodHandler
```

Candidate route:

```kotlin
// Resolve a valid Java statement/expression selection as PsiElement[] first.
// Then run ExtractMethodProcessor and ExtractMethodHandler in the supported action context.
```

Do not claim verified until a real probe extracts a method and readback/build confirms it.

#### Change Signature

Status:

```text
quick-fix discovered only / OpenAPI recipe only, not executed via MCP Steroid
```

Probe shape used earlier:

```java
final class SkillProbeChangeSignature {
    String render() {
        return join("left", "right");
    }

    String join(String left) {
        return left;
    }
}
```

Quick-fix discovered earlier:

```text
Add 'String' as 2nd parameter to method 'join()'
```

Candidate API:

```java
com.intellij.refactoring.JavaRefactoringFactory
com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
com.intellij.refactoring.changeSignature.ParameterInfoImpl
```

Do not claim verified until a real MCP Steroid run changes the method signature and call sites correctly.

#### Move Instance Method

Status:

```text
OpenAPI recipe only, not executed via MCP Steroid
```

Candidate API:

```java
com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor
com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler
```

Candidate construction pattern from JetBrains tests/source:

```java
new MoveInstanceMethodProcessor(
    project,
    method,
    targetVariable,
    newVisibility,
    MoveInstanceMethodHandler.suggestParameterNames(method, targetVariable)
).run();
```

Do not claim verified until a real MCP Steroid run moves the method and readback/build confirms it.

#### Create Method from Usage

Status:

```text
quick-fix discovered only / OpenAPI recipe only, not executed via MCP Steroid
```

Classification:

```text
Context Action / design scaffolding, not a behavior-preserving Fowler refactoring
```

Quick-fix discovered earlier:

```text
Create method 'decorateGreeting' in 'RefactoringSkillProbe'
```

Candidate APIs:

```java
com.intellij.lang.java.request.CreateMethodFromUsage
com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix
com.intellij.codeInsight.intention.impl.config.QuickFixFactoryImpl.createCreateMethodFromUsageFixes(...)
```

PSI scaffolding fallback may create a method body, but must be reported as scaffolding, not as a semantic refactoring.

### Required workflow before updating this skill

1. Create a tiny Java probe file using IntelliJ VFS/PSI, not native file IO.
2. Resolve the target symbol/selection through PSI.
3. Execute the intended refactoring through MCP Steroid or an explicitly named IntelliJ MCP tool.
4. Read back the changed file through IntelliJ VFS.
5. Run diagnostics/build when appropriate.
6. Clean up probe files.
7. Only then update this skill with the exact result.

### Reporting template

Always report:

```text
Operation: <refactoring/context action>
Mechanism: MCP Steroid / IntelliJ MCP Companion / IDE Index MCP / Quick Fix / PSI scaffolding / text edit
API/tool: <class or tool>
Probe file: <path>
Execution status: verified / failed / blocked / not tested
Readback: <what changed or did not change>
Validation: build / diagnostics / readback / not run
Behavior change: yes/no
Cleanup: done / blocked / not attempted
```

### Current cleanup note

A later cleanup attempt for the Steroid rename probe was blocked by the platform safety filter before reaching IntelliJ. Before continuing further refactoring experiments, check whether this path still exists and remove it safely:

```text
src/test/java/com/aresstack/pyloros/steroidprobe/SteroidRenameProbe.java
```

Preferred cleanup routes:

```text
intellij-index__ide_refactor_safe_delete
or IntelliJ VFS delete via MCP Steroid
```

### Practical next steps

Before documenting any more recipes as verified:

1. Find a supported MCP Steroid pattern for refactoring processors that avoids IntelliJ 2025.3.2 `SlowOperations` failures on EDT.
2. Re-run RenameProcessor on the tiny Java probe.
3. Only after Rename passes, test Change Signature, Move Instance Method, Extract Method, and Create Method from Usage.
4. Save only the actual observed results.

---

## Skill: IntelliJ MCP Steroid Verified Change Signature OpenAPI

**Skill-ID:** `intellij-mcp-steroid-verified-change-signature-openapi`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Status: verified via MCP Steroid direct OpenAPI.

Environment:
- IntelliJ IDEA 2025.3.2
- MCP Steroid plugin 0.95.0-b14969e1
- Project: pyloros

Goal:
Execute Java Change Signature directly through IntelliJ OpenAPI from `mcp-steroid__steroid_execute_code`, without UI automation.

Important finding:
The simple constructor path `ChangeSignatureProcessor(project, method, ...)` was loadable and executable but produced no code change in the tested probe. Do not use that path as the verified recipe.

Verified path:
1. Resolve the target `PsiMethod` from `VirtualFile -> PsiJavaFile -> PsiMethod`.
2. Build old and new `ParameterInfoImpl` entries.
3. Wrap the return type with `CanonicalTypes.createTypeWrapper(...)`.
4. Create `JavaChangeInfo` with `JavaChangeInfoImpl.generateChangeInfo(...)`.
5. Run `ChangeSignatureProcessor(project, changeInfo)` inside `writeIntentReadAction`.
6. Commit documents with `PsiDocumentManager.getInstance(project).commitAllDocuments()`.
7. Read back through VFS/PSI.

Minimal Kotlin shape:

```kotlin
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.util.CanonicalTypes

val method = smartReadAction {
    val vf = findProjectFile("src/test/java/com/aresstack/pyloros/steroidprobe/ChangeSignatureSteroidProbe.java") ?: error("no file")
    val psi = PsiManager.getInstance(project).findFile(vf) as PsiJavaFile
    psi.classes[0].findMethodsByName("join", false)[0]
}

val changeInfo = smartReadAction {
    val manager = PsiManager.getInstance(project)
    val stringType = PsiType.getJavaLangString(manager, GlobalSearchScope.projectScope(project))
    val oldParam = ParameterInfoImpl(0, "left", method.parameterList.parameters[0].type)
    val newParam = ParameterInfoImpl(-1, "right", stringType, "\"right\"")
    val returnType = CanonicalTypes.createTypeWrapper(method.returnType ?: PsiTypes.voidType())

    JavaChangeInfoImpl.generateChangeInfo(
        method,
        false,
        false,
        PsiModifier.PACKAGE_LOCAL,
        "join",
        returnType,
        arrayOf(oldParam, newParam),
        emptyArray<ThrownExceptionInfo>(),
        emptySet<PsiMethod>(),
        emptySet<PsiMethod>()
    )
}

writeIntentReadAction {
    ChangeSignatureProcessor(project, changeInfo).run()
}

writeAction {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
}
```

Probe:

```java
final class ChangeSignatureSteroidProbe {
    String render() {
        return join("left");
    }

    String join(String left) {
        return left;
    }
}
```

Verified readback:

```text
HAS_PARAM=true
HAS_CALL=true
PARAM_COUNT=2
PARAMS=left:String,right:String
RENDER=String render() { return join("left", "right"); }
JOIN=String join(String left, java.lang.String right) { return left; }
```

Final file shape observed:

```java
final class ChangeSignatureSteroidProbe {
    String render() {
        return join("left", "right");
    }

    public String join(String left, java.lang.String right) {
        return left;
    }
}
```

Diagnostics:
IDE diagnostics returned `problemCount=0`, but also reported the file was not open in an editor and analysis was not fresh. Treat PSI/VFS readback as the primary validation for this probe.

Status label:
`verified via MCP Steroid direct OpenAPI`

Do not mark other refactoring processors verified from this result. Each processor still needs its own practical probe.

---

## Skill: IntelliJ: GitHub-Repository klonen und in einem neuen Projektfenster öffnen

**Skill-ID:** `intellij-clone-github-repository-and-open-project-window`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

Status: **praktisch verifiziert am 2026-11-10** über Pyloros + MCP Steroid, IntelliJ IDEA 2025.3.2, MCP Steroid 0.95.0-b14969e1.
Skill-ID: `intellij-clone-github-repository-and-open-project-window`.

### Wann verwenden?
Wenn ein Nutzer ein GitHub-Repository in sein lokales Projektverzeichnis klonen und anschließend in IntelliJ öffnen möchte, während das bereits geöffnete Projekt bestehen bleibt. Auch leere GitHub-Repositories sind unterstützt. Bei einem bereits lokal vorhandenen Repository Schritt 2 überspringen und die vorhandene Working Copy prüfen.

### Verifizierte Tool-Funktionen
- `mcp__PylorosV3__mcp_steroid__steroid_list_windows({})` – geöffnete Fenster, Projektpfade, Sichtbarkeit, Initialisierungs-/Indexierungsstatus und Dialoge ermitteln.
- `mcp__PylorosV3__mcp_steroid__steroid_list_projects({})` – geöffnete Projekte unabhängig von Fenstern prüfen.
- `mcp__PylorosV3__intellij__execute_terminal_command({command, projectPath, executeInShell, timeout, maxLinesCount})` – Git in einem *bereits geöffneten* IntelliJ-Projekt ausführen.
- `mcp__PylorosV3__mcp_steroid__steroid_open_project({project_path, task_id, reason, trust_project})` – anderes Verzeichnis über IntelliJ öffnen.
- `mcp__PylorosV3__mcp_steroid__steroid_take_screenshot({project_name, window_id, task_id, reason})` – gezieltes Fenster visuell prüfen; danach ggf. `steroid_execute_feedback` verwenden.
- Bei Dialogen: `steroid_take_screenshot` und passende `steroid_input`-Aktionen.

### Ablauf

#### 1. Quellprojekt und Zielpfad bestimmen
1. Rufe `steroid_list_windows` und gegebenenfalls `steroid_list_projects` auf. Lies die **tatsächlich gemeldeten** Projektpfade; rate keine Benutzer- oder Verzeichnisnamen.
2. Bestimme über den bereits geöffneten Projektpfad das gewünschte gemeinsame Git-Projektverzeichnis (z. B. `C:/Projects/aresstack`). Prüfe, dass das Zielverzeichnis zulässig, nicht schon mit anderem Inhalt belegt und korrekt geschrieben ist.
3. Verwende den konkret vom Nutzer genannten Repository-URL und einen absoluten lokalen Zielpfad. Klone nicht, wenn bereits ein passendes Git-Repository vorhanden ist; prüfe dann dessen Remote.

#### 2. Git-Clone im IntelliJ-Terminal
Führe den Befehl im Kontext des **bestehenden** Projekts aus, z. B.:

```text
mcp__PylorosV3__intellij__execute_terminal_command({
  command: "git clone https://github.com/OWNER/REPO.git C:/Projects/aresstack/REPO",
  projectPath: "C:/Projects/aresstack/EXISTING_PROJECT",
  executeInShell: true,
  timeout: 30000,
  maxLinesCount: 100
})
```

- Kontrolliere `command_exit_code === 0`, `is_timed_out !== true` und Git-Ausgabe.
- Bei leerem Remote ist `warning: You appear to have cloned an empty repository.` **kein Fehler**; Git legt trotzdem `.git` im Ziel an.
- Bei Git-Authentifizierungs- oder Dateikonflikten nicht ungefragt Zugangsdaten, Dateien oder vorhandene Verzeichnisse überschreiben.
- `execute_terminal_command` kann abhängig von den IntelliJ-/Plugin-Einstellungen eine Nutzerfreigabe verlangen.

#### 3. Öffnen über MCP Steroid (nicht über den Terminalbefehl)
Rufe danach mit **dem neuen Projektpfad** auf:

```text
mcp__PylorosV3__mcp_steroid__steroid_open_project({
  project_path: "C:/Projects/aresstack/REPO",
  task_id: "clone-open-REPO",
  reason: "Open newly cloned repository in IntelliJ and verify project readiness.",
  trust_project: false
})
```

- `steroid_open_project` startet das Öffnen **asynchron** und meldet nur die Initiierung, **nicht** den erfolgreichen Abschluss.
- `trust_project: true` überspringt die IntelliJ-Trust-Abfrage; nur setzen, wenn der Nutzer dem Projekt ausdrücklich vertraut bzw. die Freigabe autorisiert ist. Bei `false` einen gegebenenfalls auftretenden Trust-Dialog überprüfen und die Entscheidung vom Nutzer einholen.
- Das Ergebnis kann ein separates IntelliJ-Fenster sein, **ohne** die vorhandenen Projekte zu schließen; ein eigener Parameter `new_window` wurde in der verifizierten Tool-Signatur nicht angeboten. Ob ein neues Fenster entsteht, kann von der IntelliJ-IDE-Einstellung abhängen. Deshalb unbedingt tatsächliche Fenster prüfen.

#### 4. Tatsächlichen Erfolg verifizieren
1. Rufe `steroid_list_windows({})` in sinnvollen Abständen erneut auf, bis das **neue** `projectPath` in einem Fenster auftaucht.
2. Überprüfe beim neuen Fenster:
   - `isVisible === true`
   - `projectInitialized === true`
   - `indexingInProgress === false`
   - `modalDialogShowing === false`
3. Wenn `modalDialogShowing === true`, mit Screenshot/Dialog-Interaktion klären; niemals stillschweigend Vertrauen bestätigen.
4. Führe `steroid_take_screenshot` auf der korrekten `window_id` aus, wenn erforderlich, um das neue Projektfenster visuell zu bestätigen. Große Bild-/Base64-Payloads nicht ungefiltert ins Log oder die Antwort kopieren.
5. Rufe `steroid_list_projects({})` auf und prüfe, dass **beide** Projekte aufgeführt sind.
6. Berichte nur verifizierte Resultate; wenn die IDE nur den Öffnungsstart meldet, behaupte nicht, das Fenster sei bereits bereit.

### Verifizierter konkreter Lauf (2026-11-10)
- Ausgangsfenster: `askai`, Pfad `C:/Projects/aresstack/askai`.
- Repository: `https://github.com/aresstack/drawio-java.git`.
- Terminalbefehl: `git clone https://github.com/aresstack/drawio-java.git C:/Projects/aresstack/drawio-java`.
- Git-Rückgabe: Exit-Code **0**, mit Hinweis auf ein leeres Repository.
- `steroid_open_project` mit `project_path=C:/Projects/aresstack/drawio-java`; in **diesem** explizit vom Nutzer gewünschten Lauf wurde `trust_project=true` verwendet.
- Die Nachprüfung über `steroid_list_windows` ergab ein neues sichtbares Fenster mit `projectName=drawio-java`, `projectInitialized=true`, `indexingInProgress=false`, `modalDialogShowing=false`.
- `steroid_list_projects` bestätigte weiterhin `askai` und zusätzlich `drawio-java`.
- Screenshot des neuen Fensters wurde über `steroid_take_screenshot` angefordert.
- Ergebnis: Neues Projektfenster tatsächlich geöffnet, bisheriges Fenster erhalten.

### Abgrenzung
Dieser Skill beschreibt einen **verifizierten lokalen IntelliJ-Workflow**, nicht das Erstellen von Repositories oder die Implementierung von Projektcode. Er dokumentiert ein erfolgreich beobachtetes Fensterverhalten, aber **keine garantierte Erzwingung eines neuen Fensters** bei allen IDE-Konfigurationen.

### Verified recovery: File Cache Conflict after Git branch synchronization (2026-11-10)

**Symptom:** IntelliJ shows a modal *File Cache Conflict* for a file changed externally by Git, e.g. `build.gradle`. The choices are `Load File System Changes`, `Keep Memory Changes`, `Show Difference`. Normal IntelliJ MCP edit/terminal calls may timeout while the dialog is modal; even direct `steroid_input` can timeout. Avoid repeating blocked input calls.

**Successful alternative when another IntelliJ project remains open:**

1. First verify `steroid_list_windows({})` reports both the affected project and an unaffected project; check the dialog's nature with `steroid_take_screenshot` if necessary. Preserve committed/uncommitted work before closing when possible; do not close a project if its unsaved in-memory changes are valuable and cannot be recovered.
2. Close **only** the conflicted project:
   ```text
   mcp__PylorosV3__intellij_index__ide_close_project({
     project_path: "C:/Projects/aresstack/drawio-java"
   })
   ```
   The operation reports `Project 'drawio-java' is closing.` and is asynchronous. **Never close the last project:** this endpoint refuses it.
3. Poll `steroid_list_windows({})` until the affected project disappears and the other project remains open.
4. Reopen only the affected project:
   ```text
   mcp__PylorosV3__mcp_steroid__steroid_open_project({
     project_path: "C:/Projects/aresstack/drawio-java",
     task_id: "drawio-java-restore-conflict-20261110",
     reason: "Reopen after file cache conflict without closing the unaffected project.",
     trust_project: true
   })
   ```
   **Only use `trust_project:true` if the user's trust is explicitly authorized.**
5. Poll `steroid_list_windows({})` for `projectInitialized=true`, `indexingInProgress=false`, `modalDialogShowing=false`; verify the other project remains open.
6. Run a harmless terminal/IDE inspection and `git status --short --branch` in the reopened project to validate local changes are intact and no stale memory buffer overwrote the disk version.

**Verified result:** At 2026-11-10, this sequence closed only `drawio-java`; `askai` stayed open. The reopened `drawio-java` appeared under a new window ID with `modalDialogShowing=false`, and terminal access resumed. The former `build.gradle` conflict no longer blocked tools. Do not claim this approach universally resolves every unsaved-file conflict; it worked for a Git-created disk/memory mismatch in this case.

---

## Skill: IntelliJ MCP Steroids Clean Code Refactoring Skill

**Skill-ID:** `intellij-mcp-steroids-clean-code-refactoring`  
**Quelle:** Originalinhalt über `pyloros__skills_get` abgerufen; keine redaktionelle inhaltliche Änderung.

### Purpose

Use this skill when refactoring Java code in IntelliJ through mcp-steroid__steroid_execute_code.

The goal is to produce Clean Code-style improvements using IntelliJ's semantic model instead of fragile text replacement.

### Core Rules

- Prefer IntelliJ PSI, refactoring processors, inspections, and project model APIs over raw file editing.
- Use literal text replacement only for tiny mechanical edits where no semantic API exists.
- For symbol changes, use real refactorings: Rename, Move, Safe Delete, Extract Method, Change Signature, Inline Method.
- Do a dry run before any complex refactoring.
- Apply only one coherent refactoring step at a time.
- Validate after each applied step with file inspections and build.
- Keep Java 8 compatibility unless the project explicitly allows newer language features.
- Prefer focused methods with intention-revealing names.
- Preserve architectural boundaries.
- Do not move Java classes by copy/delete when IntelliJ Move Refactoring can update packages and references.
- Do not rename symbols by string replacement.
- Do not delete symbols directly before checking usages or running Safe Delete.

### Standard Workflow

1. Discover the target through IntelliJ indexes.
2. Read the target file with line numbers.
3. Pick one Clean Code candidate.
4. Run a dry run through the appropriate processor.
5. Apply only if preparation succeeds.
6. Save documents.
7. Run inspections.
8. Build.
9. Report what changed, why, and the validation result.

### Clean Code Heuristics

Extract Method when a method contains a clearly named subtask, a block needs a comment, or a block reads like a separate step in a use case.

Move Class when a class belongs to another layer or package by responsibility, or when a factory would reduce application wiring noise.

Introduce Factory when construction logic obscures orchestration flow or creates different implementations of the same abstraction.

Introduce Strategy when branching chooses behavior based on a mode, provider id, type, or transport.

### Reporting Format

Applied:
- <refactoring summary>

Why:
- <readability / responsibility / coupling reason>

Validation:
- prepare(): <true|false>
- inspections: <count / notable issues>
- build: errors=<true|false>, aborted=<true|false>

Notes:
- <warnings, limitations, next suggested refactoring>

