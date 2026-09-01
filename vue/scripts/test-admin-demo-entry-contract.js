const fs=require('fs');const router=fs.readFileSync('src/router/index.js','utf8');const home=fs.readFileSync('src/views/management/ManagementHome.vue','utf8');const course=fs.readFileSync('src/views/user/CoursePlatform.vue','utf8');const resources=fs.readFileSync('src/views/user/ResourceCenter.vue','utf8');const training=fs.readFileSync('src/views/user/TrainingEnvironment.vue','utf8');const validation=fs.readFileSync('src/views/user/TrainingValidation.vue','utf8');
for(const path of ['demo/courses','demo/resources','demo/training','demo/validation'])if(!router.includes(path))throw new Error('missing admin demo route '+path);
if(home.includes('?preview=1'))throw new Error('home shortcuts must not use readonly preview');
for(const source of [course,resources,training,validation])if(!source.includes('adminDemo'))throw new Error('demo component missing adminDemo');
if(!course.includes('listAdminCourses')||!resources.includes('this.adminDemo')||!training.includes('startAdminTrainingEnvironment')||!validation.includes('listAdminTrainingEnvironments'))throw new Error('admin demo APIs missing');
console.log('admin demo entry contract passed');
