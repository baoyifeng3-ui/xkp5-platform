import Vue from "vue";
import VueRouter from "vue-router";

import Home from "@/views/Home.vue";
import Publicity from "@/views/Publicity.vue";
import Login from "@/views/Login.vue";
import Layout from "@/views/Layout.vue";
import Match from "@/views/Match.vue";
import Matchs from "@/views/Matchs.vue";
import Rank from "@/views/Rank.vue";
import Detect from "@/views/Detect.vue";
import QuestionA from "@/views/QuestionA.vue";
import QuestionB from "@/views/QuestionB.vue";
import Question from "@/components/Question.vue";
import dthj from "@/components/dthj.vue";
import Admin from "@/views/Admin.vue";
import ManagementShell from "@/layouts/ManagementShell.vue";
import NormalUserShell from "@/layouts/NormalUserShell.vue";
import OperationsShell from "@/layouts/OperationsShell.vue";
import ManagementHome from "@/views/management/ManagementHome.vue";
import CourseManagement from "@/views/management/CourseManagement.vue";
import ResourceManagement from "@/views/management/ResourceManagement.vue";
import TrainingManagement from "@/views/management/TrainingManagement.vue";
import CompetitionManagement from "@/views/management/CompetitionManagement.vue";
import UserManagement from "@/views/management/UserManagement.vue";
import LearningAnalysis from "@/views/management/LearningAnalysis.vue";
import DeviceManagement from "@/views/management/DeviceManagement.vue";
import PlatformSettings from "@/views/management/PlatformSettings.vue";
import PlatformLicense from "@/views/management/PlatformLicense.vue";
import CompetitionPreview from "@/views/management/CompetitionPreview.vue";
import CoursePlatform from "@/views/user/CoursePlatform.vue";
import ResourceCenter from "@/views/user/ResourceCenter.vue";
import TrainingEnvironment from "@/views/user/TrainingEnvironment.vue";
import TrainingValidation from "@/views/user/TrainingValidation.vue";
import OperationsHome from "@/views/operations/OperationsHome.vue";
import AdministratorManagement from "@/views/operations/AdministratorManagement.vue";
import LicenseDiagnostics from "@/views/operations/LicenseDiagnostics.vue";
import ProcessingAgents from "@/views/operations/ProcessingAgents.vue";
import ContainerTemplates from "@/views/operations/ContainerTemplates.vue";
import ImageRegistryView from "@/views/operations/ImageRegistryView.vue";
import ChangePassword from "@/views/ChangePassword.vue";
import CompleteProfile from "@/views/CompleteProfile.vue";
import OnlineHelp from "@/views/shared/OnlineHelp.vue";
import HelpManagement from "@/views/operations/HelpManagement.vue";
import CompetitionPractical from "@/views/competition/CompetitionPractical.vue";
import {
  getToken,
  mustChangePassword,
  getRole,
  getPlatformMode,
  landingRoute,
  getCompetitionAccessPhase,
  getUserInfo,
  getClassPolicy,
} from "@/utils/auth";
import { Message } from "element-ui";

const roleNavigation = require("@/navigation/roleNavigation");
const { isPreviewRoute } = require("@/services/participantPreview");

Vue.use(VueRouter);

const routes = [
  { path: "/login", name: "Login", component: Login },
  {
    path: "/change-password",
    name: "ChangePassword",
    component: ChangePassword,
  },
  {
    path: "/complete-profile",
    name: "CompleteProfile",
    component: CompleteProfile,
  },
  {
    path: "/operations",
    component: OperationsShell,
    meta: { roles: ["ADMIN", "SUPER_ADMIN"] },
    children: [
      {
        path: "",
        name: "OperationsHome",
        component: OperationsHome,
        meta: { roles: ["SUPER_ADMIN"] },
      },
      {
        path: "processing-agents",
        name: "ProcessingAgents",
        component: ProcessingAgents,
        meta: { roles: ["SUPER_ADMIN"] },
      },
      {
        path: "container-templates",
        name: "ContainerTemplates",
        component: ContainerTemplates,
        meta: { roles: ["SUPER_ADMIN"] },
      },
      {
        path: "image-registry",
        name: "ImageRegistry",
        component: ImageRegistryView,
        meta: { roles: ["SUPER_ADMIN"] },
      },
      {
        path: "administrators",
        name: "AdministratorManagement",
        component: AdministratorManagement,
        meta: { roles: ["SUPER_ADMIN"] },
      },
      {
        path: "license",
        name: "LicenseDiagnostics",
        component: LicenseDiagnostics,
        meta: { roles: ["SUPER_ADMIN"] },
      },
      {
        path: "help",
        name: "HelpManagement",
        component: HelpManagement,
        meta: { roles: ["SUPER_ADMIN"] },
      },
    ],
  },
  {
    path: "/management",
    component: ManagementShell,
    meta: { roles: ["ADMIN"] },
    children: [
      { path: "", name: "ManagementHome", component: ManagementHome },
      {
        path: "competition",
        name: "CompetitionManagement",
        component: CompetitionManagement,
      },
      {
        path: "courses",
        name: "CourseManagement",
        component: CourseManagement,
      },
      {
        path: "course-platform",
        name: "AdminCoursePlatform",
        component: CoursePlatform,
        props: { adminDemo: true },
      },
      {
        path: "demo/courses",
        name: "AdminDemoCourses",
        component: CoursePlatform,
        props: { adminDemo: true },
      },
      {
        path: "demo/resources",
        name: "AdminDemoResources",
        component: ResourceCenter,
        props: { adminDemo: true },
      },
      {
        path: "demo/training",
        name: "AdminDemoTraining",
        component: TrainingEnvironment,
        props: { adminDemo: true },
      },
      {
        path: "demo/validation",
        name: "AdminDemoValidation",
        component: TrainingValidation,
        props: { adminDemo: true },
      },
      {
        path: "resources",
        name: "ResourceManagement",
        component: ResourceManagement,
      },
      {
        path: "training",
        name: "TrainingManagement",
        component: TrainingManagement,
      },
      { path: "users", name: "UserManagement", component: UserManagement },
      {
        path: "learning-analysis",
        name: "LearningAnalysis",
        component: LearningAnalysis,
      },
      {
        path: "devices",
        name: "DeviceManagement",
        component: DeviceManagement,
      },
      {
        path: "platform-settings",
        name: "PlatformSettings",
        component: PlatformSettings,
      },
      { path: "license", name: "PlatformLicense", component: PlatformLicense },
      { path: "help", name: "ManagementHelp", component: OnlineHelp },
      {
        path: "/competition-preview",
        name: "CompetitionPreview",
        component: CompetitionPreview,
      },
      { path: "/Admin", name: "Admin", component: Admin },
    ],
  },
  {
    path: "/course-platform",
    component: NormalUserShell,
    meta: { roles: ["USER"], modes: ["TRAINING"] },
    children: [
      { path: "", name: "CoursePlatform", component: CoursePlatform },
      {
        path: "/resource-center",
        name: "ResourceCenter",
        component: ResourceCenter,
      },
      {
        path: "/training-environment",
        name: "TrainingEnvironment",
        component: TrainingEnvironment,
      },
      {
        path: "/training-validation",
        name: "TrainingValidation",
        component: TrainingValidation,
      },
      { path: "/online-help", name: "OnlineHelp", component: OnlineHelp },
    ],
  },
  {
    path: "/Layout",
    name: "Layout",
    component: Layout,
    meta: { roles: ["USER"], modes: ["COMPETITION"] },
    children: [
      { path: "/Home", name: "Home", component: Home },
      { path: "/Publicity", name: "Publicity", component: Publicity },
      { path: "/Match", name: "Match", component: Match },
      { path: "/Matchs", name: "Matchs", component: Matchs },
      { path: "/Rank", name: "Rank", component: Rank },
      { path: "/Detect", name: "Detect", component: Detect },
      { path: "/QuestionA", name: "QuestionA", component: QuestionA },
      { path: "/QuestionB", name: "QuestionB", component: QuestionB },
      { path: "/Question", name: "Question", component: Question },
      {
        path: "/competition-practical",
        name: "CompetitionPractical",
        component: CompetitionPractical,
      },
      { path: "/dthj", name: "dthj", component: dthj },
    ],
  },
];

const router = new VueRouter({
  mode: "hash",
  base: process.env.BASE_URL,
  routes,
});

router.beforeEach((to, from, next) => {
  const loggedIn = Boolean(getToken());
  if (!loggedIn && to.path !== "/login") {
    return next({ path: "/login" });
  }
  if (loggedIn && to.path === "/login") {
    return next(landingRoute());
  }
  if (loggedIn && mustChangePassword() && to.path !== "/change-password") {
    return next({ path: "/change-password" });
  }
  const needsProfile =
    loggedIn &&
    getRole() === "USER" &&
    getUserInfo().mustCompleteProfile === true;
  if (
    needsProfile &&
    to.path !== "/complete-profile" &&
    to.path !== "/change-password"
  )
    return next({ path: "/complete-profile" });
  if (!needsProfile && to.path === "/complete-profile")
    return next(landingRoute());
  if (loggedIn && !mustChangePassword() && to.path === "/change-password") {
    return next(landingRoute());
  }
  if (to.path === "/Admin") {
    const legacyRoute = roleNavigation.legacyAdminRoute(to.query.tab);
    if (legacyRoute) return next(legacyRoute);
  }
  const requiredRoles = to.matched.reduce(
    (roles, record) =>
      record.meta && record.meta.roles ? record.meta.roles : roles,
    null
  );
  const adminPreview =
    getRole() === "ADMIN" &&
    isPreviewRoute(to, getRole()) &&
    roleNavigation.previewDestinations.includes(to.path);
  if (!adminPreview && requiredRoles && !requiredRoles.includes(getRole())) {
    return next(landingRoute());
  }
  const classPolicy = getClassPolicy();
  if (getRole() === "USER" && classPolicy.active === true && classPolicy.courseId && to.path === "/training-environment") {
    return next({ path: "/course-platform", query: { courseId: classPolicy.courseId } });
  }
  const selectedClassCourse = getRole() === "USER" && classPolicy.active === true && classPolicy.courseId &&
    to.path === "/course-platform" && String(to.query.courseId || "") === String(classPolicy.courseId);
  if (
    getRole() === "USER" &&
    classPolicy.active === true &&
    !selectedClassCourse &&
    ![
      "/training-environment",
      "/training-validation",
      "/resource-center",
      "/change-password",
      "/complete-profile",
    ].includes(to.path)
  ) {
    Message.warning("当前上课中，禁止使用该功能");
    return next(classPolicy.courseId
      ? { path: "/course-platform", query: { courseId: classPolicy.courseId } }
      : { path: "/training-environment" });
  }
  const requiredModes = to.matched.reduce(
    (modes, record) =>
      record.meta && record.meta.modes ? record.meta.modes : modes,
    null
  );
  if (
    !adminPreview &&
    getRole() === "USER" &&
    requiredModes &&
    !requiredModes.includes(getPlatformMode())
  ) {
    return next(landingRoute());
  }
  if (
    loggedIn &&
    getRole() === "USER" &&
    ["BEFORE_LOGIN", "PRE_START", "UNSCHEDULED"].includes(
      getCompetitionAccessPhase()
    ) &&
    [
      "/Question",
      "/QuestionA",
      "/QuestionB",
      "/Match",
      "/Matchs",
      "/Detect",
    ].includes(to.path)
  ) {
    return next({ path: "/Publicity" });
  }
  if (to.path === "/") {
    return next(loggedIn ? landingRoute() : { path: "/login" });
  }
  next();
});

export default router;
