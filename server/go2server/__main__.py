"""Uso: python -m go2server [--sitio archivo.json] [--viewer] [--speed 4] [--port 8765]"""

import argparse
import asyncio
import os
import sys

from . import site as site_file
from .server import serve

DEFAULT_MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "third_party", "mujoco_menagerie", "unitree_go2")


def main():
    parser = argparse.ArgumentParser(description="Servidor de misiones del Go2 con MuJoCo")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--speed", type=int, default=4, help="multiplicador del tiempo simulado (1 a 10)")
    parser.add_argument("--model-dir", default=DEFAULT_MODEL_DIR, help="carpeta unitree_go2 de MuJoCo Menagerie")
    parser.add_argument("--sitio", default=site_file.DEFAULT_SITE,
                        help="archivo de sitio a cargar en el robot (por defecto, el data center de ejemplo)")
    parser.add_argument("--viewer", action="store_true", help="abre la ventana de MuJoCo")
    parser.add_argument("--no-console", action="store_true", help="sin comandos por consola (para correr en segundo plano)")
    args = parser.parse_args()
    try:
        site = site_file.load(args.sitio)
    except site_file.SiteError as e:
        sys.exit(f"Sitio inválido: {e}")
    try:
        asyncio.run(serve(args.host, args.port, os.path.abspath(args.model_dir), site, args.speed, args.viewer,
                          not args.no_console))
    except KeyboardInterrupt:
        print("Servidor detenido")


if __name__ == "__main__":
    main()
