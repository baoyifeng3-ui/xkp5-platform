<template>
  <div class="a-box">
    <div class="a-title">“数智杯”图像识别数据处理比赛赛题</div>
    <div class="a-tips">
      <div class="a-t1">比赛日期：2022.04.30</div>
      <div class="a-t2">赛卷类型： B</div>
      <div class="a-t3">赛项组别：高职组</div>
      <div class="a-t4">赛项组别：电子信息大类</div>
    </div>
    <div class="a-cmd" style="height: 150px">
      <div class="a-q-title">模块-常用命令与资源</div>
      <div class="a-c1"><span class="num">（一）</span>Linux命令行代码：</div>
      <div class="a-c2"><span class="num">（二）</span>平台链接：</div>
      <div class="a-c3">1. wget+源文件..................//下载指令</div>
      <div class="a-c4" style="margin-left: 80px">
        1. 模型训练工具------<a :href="`http://${url.vscodeUrl}`" target="_blank"
          >http://{{ url.vscodeUrl }}</a
        >
      </div>
      <div class="a-c5">
        2. tar -xzvf+压缩包...........................//解压指令
      </div>
      <div class="a-c6" style="margin-left: 80px">
        2. 图像标注工具------<a :href="`http://${url.cvatUrl}`" target="_blank"
          >http://{{ url.cvatUrl }}</a
        >
      </div>
      <div class="a-c7">3. rm+源文件....................//移除指令</div>
      <div class="a-c8"></div>
      <div class="a-c7">4. cp+源文件+目标路径..........//拷贝指令</div>
      <div class="a-c8"></div>
      <div class="a-c7">5. cd+目标路径............//进入目标路径指令</div>
    </div>
    <div class="a-cmd" style="height: 440px">
      <div class="a-c1"><span class="num">（三）</span>比赛参考代码：</div>
      <div class="a-c1"></div>
      <div class="a-c8" style="width: 100%">
        1. cd
        /home/student/contest...........................................................................................//进入/home/student/contest目录下
      </div>
      <div class="a-c8" style="width: 100%">
        2. ls -l
        /home/student/contest............................................................//查看/home/student/contest目录下的所有文件列表
      </div>
      <div class="a-c8" style="width: 100%">
        3.conda activate
        contest................................................................//激活环境<span
          class="red"
          >（含本次比赛所需用到的所有依赖库、检测库）</span
        >
      </div>
      <div class="a-c8" style="width: 100%">
        4.conda
        deactivate...........................................................................................................................................................//退出环境
      </div>
      <div class="a-c8" style="width: 100%">
        5.python
        denoise.py..............................................................................................................................//执行“中值滤波处理”脚本
      </div>
      <div class="a-c8" style="width: 100%">
        6.python
        bright.py........................................................................................................................................//执行“亮度增强”脚本
      </div>
      <div class="a-c8" style="width: 100%">
        7.python train.py --logtostderr --train_dir checkpoint
        --pipeline_config_path model.config.......//执行“模型训练”脚本
      </div>
      <div class="a-c8" style="width: 100%; height: 20px">
        8.tensorboard --host {{ url.cvatUrl }} --port 8889 --logdir checkpoint/
        ..................................//查看“可视化训练结果”
      </div>
      <div class="a-c8" style="width: 100%; height: 40px">
        9.python eval.py --logtostderr --checkpoint_dir checkpoint --eval_dir
        evaluation --pipeline_config_path
        model.config......................................................................................................................................................//执行“模型评估”脚本
      </div>
      <div class="a-c8" style="width: 100%; height: 20px">
        10.tensorboard --host {{ url.cvatUrl }} --port 8889 --logdir evaluation/
        .......................................//查看“模型精度评估结果”
      </div>
      <div class="a-c8" style="width: 100%; height: 60px">
        11.python export_fz.py --input_type image_tensor --pipeline_config_path
        model.config --trained_checkpoint_prefix checkpoint/model.ckpt-xx
        --output_directory
        frozen_models................................................//执行“导出冻结模型”脚本
        <br />
        <span class="red">（xx为model.config文件中num_steps的值）</span>
      </div>
      <div class="a-c8" style="width: 100%">
        12.python
        detect.py........................................................................................................................................//执行“模型检测”脚本
      </div>
      <div class="a-c8" style="width: 100%; height: 60px">
        13.python export_pb.py --pipeline_config_path model.config
        --trained_checkpoint_prefix checkpoint/model.ckpt-xx --output_directory
        tflite_models........................................................................................................//执行“模型转换to_pb”脚本
        <br />
        <span class="red">（xx为model.config文件中num_steps的值）</span>
      </div>
      <div class="a-c8" style="width: 100%; height: 40px">
        14.python
        pb_to_tflite.py.........................................................................................................//执行“模型转换pb_to_tflite”脚本
      </div>
    </div>
    <div class="a-cmd" style="height: 470px;margin-top: 20px">
      <div class="a-c1"><span class="num">（四）</span>脚本文件地址：</div>
      <div class="a-c1"></div>
      <div class="a-c8" style="width: 100%">
        1.“中值滤波处理”脚本.............................................................................................../dataset/B/denoise.py
      </div>
      <div class="a-c8" style="width: 100%">
        2.“亮度增强”脚本.........................................................................................................../dataset/B/bright.py
      </div>
      <div class="a-c8" style="width: 100%">
        3.“模型训练”脚本............................................................................................................../dataset/B/train.py
      </div>
      <div class="a-c8" style="width: 100%">
        4.“模型评估”脚本.............................................................................................................../dataset/B/eval.py
      </div>
      <div class="a-c8" style="width: 100%">
        5.“导出冻结模型”脚本............................................................................................/dataset/B/export_fz.py
      </div>
      <div class="a-c8" style="width: 100%">
        6.“模型检测”脚本........................................................................................................../dataset/B/detect.py
      </div>
      <div class="a-c8" style="width: 100%">
        7.“模型转换to_pb”脚本......................................................................................./dataset/B/export_pb.py
      </div>
      <div class="a-c8" style="width: 100%">
        8.“模型转换pb_to_tflite”脚本........................................................................../dataset/B/pb_to_tflite.py
      </div>
      <div class="a-c8" style="width: 100%">
        9.“推理”脚本................................................................................................/dataset/B/object_detector.py
      </div>
      <div class="a-c1" style="width: 100%; margin-top: 20px; margin-left: 0">
        <span class="num">（五）</span>数据资源地址：
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        1.数据集地址........................................................................................................................./dataset/B/u1.tar.gz
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        2.预训练模型地址......................................................................................../dataset/B/pretrain_model.tar.gz
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        3.训练数据集地址......................................................................................................../dataset/B/train.tfrecord
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        4.标签文件地址......................................................................................................./dataset/B/label_map.pbtxt
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        5.验证数据集地址............................................................................................................/dataset/B/val.tfrecord
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        6.模型配置文件地址..................................................................................................../dataset/B/model.config
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        7.测试图片地址......................................................................................................................../dataset/B/test.jpg
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        8.需“中值滤波”处理图像地址......................................................................................../dataset/B/noise.jpg
      </div>
      <div class="a-c8" style="width: 100%; margin-top: 3px; margin-left: 30px">
        9.需“亮度增强”处理图像地址........................................................................................../dataset/B/dark.jpg
      </div>
    </div>
    <div class="a-background">
      <div class="a-bg-title">背景介绍：</div>
      <div class="indent">
        人工智能的迅速发展将深刻改变人类社会生活、改变世界，如人工智能+养殖、人工智能+种植的产品和解决方案，会越来越多的出现在我们的身边。每个人都能够在这场人工智能的变革中找到自己的角色，特别是进入算法工具化时代之后，典型的应用开发流程变成了数据采集、数据预处理、数据标注、模型训练和模型部署。
      </div>
      <div class="indent">
        新疆当地一家农产品企业正在开展智慧果园的建设，企业负责人计划采用人工智能图像识别技术，实现对瓜果自动定量检测的效果，提高果园生产管理效率。
      </div>
      <div class="indent">
        现邀请贵校对该企业的智慧果园项目进行技术支持。贵校团队的任务便是利用企业提供的瓜果图片，开发进行图像识别任务的算法模型并将该模型部署到企业的边缘设备中。
      </div>
    </div>
    <div class="a-warning">
      上述任务的第一部分是“数据标注与增强” <br />
      下面请根据题目要求，进行规范作答
    </div>
    <div class="a-question" style="height: 255px">
      <div class="a-q-title">模块一 数据标注与增强</div>
      <div class="a-q-title">一、简答题</div>
      <div class="a-q-text h1">
        （一）请结合你所在学校的特色，描述一个人工智能+行业的应用场景，并简述数据准确的重要性（100字以内）。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （二）常见的数据采集途径包括从数据收集平台下载、数据定制化采集等，请再列举两种可以获取数据的途径。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （三）进行模型训练之前，需要对原始数据进行清洗，请简述数据清洗的内容及作用。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （四）图片旋转是常见的图像增强方式之一，请再列举三种方式，并简述图像增强的作用。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （五）图像识别中，数据集指的是完整的“训练集”、“验证集”、“测试集”，请简述它们各自的作用。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
    </div>
    <div class="a-question" style="height: 320px">
      <div class="a-q-title">二、实操题</div>
      <div class="a-q-text h2">
        （一）数据处理的第一步是数据采集，企业已经将采集好的数据集（u1.tar.gz）放在了数据平台上，请将该数据集下载并解压到指定目录/home/student/contest/data中，并通过指令展现/home/student/contest/data的文件列表（含数据集）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h2">
        （二）数据集解压完成后，请检查是否有不符合规范的图片在内（例如干扰类别图像、模糊程度较大的图像）。若有发现，请对数据集完成数据清洗（即删除干扰图像）并记录被清洗的图片的文件名，例如
        Nose.jpg。
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
        3.答题框附上被清洗图片的文件名
      </div>
      <div class="a-q-text h2">
        （三）数据集下载-解压-清洗完成后，需对noise.jpg执行“中值滤波处理”脚本处理，并将处理后的图像输出到/home/student/contest/中，命名为denoise.jpg，通过指令展现/home/student/contest/的文件列表（含denoise.jpg）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
        3.答题框附上降噪后图像截图
      </div>
      <div class="a-q-text h3">
        （四）预处理完后，若数据集图片数量不足，可通过数据增强的方式来增加图片。请调用函数完成dark.jpg亮度增强，并将处理后的图像输出到/home/student/contest/中，命名为bright.jpg，通过指令展现/home/student/contest的文件列表（含bright.jpg）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
        3.答题框附上亮度调整后的图像
      </div>
      <div class="a-q-text h1">
        （五）请打开图像标注地址，进入“
        B卷图像标注检查”，10张图中有3张标注错误，请修改这3张图的为正确标注
      </div>
      <div class="a-q-red">作答方式：答题框附上图像标注修正后的截图（3张）</div>
    </div>
    <div class="a-warning">
      数据标注与增强完成后，第二部分则是数据训练模型搭建 <br />
      下面请根据题目要求，进行规范作答
    </div>
    <div class="a-question" style="height: 160px">
      <div class="a-q-title">模块二 数据训练模型搭建</div>
      <div class="a-q-title">一、简答题</div>
      <div class="a-q-text h1">
        （一）请简述“机器学习”、“深度学习”的区别和联系（100字以内）
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （二）用计算机实现图像识别也被称为计算机视觉，请列举三种计算机视觉任务。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （三）卷积神经网络可以用来帮助计算机感知图像、声音等数据，请写出三种常见卷积神经网络的名称。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
    </div>
    <div class="a-question" style="height: 280px">
      <div class="a-q-title">二、实操题</div>
      <div class="a-q-text h2">
        （一）请将已准备好的预训练模型进行下载并解压到指定目录home/student/contest，并通过指令展现/home/student/contest/的文件列表（含预训练模型）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h9">
        （二）请打开model.config文件，对代码进行阅读理解并在相应位置填写所缺参数。
        <br />
        ①目标类别数：_______________;<span class="red">（num_classes）</span>
        <br />
        ②图像尺寸参数配置： _______________;，<span class="red"
          >（填长宽尺寸）</span
        >;<span class="red">（height：width）</span>
        <br />
        ③批数据处理量：__________;<span class="red">（batch_size）</span>
        <br />
        ④预训练模型路径：________;<span class="red"
          >（fine_tune_checkpoint）</span
        >
        <br />
        ⑤本模型所使用的特征提取网络名称：________；<span class="red"
          >（feature_extractor type）</span
        >
        <br />
        ⑥本模型所使用的优化器名称：________；<span class="red"
          >（optimizer）</span
        >
        <br />
        ⑦本模型可用于解决何种图像处理任务：_______________；
        <br />
        ⑧初始学习率：____________;<span class="red"
          >（initial_learning_rates）</span
        >
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
    </div>
    <div class="a-warning">
      数据训练模型搭建完成后，第三部分进入深度学习数据训练 <br />
      下面请根据题目要求，进行规范作答
    </div>
    <div class="a-question" style="height: 280px">
      <div class="a-q-title">模块三 深度学习数据训练</div>
      <div class="a-q-text h2">
        （一）准备训练脚本，请将本次的训练脚本放到指定目录/home/student/contest/中，并通过指令展现/home/student/contest/的文件列表（含模型训练脚本文件）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h2">
        （二）准备训练数据集，请将本次的训练数据集放到指定目录中home/student/contest/data中，并通过指令展现home/student/contest/data的文件列表（含训练数据集文件）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h2">
        （三）准备训练集标签，请将本次的训练集标签放到指定目录中home/student/contest/data，并通过指令展现home/student/contest/data的文件列表（含训练集标签文件）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h1">
        （四）下面进入/home/student/contest/，激活工程环境并执行“模型训练”脚本
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上“训练完成”结果截图
      </div>
      <div class="a-q-text h1">
        （五）训练完成后，通过Tensorboard查看可视化训练结果并分析模型的训练误差
        （total-loss value）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
        3.模型训练误差 4.模型训练误差结果截图
      </div>
    </div>
    <div class="a-warning">
      深度学习数据训练完成后，第四部分为算法模型优化 <br />
      下面请根据题目要求，进行规范作答
    </div>
    <div class="a-question" style="height: 370px">
      <div class="a-q-title">模块四 算法模型优化</div>
      <div class="a-q-text h2">
        （一）准备模型评估脚本，请将本次的评估脚本放到指定目录/home/student/contest/，并通过指令展现/home/student/contest/的文件列表（含模型评估脚本文件）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h2">
        （二）准备验证数据集，请将本次的验证数据集放到指定目录/home/student/contest/data，并通过指令展现/home/student/contest/data的文件列表（含验证数据集文件）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h1">
        （三）下面进入/home/student/contest/，激活工程环境并执行“模型评估”脚本
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上“评估完成”结果截图
      </div>
      <div class="a-q-text h1">
        （四）评估完成后，通过Tensorboard查看可视化评估结果并分析模型的评估精度
        （查看命令行中的mAP0.5@iou）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
        3.模型评估精度 4.模型评估精度结果截图
      </div>
      <div class="a-q-text h2">
        （五）下面请进行模型优化，可通过调整model.config文件中num_steps参数来进行调整。最终提交三次模型优化的模型评估精度结果截图。
      </div>
      <div class="a-q-red">作答方式：答题框附上三次模型评估精度结果截图</div>
      <div class="a-q-text h1">
        （六）下面进入/home/student/contest/，执行导出冻结模型脚本
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h2">
        （七）下面请执行模型检测脚本，对测试图片（路径/home/student/contest/test.jpg）进行模型检测（输出图片路径为/home/student/contest/out_test.jpg）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
        3.测试图片截图
      </div>
    </div>
    <div class="a-warning">
      算法模型优化完成后，第五部分为算法模型应用部署 <br />
      下面请根据题目要求，进行规范作答
    </div>
    <div class="a-question" style="height: 160px">
      <div class="a-q-title">模块五 算法模型应用部署</div>
      <div class="a-q-title">一、实操题</div>
      <div class="a-q-text h1">
        （一）下面进入/home/student/contest/并执行“模型转换to_pb”脚本，得到pb文件
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h1">
        （二）下面进入/home/student/contest/并执行“模型转换pb_to_tflite”脚本，得到detection.tflite文件
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
      <div class="a-q-text h1">
        （三）
        请将“推理”脚本、detection.tflite文件拷贝到模型应用端（地址：/home/student/zy-T100/utils_x86）
      </div>
      <div class="a-q-red">
        作答方式：1.答题框附上命令行代码 2.答题框附上完整过程的代码截图
      </div>
    </div>
    <div class="a-question" style="height: 200px">
      <div class="a-q-title">二、简答题</div>
      <div class="a-q-text h1">
        （一）贵校团队最终帮助企业实现了自动检测瓜果的需求，请归纳本次工作相关任务流程（不少于150字）。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （二）请结合你所在学校的特色，简述人工智能技术在相关领域的应用前景（不少于150字）。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
      <div class="a-q-text h1">
        （三）请简述参加本次比赛收获（不少于150字）。
      </div>
      <div class="a-q-red">作答方式：答题框中直接作答</div>
    </div>
  </div>
</template>

<script>
import { mapState } from "vuex";
export default {
  computed: {
    ...mapState("Match", ["url"]),
  },
  methods: {
    getIp() {
      this.$store.dispatch("Match/trainUrl");
    },
  },
  mounted() {
    this.getIp();
  },
};
</script>

<style scoped>
@import url(../assets/style/questiona.css);
</style>