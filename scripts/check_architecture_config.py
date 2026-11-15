#!/usr/bin/env python3
"""P0-00-3：架构配置静态检查（CI 可执行）。"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SERVICE_DIRS = [
    "annuity-service", "approval-service", "auth-service", "file-service",
    "integration-service", "intranet-bff", "internet-bff",
]
FAILS: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)


def annotation_body(text: str, name: str) -> list[str]:
    """Extract balanced parentheses bodies of @Name(...) occurrences."""
    bodies = []
    for match in re.finditer(rf"@{name}\s*\(", text):
        depth = 1
        i = match.end()
        in_str = None
        while i < len(text) and depth:
            ch = text[i]
            if in_str:
                if ch == "\\":
                    i += 2
                    continue
                if ch == in_str:
                    in_str = None
            elif ch in "\"'":
                in_str = ch
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        bodies.append(text[match.end():i - 1])
    return bodies


# 1. starter 不得携带 application.yml
for f in (ROOT / "demo-shared/shared-starter").rglob("application*.y*ml"):
    if f.parent.name == "resources":
        fail(f"starter 携带配置文件: {f.relative_to(ROOT)}")

# 2/3. 宿主不得扫描 kernel 包 / mapper
for service in SERVICE_DIRS:
    for f in (ROOT / service).rglob("*.java"):
        if "src/main/java" not in str(f):
            continue
        text = f.read_text(encoding="utf-8")
        for body in annotation_body(text, "SpringBootApplication"):
            if "com.example.core" in body:
                fail(f"宿主扫描 kernel 包: {f.relative_to(ROOT)}")
        for body in annotation_body(text, "MapperScan"):
            if "com.example.core" in body:
                fail(f"宿主扫描 kernel mapper: {f.relative_to(ROOT)}")

# 4. shared-web-core 不得重新引入 sa-token
web_pom = (ROOT / "demo-shared/shared-core/shared-web-core/pom.xml").read_text()
if re.search(r"<artifactId>sa-token", web_pom):
    fail("shared-web-core 重新引入 sa-token")

# 5. 非 local 配置不得关闭权限强制
for service in SERVICE_DIRS:
    for f in (ROOT / service).rglob("*.y*ml"):
        if "application-local" in f.name:
            continue
        text = f.read_text(encoding="utf-8")
        if re.search(r"permission\.enforcement\.enabled:\s*false", text):
            fail(f"非本地配置关闭权限强制: {f.relative_to(ROOT)}")

# 6. 对外 API / 纯业务领域层不得反向依赖 kernel 的强类型 ID（AU-02/F-03/P-02）
CORE_TYPES_FREE_POMS = {
    "auth-service/auth-api/pom.xml": "AU-02",
    "file-service/file-api/pom.xml": "F-03",
    "approval-service/approval-domain/pom.xml": "P-02",
}
for pom, task in CORE_TYPES_FREE_POMS.items():
    text = (ROOT / pom).read_text(encoding="utf-8")
    if re.search(r"<artifactId>business-core-types</artifactId>", text):
        fail(f"{task}: {pom} 重新引入 business-core-types")

if FAILS:
    for msg in FAILS:
        print(f"[FAIL] {msg}")
    sys.exit(1)
print("[OK] 架构配置检查通过")
