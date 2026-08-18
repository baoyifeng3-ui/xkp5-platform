import json
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.request import urlopen

import app


class ModelPathTests(unittest.TestCase):
    def setUp(self):
        self.original_root = app.T100_ROOT
        self.original_single_paper = app.T100_SINGLE_MODEL_PAPER
        self.temp_dir = tempfile.TemporaryDirectory()
        app.T100_ROOT = Path(self.temp_dir.name)
        app.T100_SINGLE_MODEL_PAPER = ""

    def tearDown(self):
        app.T100_ROOT = self.original_root
        app.T100_SINGLE_MODEL_PAPER = self.original_single_paper
        self.temp_dir.cleanup()

    def test_prefers_paper_model_directory(self):
        model = app.T100_ROOT / "utils_x86/models/B/detection.tflite"
        model.parent.mkdir(parents=True)
        model.touch()

        paper, config, resolved = app.model_path("b")

        self.assertEqual("B", paper)
        self.assertEqual("utils_x86/models/B", config["model_dir"])
        self.assertEqual(model.resolve(), resolved)

    def test_rejects_missing_paper_without_fallback(self):
        model = app.T100_ROOT / "utils_x86/models/A/detection.tflite"
        model.parent.mkdir(parents=True)
        model.touch()

        with self.assertRaisesRegex(FileNotFoundError, "B 卷模型尚未部署"):
            app.model_path("B")

    def test_allows_explicit_legacy_single_model_paper(self):
        model = app.T100_ROOT / "utils_x86/detection.tflite"
        model.parent.mkdir(parents=True)
        model.touch()
        app.T100_SINGLE_MODEL_PAPER = "B"

        _, _, resolved = app.model_path("B")

        self.assertEqual(model.resolve(), resolved)

    def test_does_not_share_legacy_single_model_between_papers(self):
        model = app.T100_ROOT / "utils_x86/detection.tflite"
        model.parent.mkdir(parents=True)
        model.touch()
        app.T100_SINGLE_MODEL_PAPER = "B"

        with self.assertRaisesRegex(FileNotFoundError, "A 卷模型尚未部署"):
            app.model_path("A")

    def test_health_check_reports_resolved_paper(self):
        model = app.T100_ROOT / "utils_x86/models/B/detection.tflite"
        model.parent.mkdir(parents=True)
        model.touch()
        server = ThreadingHTTPServer(("127.0.0.1", 0), app.T100Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        try:
            port = server.server_address[1]
            with urlopen(
                f"http://127.0.0.1:{port}/api/v2/checkUtils_x86?paperType=B"
            ) as response:
                payload = json.load(response)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

        self.assertEqual(1, payload["code"])
        self.assertEqual("B", payload["data"]["paperType"])


if __name__ == "__main__":
    unittest.main()
