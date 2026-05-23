# IntelliJ MCP Steroids Clean Code Refactoring Skill

## Purpose

Use this skill when refactoring Java code in IntelliJ through `mcp-steroid__steroid_execute_code`.

The goal is to produce Clean Code-style improvements using IntelliJ's semantic model instead of fragile text replacement.

This skill is optimized for Java 8 projects and Uncle Bob-style incremental refactoring: small, safe transformations, expressive names, short methods, clear responsibilities, and validation after each change.

## Core Rules

- Prefer IntelliJ PSI, refactoring processors, inspections, and project model APIs over raw file editing.
- Use literal text replacement only for tiny mechanical edits where no semantic API exists.
- For symbol changes, use real refactorings: Rename, Move, Safe Delete, Extract Method, Change Signature, Inline Method.
- Do a dry run before any complex refactoring.
- Apply only one coherent refactoring step at a time.
- Validate after each applied step with file inspections and build.
- Keep Java 8 compatibility. Do not introduce `var`, `record`, `List.of`, switch expressions, text blocks, or pattern matching.
- Prefer focused methods with intention-revealing names.
- Preserve architectural boundaries. Do not let UI, infrastructure, domain, and application wiring leak into each other.
- Prefer interfaces or small collaborators when moving responsibilities across classes.
- Do not move Java classes by copy/delete when IntelliJ Move Refactoring can update packages and references.
- Do not rename symbols by string replacement.
- Do not delete symbols directly before checking usages or running Safe Delete.

## Standard Workflow

1. Discover the target through IntelliJ indexes.
2. Read the target file with line numbers.
3. Pick one Clean Code candidate.
4. Run a dry run through the appropriate processor.
5. Apply only if preparation succeeds.
6. Save documents.
7. Run inspections.
8. Build.
9. Report what changed, why, and the validation result.

## Discover Target File

```kotlin
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

val files = readAction {
    FilenameIndex.getVirtualFilesByName("TargetClass.java", GlobalSearchScope.projectScope(project)).toList()
}

println("MATCHES=" + files.size)
files.forEachIndexed { index, file -> println("FILE[" + index + "]=" + file.path) }
```

## Inspect File With Line Numbers

```kotlin
val filePath = "module/src/main/java/com/example/TargetClass.java"
val virtualFile = readAction { findProjectFile(filePath) } ?: error("File not found: " + filePath)
val text = readAction { String(virtualFile.contentsToByteArray(), virtualFile.charset) }

text.lines().forEachIndexed { index, line ->
    println("%4d | %s".format(index + 1, line))
}
```

## Extract Method Without UI

Use this when a line range contains complete Java statements.

### Dry Run

```kotlin
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val filePath = "module/src/main/java/com/example/TargetClass.java"
val startLine = 10
val endLine = 15
val newMethodName = "createMeaningfulName"

val (psiFile, document) = readAction {
    val virtualFile = findProjectFile(filePath) ?: return@readAction null to null
    val psi = PsiManager.getInstance(project).findFile(virtualFile)
    val doc = FileDocumentManager.getInstance().getDocument(virtualFile)
    psi to doc
}

if (psiFile == null || document == null) {
    println("File not found or no document: " + filePath)
    return
}

val editor = withContext(Dispatchers.EDT) {
    EditorFactory.getInstance().createEditor(document, project)
}

try {
    val startOffset = readAction { document.getLineStartOffset(startLine - 1) }
    val endOffset = readAction { document.getLineEndOffset(endLine - 1) }

    val statements = readAction {
        PsiTreeUtil.collectElements(psiFile) { element ->
            element is PsiStatement &&
                    element.textRange.startOffset >= startOffset &&
                    element.textRange.endOffset <= endOffset
        }.filterIsInstance<PsiStatement>().toTypedArray()
    }

    println("STATEMENTS=" + statements.size)
    if (statements.isEmpty()) {
        println("No statements found in specified line range")
        return
    }

    val processor = ExtractMethodProcessor(
        project,
        editor,
        statements,
        null,
        "Extract Method",
        newMethodName,
        null
    )

    processor.setShowErrorDialogs(false)

    val prepared = readAction { processor.prepare() }
    println("PREPARED=" + prepared)
    if (!prepared) return

    readAction {
        processor.setMethodName(newMethodName)
        processor.prepareVariablesAndName()
        processor.prepareNullability()
    }

    println("DRY_RUN_OK method=" + newMethodName + " lines=" + startLine + "-" + endLine)
} finally {
    withContext(Dispatchers.EDT) {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
```

### Apply

```kotlin
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val filePath = "module/src/main/java/com/example/TargetClass.java"
val startLine = 10
val endLine = 15
val newMethodName = "createMeaningfulName"

val (psiFile, document) = readAction {
    val virtualFile = findProjectFile(filePath) ?: return@readAction null to null
    val psi = PsiManager.getInstance(project).findFile(virtualFile)
    val doc = FileDocumentManager.getInstance().getDocument(virtualFile)
    psi to doc
}

if (psiFile == null || document == null) {
    println("File not found or no document: " + filePath)
    return
}

val editor = withContext(Dispatchers.EDT) {
    EditorFactory.getInstance().createEditor(document, project)
}

try {
    val startOffset = readAction { document.getLineStartOffset(startLine - 1) }
    val endOffset = readAction { document.getLineEndOffset(endLine - 1) }

    val statements = readAction {
        PsiTreeUtil.collectElements(psiFile) { element ->
            element is PsiStatement &&
                    element.textRange.startOffset >= startOffset &&
                    element.textRange.endOffset <= endOffset
        }.filterIsInstance<PsiStatement>().toTypedArray()
    }

    if (statements.isEmpty()) {
        println("No statements found in specified line range")
        return
    }

    val processor = ExtractMethodProcessor(
        project,
        editor,
        statements,
        null,
        "Extract Method",
        newMethodName,
        null
    )

    processor.setShowErrorDialogs(false)

    val prepared = readAction { processor.prepare() }
    println("PREPARED=" + prepared)
    if (!prepared) return

    readAction {
        processor.setMethodName(newMethodName)
        processor.prepareVariablesAndName()
        processor.prepareNullability()
    }

    writeIntentReadAction {
        ExtractMethodHandler.extractMethod(project, processor)
    }

    writeAction {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
    }

    println("APPLIED Extract Method: " + newMethodName)
} finally {
    withContext(Dispatchers.EDT) {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
```

## Move Refactoring Rules

Use Move Refactoring when moving Java classes between packages or modules.

Dry-run checklist:

- Resolve source class or file via PSI/index.
- Resolve target directory/package.
- Check references.
- Check package naming.
- Print affected source and target.
- Do not mutate files.

Preferred tool when available:

```text
intellij-index__ide_move_file
```

Rule:

```text
Move refactoring > git mv > VFS copy/delete
```

Use copy/delete only for non-code assets or generated files where no semantic references exist.

## Safe Delete

Use Safe Delete before removing classes, methods, fields, or files.

Workflow:

1. Resolve target PSI element.
2. Run usage search or Safe Delete dry run.
3. If usages exist, report them instead of deleting.
4. Only force delete after explicit approval or when usages are intentionally removed in the same refactoring step.

Preferred tool when available:

```text
intellij-index__ide_refactor_safe_delete
```

## Rename

Never rename Java symbols by string replacement. Use `RenameProcessor`.

```kotlin
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManager

val oldClassName = "com.example.OldName"
val newClassName = "NewName"

val psiClass = readAction {
    JavaPsiFacade.getInstance(project)
        .findClass(oldClassName, GlobalSearchScope.projectScope(project))
} ?: error("Class not found: " + oldClassName)

writeIntentReadAction {
    RenameProcessor(project, psiClass, newClassName, true, true).run()
}

writeAction {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    FileDocumentManager.getInstance().saveAllDocuments()
}

println("Renamed " + oldClassName + " to " + newClassName)
```

## Clean Code Heuristics

Extract Method when:

- A method contains a clearly named subtask.
- Comments would otherwise be needed to explain a block.
- A block has one input/output concept.
- A block reads like a separate step in a use case.

Move Class when:

- A class belongs to another layer or package by responsibility.
- A provider/adapter lives in application wiring but belongs in infrastructure.
- Domain objects depend on infrastructure details.
- A factory would reduce wiring noise in the application entry point.

Introduce Factory when:

- Branches create different implementations of the same abstraction.
- Construction logic obscures the orchestration flow.
- You need a seam for testing.

Introduce Strategy when:

- A branch chooses behavior based on a mode, provider id, type, or transport.
- New variants are expected.
- The current method violates Open/Closed Principle.

## Reporting Format

```text
Applied:
- Extracted <methodName> from <className>.

Why:
- <one sentence about readability / responsibility / coupling>.

Validation:
- prepare(): true
- inspections: <count / notable issues>
- build: errors=<true|false>, aborted=<true|false>

Notes:
- <warnings, limitations, next suggested refactoring>
```

## Example: Router Setup Extraction

Clean Code extraction:

```java
private Router createRouter(PylorosConfig config, SecurityModule securityModule, ToolCatalog toolCatalog, ToolRouter toolRouter) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    new HealthRoutes().mount(router);
    securityModule.mountRoutes(router);
    new McpRoutes(config, securityModule.authenticator(), toolCatalog, toolRouter).mount(router);
    return router;
}
```

Resulting startup code:

```java
Router router = createRouter(config, securityModule, toolCatalog, toolRouter);
```

This makes the startup method read at a higher level of abstraction and gives the routing setup a single intentional name.
