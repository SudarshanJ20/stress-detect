import sys
from pathlib import Path

# make `import features.spec_constants` etc. work under pytest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
