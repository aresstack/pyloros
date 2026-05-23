What was verified, changed or implemented?
- The canonical public MCP endpoint was changed to `/pyloros`.
- The legacy alias `/sse` remains mounted as a deprecated compatibility endpoint.
- Both `/pyloros` and `/sse` serve the same MCP operations:
  - `initialize`
  - `tools/list`
  - `tools/call`
  - `resources/list`
  - `prompts/list` remains supported.
- MCP POST logging now marks whether the request used the deprecated alias:
  - `/pyloros` -> `deprecated=false`
  - `/sse` -> `deprecated=true`
- The SSE endpoint event now returns the actually mounted base path, so `/sse` remains compatible as an alias and `/pyloros` is canonical.
- Public metadata continues to advertise the canonical endpoint via `/pyloros`, while legacy metadata aliases for `/sse` remain available.
- Default public config was updated from `/sse` to `/pyloros`.
- README connector URL and examples were updated to `https://current-car.com/pyloros`.
- Local smoke-test default URL was updated to `http://127.0.0.1:8081/pyloros` while keeping `/sse` as documented legacy override.
- No changes were made to `ToolCatalog`, `ProviderRegistry`, provider dispatch, or routing semantics.
- Default tool-name separator remains `__`.
- Additional compatibility coverage was verified with a dedicated HTTP test for both canonical and legacy endpoints, including direct path-based tool invocation.

Which files were changed or newly created?
- Changed:
  - `README.md`
  - `pyloros-app/build.gradle`
  - `pyloros-app/src/main/java/com/aresstack/pyloros/config/PylorosConfig.java`
  - `pyloros-app/src/main/java/com/aresstack/pyloros/smoke/McpAggregationSmokeTest.java`
  - `pyloros-app/src/main/resources/application.properties`
  - `pyloros-server/build.gradle`
  - `pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
  - `pyloros-server/src/main/java/com/aresstack/pyloros/http/MetadataRoutes.java`
  - `pyloros-server/src/test/java/com/aresstack/pyloros/http/PublicEndpointCompatibilityTest.java`
  - `pyloros-server/src/test/java/com/aresstack/pyloros/oauth/OAuthServiceRefreshReplayTest.java`
  - `docs/agent/report.md`
- Newly created:
  - `pyloros-server/src/test/java/com/aresstack/pyloros/http/PublicEndpointCompatibilityTest.java`

Which architecture decision was touched?
- The public gateway endpoint is now canonically `/pyloros`.
- `/sse` remains only as a deprecated compatibility alias.
- Public endpoint naming changed, but internal MCP routing architecture did not:
  - no provider-specific public endpoints
  - no change to `ToolCatalog`
  - no change to `ProviderRegistry`
  - no change to exact tool routing behavior.

Which tests, builds and runtime checks were executed?
- Environment:
  - Java 21 via `C:\Program Files\Zulu\zulu-21`
- Executed commands:
  1. `./gradlew.bat :pyloros-server:test --tests com.aresstack.pyloros.http.PublicEndpointCompatibilityTest --no-daemon --console=plain`
     - result: successful
  2. `./gradlew.bat :pyloros-app:test --tests com.aresstack.pyloros.config.ToolNameSeparatorResolverTest --no-daemon --console=plain`
     - result: successful
  3. `./gradlew.bat build --no-daemon --console=plain`
     - result: successful
  4. `./gradlew.bat clean build --no-daemon --console=plain`
     - result: successful
- Additional runtime/build handling:
  - A running `java -jar .\pyloros-app\build\libs\pyloros.jar` process was identified as the lock owner for `pyloros.jar` and was stopped before rerunning `clean build`.
- Functional coverage confirmed by `PublicEndpointCompatibilityTest`:
  - `/pyloros` RPC compatibility
  - `/sse` RPC compatibility
  - `/pyloros/<tool>` direct path invocation
  - `/sse/<tool>` direct path invocation
  - canonical metadata advertisement on `/pyloros`
  - legacy metadata alias preservation on `/sse`
  - deprecated logging flag behavior.

Result: successful

If failed: exact error and recommended next step
- No remaining failure.
- Note: a real ChatGPT connector `api_tool.call_tool` run was not executed directly from this environment. Local endpoint compatibility and path-based invocation behavior were verified through automated tests.

Exact commit hash, or No commit created
- No commit created
