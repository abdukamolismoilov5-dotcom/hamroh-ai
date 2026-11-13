#!/usr/bin/env python3
"""
SafeDrive AI / Hamroh AI — Production Backend Server Launcher
Can be run from any folder:
    python server.py
"""
import os
import sys
from pathlib import Path

# Add backend directory to sys.path so 'app' is always importable
BACKEND_DIR = Path(__file__).resolve().parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

import uvicorn
from app.main import app

def main():
    port = int(os.environ.get("PORT", 8000))
    host = os.environ.get("HOST", "0.0.0.0")
    print(f"============================================================")
    print(f"  SAFEDRIVE AI (HAMROH AI) - BACKEND SERVER")
    print(f"  URL: http://localhost:{port}")
    print(f"  Swagger Docs: http://localhost:{port}/docs")
    print(f"  Radars API: http://localhost:{port}/api/v1/radars")
    print(f"============================================================")
    uvicorn.run(app, host=host, port=port, log_level="info")

if __name__ == "__main__":
    main()
