009-H - Konfigurierbarer Separator für externe Toolnamen

Ziel:
Externe Toolnamen zentral formatieren mit konfigurierbarem Separator, ohne die interne Routing-Architektur zu ändern.

Anforderungen:
- Default-Separator ist `__`.
- Override-Priorität:
  1. CLI `--tool-name-separator=...`
  2. JVM Property `-Dmcp.tool-name-separator=...`
  3. Default `__`
- Separator betrifft nur externe Toolnamen (Darstellung in `tools/list` und Lookup-Key), nicht interne Adressierung.

Nicht ändern:
- `ToolAddress` bleibt `ToolAddress(providerId, upstreamToolName)`.
- `ToolRouter` bleibt exakter Lookup via `toolsByExternalName.get(requestToolName)`.
- Kein Split-/Prefix-Routing.
- Keine Änderungen in Providern bzgl. Namebildung der Upstream-Tools.

Umsetzung:
1. Neue zentrale Komponente `ToolNameFormatter` (oder gleichwertig) mit API:
   - `externalName(providerId, upstreamToolName)`
2. Konfig-Auflösung des Separators gemäß Priorität (CLI > JVM Property > Default).
3. Wiring in App/Bootstrap, sodass `ToolCatalog` den Formatter/Separator nutzt.
4. `ToolCatalog` auf Formatter umstellen.
5. Tests anpassen/ergänzen:
   - Default `__`-Namen.
   - Override `/` zeigt Slash-Namen in list und Routing funktioniert lokal.
   - Interne Address bleibt gleich.
6. README knapp aktualisieren:
   - neuer Separator-Override inkl. Default `__`
   - Hinweis, dass `/` nur Test/Kompatibilitätsmodus ist.

Akzeptanz:
1. Build grün.
2. Relevante Tests grün.
3. Kein Scope-Creep über diesen Slice hinaus.
4. Kein Commit ohne explizite Freigabe.
