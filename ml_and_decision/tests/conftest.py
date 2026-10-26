"""Add ml_and_decision root to sys.path so tests can import decision.* and ml.*"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
