import sys
import os
import json
import logging
import numpy as np
import re
from pathlib import Path
from xml.dom.minidom import parse
"""
Note:
box已经转化了宽高比例
box = [xmin, ymin, xmax, ymax]
pred_cls = [pred_cls]
confidence = [confidence]
bboxes = np.array([[bbox, pred_cls, confidence]...])
ground truth
gtboxes = [[gtbox, gt_cls]]


# 输入: (img_url, gtboxes), (img_url, bboxes)
# 输出: p, r, map
img_url_0 = "competition_a/test/0.jpg"
bboxes_0 = np.array([[45, 60, 100, 96, 0, 0.88],
[35, 6, 100, 196, 0, 0.74]])
gtboxes_0 = np.array([[33, 27, 100, 196, 0], 
[18, 36, 121.1, 96, 0],
[45, 26, 168.1, 96, 0],
[85, 6, 185, 96, 1]])
fetch_0_gtboxes = (img_url_0, gtboxes_0.tobytes())
fetch_0_bboxes = (img_url_0, bboxes_0.tobytes())

img_url_1 = "competition_a/test/1.jpg"
bboxes_1 = np.array([[45, 60, 100, 96, 0, 0.88],
[35, 6, 100, 196, 0, 0.74]])
gtboxes_1 = np.array([[33, 27, 100, 196, 0],
[18, 36, 121, 96.4, 0],
[40, 66, 155, 96, 1],
[48, 16.1, 132, 96, 2]])
fetch_1_gtboxes = (img_url_1, gtboxes_1.tobytes())
fetch_1_bboxes = (img_url_1, bboxes_1.tobytes())

img_url_2 = "competition_a/test/2.jpg"
bboxes_2 = np.array([[45, 60, 100, 96, 0, 0.88],
[45, 60, 100, 96, 1, 0.88],
[150, 160, 250, 336, 0, 0.26],
[34, 20, 198, 96, 2, 0.91],
[35, 6, 100, 196, 0, 0.74]])
gtboxes_2 = np.array([[33, 27, 100, 196, 0], 
[18, 36, 121.9, 96, 0],
[40, 66, 155, 96, 1],
[48, 16, 100, 96, 2],
[45, 26.1, 168, 96, 0],
[15, 6, 102.5, 96, 0],
[85, 6, 185, 96, 1]])
fetch_2_gtboxes = (img_url_2, gtboxes_2.tobytes())
fetch_2_bboxes = (img_url_2, bboxes_2.tobytes())

n_gtboxes = [fetch_0_gtboxes, fetch_1_gtboxes, fetch_2_gtboxes]
n_bboxes = [fetch_0_bboxes, fetch_1_bboxes, fetch_2_bboxes]
# print(n_gtboxes)
# print(n_bboxes)
CATEGROY_THRES = 0.5
# IOU_THRES = 0.5
IOU_THRES = np.linspace(0.5, 0.95, 10)

# 从数据库fetchall()的数据转变为字典, key为img_url, value为boxes
n_gtboxes_dict = dict(gtboxes for gtboxes in n_gtboxes)
for img_url in n_gtboxes_dict.keys():
    gtboxes_bytes = n_gtboxes_dict.get(img_url)
    gtboxes = np.frombuffer(gtboxes_bytes, dtype=np.float64)
    gtboxes.shape = (-1, 5)
    n_gtboxes_dict[img_url] = gtboxes
n_bboxes_dict = dict(bboxes for bboxes in n_bboxes)
for img_url in n_gtboxes_dict.keys():
    bboxes_bytes = n_bboxes_dict.get(img_url)
    bboxes = np.frombuffer(bboxes_bytes, dtype=np.float64)
    bboxes.shape = (-1, 6)
    n_bboxes_dict[img_url] = bboxes
# print(n_gtboxes_dict)
# print(n_bboxes_dict)
"""
logging.basicConfig(level=logging.INFO, format=' %(asctime)s - %(filename)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)
XML_PATH = None
CATEGROY_THRES = 0.5
# IOU_THRES = [0.5]
IOU_THRES = np.linspace(0.5, 0.95, 10)


def parse_class():
    CLASS = []
    dom_tree = parse(XML_PATH)
    annotations = dom_tree.documentElement
    labels = annotations.getElementsByTagName("label")
    for node in labels:
        label = node.getElementsByTagName("name")
        for node_ in label:
            name = node_.firstChild.nodeValue
            CLASS.append(name)
    return CLASS


def label_to_index(label):
    CLASS = parse_class()
    label2index = dict((name, int(index)) for index, name in enumerate(CLASS))
    class_index = label2index.get(label)
    return class_index


def parse_xml():
    n_gtboxes_dict = dict()
    dom_tree = parse(XML_PATH)
    annotations = dom_tree.documentElement
    image = annotations.getElementsByTagName("image")
    for node_image in image:
        image_name = node_image.getAttribute("name")
        box = node_image.getElementsByTagName("box")
        gtboxes = []
        for node in box:
            xmin = float(node.getAttribute("xtl"))
            ymin = float(node.getAttribute("ytl"))
            xmax = float(node.getAttribute("xbr"))
            ymax = float(node.getAttribute("ybr"))
            label = node.getAttribute("label")
            index = label_to_index(label)
            gtbox = [xmin, ymin, xmax, ymax, index]
            gtboxes.append(gtbox)
        n_gtboxes_dict[image_name] = gtboxes
    return n_gtboxes_dict


def transform_list_to_numpy(input_dict):
    # 将每个dict下键值对的值转换成numpy.ndarray格式数据
    # 返回一个新的<dict>对象, 该对象键是原来的键, 值的数据类型改变
    output_dict = dict()
    for image_name, box in input_dict.items():
        value = json.loads(box) if isinstance(box, str) else box
        output_dict[image_name] = np.array(value, dtype=np.float64)
    return output_dict


def box_iou(box1, box2):
    # 计算box1与box2的iou
    x1_1, y1_1, x2_1, y2_1 = box1
    x1_2, y1_2, x2_2, y2_2 = box2

    x1 = max(x1_1, x1_2)
    y1 = max(y1_1, y1_2)
    x2 = min(x2_1, x2_2)
    y2 = min(y2_1, y2_2)

    if x2-x1+1 <= 0 or y2-y1+1 <= 0:
        return 0
    else:
        inter = (x2-x1+1)*(y2-y1+1)
        union = (x2_1-x1_1+1)*(y2_1-y1_1+1)+(x2_2-x1_2+1)*(y2_2-y1_2+1)-inter
        iou = inter / union
        return iou


def select_gtboxes(category, gtboxes):
    # 筛选出类别为category的gtboxes
    object_gtboxes = []
    for gtbox in gtboxes:
        if gtbox[4] == category:
            object_gtboxes.append(gtbox)
    return object_gtboxes


def evaluation_iou(n_bboxes_dict, n_gtboxes_dict, iou_thres):
    """
    先判断是否为同一张图片, 如果为同一张图片，那么继续计算同一张图片中的bboxes, gtboxes, 
    否则将bboxes置为0
    判断bboxes是TP还是FP的过程: 对于每个预测的bbox，计算其与图片中所有类别相同的gtbox的IOU值，
    取其中最大IOU值iou_max对应的gtbox，如果iou_max>iou_thres，则该预测box即为TP，否则为FP。
    tp，如果TP则为1，FP为0
    返回np.array()->gt_cls, pred_cls, confidence, tp
    """
    bboxes_default = np.zeros((1, 6))
    gt_cls, pred_cls, confidence, tp = [], [], [], []
    for img_url in n_gtboxes_dict.keys():
        bboxes = n_bboxes_dict.get(img_url, bboxes_default)
        gtboxes = n_gtboxes_dict.get(img_url)

        gt_cls += gtboxes[:, 4].astype(np.int32).tolist()
        for i in range(bboxes.shape[0]):
            object_gtboxes = select_gtboxes(bboxes[i][4], gtboxes)
            if bboxes[i][5] >= CATEGROY_THRES:
                pred_cls.append(bboxes[i][4].astype(np.int32))
                confidence.append(bboxes[i][5])
                iou_list = [box_iou(gtbox[:4], bboxes[i][:4]) for gtbox in object_gtboxes]
                if iou_list:
                    iou = max(iou_list)
                    if iou > iou_thres:
                        tp.append(1)
                    else:
                        tp.append(0)
                else:
                    tp.append(0)
    gt_cls, pred_cls, confidence, tp = np.array(gt_cls), np.array(pred_cls), np.array(confidence), np.array(tp)
    return gt_cls, pred_cls, confidence, tp


def compute_ap(recall, precision):
    """
    通过PR曲线计算AP值
    # Arguments
        recall:    The recall curve.
        precision: The precision curve.
    # Returns
        The average precision.
    """
    mrec = np.concatenate(([0.0], recall, [1.0]))
    mpre = np.concatenate(([0.0], precision, [0.0]))
    for i in range(mpre.size - 1, 0, -1):
        mpre[i - 1] = np.maximum(mpre[i - 1], mpre[i])

    i = np.where(mrec[:-1] != mrec[1:])[0]
    ap = np.sum((mrec[i + 1] - mrec[i]) * mpre[i + 1])
    return ap


def per_cls_ap(n_bboxes_dict, n_gtboxes_dict, iou_thres):
    """
    计算每个类别的评价指标，返回类别评价指标列表
    """
    gt_cls, pred_cls, confidence, tp = evaluation_iou(n_bboxes_dict, n_gtboxes_dict, iou_thres)
    i = np.argsort(-confidence)
    tp, pred_cls, conf = tp[i], pred_cls[i], confidence[i]
    unique_cls = np.unique(gt_cls)
    p, r, ap = [], [], []

    for c in unique_cls:
        n_gt = (gt_cls == c).sum()
        n_p = (pred_cls == c).sum()
        j = np.squeeze(np.argwhere(pred_cls == c))
        n_tp = tp[j]

        if n_p == 0 and n_gt == 0:
            continue
        elif n_p == 0 or n_gt == 0:
            ap.append(0)
            r.append(0)
            p.append(0)
        else:
            fpc = (1 - n_tp).cumsum()
            tpc = n_tp.cumsum()

            # Recall
            recall_curve = tpc / (n_gt + 1e-16)
            r.append(recall_curve[-1])
            # Precision
            precision_curve = tpc / (tpc + fpc)
            p.append(precision_curve[-1])
            # AP
            ap.append(compute_ap(recall_curve, precision_curve))
    # Compute F1 score
    # p, r, ap = np.array(p), np.array(r), np.array(ap)
    # f1 = 2 * p * r / (p + r + 1e-16)
    return p, r, ap, unique_cls.astype("int32")


def mean_cls_ap(n_bboxes_dict, n_gtboxes_dict, iou_thres):
    """
    计算所有类别的平均评价指标
    """
    p, r, ap, cls_list = per_cls_ap(n_bboxes_dict, n_gtboxes_dict, iou_thres)
    return np.mean(p), np.mean(r), np.mean(ap)


def evaluation(n_bboxes_dict, n_gtboxes_dict):
    """
    加入多个IOU阈值综合计算均值
    """
    p, r, ap = [], [], []
    for iou_thres in IOU_THRES:
        p_, r_, ap_ = mean_cls_ap(n_bboxes_dict, n_gtboxes_dict, iou_thres)
        p.append(p_)
        r.append(r_)
        ap.append(ap_)
    return np.mean(p), np.mean(r), np.mean(ap)


if __name__ == '__main__':
    if len(sys.argv) != 2 or not re.fullmatch(r'[a-zA-Z]', sys.argv[1]):
        raise SystemExit('paper type must be a single letter')
    annotations_root = Path(os.environ.get(
        'MATCH_SCORING_ANNOTATIONS_ROOT', Path(__file__).resolve().parent
    )).resolve()
    XML_PATH = str(annotations_root / sys.argv[1].lower() / 'annotations.xml')
    payload = sys.stdin.read()
    if not payload.strip():
        raise SystemExit('score payload is empty')
    n_bboxes_dict = json.loads(payload)
    # print(type(n_bboxes_dict))
    n_bboxes_dict = transform_list_to_numpy(n_bboxes_dict)
    n_gtboxes_dict = parse_xml()
    n_gtboxes_dict = transform_list_to_numpy(n_gtboxes_dict)
    # logger.info(n_bboxes_dict)
    # logger.info(n_gtboxes_dict)
    p, r, ap = evaluation(n_bboxes_dict, n_gtboxes_dict)
    # 求出一个加权值
    student_score = ap * 100
    print(student_score)
    # logger.info(p, r, ap)
