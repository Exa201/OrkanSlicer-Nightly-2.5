#!/usr/bin/env python3
"""Generate OrkanSlicer's bundled Orca profiles from an OrcaSlicer checkout.

Usage:
  python tools/update_orca_profile_database.py <OrcaSlicer>/resources/profiles \
      app/src/main/assets/orca_profiles --source-revision <git-sha> --archive-legacy

The output is the same combined INI format consumed by Slic3rConfigWrapper. Only
keys supported by the current Android config wrapper are emitted, and profile
inheritance is flattened before writing so the app is fully offline.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def json_value(value: Any, key: str = "") -> str:
    if isinstance(value, list):
        separator = ";" if key == "default_materials" else ","
        return separator.join(json_value(item) for item in value)
    if value is None:
        return ""
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return str(value)


def supported_keys(java_root: Path) -> dict[str, set[str]]:
    wrapper = (java_root / "slic3r/Slic3rConfigWrapper.kt").read_text(encoding="utf-8")
    config_object = (java_root / "config/ConfigObject.kt").read_text(encoding="utf-8")
    io_utils = (java_root / "utils/IOUtils.kt").read_text(encoding="utf-8")
    catalog = (java_root / "ui/settings/OrcaProcessSettingsCatalog.kt").read_text(encoding="utf-8")

    def literal_keys(block: str) -> set[str]:
        return set(re.findall(r'"([A-Za-z0-9_]+)"', block))

    print_start = wrapper.index("private fun buildPrintConfigKeys()")
    print_end = wrapper.index("@JvmField", print_start)
    print_keys = literal_keys(wrapper[print_start:print_end])
    # Settings added by the Compose settings catalog are accepted by the runtime list.
    print_keys.update(literal_keys(catalog))
    print_keys.update(literal_keys(config_object[config_object.index("private val PRINT_CONFIG_KEY_ALIASES"):config_object.index("private val KEY_MIGRATION_REVERSE")]))

    def key_block(name: str) -> set[str]:
        start = wrapper.index("val " + name + ":")
        end = wrapper.find("@JvmField", start)
        if end < 0:
            end = len(wrapper)
        return literal_keys(wrapper[start:end])

    keys = {
        "process": print_keys,
        "machine": key_block("PRINTER_CONFIG_KEYS"),
        "filament": key_block("FILAMENT_CONFIG_KEYS"),
    }

    migration_start = config_object.index("private fun buildKeyMigration()")
    migration_end = config_object.index("private fun buildReverse()", migration_start)
    migrations = dict(re.findall(r'put\("([^"]+)", "([^"]+)"\)', config_object[migration_start:migration_end]))
    aliases_start = config_object.index("private val ORCA_KEY_ALIASES")
    aliases_end = config_object.index("private val PRINT_CONFIG_KEY_ALIASES", aliases_start)
    aliases = dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', config_object[aliases_start:aliases_end]))
    io_aliases = {"chamber_temperatures": "chamber_temperature"}

    def canonical(key: str) -> str:
        mapped = io_aliases.get(key, migrations.get(key, key))
        return aliases.get(mapped, mapped)

    # Upstream values are translated by ConfigObject.put in the Android app.
    return {kind: {canonical(key) for key in values} | values for kind, values in keys.items()}


def section_name(title: str) -> str:
    return title.replace("\n", " ").replace("\r", " ")


def section_text(category: str, title: str, values: dict[str, str]) -> str:
    rows = [f"[{category}:{section_name(title)}]"]
    rows.extend(f"{key} = {value.replace(chr(10), r'\n')}" for key, value in values.items() if value != "")
    return "\n".join(rows) + "\n\n"


def write_vendor(
    source: Path,
    output: Path,
    whitelist: dict[str, set[str]],
    shared_profiles: dict[str, dict[str, dict[str, Any]]],
) -> tuple[int, int, int, set[str]]:
    manifest = load_json(source)
    vendor = manifest["name"]
    folder = source.parent / vendor
    category_map = {"machine": "machine_list", "process": "process_list", "filament": "filament_list"}
    listed: dict[str, list[tuple[str, Path]]] = defaultdict(list)
    models: list[tuple[str, Path]] = []
    for item in manifest.get("machine_model_list", []):
        path = folder / item["sub_path"]
        if path.is_file():
            models.append((item["name"], path))
    for kind, list_key in category_map.items():
        for item in manifest.get(list_key, []):
            path = folder / item["sub_path"]
            if path.is_file():
                listed[kind].append((item["name"], path))

    # Index all source presets for resolving inherited system/common presets.
    by_title: dict[str, dict[str, dict[str, Any]]] = {kind: dict(shared_profiles.get(kind, {})) for kind in category_map}
    for kind in category_map:
        category_dir = folder / kind
        if not category_dir.exists():
            continue
        for path in category_dir.rglob("*.json"):
            try:
                raw = load_json(path)
            except (OSError, json.JSONDecodeError):
                continue
            title = raw.get("name") or raw.get({"machine": "printer_settings_id", "process": "print_settings_id", "filament": "filament_settings_id"}[kind])
            if title:
                by_title[kind][str(title)] = raw

    misses: set[str] = set()
    resolving: set[tuple[str, str]] = set()
    cache: dict[tuple[str, str], dict[str, str]] = {}

    def flatten(kind: str, title: str, raw: dict[str, Any]) -> dict[str, str]:
        cache_key = (kind, title)
        if cache_key in cache:
            return cache[cache_key]
        if cache_key in resolving:
            misses.add(f"{vendor}/{kind}/{title}: inheritance cycle")
            return {}
        resolving.add(cache_key)
        result: dict[str, str] = {}
        inherited = raw.get("inherits", "")
        if inherited:
            for parent in str(inherited).split(";"):
                parent = parent.strip()
                parent_raw = by_title[kind].get(parent)
                if parent_raw is None:
                    # Orca uses system-profile aliases with vendor qualifiers in a few bundles.
                    candidates = [name for name in by_title[kind] if name.endswith(parent) or parent.endswith(name)]
                    if len(candidates) == 1:
                        parent_raw = by_title[kind][candidates[0]]
                        parent = candidates[0]
                if parent_raw is None:
                    misses.add(f"{vendor}/{kind}/{title}: {parent}")
                    continue
                result.update(flatten(kind, parent, parent_raw))
        for key, value in raw.items():
            if key in ("inherits", "type", "from", "name", "setting_id", "instantiation", "printer_settings_id", "print_settings_id", "filament_settings_id"):
                continue
            mapped = {"chamber_temperatures": "chamber_temperature"}.get(key, key)
            # ConfigObject.put applies the same key migration and aliases used by profile import.
            for legacy, current in re.findall(r'put\("([^"]+)", "([^"]+)"\)', CONFIG_MIGRATION):
                if legacy == mapped:
                    mapped = current
                    break
            for alias, current in re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', CONFIG_ALIASES):
                if mapped == alias:
                    mapped = current
                    break
            if mapped not in whitelist[kind]:
                continue
            text = json_value(value, mapped)
            if mapped in ("start_filament_gcode", "end_filament_gcode", "start_gcode", "end_gcode"):
                text = text.replace("nozzle_temperature_initial_layer", "first_layer_temperature")
                text = text.replace("bed_temperature_initial_layer_single", "first_layer_bed_temperature")
            result[mapped] = text
        resolving.remove(cache_key)
        cache[cache_key] = result
        return result

    pieces = ["[vendor]\n" + f"name = {vendor}\nversion = {manifest.get('version', '')}\ndescription = {manifest.get('description', '')}\n\n"]
    for title, path in models:
        raw = load_json(path)
        values = {key: json_value(value, key) for key, value in raw.items() if key not in ("type", "sub_path") and value is not None}
        values.setdefault("name", title)
        pieces.append(section_text("printer_model", title, values))

    counts = {"machine": 0, "process": 0, "filament": 0}
    models_by_machine = {}
    for kind in category_map:
        for title, path in listed[kind]:
            raw = load_json(path)
            actual_title = str(raw.get("name") or title)
            values = flatten(kind, actual_title, raw)
            # Restore the title in settings expected by the original importer.
            values[{"machine": "printer_settings_id", "process": "print_settings_id", "filament": "filament_settings_id"}[kind]] = actual_title
            pieces.append(section_text({"machine": "printer", "process": "print", "filament": "filament"}[kind], actual_title, values))
            counts[kind] += 1
            if kind == "machine":
                model = values.get("printer_model")
                if model:
                    models_by_machine[model] = output.name

    output.write_text("".join(pieces), encoding="utf-8", newline="\n")
    return len(models), counts["machine"], counts["process"] + counts["filament"], misses


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_profiles", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--source-revision", required=True)
    parser.add_argument("--archive-legacy", action="store_true")
    args = parser.parse_args()
    source = args.source_profiles.resolve()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if args.archive_legacy:
        archive = output / "legacy_factory_profiles.zip"
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            for path in sorted(output.glob("*.ini")):
                zf.write(path, f"{path.name}")
        print(f"Legacy INIs archived: {archive} ({archive.stat().st_size:,} bytes)")

    java_root = Path("app/src/main/java/ru/ytkab0bp/slicebeam")
    global CONFIG_MIGRATION, CONFIG_ALIASES
    config_source = (java_root / "config/ConfigObject.kt").read_text(encoding="utf-8")
    migration_start = config_source.index("private fun buildKeyMigration()")
    migration_end = config_source.index("private fun buildReverse()", migration_start)
    CONFIG_MIGRATION = config_source[migration_start:migration_end]
    alias_start = config_source.index("private val ORCA_KEY_ALIASES")
    alias_end = config_source.index("private val PRINT_CONFIG_KEY_ALIASES", alias_start)
    CONFIG_ALIASES = config_source[alias_start:alias_end]
    whitelist = supported_keys(java_root)

    manifests = sorted(
        path for path in source.glob("*.json")
        if any(load_json(path).get(key) is not None for key in ("machine_model_list", "machine_list", "process_list", "filament_list"))
    )
    shared_profiles: dict[str, dict[str, dict[str, Any]]] = {"machine": {}, "process": {}, "filament": {}}
    library = source / "OrcaFilamentLibrary.json"
    if library.is_file():
        library_json = load_json(library)
        library_root = source / library_json["name"]
        for kind, list_key in (("machine", "machine_list"), ("process", "process_list"), ("filament", "filament_list")):
            for item in library_json.get(list_key, []):
                profile_path = library_root / item["sub_path"]
                if not profile_path.is_file():
                    continue
                raw = load_json(profile_path)
                title = raw.get("name") or raw.get({"machine": "printer_settings_id", "process": "print_settings_id", "filament": "filament_settings_id"}[kind])
                if title:
                    shared_profiles[kind][str(title)] = raw
    model_index: dict[str, str] = {}
    totals = {"vendors": 0, "models": 0, "machines": 0, "presets": 0}
    generated_ini_names: set[str] = set()
    generated_ini_names_casefolded: set[str] = set()
    unresolved: set[str] = set()
    for manifest_path in manifests:
        vendor = load_json(manifest_path)["name"]
        filename = vendor.replace("/", "_") + ".ini"
        # Windows builds cannot package asset names that differ only by case
        # (for example upstream's RatRig and Ratrig vendor manifests).
        suffix = 2
        while filename.casefold() in generated_ini_names_casefolded:
            filename = f"{vendor.replace('/', '_')}-{suffix}.ini"
            suffix += 1
        output_path = output / filename
        models, machines, presets, misses = write_vendor(manifest_path, output_path, whitelist, shared_profiles)
        generated_ini_names.add(filename)
        generated_ini_names_casefolded.add(filename.casefold())
        manifest = load_json(manifest_path)
        for model in manifest.get("machine_model_list", []):
            model_index[model["name"]] = filename
        unresolved.update(misses)
        totals["vendors"] += 1
        totals["models"] += models
        totals["machines"] += machines
        totals["presets"] += presets
    (output / "model_vendor_index.json").write_text(json.dumps(model_index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "UPSTREAM_VERSION.txt").write_text(
        f"OrcaSlicer profiles main @ {args.source_revision}\n", encoding="utf-8"
    )
    # Delete only superseded vendor INIs from the generated bundle. Keep app-owned support assets.
    for path in output.glob("*.ini"):
        if path.name not in generated_ini_names:
            path.unlink()
    print("Generated", totals)
    print("Supported keys", {k: len(v) for k, v in whitelist.items()})
    print("Unresolved inheritance", len(unresolved))
    for issue in sorted(unresolved)[:30]:
        print("  ", issue)
    print("Asset INI bytes", sum(path.stat().st_size for path in output.glob("*.ini")))
    print("Model index entries", len(model_index))


if __name__ == "__main__":
    main()
