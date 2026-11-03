#!/usr/bin/env python3
"""
nucleus_router.py — Plano de control NucleusFlow (lado PC).

El chat de la app NucleusFlow (Android) deja de ser un cartón: cada mensaje
se ENRUTA aquí, y de aquí a cada agente del ecosistema:

    ┌────────────┐   TCP 41050    ┌────────────── PC ──────────────┐
    │  Chat app  │ ─────────────► │ nucleus_router (este script)    │
    │  (móvil)   │ ◄───────────── │  ROUTER agent=X text=...       │
    └────────────┘   PUSH ...     │  POLL / SYNC / STATUS           │
                                   └───────┬──────────┬────────────┘
                                           │ inbox/    │ outbox/
                     hermes (Hermes, este PC, cron de bucle)
                     web    (Supabase bus: agentes ChatGPT/Grok/Dipsy)
                     windows (agentes locales futuros en el PC)
                     phone  (el propio móvil; lo resuelve local)

Protocolo (lineas \n, mismo estilo que AndroidComputeBridge):
    AUTH:<token>                 -> AUTH:OK  / ERR:AUTH
    PING                         -> PONG
    STATUS                       -> STATUS:agents=...|inbox=...|outbox=...
    ROUTER agent=<a> text=<t>   -> Routed: <a>|<estado>
    POLL                         -> [PUSH from=<a> text=<t>]*  o  POLL:EMPTY
    SYNC|<linea CHAT_MSG>       -> re-enrutado igual que ROUTER (cola del
                                   telefono tras un reinicio)

El token es el MISMO que el nodo de cómputo del teléfono
(EncryptedSharedPreferences "nucleus_compute_auth", lo ve ComputeScreen):
una sola llave para todo el plano. El usuario la pega UNA VEZ en
nucleus_router.json (misma práctica que el adb forward actual: la clave
nunca vive en el código, solo en los dispositivos del usuario).

Datos (misma carpeta, editables a mano; los agentes pueden leerlos libremente):
    inbox/<agente>/*.json   mensajes pendientes del agente  {id,ts,from,text,agent}
    inbox/<agente>/done/    procesados (arquivados por el consumidor)
    outbox/phone/*.json    respuestas del router hacia el móvil {id,ts,from,text}
    outbox/phone/sent/     enviadas (POLL)
    log/router.log

Sin dependencias: solo librería estándar de Python 3.
"""

import hmac
import json
import logging
import os
import re
import signal
import socket
import socketserver
import sys
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
# La ruta de la config se puede forzar por entorno (el test usa su propia
# nucleus_router.test.json para no pisar la config de produccion).
CONFIG_PATH = Path(os.environ.get("NUCLEUS_ROUTER_CONFIG") or (HERE / "nucleus_router.json"))

AGENTS = ("hermes", "web", "windows", "phone")
BROADCAST = "*"
MAX_LINE = 8192          # limite por linea (el texto viaja en una sola linea)
MAX_AUTH_FAILURES = 5
BLOCK_MS = 60_000

# ---------------------------------------------------------------- config ----

DEFAULT_CONFIG = {
    "port": 41050,
    "host": "0.0.0.0",
    "token": "",   # el usuario pega aqui el token del nodo Android (64 hex)
}


def load_config() -> dict:
    cfg = dict(DEFAULT_CONFIG)
    if CONFIG_PATH.exists():
        try:
            cfg.update(json.loads(CONFIG_PATH.read_text(encoding="utf-8")))
        except Exception as e:  # config roto -> arrancar con defectos y avisar
            print(f"[router] config ilegible ({e}); usando defectos", file=sys.stderr)
    return cfg


CFG = load_config()

# ------------------------------------------------------------------ paths ---

INBOX = HERE / "inbox"
OUTBOX = HERE / "outbox" / "phone"
LOG_DIR = HERE / "log"
for p in [INBOX, OUTBOX, LOG_DIR, *[INBOX / a for a in AGENTS], OUTBOX / "sent"]:
    p.mkdir(parents=True, exist_ok=True)
for a in AGENTS:
    (INBOX / a / "done").mkdir(parents=True, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    handlers=[logging.FileHandler(LOG_DIR / "router.log", encoding="utf-8"),
              logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger("nucleus-router")

_lock = threading.Lock()


def _next_id() -> int:
    with _lock:
        return int(time.time() * 1000)


def write_json(dirpath: Path, agent: str, msg: dict) -> Path:
    p = dirpath / f"{msg['id']}-{agent}.json"
    p.write_text(json.dumps(msg, ensure_ascii=False, indent=1), encoding="utf-8")
    return p


# ------------------------------------------------------------ routing core ---

def _agent_dir(agent: str) -> Path:
    return INBOX / agent


def route(agent: str, text: str, from_who: str, msg_id: int) -> tuple[str, str]:
    """Devuelve (respuesta_para_el_cliente, detalle)."""
    msg = {"id": msg_id, "ts": int(time.time()), "from": from_who,
           "agent": agent, "text": text}
    targets = [a for a in AGENTS if a == agent] if agent != BROADCAST else list(AGENTS)
    if not targets:
        return f"ERR:UNKNOWN_AGENT:{agent}", ""
    for a in targets:
        if a == "phone":
            # El agente "phone" lo resuelve el propio móvil (respuesta local);
            # el router solo lo confirma. No se archiva.
            continue
        write_json(_agent_dir(a), a, msg)
    detail = ",".join(sorted(set(targets)))
    if agent == BROADCAST:
        return f"ROUTED:{BROADCAST}|queued", detail
    if agent == "phone":
        return f"ROUTED:phone|local", "phone"
    return f"ROUTED:{agent}|queued", detail


def status_line() -> str:
    counts = {}
    for a in AGENTS:
        d = _agent_dir(a)
        n = sum(1 for f in d.glob("*.json"))
        counts[a] = n
    outbox_n = sum(1 for f in OUTBOX.glob("*.json"))
    return ("STATUS:agents=" + ",".join(AGENTS)
            + "|inbox=" + ",".join(f"{a}:{counts[a]}" for a in AGENTS)
            + f"|outbox={outbox_n}")


def poll_lines() -> list[str]:
    """Vacía la outbox en lineas PUSH (orden por id)."""
    lines = []
    files = sorted(OUTBOX.glob("*.json"), key=lambda f: f.name)
    for f in files:
        try:
            m = json.loads(f.read_text(encoding="utf-8"))
        except Exception:
            continue
        lines.append("PUSH from=%s text=%s" % (m.get("from", "?"), m.get("text", "")))
        sent = OUTBOX / "sent" / f.name
        try:
            f.replace(sent)
        except Exception as e:
            log.warning("outbox move failed: %s", e)
    return lines


# -------------------------------------------------------------- handlers ---

def handle_line(conn: "RouterHandler", line: str) -> list[str]:
    out = []
    if line.startswith("AUTH:"):
        given = line[len("AUTH:"):]
        if CFG.get("token") and hmac.compare_digest(
                given.encode(), str(CFG["token"]).encode()):
            conn.authed = True
            conn.failures = 0
            out.append("AUTH:OK")
        else:
            conn.failures += 1
            if conn.failures >= MAX_AUTH_FAILURES:
                conn.blocked_until = time.time() * 1000 + BLOCK_MS
                out.append("ERR:AUTH")
                conn.close_after = True
            else:
                out.append(f"ERR:AUTH:{conn.failures}")
        return out

    if not conn.authed:
        return ["ERR:AUTH_REQUIRED"]
    if time.time() * 1000 < conn.blocked_until:
        return ["ERR:TOO_MANY_FAILURES"]

    if line == "PING":
        out.append("PONG")
    elif line == "STATUS":
        out.append(status_line())
    elif line == "POLL":
        ps = poll_lines()
        out.extend(ps)
        # Marcador final: el cliente (el movil) sabe cuando acaba el lote.
        out.append("POLL:END" if ps else "POLL:EMPTY")
    elif line.startswith("ROUTER agent="):
        m = re.match(r"ROUTER agent=([^\s|]+)\s+text=(.*)$", line, re.S)
        if not m:
            out.append("ERR:ROUTER_FORMAT")
        else:
            agent, text = m.group(1), m.group(2)
            reply, _ = route(agent, text, "phone", _next_id())
            out.append(reply)
    elif line.startswith("SYNC|"):
        # Cola del telefono: lineas CHAT_MSG|from=..|agent=..|text=..
        body = line[len("SYNC|"):]
        m = re.match(r"CHAT_MSG\|from=[^|]*\|agent=([^\||]+)\|text=(.*)$", body, re.S)
        if not m:
            out.append("ERR:SYNC_FORMAT")
        else:
            reply, _ = route(m.group(1), m.group(2), "phone", _next_id())
            out.append(reply)
    else:
        out.append("UNKNOWN_COMMAND")
    return out


class RouterHandler(socketserver.BaseRequestHandler):
    """Conexion de un cliente (el telefono)."""
    authed = False
    close_after = False

    def setup(self):
        self.authed = False
        self.failures = 0
        self.blocked_until = 0
        self.close_after = False
        self.peer = self.client_address

    def handle(self):
        log.info("client from %s", self.peer)
        rbuf = b""
        try:
            while not self.close_after:
                chunk = self.request.recv(65536)
                if not chunk:
                    break
                rbuf += chunk
                while b"\n" in rbuf:
                    line, rbuf = rbuf.split(b"\n", 1)
                    line = line.decode("utf-8", "replace").rstrip("\r")
                    if len(line) > MAX_LINE:
                        self.reply("ERR:LINE_TOO_LONG")
                        self.close_after = True
                        continue
                    if not line:
                        continue
                    self.reply("\n".join(handle_line(self, line)))
        except OSError:
            pass
        finally:
            log.info("client closed %s (authed=%s)", self.peer, self.authed)

    def reply(self, text: str):
        try:
            self.request.sendall(text.encode("utf-8") + b"\n")
        except OSError:
            self.close_after = True


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    if len(sys.argv) > 1:
        doc = (__doc__ or "").split("Protocolo")[0].strip()
        print(doc)
        print("Uso:  python nucleus_router.py   (sin argumentos; config en nucleus_router.json)")
        return
    port = int(CFG.get("port", 41050))
    host = str(CFG.get("host", "0.0.0.0"))
    if not CFG.get("token"):
        log.warning("SIN token en %s: el router rechaza todo AUTH "
                    "(pega el token del nodo Android y relanza).", CONFIG_PATH.name)
    srv = Server((host, port), RouterHandler)
    log.info("nucleus_router escuchando en %s:%d (agentes: %s)",
             host, port, ",".join(AGENTS))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        log.info("nucleus_router detenido")


if __name__ == "__main__":
    main()
