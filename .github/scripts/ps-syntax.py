"""Sammelt alle PowerShell-Schritte aus .github/workflows/*.yml für die Syntaxprüfung (ps-syntax.ps1).

Hintergrund: Ein Release-Schritt lief nur bei Tags und brach dort zweimal an einem ParserError ab
(typografische Anführungszeichen in einem String, die PowerShell als Begrenzer liest). Die Prüfung läuft
deshalb bei jedem Push und PR für alle Schritte, auch für die, die nur bei Tags oder auf main laufen.
"""
import glob
import json
import sys

import yaml

out = []
for path in sorted(glob.glob(".github/workflows/*.yml")):
    with open(path, encoding="utf-8") as f:
        doc = yaml.safe_load(f)
    default = ((doc.get("defaults") or {}).get("run") or {}).get("shell")
    for job_name, job in (doc.get("jobs") or {}).items():
        job_default = ((job.get("defaults") or {}).get("run") or {}).get("shell") or default
        windows = "windows" in str(job.get("runs-on", ""))
        for i, step in enumerate(job.get("steps") or []):
            if "run" not in step:
                continue
            shell = step.get("shell") or job_default or ("pwsh" if windows else "bash")
            if shell in ("pwsh", "powershell"):
                out.append({"name": f"{path}:{job_name}:{step.get('name', i)}", "code": step["run"]})

json.dump(out, sys.stdout)
