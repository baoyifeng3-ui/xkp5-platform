import base64
import os

import cv2
import numpy as np
from tflite_runtime.interpreter import Interpreter


PAPER_LABELS = {
    "A": ("horse", "cow", "sheep"),
    "B": ("Apple", "Pear", "Cantaloupe"),
}


def normalize_paper_type(paper_type, model_dir=""):
    """Return one uppercase paper letter, inferring it from model_dir when possible."""
    paper = str(paper_type or "").strip().upper()
    if not paper:
        directory_name = os.path.basename(os.path.normpath(model_dir)).strip().upper()
        if len(directory_name) == 1 and directory_name.isalpha():
            paper = directory_name
    if not paper:
        paper = "A"
    if len(paper) != 1 or not paper.isalpha() or not paper.isascii():
        raise ValueError("paperType 必须是单个英文字母")
    return paper


def labels_for_paper(paper_type, labels=None):
    """Resolve labels without requiring a separate labels.txt file."""
    if labels is not None:
        resolved = tuple(str(label) for label in labels)
        if not resolved:
            raise ValueError("标签列表不能为空")
        return resolved

    paper = normalize_paper_type(paper_type)
    try:
        return PAPER_LABELS[paper]
    except KeyError as error:
        raise ValueError(f"{paper} 卷未配置类别名称") from error


def update_image(
    image_data,
    GRAPH_NAME="detection.tflite",
    LABELMAP_NAME="labelmap.txt",
    min_conf_threshold=0.5,
    use_TPU=False,
    model_dir="utils_x86",
    paper_type=None,
    labels=None,
    model_root=None,
):
    """Run one TFLite detection and draw labels for the selected paper.

    LABELMAP_NAME and use_TPU remain in the signature for compatibility with the
    original T100 caller. Labels are selected from the paper registry or the
    explicit ``labels`` argument; no labels.txt file is required.
    """
    del LABELMAP_NAME, use_TPU

    paper = normalize_paper_type(paper_type, model_dir)
    label_names = labels_for_paper(paper, labels)
    model_directory = model_dir
    if not os.path.isabs(model_directory):
        model_directory = os.path.join(
            model_root or os.environ.get("T100_ROOT", "/home/student/zy-T100"),
            model_directory,
        )
    model_path = os.path.join(model_directory, GRAPH_NAME)

    interpreter = Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    height = input_details[0]["shape"][1]
    width = input_details[0]["shape"][2]
    floating_model = input_details[0]["dtype"] == np.float32
    input_mean = 127.5
    input_std = 127.5

    image_bytes = base64.b64decode(image_data)
    image_array = np.frombuffer(image_bytes, np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("无法读取图片")

    image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    image_height, image_width, _ = image.shape
    image_resized = cv2.resize(image_rgb, (width, height))
    input_data = np.expand_dims(image_resized, axis=0)

    if floating_model:
        input_data = (np.float32(input_data) - input_mean) / input_std

    interpreter.set_tensor(input_details[0]["index"], input_data)
    interpreter.invoke()

    boxes = interpreter.get_tensor(output_details[0]["index"])[0]
    classes = interpreter.get_tensor(output_details[1]["index"])[0]
    scores = interpreter.get_tensor(output_details[2]["index"])[0]

    bboxes = []
    for index, score in enumerate(scores):
        if score <= min_conf_threshold or score > 1.0:
            continue

        ymin = int(max(1, boxes[index][0] * image_height))
        xmin = int(max(1, boxes[index][1] * image_width))
        ymax = int(min(image_height, boxes[index][2] * image_height))
        xmax = int(min(image_width, boxes[index][3] * image_width))
        class_index = int(classes[index])

        if class_index < 0 or class_index >= len(label_names):
            raise ValueError(f"{paper} 卷模型返回了未配置的类别编号 {class_index}")

        bboxes.append([xmin, ymin, xmax, ymax, classes[index], score])
        cv2.rectangle(image, (xmin, ymin), (xmax, ymax), (10, 255, 0), 2)

        object_name = label_names[class_index]
        label = "%s: %d%%" % (object_name, int(score * 100))
        label_size, base_line = cv2.getTextSize(
            label, cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2
        )
        label_ymin = max(ymin, label_size[1] + 10)
        cv2.rectangle(
            image,
            (xmin, label_ymin - label_size[1] - 10),
            (xmin + label_size[0], label_ymin + base_line - 10),
            (255, 255, 255),
            cv2.FILLED,
        )
        cv2.putText(
            image,
            label,
            (xmin, label_ymin - 7),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 0, 0),
            2,
        )

    encoded_image = cv2.imencode(".jpg", image)[1].tobytes()
    return base64.b64encode(encoded_image).decode(), np.array(bboxes)


def test(image_data):
    image_bytes = base64.b64decode(image_data)
    image_array = np.frombuffer(image_bytes, np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("无法读取图片")

    encoded_image = cv2.imencode(".jpg", image)[1].tobytes()
    return base64.b64encode(encoded_image).decode()
