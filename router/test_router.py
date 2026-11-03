#!/usr/bin/env python3
"""
test_router.py — loopback del plano de control NucleusFlow.

Simula los dos extremos contra un nucleus_router.py REAL (arrancado en
background por este script, en un puerto de prueba y con un token de prueba):

  1. Falso movil:  AUTH + STATUS + ROUTER agent=hermes + ROUTER agent=web
                    + ROUTER agent=phone + SYNC (cola) + PING
  2. Falso hermes: lee inbox/hermes/*.json, escribe respuesta en outbox/phone
  3. Falso movil:  POLL -> debe recibir la PUSH de hermes
  4. Falso web:    el router debe haber archivado el task en inbox/web

Verifica respuestas linea a linea y archivos en disco. Sale con exit 0 si todo
pasa; imprime lo que ve en cada paso (tambien sirve como demo del protocolo).
"""
import json
import os
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
PORT = 41099  # puerto de prueba, no colisiona con nada
TOKEN = "f" * 64

FAILURES = []


def check(label: str, ok: bool, detail=""):
    mark = "ok  " if ok else "FAIL"
    print(f"  [{mark}] {label}" + (f" — {detail}" if detail and not ok else ""))
    if not ok:
        FAILURES.append(label)


class Client:
    """Cliente de linea minimalista del protocolo router."""

    def __init__(self, port: int, name: str = "phone"):
        self.name = name
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=10)
        self.buf = b""

    def send(self, line: str):
        self.sock.sendall(line.encode() + b"\n")

    def read_until(self, prefix: str, timeout: float = 10.0) -> str:
        """Lee lineas hasta una que empieza por prefix; devuelve esa linea."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            while b"\n" in self.buf:
                line, self.buf = self.buf.split(b"\n", 1)
                text = line.decode("utf-8", "replace")
                if text.startswith(prefix):
                    return text
            chunk = self.sock.recv(65536)
            if not chunk:
                raise RuntimeError(f"{self.name}: conexion cerrada por el router")
            self.buf += chunk
        raise TimeoutError(f"{self.name}: sin {prefix} en {timeout}s")

    def has_push_pending(self) -> bool:
        """Preguntar (sin consumir) si hay una linea PUSH ya recibida."""
        if b"\n" not in self.buf:
            try:
                deadline = time.time() + 2
                while time.time() < deadline and b"\n" not in self.buf:
                    chunk = self.sock.recv(65536)
                    if not chunk:
                        return False
                    self.buf += chunk
            except socket.timeout:
                pass
        if b"\n" not in self.buf:
            return False
        first = self.buf.split(b"\n", 1)[0].decode("utf-8", "replace")
        return first.startswith("PUSH")

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def test_bootstrap():
    """== 0) Bootstrap: config sin token + origen permitido => el nodo
    Android ESTABLECE el token (AUTH:OK:BOOTSTRAP). Configura un fichero
    de config propio (nucleus_router.boot.json) y un router en 41100."""
    print("== 0) Bootstrap: el nodo Android establece el token ==")
    bport = 41100
    bcfg = HERE / "nucleus_router.boot.json"
    boot_tok = ("a1b2" * 16)  # 64 hex
    if not boot_tok[0].islower() or len(boot_tok) != 64:
        raise RuntimeError("token de prueba malformado")
    # Estado fresco cada vez que corre el test (sobreescribe re-execuciones).
    bcfg.write_text(json.dumps({"port": bport, "host": "127.0.0.1",
                                "token": "", "bootstrap_ips": ["127.0.0.1"]}))
    os.environ["NUCLEUS_ROUTER_CONFIG"] = str(bcfg)

    import socket as _socket
    try:
        _p = _socket.socket()
        _p.bind(("127.0.0.1", bport))
        _p.close()
    except OSError as e:
        print(f"ABORT: puerto {bport} ocupado ({e}); mata el zombi y relanza.")
        sys.exit(2)

    dbg = open(HERE / "router-boot-stdout.txt", "wb")
    router = subprocess.Popen(
        [sys.executable, str(HERE / "nucleus_router.py")],
        stdout=dbg, stderr=subprocess.STDOUT, cwd=str(HERE),
    )
    try:
        time.sleep(1.2)
        if router.poll() is not None:
            print("ABORT: router bootstrap murio: ----\n"
                  + (HERE / "router-boot-stdout.txt").read_text(
                      encoding="utf-8", errors="replace"))
            sys.exit(2)

        ph = Client(bport, "boot-phone")
        ph.send("AUTH:123")  # malformado (no 64 hex) desde origen permitido
        check("bootstrap: token malformado rechazado",
              ph.read_until("ERR:").startswith("ERR:AUTH"))
        ph.send("AUTH:" + boot_tok)
        r = ph.read_until("AUTH:OK")
        check("bootstrap aplicado (AUTH:OK:BOOTSTRAP)", r == "AUTH:OK:BOOTSTRAP", r)
        ondisk = json.loads(bcfg.read_text())
        check("token persistido en config", ondisk.get("token") == boot_tok)
        ph.send("STATUS")
        check("cliente autenticado tras bootstrap",
              ph.read_until("STATUS:") != "")
        ph.close()

        # --- comprobaciones unitarias de bootstrap_token, en un HIJO: el
        # import del modulo deja un FileHandler abierto en log/router.log
        # dentro de ESTE proceso y en Windows bloquearia el rmtree de main().
        unit_src = (
            "import importlib.util, json\n"
            "spec = importlib.util.spec_from_file_location('m', %s)\n"
            "mod = importlib.util.module_from_spec(spec)\n"
            "spec.loader.exec_module(mod)\n"
            "a = mod.bootstrap_token('0' * 64, '127.0.0.1')\n"
            "mod.CFG['token'] = ''\n"
            "b = mod.bootstrap_token('0' * 64, '8.8.8.8')\n"
            "c = mod.bootstrap_token('xyz', '127.0.0.1')\n"
            "print(json.dumps([a, b, c]))\n"
        ) % json.dumps(str(HERE / "nucleus_router.py"))
        up = subprocess.run([sys.executable, "-c", unit_src],
                           capture_output=True, text=True, timeout=60)
        try:
            unit_res = json.loads(up.stdout.strip().splitlines()[-1])
        except Exception:
            unit_res = None
        detail = (up.stdout or up.stderr)[-200:]
        check("unit: token ya puesto => bootstrap no",
              unit_res is not None and unit_res[0] is False, detail)
        check("unit: origen no permitido => no",
              unit_res is not None and unit_res[1] is False)
        check("unit: formato no 64hex => no",
              unit_res is not None and unit_res[2] is False)
    finally:
        router.terminate()
        try:
            router.wait(timeout=5)
        except subprocess.TimeoutExpired:
            router.kill()
        dbg.close()
    # dejar bcfg con token vacio para que no interfiera en la fase principal
    bcfg.write_text(json.dumps({"port": bport, "host": "127.0.0.1",
                                "token": "", "bootstrap_ips": []}))


def main():
    test_bootstrap()
    # --- limpiar datos de pruebas anteriores
    data = HERE / "test-data"
    if data.exists():
        shutil.rmtree(data)
    for sub in ("inbox", "outbox", "log"):
        p = HERE / sub
        if p.exists():
            shutil.rmtree(p)

    # --- config de prueba (fichero propio: no se pisa la de produccion)
    test_cfg = HERE / "nucleus_router.test.json"
    cfg = {"port": PORT, "host": "127.0.0.1", "token": TOKEN}
    test_cfg.write_text(json.dumps(cfg, indent=1))
    os.environ["NUCLEUS_ROUTER_CONFIG"] = str(test_cfg)

    # --- arrancar router real
    # --- arrancar router real (con guardas: puerto libre + el hijo debe vivir)
    # Pre-check: si otro proceso ya ocupa el puerto, el test se conectaria al
    # ZOMBI equivocado y los datos irian a otro directorio (fallos espurios).
    import socket as _socket
    try:
        _probe = _socket.socket()
        _probe.bind(("127.0.0.1", PORT))
        _probe.close()
    except OSError as e:
        print(f"ABORT: el puerto {PORT} ya lo ocupa otro proceso ({e}); "
              f"mata el router zombi y relanza el test.")
        sys.exit(2)
    dbg_file = open(HERE / "router-stdout.txt", "wb")
    router = subprocess.Popen(
        [sys.executable, str(HERE / "nucleus_router.py")],
        stdout=dbg_file, stderr=subprocess.STDOUT, cwd=str(HERE),
    )
    try:
        time.sleep(1.2)
        if router.poll() is not None:
            print("ABORT: el router murio al arrancar. Su salida: ----\n"
                  + (HERE / "router-stdout.txt").read_text(encoding="utf-8", errors="replace"))
            sys.exit(2)

        print("== 1) Falso movil: AUTH + STATUS ==")
        ph = Client(PORT, "phone")
        ph.send("AUTH:" + "x")  # token malo a proposito
        check("token malo rechazado", ph.read_until("ERR:").startswith("ERR:AUTH"))
        ph.send("AUTH:" + TOKEN)
        check("AUTH ok", ph.read_until("AUTH:OK") == "AUTH:OK")
        ph.send("STATUS")
        st = ph.read_until("STATUS:")
        check("STATUS lista agentes", "hermes" in st and "web" in st, st)

        print("== 2) Falso movil: enrutado de mensajes ==")
        ph.send("PING")
        check("PING -> PONG", ph.read_until("PONG") == "PONG")

        ph.send("ROUTER agent=hermes text=Revisa el estado del bus y confirma")
        r = ph.read_until("ROUTED:")
        check("routed hermes", r == "ROUTED:hermes|queued", r)
        hermes_file = next((HERE / "inbox" / "hermes").glob("*.json"), None)
        check("inbox hermes escrito", hermes_file is not None)
        if hermes_file:
            m = json.loads(hermes_file.read_text())
            check("contenido hermes", "Revisa el estado del bus" in m["text"], m["text"])

        ph.send("ROUTER agent=web text=Busca noticias de mastering metal")
        r = ph.read_until("ROUTED:")
        check("routed web", r == "ROUTED:web|queued", r)
        check("inbox web escrito",
              any((HERE / "inbox" / "web").glob("*.json")))

        ph.send("ROUTER agent=phone text=saludo local")
        r = ph.read_until("ROUTED:")
        check("routed phone (local)", r == "ROUTED:phone|local", r)

        ph.send("ROUTER agent=* text=anuncio general del plano")
        r = ph.read_until("ROUTED:")
        check("routed broadcast", r == "ROUTED:*|queued", r)
        for a in ("hermes", "web", "windows"):
            n = sum(1 for _ in (HERE / "inbox" / a).glob("*.json"))
            check(f"inbox {a} tiene el broadcast", n >= 1, f"{n} msgs")

        ph.send("ROUTER agent=zzz text=agente inexistente")
        r = ph.read_until("ERR:")
        check("agente inexistente rechazado", r.startswith("ERR:UNKNOWN_AGENT"), r)

        print("== 3) SYNC: cola del telefono tras reinicio ==")
        ph.send("SYNC|CHAT_MSG|from=Tú|agent=hermes|text=mensaje en cola de ayer")
        r = ph.read_until("ROUTED:")
        check("SYNC re-enruta", r == "ROUTED:hermes|queued", r)
        ph.close()

        print("== 4) Falso hermes: consume inbox y responde ==")
        # El "hermes real" (mi cron) haria esto: leer inbox/hermes, escribir outbox/phone
        hermes_dir = HERE / "inbox" / "hermes"
        for f in sorted(hermes_dir.glob("*.json")):
            m = json.loads(f.read_text())
            reply = {"id": m["id"], "ts": int(time.time()), "from": "hermes",
                    "text": f"Recibido: {m['text'][:40]} (respuesta de prueba)"}
            (HERE / "outbox" / "phone" / f"{reply['id']}-phone.json").write_text(
                json.dumps(reply, ensure_ascii=False, indent=1))
            f.replace(hermes_dir / "done" / f.name)
        n_pending = sum(1 for _ in (HERE / "outbox" / "phone").glob("*.json"))
        check("outbox con respuestas", n_pending >= 3, f"{n_pending} msgs")

        print("== 5) Falso movil: POLL recoge las respuestas ==")
        ph2 = Client(PORT, "phone2")
        ph2.send("AUTH:" + TOKEN)
        check("AUTH ok (2a conexion)", ph2.read_until("AUTH:OK") == "AUTH:OK")
        ph2.send("POLL")
        got = []
        # El POLL devuelve N lineas PUSH y termina en POLL:END (o POLL:EMPTY).
        while True:
            if ph2.has_push_pending():
                line = ph2.read_until("PUSH")
            else:
                line = ph2.read_until("POLL:")
            if line.startswith("POLL:"):
                check("POLL finaliza con marcador", line in ("POLL:END", "POLL:EMPTY"), line)
                break
            got.append(line)
        check("recibidas 3+ PUSH", len(got) >= 3, f"{len(got)}")
        check("una PUSH de hermes", any(l.startswith("PUSH from=hermes") for l in got))
        check("outbox vaciado a sent/",
              not list((HERE / "outbox" / "phone").glob("*.json")))
        ph2.close()

        print("== 6) Sin token => todo fuera ==")
        ph3 = Client(PORT, "intruso")
        ph3.send("ROUTER agent=hermes text=oye")
        check("sin AUTH se rechaza", ph3.read_until("ERR:") == "ERR:AUTH_REQUIRED")
        ph3.close()

    finally:
        router.terminate()
        try:
            router.wait(timeout=5)
        except subprocess.TimeoutExpired:
            router.kill()

    print()
    if FAILURES:
        print(f"RESULTADO: {len(FAILURES)} fallos: {FAILURES}")
        sys.exit(1)
    print("RESULTADO: todo el loopback OK (auth, ruteo hermes/web/phone/*, sync, poll/push, outbox).")


if __name__ == "__main__":
    main()
