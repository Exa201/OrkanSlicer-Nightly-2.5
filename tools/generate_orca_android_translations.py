#!/usr/bin/env python3
"""Generate Android string resources from the bundled Orca PO catalogs.

Only exact, non-contextual singular msgid matches are imported. This avoids
choosing an arbitrary translation for ambiguous gettext msgctxt entries and
keeps Android format placeholders safe.
"""

from __future__ import annotations

import ast
import re
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
PO_DIR = ROOT / "app/src/main/assets/localization"
SUPPORTED = {
    "ca": "ca", "cs": "cs", "es": "es", "eu": "eu", "fr": "fr",
    "hu": "hu", "it": "it", "ja": "ja", "ko": "ko", "lt": "lt",
    "nl": "nl", "pl": "pl", "pt-BR": "pt_BR", "sv": "sv", "th": "th",
    "tr": "tr", "uk": "uk", "vi": "vi", "zh-CN": "zh_CN", "zh-TW": "zh_TW",
}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-+# 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")
CONTEXT_OVERRIDES = {
    "NavigationPrintCompact": "Noun",
    "SlotPrintConfig": "Noun",
    "MenuToolbar": "Menu",
    "MenuToolbarNavigation": "Menu",
    "MenuToolbarNavigationCompact": "Menu",
}
SOURCE_OVERRIDES = {"SettingsInterfaceLanguage": "Language"}


def unquote(value: str) -> str:
    return ast.literal_eval(value)


def parse_po(path: Path) -> tuple[dict[str, str], dict[tuple[str, str], str]]:
    """Read singular PO entries while preserving contextual translations."""
    result: dict[str, str] = {}
    contextual: dict[tuple[str, str], str] = {}
    fields: dict[str, str] = {}
    active_field: str | None = None
    fuzzy = False

    def finish() -> None:
        nonlocal fields, fuzzy
        if not fuzzy and "msgid_plural" not in fields:
            msgid = fields.get("msgid", "")
            msgstr = fields.get("msgstr", "")
            context = fields.get("msgctxt")
            if msgid and msgstr:
                if context:
                    contextual.setdefault((context, msgid), msgstr)
                else:
                    result.setdefault(msgid, msgstr)
        fields = {}
        fuzzy = False

    for raw in path.read_text(encoding="utf-8").splitlines() + [""]:
        line = raw.strip()
        if not line:
            finish()
            active_field = None
            continue
        if line.startswith("#,"):
            fuzzy = "fuzzy" in line.split(",", 1)[1].split()
            continue
        if line.startswith("#"):
            continue
        match = re.match(r"(msgctxt|msgid_plural|msgid|msgstr(?:\[\d+\])?)\s+(\".*\")$", line)
        if match:
            active_field = match.group(1)
            if active_field.startswith("msgstr["):
                continue
            fields[active_field] = unquote(match.group(2))
            continue
        if line.startswith('"') and line.endswith('"') and active_field is not None:
            fields[active_field] = fields.get(active_field, "") + unquote(line)
    return result, contextual


def android_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def placeholder_signature(value: str) -> list[str]:
    return sorted(PLACEHOLDER.findall(value))


def main() -> None:
    source_root = ET.parse(RES / "values/strings.xml").getroot()
    source_strings = {
        item.attrib["name"]: android_text(item)
        for item in source_root.findall("string")
        if "name" in item.attrib
    }
    total = 0
    for tag, catalog_name in SUPPORTED.items():
        catalog, contextual = parse_po(PO_DIR / f"{catalog_name}.po")
        qualifier = tag.replace("-", "-r", 1) if "-" in tag else tag
        out_dir = RES / f"values-{qualifier}"
        out_dir.mkdir(parents=True, exist_ok=True)
        resources = ET.Element("resources")
        count = 0
        for name, source in source_strings.items():
            source = SOURCE_OVERRIDES.get(name, source)
            context = CONTEXT_OVERRIDES.get(name)
            translation = contextual.get((context, source)) if context else None
            translation = translation or catalog.get(source)
            if not translation or translation == source:
                continue
            if placeholder_signature(source) != placeholder_signature(translation):
                continue
            element = ET.SubElement(resources, "string", {"name": name})
            # Android strings have their own backslash escape syntax on top of XML.
            element.text = (
                translation.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace('"', '\\"')
            )
            count += 1
        tree = ET.ElementTree(resources)
        ET.indent(tree, space="    ")
        tree.write(out_dir / "strings.xml", encoding="utf-8", xml_declaration=True)
        print(f"{tag}: {count}/{len(source_strings)} Android strings")
        total += count
    print(f"Imported {total} translations across {len(SUPPORTED)} additional languages")


if __name__ == "__main__":
    main()
