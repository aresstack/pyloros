#!/usr/bin/env python3
"""Probe whether Caddy forwards a large MCP-sized JSON response byte-exactly.

Round 5 of the "Error in input stream" investigation. Pyloros itself was shown to
deliver a 686 KB tools/list response complete and parseable; this checks the next
hop, the reverse proxy, under the conditions that could plausibly truncate it.

Everything here is isolated. A throwaway Caddyfile on free ports, plain HTTP (no
TLS, so no ACME and no certificate store is touched), and CADDY_* / XDG_* data
directories pointed at a temp dir. The production Caddyfile, its data directory
and the running instance are never read for configuration or modified.

Not runnable in CI: it needs a caddy binary. Pass its path with --caddy, or let
the script find the one the aresstack webserver downloaded.

Scenarios
  full-421795     the exact body size measured on the live instance
  full-686399     the larger body produced by ToolsListResponseSizeTest
  truncated       upstream announces Content-Length but sends half, then closes
  delayed-chunks  upstream streams the body slowly in chunks
  concurrent      N simultaneous requests for the large body

For each, the body is fetched directly from the upstream and through Caddy and
the two are compared by SHA-256, so "forwarded intact" is a byte-level claim.
"""

from __future__ import annotations

import argparse
import hashlib
import http.server
import json
import os
import socket
import socketserver
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

LIVE_BODY_BYTES = 421_795
TEST_BODY_BYTES = 686_399


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def build_payload(target_bytes: int) -> bytes:
    """A JSON-RPC tools/list envelope padded to exactly `target_bytes` UTF-8 bytes."""
    envelope = {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {"tools": [{"name": "probe__tool", "description": "", "inputSchema": {"type": "object"}}]},
    }
    # Unicode on purpose: the live catalog had non-ASCII in 126 of 259 tools.
    unit = "Grüße — 😀 "
    encoded = unit.encode("utf-8")
    base = len(json.dumps(envelope, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
    repeats = max(0, (target_bytes - base) // len(encoded))
    envelope["result"]["tools"][0]["description"] = unit * repeats
    body = json.dumps(envelope, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    # Pad with ASCII to land exactly on the requested size.
    shortfall = target_bytes - len(body)
    if shortfall > 0:
        envelope["result"]["tools"][0]["description"] += "x" * shortfall
        body = json.dumps(envelope, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return body


class Upstream(http.server.BaseHTTPRequestHandler):
    """Controlled upstream. The requested path selects the fault to inject."""

    payloads: dict[str, bytes] = {}
    protocol_version = "HTTP/1.1"

    def log_message(self, *_args):  # silence per-request logging
        pass

    def do_POST(self):  # noqa: N802
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        self.respond()

    def do_GET(self):  # noqa: N802
        self.respond()

    def respond(self):
        route = self.path.rstrip("/").split("/")[-1]

        if route == "truncated":
            body = self.payloads["large"]
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            # Announce the full length, deliver half, then drop the connection.
            self.wfile.write(body[: len(body) // 2])
            self.wfile.flush()
            self.close_connection = True
            try:
                self.connection.close()
            except OSError:
                pass
            return

        if route == "abort":
            body = self.payloads["large"]
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
            # Two valid chunks, then the connection dies without a terminating chunk.
            step = len(body) // 4
            for offset in (0, step):
                chunk = body[offset : offset + step]
                self.wfile.write(f"{len(chunk):X}\r\n".encode())
                self.wfile.write(chunk)
                self.wfile.write(b"\r\n")
                self.wfile.flush()
            self.close_connection = True
            try:
                self.connection.close()
            except OSError:
                pass
            return

        if route == "invalid":
            # Complete and length-correct, but not parseable: isolates "transfer intact"
            # from "content valid".
            body = b'{"jsonrpc":"2.0","id":1,"result":{"tools":[' + b"x" * 4096
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            self.wfile.flush()
            return

        if route == "delayed":
            body = self.payloads["large"]
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
            chunk_count = 10
            step = len(body) // chunk_count + 1
            for offset in range(0, len(body), step):
                chunk = body[offset : offset + step]
                self.wfile.write(f"{len(chunk):X}\r\n".encode())
                self.wfile.write(chunk)
                self.wfile.write(b"\r\n")
                self.wfile.flush()
                time.sleep(0.15)
            self.wfile.write(b"0\r\n\r\n")
            self.wfile.flush()
            return

        body = self.payloads["live" if route == "live" else "large"]
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def start_upstream(port: int, payloads: dict[str, bytes]) -> ThreadedHTTPServer:
    Upstream.payloads = payloads
    server = ThreadedHTTPServer(("127.0.0.1", port), Upstream)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def find_caddy(explicit: str | None) -> str:
    if explicit:
        return explicit
    candidates = [
        Path(r"D:\OneDrive\Projects\runtime\caddy\bin\caddy.exe"),
        Path("/usr/bin/caddy"),
        Path("/usr/local/bin/caddy"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return str(candidate)
    raise SystemExit("caddy binary not found; pass --caddy <path>")


def start_caddy(caddy: str, listen_port: int, upstream_port: int, workdir: Path) -> subprocess.Popen:
    caddyfile = workdir / "Caddyfile"
    admin_port = free_port()
    # Plain HTTP on an explicit port: no automatic TLS, so no ACME traffic and no
    # certificate storage. `auto_https off` makes that explicit.
    caddyfile.write_text(
        "{\n"
        f"    admin 127.0.0.1:{admin_port}\n"
        "    auto_https off\n"
        f"    storage file_system {json.dumps(str(workdir / 'data'))}\n"
        "}\n\n"
        f"http://127.0.0.1:{listen_port} {{\n"
        f"    reverse_proxy 127.0.0.1:{upstream_port}\n"
        "}\n",
        encoding="utf-8",
    )

    environment = dict(os.environ)
    environment["XDG_DATA_HOME"] = str(workdir / "xdg-data")
    environment["XDG_CONFIG_HOME"] = str(workdir / "xdg-config")
    process = subprocess.Popen(
        [caddy, "run", "--config", str(caddyfile), "--adapter", "caddyfile"],
        cwd=str(workdir),
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return process


def wait_until_listening(port: int, timeout: float = 20.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        with socket.socket() as sock:
            sock.settimeout(0.5)
            if sock.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(0.1)
    raise SystemExit(f"nothing listening on port {port} after {timeout}s")


def fetch(url: str, timeout: float = 60.0) -> dict:
    request = urllib.request.Request(
        url,
        data=b'{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}',
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.time()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
            return {
                "ok": True,
                "status": response.status,
                "bytes": len(body),
                "sha256": hashlib.sha256(body).hexdigest(),
                "content_length": response.headers.get("Content-Length"),
                "transfer_encoding": response.headers.get("Transfer-Encoding"),
                "content_type": response.headers.get("Content-Type"),
                "parses": parses(body),
                "seconds": round(time.time() - started, 3),
            }
    except Exception as error:  # noqa: BLE001 - the failure mode is the result
        return {
            "ok": False,
            "error": f"{type(error).__name__}: {error}",
            "seconds": round(time.time() - started, 3),
        }


def parses(body: bytes) -> bool:
    try:
        json.loads(body)
        return True
    except Exception:  # noqa: BLE001
        return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--caddy", default=None, help="path to the caddy binary")
    parser.add_argument("--concurrency", type=int, default=10)
    arguments = parser.parse_args()

    caddy = find_caddy(arguments.caddy)
    payloads = {"live": build_payload(LIVE_BODY_BYTES), "large": build_payload(TEST_BODY_BYTES)}
    assert len(payloads["live"]) == LIVE_BODY_BYTES, len(payloads["live"])
    assert len(payloads["large"]) == TEST_BODY_BYTES, len(payloads["large"])

    upstream_port = free_port()
    proxy_port = free_port()
    upstream = start_upstream(upstream_port, payloads)
    wait_until_listening(upstream_port)

    workdir = Path(tempfile.mkdtemp(prefix="caddy-probe-"))
    caddy_process = start_caddy(caddy, proxy_port, upstream_port, workdir)

    failures = 0
    try:
        wait_until_listening(proxy_port)
        direct = f"http://127.0.0.1:{upstream_port}"
        proxied = f"http://127.0.0.1:{proxy_port}"

        print(f"caddy={caddy}")
        print(f"upstream=127.0.0.1:{upstream_port} proxy=127.0.0.1:{proxy_port} workdir={workdir}\n")

        for label, route, expected in (
            ("full-421795", "live", LIVE_BODY_BYTES),
            ("full-686399", "large", TEST_BODY_BYTES),
        ):
            straight = fetch(f"{direct}/{route}")
            through = fetch(f"{proxied}/{route}")
            identical = straight.get("sha256") == through.get("sha256")
            intact = through.get("bytes") == expected and through.get("parses") and identical
            failures += 0 if intact else 1
            print(f"[{label}] expected={expected}")
            print(f"    direct : {straight}")
            print(f"    caddy  : {through}")
            print(f"    byte-identical={identical} intact={intact}\n")

        straight = fetch(f"{direct}/truncated")
        through = fetch(f"{proxied}/truncated")
        # The point is that neither hop may present a short body as a complete one.
        direct_rejected = not straight.get("ok") or straight.get("bytes") != TEST_BODY_BYTES
        proxy_rejected = not through.get("ok") or through.get("bytes") != TEST_BODY_BYTES
        failures += 0 if (direct_rejected and proxy_rejected) else 1
        print("[truncated] upstream announces full length, sends half, closes")
        print(f"    direct : {straight}")
        print(f"    caddy  : {through}")
        print(f"    direct_rejected={direct_rejected} proxy_rejected={proxy_rejected}\n")

        straight = fetch(f"{direct}/delayed")
        through = fetch(f"{proxied}/delayed")
        intact = through.get("bytes") == TEST_BODY_BYTES and through.get("parses")
        failures += 0 if intact else 1
        print("[delayed-chunks] upstream streams 10 chunks, 150ms apart")
        print(f"    direct : {straight}")
        print(f"    caddy  : {through}")
        print(f"    intact={intact}\n")

        straight = fetch(f"{direct}/abort")
        through = fetch(f"{proxied}/abort")
        direct_rejected = not straight.get("ok")
        proxy_rejected = not through.get("ok")
        failures += 0 if (direct_rejected and proxy_rejected) else 1
        print("[aborted-midstream] chunked, connection dies without a terminating chunk")
        print(f"    direct : {straight}")
        print(f"    caddy  : {through}")
        print(f"    direct_rejected={direct_rejected} proxy_rejected={proxy_rejected}\n")

        straight = fetch(f"{direct}/invalid")
        through = fetch(f"{proxied}/invalid")
        # Transfer must succeed and parsing must fail: the two are separate concerns.
        separated = (
            through.get("ok")
            and through.get("bytes") == straight.get("bytes")
            and through.get("parses") is False
        )
        failures += 0 if separated else 1
        print("[complete-but-invalid-json] body arrives whole, content is not JSON")
        print(f"    direct : {straight}")
        print(f"    caddy  : {through}")
        print(f"    transferIntactParseFails={separated}\n")

        with ThreadPoolExecutor(max_workers=arguments.concurrency) as pool:
            results = list(pool.map(lambda _: fetch(f"{proxied}/large"), range(arguments.concurrency)))
        complete = sum(1 for result in results if result.get("bytes") == TEST_BODY_BYTES and result.get("parses"))
        digests = {result.get("sha256") for result in results}
        failures += 0 if complete == arguments.concurrency and len(digests) == 1 else 1
        print(f"[concurrent] {arguments.concurrency} simultaneous requests through caddy")
        print(f"    complete={complete}/{arguments.concurrency} distinctDigests={len(digests)}")
        print(f"    slowest={max(result.get('seconds', 0) for result in results)}s\n")

        print("RESULT:", "all scenarios intact" if failures == 0 else f"{failures} scenario(s) failed")
        return 1 if failures else 0
    finally:
        upstream.shutdown()
        caddy_process.terminate()
        try:
            caddy_process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            caddy_process.kill()


if __name__ == "__main__":
    sys.exit(main())
