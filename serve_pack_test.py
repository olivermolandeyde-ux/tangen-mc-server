#!/usr/bin/env python3

import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASEDIR = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.join(BASEDIR, "PureBDcraft_256x_MC262.zip")
PORT = 18080
ALLOWED = {"/purebdcraft.zip", "/PureBDcraft_256x_MC262.zip"}


class PackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ALLOWED:
            self.send_error(404, "Not found - only the resource pack file is served")
            return
        if not os.path.isfile(ZIP):
            self.send_error(503, "Resource pack file missing")
            return
        size = os.path.getsize(ZIP)
        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("Content-Length", str(size))
        self.send_header("Accept-Ranges", "none")
        self.send_header("Cache-Control", "public, max-age=86400")
        self.end_headers()
        try:
            with open(ZIP, "rb") as f:
                while True:
                    chunk = f.read(1 << 16)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass

    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), PackHandler)
    server.daemon_threads = True
    print("Serving %s on port %d" % (ZIP, PORT), flush=True)
    server.serve_forever()