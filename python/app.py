import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


T100_ROOT = Path(os.environ.get("T100_ROOT", "/home/student/zy-T100")).expanduser().resolve()
T100_HOST = os.environ.get("T100_HOST", "0.0.0.0")
T100_PORT = int(os.environ.get("T100_PORT", "5001"))
T100_SINGLE_MODEL_PAPER = os.environ.get("T100_SINGLE_MODEL_PAPER", "")

PAPER_CONFIG = {
    "A": {
        "model_dir": "utils_x86/models/A",
    },
    "B": {
        "model_dir": "utils_x86/models/B",
    },
}


def normalize_paper_type(value):
    paper = str(value or "").strip().upper()
    if len(paper) != 1 or not paper.isascii() or not paper.isalpha():
        raise ValueError("paperType 必须是单个英文字母")
    return paper


def paper_config(paper_type):
    paper = normalize_paper_type(paper_type)
    try:
        return paper, PAPER_CONFIG[paper]
    except KeyError as error:
        raise ValueError(f"{paper} 卷模型尚未配置") from error


def model_path(paper_type):
    paper, config = paper_config(paper_type)
    candidates = [
        T100_ROOT / config["model_dir"] / "detection.tflite",
        T100_ROOT / "utils_x86" / paper / "detection.tflite",
    ]
    if T100_SINGLE_MODEL_PAPER.strip().upper() == paper:
        candidates.append(T100_ROOT / "utils_x86" / "detection.tflite")

    for path in candidates:
        if path.is_file():
            return paper, config, path.resolve()

    expected = "、".join(str(path) for path in candidates)
    raise FileNotFoundError(f"{paper} 卷模型尚未部署，请检查：{expected}")


def success(data, message="成功"):
    return {"code": 1, "message": message, "data": data}


def failure(message, code=1002):
    return {"code": code, "message": str(message)}


class T100Handler(BaseHTTPRequestHandler):
    def _write_json(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self._write_json({"code": 1, "message": "成功"})

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/":
            self._write_json(success({"service": "t100"}, "T100 服务运行中"))
            return
        if parsed.path != "/api/v2/checkUtils_x86":
            self._write_json(failure("接口不存在"), status=404)
            return

        try:
            paper_values = parse_qs(parsed.query).get("paperType", [])
            paper = paper_values[0] if paper_values else ""
            paper, _, path = model_path(paper)
            self._write_json(success({"paperType": paper}, "模型部署成功"))
            print(f"health paper={paper} model={path}")
        except Exception as error:
            self._write_json(failure(error))

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path != "/api/v2/t100":
            self._write_json(failure("接口不存在"), status=404)
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(content_length).decode("utf-8"))
            paper, config, path = model_path(body.get("paperType"))
            image_data = body.get("image")
            name = body.get("name")
            if not image_data or not name:
                raise ValueError("image 和 name 不能为空")

            # Import lazily so health checks can still report missing model files
            # without importing the TFLite runtime.
            from object_detector import update_image

            image, bboxes = update_image(
                image_data,
                model_dir=str(path.parent),
                model_root=str(T100_ROOT),
                paper_type=paper,
            )
            payload = {
                "paperType": paper,
                "name": name,
                "image": image,
                "bboxes": bboxes.tolist(),
            }
            self._write_json(success(payload))
            print(f"inference paper={paper} model={path} name={name}")
        except Exception as error:
            self._write_json(failure(error))

    def log_message(self, format_string, *args):
        print("t100", format_string % args)


def main():
    server = ThreadingHTTPServer((T100_HOST, T100_PORT), T100Handler)
    print(f"T100 listening on {T100_HOST}:{T100_PORT}, root={T100_ROOT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
