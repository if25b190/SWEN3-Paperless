import time
from pathlib import Path

HEARTBEAT = Path("/tmp/paperless-worker-heartbeat")


def healthy() -> bool:
    try:
        return time.time() - HEARTBEAT.stat().st_mtime < 60
    except FileNotFoundError:
        return False


if __name__ == "__main__":
    raise SystemExit(0 if healthy() else 1)
