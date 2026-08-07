"""`.claude/hooks/` 直下のスクリプトを `import <name>` で読めるようにする。

`.claude/hooks/` はパッケージではないため、`sys.path` へ親ディレクトリを足す。
`importlib` で個別に読み込むと dataclass の型解決が `sys.modules` 未登録で
落ちるため、通常の import 経路に載せる。
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
