<template>
  <div
    class="sample-form"
    v-loading="loading"
    element-loading-text="拼命加载中"
    element-loading-spinner="el-icon-loading"
    element-loading-background="rgba(0, 0, 0, 0.8)"
  >
    <el-row>
      <div class="form-back" style="z-index: 99" @click="$router.go(-1)">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 48 48"
          width="51"
          height="51"
          style="
            border-color: rgba(0, 0, 0, 0);
            border-width: apx;
            border-style: undefined;
          "
          filter="none"
        >
          <rect
            width="48"
            height="48"
            fill="rgba(238, 224, 224, 1)"
            fill-opacity="0.01"
            stroke="none"
          ></rect>
          <path
            d="M24 36L12 24L24 12"
            stroke="rgba(238, 224, 224, 1)"
            stroke-width="4"
            stroke-linecap="round"
            stroke-linejoin="round"
            fill="none"
          ></path>
          <path
            d="M36 36L24 24L36 12"
            stroke="rgba(238, 224, 224, 1)"
            stroke-width="4"
            stroke-linecap="round"
            stroke-linejoin="round"
            fill="none"
          ></path>
        </svg>
      </div>
      <el-col :span="16" class="form-left">
        <div class="grid-content bg-purple">
          <div
            ref="noneUpload"
            class="none-upload"
            v-if="!isAdd && fileImg.length"
          >
            <img class="updateImg" :src="imgSrc" alt="" />
            <div class="form-upload-title">
              <span> 已上传{{ fileImg.length }}张图片 </span>
            </div>
          </div>
          <div v-else class="form-upload" @click="doUpload">
            <div class="form-upload-pic">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="150"
                height="150"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  fill="#BFB3B3"
                  d="M6.437 28l-0.027 0.027-0.028-0.027h-2.393c-0.731-0.001-1.323-0.593-1.323-1.324v0-21.352c0.005-0.729 0.594-1.318 1.322-1.324h24.022c0.731 0 1.323 0.593 1.323 1.324v21.352c-0.005 0.729-0.594 1.318-1.322 1.324h-21.574zM26.667 20v-13.333h-21.333v18.667l13.333-13.333 8 8zM26.667 23.771l-8-8-9.563 9.563h17.563v-1.563zM10.667 14.667c-1.473 0-2.667-1.194-2.667-2.667s1.194-2.667 2.667-2.667v0c1.473 0 2.667 1.194 2.667 2.667s-1.194 2.667-2.667 2.667v0z"
                ></path>
              </svg>
            </div>
            <input
              type="file"
              v-show="false"
              ref="formFile"
              @change="onUpload"
              multiple="multiple"
            />
            <div class="form-upload-title">
              <span v-if="!fileImg.length"> 点击上传图片 </span>
              <span v-else> 已上传{{ fileImg.length }}张图片 </span>
            </div>
          </div>

          <div class="form-status">
            <div class="status-items">
              <div class="fs1">
                <img v-if="isAnalysis" src="../assets/icon/on-fs1.png" alt="" />
                <img v-else src="../assets/icon/fs1.png" alt="" />
              </div>
              <span>状态：{{ isAnalysis ? "已" : "未" }}开始解析</span>
            </div>
            <div class="status-items">
              <div class="fs2">
                <img
                  v-if="form.mineralType"
                  src="../assets/icon/on-fs2.png"
                  alt=""
                />
                <img v-else src="../assets/icon/fs2.png" alt="" />
              </div>
              <span v-if="form.mineralType == 'FLUORITE'">目标矿物：萤石</span>
              <span v-else-if="form.mineralType == 'MAGNETITE'"
                >目标矿物：磁铁矿</span
              >
              <span v-else>目标矿物：</span>
            </div>
            <div class="status-items">
              <div class="fs1">
                <img
                  v-if="fileImg.length"
                  src="../assets/icon/on-fs3.png"
                  alt=""
                />
                <img v-else src="../assets/icon/fs3.png" alt="" />
              </div>
              <span>状态：{{ fileImg.length ? "已载入" : "未载入" }}</span>
            </div>
          </div>
          <button
            :disabled="!fileImg.length || form.mineralType == ''"
            :class="
              fileImg.length && form.mineralType != ''
                ? 'canClick-bottom'
                : 'form-bottom'
            "
            @click="onAnalysis"
          ></button>
        </div>
      </el-col>
      <el-col :span="8" class="form-right">
        <div class="grid-content bg-purple-light">
          <!-- <div class="reset-btns">
            <div>再建</div>
            <div>新建</div>
          </div> -->
          <el-form
            ref="form"
            style="margin-left: 40px"
            :model="form"
            label-width="90px"
            label-position="left"
          >
            <el-form-item>
              <div class="form-title">
                <!-- <p>
                  <img src="@/assets/icon/import.png" alt="" />
                </p> -->
                样本参数建立
                <!-- <div>?</div> -->
              </div>
            </el-form-item>
            <el-form-item label="采集位置">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M6.667 16.667c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895zM25.333 20.439c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895zM4 23.333v-13.333c0-3.313 5.373-6 12-6s12 2.687 12 6v13.333c0 3.313-5.373 6-12 6s-12-2.687-12-6zM16 13.333c2.831 0 5.448-0.553 7.293-1.476 1.425-0.713 2.040-1.44 2.040-1.857s-0.615-1.144-2.040-1.857c-1.845-0.923-4.463-1.476-7.293-1.476s-5.448 0.553-7.293 1.476c-1.425 0.713-2.040 1.44-2.040 1.857s0.615 1.144 2.040 1.857c1.845 0.923 4.463 1.476 7.293 1.476z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
              </svg>
              <el-select
                v-model="form.diggingsWellId"
                placeholder="请选择"
                @change="onSampleChange"
              >
                <el-option
                  v-for="(item, index) in wellList"
                  :key="index"
                  :label="item.well"
                  :value="item.diggingsWellId"
                >
                </el-option>
              </el-select>
              <!-- <el-input
                v-model="form.diggingsWellId"
                clearable
                placeholder="请输入采集区域"
                @input="onInput()"
                disabled
              ></el-input> -->
            </el-form-item>
            <el-form-item label="矿区名称">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M6.667 16.667c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895zM25.333 20.439c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895zM4 23.333v-13.333c0-3.313 5.373-6 12-6s12 2.687 12 6v13.333c0 3.313-5.373 6-12 6s-12-2.687-12-6zM16 13.333c2.831 0 5.448-0.553 7.293-1.476 1.425-0.713 2.040-1.44 2.040-1.857s-0.615-1.144-2.040-1.857c-1.845-0.923-4.463-1.476-7.293-1.476s-5.448 0.553-7.293 1.476c-1.425 0.713-2.040 1.44-2.040 1.857s0.615 1.144 2.040 1.857c1.845 0.923 4.463 1.476 7.293 1.476z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
              </svg>
              <!-- <el-select
                v-model="form.diggings"
                placeholder="请选择"
                @change="onSampleChange"
              >
                <el-option
                  v-for="(item, index) in wellList"
                  :key="index"
                  :label="item.diggings"
                  :value="item.diggingsWellId"
                >
                </el-option>
              </el-select> -->
              <el-input
                v-model="form.diggings"
                clearable
                placeholder="请输入矿区名称"
                @input="onInput()"
                disabled
              ></el-input>
            </el-form-item>
            <el-form-item label="采样地经度">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  fill-rule="evenodd"
                  clip-rule="evenodd"
                  d="M1 12C1 5.92487 5.92487 1 12 1C18.0751 1 23 5.92487 23 12C23 18.0751 18.0751 23 12 23C5.92487 23 1 18.0751 1 12ZM12 4C7.58172 4 4 7.58172 4 12H8.71429C11.0812 12 13 13.9188 13 16.2857C13 16.6802 12.6802 17 12.2857 17C12.2857 17 10.5 17 10 17C9.5 17 9.08907 17.5 9.08907 18.5C9.08907 19.5 10 20 10.5 20C11 20 11.3094 20 12 20C16.0015 20 19.3169 17.0621 19.9067 13.2256C19.2107 13.716 18.3852 14 17.5 14C15.6547 14 14.0688 12.7659 13.3744 11H10C9.44771 11 8.99034 10.5486 9.08907 10.0052C9.60145 7.18519 11.5 6 13.8669 6.0491C14.0571 5.76054 14.2744 5.49548 14.5147 5.25857C14.7976 4.97973 14.7477 4.47377 14.3682 4.35634C13.6199 4.12473 12.8245 4 12 4Z"
                  fill="rgba(134, 117, 117, 1)"
                  stroke="none"
                ></path>
              </svg>
              <el-input
                v-model="form.longitude"
                clearable
                placeholder="请输入采样地经度"
                @input="onInput()"
                disabled
              ></el-input>
            </el-form-item>
            <el-form-item label="采样地纬度">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  fill-rule="evenodd"
                  clip-rule="evenodd"
                  d="M1 12C1 5.92487 5.92487 1 12 1C18.0751 1 23 5.92487 23 12C23 18.0751 18.0751 23 12 23C5.92487 23 1 18.0751 1 12ZM12 4C7.58172 4 4 7.58172 4 12H8.71429C11.0812 12 13 13.9188 13 16.2857C13 16.6802 12.6802 17 12.2857 17C12.2857 17 10.5 17 10 17C9.5 17 9.08907 17.5 9.08907 18.5C9.08907 19.5 10 20 10.5 20C11 20 11.3094 20 12 20C16.0015 20 19.3169 17.0621 19.9067 13.2256C19.2107 13.716 18.3852 14 17.5 14C15.6547 14 14.0688 12.7659 13.3744 11H10C9.44771 11 8.99034 10.5486 9.08907 10.0052C9.60145 7.18519 11.5 6 13.8669 6.0491C14.0571 5.76054 14.2744 5.49548 14.5147 5.25857C14.7976 4.97973 14.7477 4.47377 14.3682 4.35634C13.6199 4.12473 12.8245 4 12 4Z"
                  fill="rgba(134, 117, 117, 1)"
                  stroke="none"
                ></path>
              </svg>
              <el-input
                v-model="form.dimensionality"
                clearable
                placeholder="请输入采样地纬度"
                @input="onInput()"
                disabled
              ></el-input>
            </el-form-item>
            <el-form-item :label="depthOrHeight == 'DEPTH' ? '深度' : '标高'">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="26"
                height="26"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M2.016 6.528c0.096 0 0.16-0.096 0.256-0.128 1.248-0.8 2.528-1.6 3.776-2.432 0.16-0.096 0.256-0.096 0.352 0.096 0.032 0.096 0.096 0.16 0.16 0.256 0.16 0.192 0.32 0.416-0.064 0.576-0.128 0.032-0.032 0.128 0 0.224 0.224 0.352 0.448 0.704 0.704 1.056 0.096 0.128 0.096 0.256 0.064 0.416-0.128 0.544-0.224 1.12-0.384 1.664-0.032 0.128 0 0.224 0.064 0.352 0.8 1.344 1.632 2.656 2.432 4 0.096 0.192 0.16 0.192 0.32 0.096 0.576-0.416 1.184-0.8 1.792-1.184 0.096-0.064 0.192-0.128 0.288 0 0.736 1.184 1.44 2.4 1.856 3.744 0.192 0.544 0.128 1.088-0.032 1.664-0.216 0.728-0.455 1.344-0.738 1.934l0.034-0.078c-0.064 0.16-0.16 0.192-0.288 0.192-0.125 0.005-0.272 0.008-0.419 0.008-0.724 0-1.43-0.073-2.113-0.212l0.068 0.012c-0.512-0.096-0.896-0.384-1.248-0.704-0.992-0.928-1.728-2.016-2.464-3.104-0.096-0.16 0-0.224 0.096-0.288l1.824-1.152c0.192-0.096 0.16-0.192 0.064-0.352-0.8-1.312-1.568-2.624-2.368-3.936-0.096-0.16-0.192-0.224-0.384-0.288-0.512-0.096-1.056-0.224-1.568-0.32-0.16-0.032-0.288-0.096-0.352-0.256-0.224-0.352-0.448-0.672-0.672-1.024-0.096-0.16-0.16-0.224-0.32-0.096-0.16 0.16-0.256 0.096-0.352-0.064-0.128-0.192-0.224-0.416-0.384-0.576v-0.096zM6.528 6.592c0.032-0.096 0-0.128-0.032-0.192-0.224-0.32-0.416-0.64-0.608-0.928-0.064-0.128-0.128-0.128-0.256-0.064-0.64 0.416-1.28 0.832-1.952 1.248-0.096 0.096-0.128 0.16-0.032 0.256 0.192 0.288 0.384 0.608 0.576 0.896 0.034 0.093 0.121 0.159 0.224 0.16h0l1.536 0.352c0.096 0 0.16 0.032 0.192-0.096 0.128-0.544 0.224-1.088 0.352-1.632zM30.016 27.52c-0.256 0-0.448 0.096-0.672 0.192-0.352 0.128-0.672-0.032-0.96-0.224-0.064-0.032-0.096-0.096-0.064-0.16l0.192-0.768c0.032-0.064 0.064-0.096 0.128-0.096 0.288-0.096 0.608-0.32 0.864-0.256 0.224 0.064 0.192 0.48 0.32 0.736 0.064 0.16 0.064 0.32 0.192 0.48v0.096zM26.144 28.096h-20.192c-0.32 0-0.32 0-0.288-0.32 0.064-0.352 0.128-0.704 0.16-1.088 0.032-0.16 0.128-0.224 0.224-0.288 0.608-0.32 1.216-0.672 1.856-0.992 0.16-0.096 0.224-0.16 0.192-0.384-0.128-0.704-0.192-1.408-0.288-2.112-0.032-0.224 0-0.32 0.256-0.32 0.8-0.064 1.632-0.16 2.464-0.224 0.192 0 0.256-0.064 0.288-0.256 0-0.32 0.064-0.64 0.096-0.96 0-0.128 0.032-0.192 0.192-0.192 1.024-0.096 2.016-0.192 3.040-0.256 0.224-0.032 0.256-0.096 0.288-0.288 0.032-0.704 0.096-1.408 0.16-2.112 0.032-0.256 0.256-0.416 0.384-0.608 0.128-0.256 0.32-0.32 0.608-0.224 0.192 0.064 0.448 0.128 0.672 0.16 0.288 0 0.352 0.16 0.384 0.384 0 0.256 0.064 0.512 0.064 0.736 0 0.192 0.096 0.256 0.288 0.256l1.696-0.128c0.224-0.032 0.288 0.032 0.32 0.256 0 0.672 0.064 1.344 0.096 1.984 0 0.192 0.064 0.288 0.224 0.352 0.704 0.256 1.376 0.544 2.080 0.8 0.16 0.064 0.224 0.128 0.224 0.32l0.096 1.536c0.032 0.224 0.16 0.288 0.32 0.256 0.256 0 0.544-0.064 0.8-0.096 0.32-0.032 0.352 0.032 0.384 0.32 0 0.352 0.16 0.576 0.448 0.768 0.256 0.16 0.544 0.256 0.832 0.32 0.096 0.032 0.224 0.032 0.224 0.16 0.128 0.896 0.736 1.44 1.28 2.080 0.048 0.047 0.090 0.1 0.126 0.156l0.002 0.004zM27.008 21.696c0.672 0 1.12 0.32 1.472 0.832 0.32 0.512 0.544 1.088 0.736 1.632 0.064 0.192 0.032 0.384-0.096 0.576-0.2 0.355-0.526 0.619-0.917 0.733l-0.011 0.003-1.344 0.48c-0.064 0-0.128 0.064-0.224-0.032-0.352-0.416-0.768-0.768-1.184-1.12-0.256-0.224-0.352-0.448-0.288-0.768 0.128-0.768 0.48-1.44 0.992-2.016 0.224-0.288 0.576-0.256 0.864-0.32zM26.016 18.368c-0.032 0.288-0.064 0.512-0.128 0.704-0.058 0.228-0.226 0.407-0.443 0.479l-0.005 0.001c-0.16 0.064-0.288 0.128-0.448 0.192-0.64 0.288-0.928 0.096-1.216-0.48-0.256-0.544-0.256-1.152-0.064-1.728 0.128-0.416 0.32-0.48 0.736-0.352 0.32 0.096 0.672 0.256 1.056 0.224 0.16-0.032 0.256 0.096 0.32 0.224 0.128 0.256 0.16 0.512 0.192 0.736zM21.28 15.584c0.192 0 0.384 0.064 0.544 0.192 0.224 0.16 0.352 0.352 0.288 0.64-0.096 0.32-0.32 0.576-0.608 0.608-0.224 0.032-0.448 0.064-0.704 0.096-0.16 0.032-0.288 0-0.256-0.224 0-0.192 0-0.384-0.032-0.544-0.032-0.512 0.224-0.768 0.768-0.768zM28.48 20.672c-0.287-0.034-0.547-0.125-0.777-0.261l0.009 0.005c-0.096-0.064-0.064-0.16-0.064-0.224l0.192-0.672c0.032-0.064 0.032-0.128 0.096-0.128l0.864-0.288c0.096-0.032 0.128 0.032 0.16 0.096 0.096 0.352 0.224 0.704 0.352 1.056 0 0.064 0.032 0.128-0.064 0.16-0.256 0.096-0.544 0.192-0.768 0.256zM24 22.4c-0.128-0.032-0.192-0.064-0.288-0.096-0.544-0.128-0.576-0.192-0.416-0.704 0.032-0.192 0.032-0.416 0.192-0.512 0.256-0.128 0.576-0.192 0.864-0.288 0.096-0.032 0.096 0.064 0.128 0.128l0.352 0.992c0 0.096 0.064 0.16-0.032 0.192-0.288 0.096-0.576 0.192-0.8 0.288zM20.864 18.72c0.032-0.256 0.192-0.416 0.384-0.576 0.032-0.032 0.064-0.032 0.128 0 0.288 0.096 0.608 0.192 0.928 0.32 0.16 0.064 0.224 0.288 0.224 0.48s-0.128 0.288-0.288 0.352l-0.256 0.096c-0.576 0.192-0.704 0.16-1.056-0.384-0.064-0.096-0.064-0.192-0.064-0.288z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
              </svg>
              <el-input
                :class="depthOrHeight == 'DEPTH' ? 'abs-input' : ''"
                v-model="form.depthOrHeightValue"
                type="number"
                @input="[onInput(), handleForm(form)]"
                clearable
                :placeholder="`请输入${
                  depthOrHeight == 'DEPTH' ? '深度' : '标高'
                }`"
              ></el-input>
            </el-form-item>
            <el-form-item label="采集时间">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M8 2h2v2h-2v-2zM20 2h2v2h-2v-2zM26 4h-4v2h-2v-2h-10v2h-2v-2h-6v24h26v-24h-2zM26 26h-22v-14h22v14z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
                <path
                  d="M14 14h2v2h-2v-2zM10 14h2v2h-2v-2zM18 14h2v2h-2v-2zM22 14h2v2h-2v-2zM8 18h2v2h-2v-2zM12 18h2v2h-2v-2zM16 18h2v2h-2v-2zM20 18h2v2h-2v-2zM6 22h2v2h-2v-2zM10 22h2v2h-2v-2zM14 22h2v2h-2v-2zM18 22h2v2h-2v-2z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
              </svg>
              <el-date-picker
                type="datetime"
                placeholder="请选择采集时间"
                v-model="form.samplingTime"
                style="width: 100%"
                @input="onInput()"
                :picker-options="pickerOptions0"
                disabled
              ></el-date-picker>
            </el-form-item>
            <el-form-item label="制样时间">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M8 2h2v2h-2v-2zM20 2h2v2h-2v-2zM26 4h-4v2h-2v-2h-10v2h-2v-2h-6v24h26v-24h-2zM26 26h-22v-14h22v14z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
                <path
                  d="M14 14h2v2h-2v-2zM10 14h2v2h-2v-2zM18 14h2v2h-2v-2zM22 14h2v2h-2v-2zM8 18h2v2h-2v-2zM12 18h2v2h-2v-2zM16 18h2v2h-2v-2zM20 18h2v2h-2v-2zM6 22h2v2h-2v-2zM10 22h2v2h-2v-2zM14 22h2v2h-2v-2zM18 22h2v2h-2v-2z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
              </svg>
              <el-date-picker
                type="datetime"
                placeholder="请选择制样时间"
                v-model="form.samplePreparationTime"
                style="width: 100%"
                @input="onInput()"
                :picker-options="pickerOptions0"
                disabled
              ></el-date-picker>
            </el-form-item>
            <el-form-item label="分析模型">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 48 48"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M5 35C5 33.8954 5.89543 33 7 33H41C42.1046 33 43 33.8954 43 35V42H5V35Z"
                  fill="rgba(134, 117, 117, 1)"
                  stroke="rgba(81, 78, 78, 1)"
                  stroke-width="4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                ></path>
                <path
                  d="M42 18L34 18L28 12L34 6L42 6"
                  stroke="rgba(81, 78, 78, 1)"
                  stroke-width="4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
                <circle
                  cx="8"
                  cy="12"
                  r="4"
                  fill="rgba(134, 117, 117, 1)"
                  stroke="rgba(81, 78, 78, 1)"
                  stroke-width="4"
                ></circle>
                <path
                  d="M12 12L28 12"
                  stroke="rgba(81, 78, 78, 1)"
                  stroke-width="4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  fill="none"
                ></path>
                <path
                  d="M10 16L18 33"
                  stroke="rgba(81, 78, 78, 1)"
                  stroke-width="4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  fill="none"
                ></path>
              </svg>
              <el-select
                v-model="form.mineralType"
                placeholder="请选择分析模型"
                @change="handleForm(form)"
              >
                <el-option label="萤石" value="FLUORITE"></el-option>
                <el-option label="磁铁矿" value="MAGNETITE"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="矿物密度">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 32 32"
                width="24"
                height="24"
                style="
                  border-color: rgba(0, 0, 0, 0);
                  border-width: bpx;
                  border-style: undefined;
                "
                filter="none"
              >
                <path
                  d="M14.528 6.080v10.208c0 0.64 0.512 1.184 1.152 1.184h10.24c0.64 0 1.152 0.512 1.088 1.152-0.576 6.368-5.952 11.392-12.48 11.392-6.912 0-12.512-5.632-12.512-12.544s5.568-12.512 12.448-12.544h0.032s0.032 0.512 0.032 1.152zM29.984 14.336c0.003 0.030 0.005 0.065 0.005 0.1 0 0.599-0.485 1.084-1.084 1.084-0.003 0-0.006 0-0.009-0h-11.2c-0.64 0-1.152-0.512-1.152-1.152 0-3.264-0.032-12.352 0-12.352 7.072 0 12.864 5.44 13.44 12.32z"
                  fill="rgba(134, 117, 117, 1)"
                ></path>
              </svg>
              <el-input
                v-model="form.mineralDensity"
                clearable
                placeholder="请输入矿物密度"
                @input="onInput()"
                disabled
              ></el-input>
            </el-form-item>
            <el-form-item>
              <el-button
                :class="canSubmit ? 'can-submit' : 'form-submit'"
                :disabled="!canSubmit"
                type="primary"
                @click="onSubmit"
                >提交</el-button
              >
            </el-form-item>
          </el-form>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { baseURL } from "@/utils/BaseURL";
import { mapState } from "vuex";
// import Tiff from "tiff.js";
export default {
  data() {
    return {
      form: {
        depthOrHeight: "depth",
        mineralType: ""
      },
      depthOrHeight: "",
      fileList: [],
      fileImg: [],
      dId: 0,
      loading: false,
      canSubmit: false,
      pickerOptions0: {
        disabledDate(time) {
          return time.getTime() > Date.now() - 8.64e6;
        },
      },
      isAnalysis: false,
      isAdd: true,
      imgSrc: "",
    };
  },
  watch: {
    wellList(nV, oV) {
      if (nV.length) {
        this.setForm();
      }
    },
    form(nV, oV) {
      this.handleForm(nV);
    },
    isAdd(nV) {
      if (!nV) {
        this.canSubmit = true;
      }
    },
  },
  methods: {
    // 检测图片
    onAnalysis() {
      console.log(this.canSubmit);
      if (!this.canSubmit) {
        this.$message({
          message: "请先提交样本参数",
          type: "error",
        });
      } else {
        if (this.$route.params.row) {
          this.sampleNo = this.$route.params.row.sampleNo;
        }
        this.loading = true;
        let files = [];
        if (!this.isAdd) {
          files = this.fileImg;
        }
        let data = {
          sampleNo: this.sampleNo,
          files,
        };
        let config = {
          headers: { "Content-Type": "application/json;charset=UTF-8" },
        };
        this.$axios
          .post(baseURL + "sample/redetect", data, config)
          .then((res) => {
            this.loading = false;
            if (res.data.code == 200) {
              this.$message({
                message: "检测完成",
                type: "success",
              });
            }
            this.isAnalysis = true;
          });
      }
    },
    handleForm(form) {
      if (form.diggings && form.depthOrHeight && form.mineralType) {
        this.canSubmit = true;
      }
    },
    onInput() {
      this.$forceUpdate();
    },
    // 提交
    onSubmit() {
      this.isAdd = this.$route.params.row == undefined;
      let form = this.form;

      // 新增data
      let addData = {
        sampleCode:
          form.diggings +
          "-" +
          form.diggingsWellId +
          "-" +
          form.depthOrHeightValue +
          "-" +
          form.mineralType,
        diggingsWellId: form.diggingsWellId,
        analyticalModel:
          form.mineralType == "FLUORITE" ? "萤石含量分析" : "磁铁矿含量分析",
        mineralType: form.mineralType,
        createUserId: "1",
        depthOrHeightValue: form.depthOrHeightValue,
      };
      // 修改data
      let updateData = {
        sampleNo: this.isAdd ? 0 : this.$route.params.row.sampleNo,
        sampleCode:
          form.diggings +
          "-" +
          form.diggingsWellId +
          "-" +
          form.depthOrHeightValue +
          "-" +
          form.mineralType,
        diggingsWellId: form.diggingsWellId,
        analyticalModel:
          form.mineralType == "FLUORITE" ? "萤石含量分析" : "磁铁矿含量分析",
        mineralType: form.mineralType,
        createUserId: "1",
        diggings: form.diggings,
        longitude: form.longitude,
        dimensionality: form.dimensionality,
        mineralDensity: form.mineralDensity,
        depthOrHeightValue: form.depthOrHeightValue,
      };
      let config = {
        headers: { "Content-Type": "application/json;charset=UTF-8" },
      };
      if (this.isAdd) {
        this.loading = true;
        this.$store.dispatch("Sample/addSample", addData).then((res) => {
          if (res == 200) {
            this.loading = false;
            this.$message({
              message: "新增成功，可以检测了",
              type: "success",
            });
          }
        });
      } else {
        this.$store.dispatch("Sample/updateSample", updateData).then((res) => {
          if (res == 200) {
            this.loading = false;
            this.$message({
              message: "修改成功",
              type: "success",
            });
            this.$router.go(-1);
          }
        });
      }
    },
    // 上传图片
    onUpload(event) {
      this.fileImg = [];
      let etf = event.target.files;
      for (let i = 0; i < etf.length; i++) {
        let reader = new FileReader();
        let that = this;
        reader.readAsDataURL(etf[i]);
        reader.onload = function (e) {
          let ecr = e.currentTarget.result;
          let ecrArr = ecr.split(",");
          that.fileImg.push({
            suffix: ecrArr[0].substring(11, 14),
            value: ecrArr[1],
          });
          that.form.files = that.fileImg;
        };
      }
    },
    doUpload() {
      this.$refs.formFile.click();
    },
    getWell() {
      this.$store.dispatch("Sample/getWellList");
    },
    onSampleChange(val) {
      let sampleObj = this.wellList.find((v) => {
        return v.diggingsWellId == val;
      });
      this.form.diggings = sampleObj.diggings;
      this.depthOrHeight = sampleObj.depthOrHeight;
      this.form.samplingTime = sampleObj.samplingTime;
      this.form.samplePreparationTime = sampleObj.samplePreparationTime;
      this.form.dimensionality = sampleObj.dimensionality;
      this.form.longitude = sampleObj.longitude;
      this.form.mineralDensity = sampleObj.mineralDensity;
      this.form.diggingsWellId = sampleObj.diggingsWellId;
      if (this.depthOrHeight == "DEPTH") {
        this.form.depthOrHeightValue = -1;
      }
    },
    setForm() {
      if (this.$route.params.row) {
        let dRow = this.$route.params.row;
        this.form = dRow;

        if (this.$route.params.row.sampleInspectionRecord) {
          let imgList =
            this.$route.params.row.sampleInspectionRecord
              .sampleImgInspectionRecordList;
          this.fileImg = imgList.map((v) => {
            return {
              suffix: v.imgOld.split(".")[v.imgOld.split(".").length - 1],
              value: v.imgOld,
            };
          });
          this.imgSrc = this.$route.params.row.sampleInspectionRecord.imgNewAvg;
        } else {
          this.fileImg = [];
        }

        this.isAdd = false;
        this.dId = this.$route.params.row.diggingsWellId;
        let dObj = this.wellList.find((v) => {
          return v.diggingsWellId == this.dId;
        });
        this.form.diggings = dObj.diggingsWellId;
        this.onSampleChange(dObj.diggingsWellId);
      }
      if (this.$route.params.h) {
        this.form.depthOrHeightValue = this.$route.params.h;
      }
    },
  },
  computed: {
    ...mapState("Sample", ["wellList"]),
  },
  mounted() {
    this.getWell();
  },
};
</script>

<style>
.sample-form .el-select {
  width: 312px !important;
}

.sample-form .el-input {
  width: 90% !important;
}

.sample-form .el-form-item {
  margin-bottom: 15px !important;
  position: relative;
}

.form-title {
  font-size: 22px;
  font-weight: bold;
  text-align: center;
  height: 53px;
  line-height: 53px;
  width: 100%;
  margin-left: -70px;
  position: relative;
  color: #514e4e;
  letter-spacing: 2px;
}

.form-title div {
  width: 25px;
  height: 25px;
  border: 2px solid #8a7979;
  border-radius: 50%;
  text-align: center;
  line-height: 25px;
  color: #8a7979;
  font-size: 20px;
  top: 12px;
  position: absolute;
  left: 240px;
  cursor: pointer;
  letter-spacing: 0;
}

.form-right {
  height: calc(100vh - 80px);
  background-color: #eee0e0;
  margin-top: -10px;
  padding-bottom: 7px;
  box-shadow: -2px 0px 6px 0px rgba(0, 0, 0, 0.4);
  border-radius: 5px 0px 0px 0px;
  position: absolute;
  right: 0;
}

.sample-form .el-form-item svg {
  position: absolute;
  left: -120px;
  top: 8px;
}

.sample-form .el-form-item__label {
  font-size: 14px;
  font-weight: bold;
  color: #796e58;
  text-indent: 0;
}

.sample-form .form-submit {
  width: 170px;
  height: 30px;
  border-radius: 30px;
  line-height: 5px !important;
  /* background-color: #514e4e !important;
  border-color: #514e4e !important;
  bottom: -60px !important; */
  position: absolute;
}

.sample-form .can-submit {
  width: 170px;
  height: 30px;
  border-radius: 30px;
  line-height: 5px !important;
  /* background-color: #514e4e !important; */
  /* border-color: #514e4e !important; */
  bottom: -60px !important;
  position: absolute;
}

.form-left {
  width: 870px;
  height: 632px;
  margin-top: -10px;
  padding-bottom: 7px;
  float: left;
  position: relative;
}

.form-back {
  width: 50px;
  height: 50px;
  position: absolute;
  cursor: pointer;
  left: 30px;
  top: 20px;
}

.form-upload {
  width: 400px;
  height: 360px;
  position: absolute;
  left: 50%;
  margin-left: -200px;
  border-radius: 50%;
  top: 30px;
  background-color: rgba(238, 224, 224, 100);
  text-align: center;
  box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
  cursor: pointer;
}

.form-upload-pic {
  width: 150px;
  height: 150px;
  position: absolute;
  top: 80px;
  left: 50%;
  margin-left: -75px;
}

.form-upload-title {
  width: 100%;
  text-align: center;
  color: rgba(134, 117, 117, 100);
  font-size: 30px;
  font-weight: bold;
  position: absolute;
  top: 260px;
  letter-spacing: 5px;
  text-indent: 5px;
}

.form-status {
  width: 100%;
  height: 80px;
  position: absolute;
  bottom: 130px;
}

.status-items {
  width: 33%;
  height: 80px;
  float: left;
  margin-right: 0.5%;
  position: relative;
  /* text-align: center; */
}

.status-items:nth-child(3) {
  margin-right: 0;
}

.fs1 {
  width: 48px;
  height: 48px;
  left: 50%;
  margin-left: -24px;
  position: absolute;
}

.fs2 {
  width: 80px;
  height: 53px;
  left: 50%;
  margin-left: -40px;
  position: absolute;
}

.status-items span {
  display: inline-block;
  width: 100%;
  text-align: center;
  position: absolute;
  bottom: 0;
  font-size: 14px;
  color: #868180;
  font-weight: bold;
}

.form-bottom {
  width: 0px;
  height: 0px;
  border-width: 35px 55px;
  border-style: solid;
  border-color: transparent transparent transparent #514e4e;
  background-color: transparent;
  position: absolute;
  bottom: 30px;
  left: 50%;
  margin-left: -25px;
  cursor: not-allowed;
}

.canClick-bottom {
  width: 0px;
  height: 0px;
  border-width: 35px 55px;
  border-style: solid;
  border-color: transparent transparent transparent #228edc;
  background-color: transparent;
  position: absolute;
  bottom: 30px;
  left: 50%;
  margin-left: -25px;
  cursor: pointer;
}

.form-title p {
  width: 24px;
  height: 24px;
  position: absolute;
  left: -48px;
  top: 15px;
  cursor: pointer;
  margin: 0;
  padding: 0;
}

.form-title p img {
  width: 100%;
  height: 100%;
  float: left;
}

.reset-btns {
  width: 100px;
  height: 100px;
  position: absolute;
  right: 450px;
  bottom: 2px;
  background-color: #eee0e0;
  box-shadow: -2px 0px 6px 0px rgb(0 0 0 / 40%);
  border-radius: 5px 0px 0px 0px;
}

.reset-btns div:nth-child(1) {
  width: 100px;
  height: 49px;
  border: 1px solid rgba(187, 187, 187, 100);
  font-size: 14px;
  font-weight: bold;
  color: #228edc;
  text-align: center;
  line-height: 49px;
  cursor: pointer;
}

.reset-btns div:nth-child(2) {
  width: 100px;
  height: 50px;
  font-size: 14px;
  font-weight: bold;
  color: #dc2222;
  text-align: center;
  line-height: 50px;
  cursor: pointer;
}

.abs-input {
  width: 87% !important;
}

.sample-form .el-loading-mask {
  left: -22px !important;
  top: -10px !important;
  height: calc(100vh - 80px) !important;
}

.updateImg {
  width: 250px;
  height: 200px;
  margin-top: 50px;
  /* float: left; */
  margin-left: 75px;
}

.none-upload {
  width: 400px;
  height: 360px;
  position: absolute;
  left: 50%;
  margin-left: -200px;
  border-radius: 50%;
  top: 30px;
  background-color: rgba(238, 224, 224, 100);
  box-shadow: 0px 2px 6px 0px rgb(0 0 0 / 40%);
  cursor: not-allowed;
}

.cell {
  font-size: 12px !important;
}

.myCanvas {
  width: 130px !important;
  height: 102px !important;
  position: absolute;
  left: 135px;
  top: 120px;
}
</style>