"""Capture aggregate evaluation without inventing observations or accuracy claims."""
import datetime
import json
import sys
import urllib.request

url = sys.argv[1].rstrip("/") + "/api/model/metrics"
with urllib.request.urlopen(url, timeout=30) as response:
    metrics = json.load(response)
print(json.dumps({"captured_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  "endpoint": url, "metrics": metrics}, indent=2))
