import cv2
import imgaug. augmenters as iaa

input_img = cv2.imread("./###")
seq = iaa.Sequential([###((###,###))])
output_img = seq.augment_image(input_img)
cv2.imwrite("./###", output_img)
print("已完成增强处理!")
