<template>
    <div :class="['h-box', 'module-page', { 'is-preview': previewOnly }]">
        <header class="module-heading participant-heading">
            <div><h1>赛程赛规</h1><p>查看竞赛内容、比赛方式、流程与评分规则。</p></div>
        </header>
        <nav class="competition-tabs" aria-label="赛规赛程栏目">
            <button v-for="item in competitionTabs" :key="item.key" :class="{ active: tab === item.key }" type="button" @click="tab = item.key">{{ item.label }}</button>
        </nav>
        <div class="home-content" v-if="currentCustomContent">
            <div class="content-head">
                <div class="content-title">{{ currentCustomContent.title }}</div>
            </div>
            <div class="content-body custom-competition-content">{{ currentCustomContent.body }}</div>
        </div>
        <div class="home-content" v-else-if="tab == 'matchContent'">
            <div class="content-head">
                <div class="content-title">竞赛内容</div>
            </div>
            <div class="content-body">
                <div class="content-item">
                    <ul>
                        <li>
                            <div class="item-ico">（一）</div>
                            <div class="item-text">
                                竞赛内容适应国家产业结构调整与社会发展需要，展示知识经济时代高技能人才培养的特点；与地区重点产业相吻合，反映地区产业结构升级和高新技术发展对人才培养的需要。
                            </div>
                        </li>
                        <li>
                            <div class="item-ico">（二）</div>
                            <div class="item-text">
                                竞赛内容聚焦人工智能技术应用领域相关岗位的主要技能，针对人工智能技术应用专业学生的突出需求，围绕人工智能技术应用领域的先进技术、主流产品，力求突出面向应用的技能培养。
                            </div>
                        </li>
                        <li>
                            <div class="item-ico">（三）</div>
                            <div class="item-text">
                                为全面考查参赛选手的职业综合素质和技术技能水平，实际技能操作竞赛包括数据标注与增强、数据训练模型搭建、深度学习数据训练、算法模型优化、算法模型应用部署等
                            </div>
                        </li>
                    </ul>
                </div>
                <div class="content-image">
                    <div class="image-title">竞赛内容、推荐用时及各模块权重分配表：</div>
                    <div class="image-pic">
                        <img :src="require('../assets/image/module.png')" alt="" />
                    </div>
                </div>
                <div class="content-module">
                    <div class="module-label">
                        本次比赛共计五个模块，每个模块独立评分为评价分，完成所有模块后提交的应用经过标准测试后的自动评分为测量分，两项加总后满分200分。
                    </div>
                    <div class="module-item">
                        <ul>
                            <li>
                                <div class="md-title">
                                    模块A：数据标注与增强<span class="last">（24%）</span>
                                </div>
                                <div class="md-detali">
                                    包括去掉噪声图片，规范图片文件格式，给图片分类，统一文件命名，规范存储介质，设计存储路径，标注人脸图像，标注人体图像，标注手势图像，标注道路场景，标注目标对象，旋转图片，拉伸图片，平移图片、变换图片颜色等。
                                </div>
                            </li>
                            <li style="margin-bottom: 10px">
                                <div class="md-title">
                                    模块B：数据训练模型搭建<span class="last">（16%）</span>
                                </div>
                                <div class="md-detali">
                                    包括使用Python语言，搭建AlexNet网络模型，搭建VGG网络模型，搭建YOLO网络模型，搭建SSD网络模型等。
                                </div>
                            </li>
                            <li>
                                <div class="md-title">
                                    模块C：深度学习数据训练<span class="last">（24%）</span>
                                </div>
                                <div class="md-detali">
                                    包括使用TensorFlow框架，设置训练模型参数（输入大小、特征层大小等），定义Loss参数，确定训练样本，确定测试样本，定义正负样本，使用Tensor
                                    Board工具，可视化训练结果，分析准确率、召回率，验证算法模型等。
                                </div>
                            </li>
                            <li>
                                <div class="md-title">
                                    模块D：算法模型优化<span class="last">（16%）</span>
                                </div>
                                <div class="md-detali">
                                    包括使用梯度下降法，使用随机梯度下降法，使用小批量随机梯度下降法，使用动量优化法，使用自适应梯度下降法,分析深度学习性能，使用Timeline工具,配置线程优化性能，压缩模型等。
                                </div>
                            </li>
                            <li>
                                <div class="md-title">
                                    模块E：算法模型应用部署<span class="last">（20%）</span>
                                </div>
                                <div class="md-detali">
                                    部署已训练完成的算法模型，调用图像识别应用服务，验证数据推理结果等。
                                </div>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>

        <div class="home-content" v-else-if="tab == 'matchMethod'">
            <div class="content-head">
                <div class="content-title">竞赛方式</div>
            </div>
            <div class="content-body">
                <div class="content-module" style="height: 400px">
                    <div class="module-item" style="height: 350px">
                        <ul>
                            <li style="margin-bottom: 10px">
                                <div class="md-title">（一）选手构成</div>
                                <div class="md-detali">
                                    本项目为团队赛方式，由2名选手组队参赛完成本项目竞赛的考核内容，每个参赛队可以有1位指导老师。
                                </div>
                            </li>
                            <li style="height: 100px">
                                <div class="md-title">（二）竞赛时间安排</div>
                                <div class="md-detali">
                                    竞赛时间：4月12日 <br />
                                    本赛项分A、B、C、D、E五个模块。参赛队伍在现场根据给定的项目任务，依次完成“数据标注与增强”、“数据训练模型搭建”、“深度学习数据训练”、“算法模型优化”、“算法模型应用部署”五个竞赛模块，再通过标准测试得到系统自动评分。<br />
                                    所有参赛队按照指定时间完成比赛任务，累计竞赛时间为4小时。
                                </div>
                            </li>
                            <li>
                                <div class="md-title">（三）竞赛评分</div>
                                <div class="md-detali">
                                    本赛项由评分裁判对各参赛选手提交的作品采取测量性结果评分和评价性结果评分相结合的方式进行评分。本次比赛共计五个模块，每个模块独立评分为评价分，完成所有模块后提交的应用经过标准测试后的系统自动评分为测量分。各参赛选手总成绩=数据标注与增强模块得分+数据训练模型搭建模块得分+深度学习数据训练模块得分+算法模型优化模块得分+算法模型应用部署模块得分+系统自动评分得分。
                                </div>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>

        <div class="home-content" v-else-if="tab == 'matchProgress'">
            <div class="content-head">
                <div class="content-title">竞赛流程</div>
            </div>
            <div class="content-body">
                <div class="content-item" style="height: 100px">
                    <ul>
                        <li style="margin-bottom: 10px">
                            <div class="item-ico">（一）</div>
                            <div class="item-text">比赛时间：共240分钟（4小时）。</div>
                        </li>
                        <li style="margin-bottom: 10px">
                            <div class="item-ico">（二）</div>
                            <div class="item-text">比赛流程：直接进行技能实操比赛。</div>
                        </li>
                    </ul>
                </div>
                <div class="content-image" style="height: 600px">
                    <div class="image-title">竞赛时间安排参考如下：</div>
                    <div class="image-pic" style="height: 550px">
                        <img :src="require('../assets/image/time1.png')" alt="" />
                    </div>
                </div>
                <div class="content-image" style="height: 600px">
                    <div class="image-title">竞赛流程安排参考如下：</div>
                    <div class="image-pic" style="height: 476px">
                        <img :src="require('../assets/image/time2.png')" alt="" />
                    </div>
                </div>
            </div>
        </div>

        <div class="home-content" v-else-if="tab == 'matchRule'">
            <div class="content-head">
                <div class="content-title">竞赛规则</div>
            </div>
            <div class="content-body">
                <div class="content-item">
                    <ul>
                        <li class="li1">
                            <div class="item-text">
                                （一）比赛赛位通过抽签决定，比赛期间参赛选手原则上不得离开比赛场地。
                            </div>
                        </li>
                        <li class="li1">
                            <div class="item-text">
                                （二）竞赛所需的硬件、软件和辅助工具统一提供，参赛队不得使用自带的任何具有存储和通信功能的设备，如硬盘、光盘、U
                                盘、手机、随身听、智能手表、PDA 等。
                            </div>
                        </li>
                        <li class="li1">
                            <div class="item-text">
                                （三）参赛选手在赛前 10
                                分钟领取比赛任务，并进入比赛赛位。比赛正式开始后方可进行相关操作。
                            </div>
                        </li>
                        <li>
                            <div class="item-text">
                                （四）在比赛过程中，参赛选手如有疑问，应举手示意，现场裁判
                                应按要求及时予以答疑。如遇设备或软件等故障，参赛选手应举手示意，现场裁判、技术人员等应及时予以解决。确因计算机软件或硬件故障，致使操作无法继续，经赛场裁判长确认，予以启用备用设备。
                            </div>
                        </li>
                        <li class="li1">
                            <div class="item-text">
                                （五）比赛时间结束，选手应全体起立，结束操作。经工作人员查收清点所有文档后方可离开赛场，离开赛场时不得带走任何资料。
                            </div>
                        </li>
                        <li>
                            <div class="item-text">
                                （六）赛项裁判应严格遵守赛项各项规章制度，确保比赛公平、公正、公开。比赛当天
                                8:00
                                起，赛项裁判应上交所有通信设备，由赛项执委会统一保管，并安排赛项裁判在指定区域休息或工作，直至赛项成绩评定结束。
                            </div>
                        </li>
                        <li>
                            <div class="item-text">
                                （七）比赛结束，经加密裁判对各参赛选手提交的竞赛结果进行第三次加密后，评分裁判方可入场进行成绩评判。最终竞赛成绩经复核无误，由裁判长、监督长签字确认后，以纸质形式向全体参赛队进行公布，并在闭赛式上予以宣布。
                            </div>
                        </li>
                        <li>
                            <div class="item-text">
                                （八）本赛项各参赛队最终成绩，由承办单位信息员录入赛务管理系统。承办单位信息员对成绩数据审核后，将赛务系统中录入的成绩导出打印，经赛项裁判长审核无误后签字。承办单位信息员将裁判长确认的电子版赛项成绩上传赛务管理系统；同时，将裁判长签字的纸质打印成绩单报送大赛执委会。
                            </div>
                        </li>
                        <li>
                            <div class="item-text">
                                （九）赛项结束后，专家工作组根据裁判评分情况，分析参赛选手在比赛过程中对各知识点、技术的掌握程度，并将分析报告报备大赛执委会办公室，执委会办公室根据实际情况适时公布。
                            </div>
                        </li>
                        <li>
                            <div class="item-text">
                                （十）赛项中每个比赛环节裁判评分的原始材料和最终成绩等结果性材料，经监督组人员和裁判长签字后，装袋密封留档；并由赛项承办院校封存，委派专人妥善保管。
                            </div>
                        </li>
                    </ul>
                </div>
            </div>
        </div>

        <div class="home-content" v-else>
            <div class="content-head">
                <div class="content-title">评分规则</div>
            </div>
            <div class="content-body">
                <div class="content-module">
                    <div class="module-item">
                        <ul>
                            <li style="height: 420px">
                                <div class="md-title">（一）评分原则</div>
                                <div class="md-detali">
                            <li>
                                <div class="item-text">
                                    1．根据裁判分工，负责相同模块评分工作的不同裁判，采取随机抽签方式对参赛选手提交的竞赛作品，依据赛项评价标准独立评分，确保成绩评定严谨、客观、准确。
                                    <br />（1）裁判每次评分前须进行随机抽签分组，杜绝主观意愿组队，各自完全独立评分，裁判员间互不干涉。
                                    <br />（2）加密裁判加密当前评判模块赛位号后，由评分裁判统一在工作区内评分，比赛监督人员可随机监督。
                                    <br />（3）评分裁判核定当前模块成绩后将成绩录入系统。
                                </div>
                            </li>
                            <li style="height: 40px">
                                <div class="item-text">
                                    2．错误不传递原则
                                    <br />各环节分别计算得分，错误不传递，按规定比例计入总分。
                                </div>
                            </li>
                            <li style="height: 40px">
                                <div class="item-text">
                                    3. 结果评分原则
                                    <br />
                                    竞赛名次按照总成绩从高到低排序，相同成绩的依次按功能模块开发、初步设计、测试与交付、需求分析模块得分高低决定排名次序。
                                </div>
                            </li>
                            <li style="height: 60px">
                                <div class="item-text">
                                    4. 三层加密原则
                                    <br />
                                    比赛过程采取三层加密，通过抽取参赛编号、工位号和竞赛成果号，屏蔽参赛选手信息，每个环节设置一名独立裁判，每个环节结束后，数据立即封存于裁判长处，加密裁判直接隔离，确保成绩评定公平、公正。
                                </div>
                            </li>
                            <li>
                                <div class="item-text">
                                    5. 抽查复核原则
                                    <br />
                                    （1）为保障成绩评判的准确性，监督组对赛项总成绩排名前30%的所有参赛选手的成绩进行复核；对其余成绩进行抽检复核，抽检覆盖率不得低于15%。
                                    <br />
                                    （2）监督组需将复核中发现的错误以书面方式及时告知裁判长，由裁判长更正成绩并签字确认。
                                    <br />
                                    （3）复核、抽检错误率超过5%的，则认定为非小概率事件，裁判组需对所有成绩进行复核。
                                </div>
                            </li>
                    </div>
                    </li>
                    <li style="height: 1980px">
                        <div class="md-title">（二）评分点</div>
                        <div class="content-image" style="height: 424px">
                            <div class="image-title">模块A：数据准备</div>
                            <div class="image-pic" style="height: 374px">
                                <img :src="require('../assets/image/mdA.png')" alt="" />
                            </div>
                        </div>
                        <div class="content-image" style="height: 505px">
                            <div class="image-title">模块B：数据增强</div>
                            <div class="image-pic" style="height: 455px; border-left: 1px solid #000">
                                <img :src="require('../assets/image/mdB.png')" alt="" />
                            </div>
                        </div>
                        <div class="content-image" style="height: 362px">
                            <div class="image-title">模块C：数据清洗</div>
                            <div class="image-pic" style="height: 312px; border-left: 1px solid #000">
                                <img :src="require('../assets/image/mdC.png')" alt="" />
                            </div>
                        </div>
                        <div class="content-image" style="height: 279px">
                            <div class="image-title">模块D：数据标注</div>
                            <div class="image-pic" style="height: 229px; border-left: 1px solid #000">
                                <img :src="require('../assets/image/mdD.png')" alt="" />
                            </div>
                        </div>
                        <div class="content-image" style="height: 281px">
                            <div class="image-title">模块E：数据导出</div>
                            <div class="image-pic" style="height: 231px; border-left: 1px solid #000">
                                <img :src="require('../assets/image/mdE.png')" alt="" />
                            </div>
                        </div>
                    </li>
                    <li>
                        <div class="md-title">（三）评分标准</div>
                        <div class="md-detali">
                    <li style="height: 120px">
                        <div class="item-text">
                            本次图像目标识别比赛的评价指标以目标检测中 mAP
                            指标为准，使用VOC2010以后提出的标准。AP总
                            结了插值平滑后的Precision -
                            Recall曲线形状，并定义为一组11个等距召回级别的平均精度。
                            准确率 (precision)
                            描述了预测正确的物体个数占总的正类预测个数的比例。计算公式如下：
                        </div>
                        <div class="content-image" style="height: 120px">
                            <div class="image-pic" style="width: 810px; height: 60px; margin-top: 20px">
                                <img style="margin-top: 20px" src="../assets/image/sf1.png" alt="" />
                            </div>
                        </div>
                    </li>
                    <li style="height: 130px">
                        <div class="item-text">
                            召回率 (recall)
                            确定了正类被预测为正类目标占所有标注图片的个数。计算公式如下：
                        </div>
                        <div class="content-image" style="height: 130px">
                            <div class="image-pic" style="width: 620px; height: 70px; margin-top: 20px">
                                <img :src="require('../assets/image/sf2.png')" alt="" />
                            </div>
                        </div>
                    </li>
                    <li style="height: 220px">
                        <div class="item-text">
                            AP总结了插值平滑后的Precision -
                            Recall曲线形状，并定义为一组11个等距召回级别的平均精度。计算
                            公式如下：
                        </div>
                        <div class="content-image" style="height: 240px">
                            <div class="image-pic" style="width: 630px; height: 166px; margin-top: 20px">
                                <img :src="require('../assets/image/sf3.png')" alt="" />
                            </div>
                        </div>
                    </li>
                    <li style="height: 60px">
                        <div class="item-text">
                            测试集有若干张图片，学生完成应用部署后，从模型应用页面检测查看部署结果，最终mAP评测指标得
                            分越大，成绩越好
                        </div>
                    </li>
                </div>
                </li>
                </ul>
            </div>
        </div>
    </div>
    </div>
    <!-- <el-tabs>
      <el-tab-pane label="竞赛内容"> </el-tab-pane>
      <el-tab-pane label="竞赛方式"> </el-tab-pane>
      <el-tab-pane label="竞赛流程"> </el-tab-pane>
      <el-tab-pane label="竞赛规则"> </el-tab-pane>
      <el-tab-pane label="评分规则"> </el-tab-pane>
    </el-tabs> -->
    </div>
</template>

<script>
import { competitionApi } from '@/api/Match'
import { competitionContentTabs } from '@/utils/competitionDefaults'
import { getRole } from '@/utils/auth'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
    data () {
        return {
            tab: 'matchContent',
            competitionContent: {},
            competitionTabs: competitionContentTabs,
        };
    },
    computed: {
        previewOnly () {
            return isPreviewRoute(this.$route, getRole());
        },
        currentCustomContent () {
            const key = this.tab === 'scoringRule' ? 'scoringRule' : this.tab;
            const section = this.competitionContent[key];
            return section && String(section.body || '').trim() ? section : null;
        },
    },
    watch: {
        $route (newValue, oldValue) {
            if (this.$route.query.tab) {
                this.tab = this.$route.query.tab;
            }
        },
    },
    async mounted () {
        if (this.$route.query.tab) this.tab = this.$route.query.tab;
        try {
            const result = await competitionApi();
            if (result.code === 200) this.competitionContent = result.data.competitionContent || {};
        } catch (error) {
            // The original static content remains available while the service is unavailable.
        }
    },
};
</script>

<style scoped>
@import url(../assets/style/home.css);

.custom-competition-content {
    padding: 28px 8px;
    color: var(--ui-text);
    font-size: 15px;
    line-height: 1.9;
    white-space: pre-wrap;
    word-break: break-word;
}
.participant-heading { padding: 20px 24px 0; }
.participant-heading h1 { font-size: 24px; }
@media (max-width: 760px) { .participant-heading { padding: 16px 16px 0; } }
</style>
