FROM node:16 AS build-stage
WORKDIR /app
COPY vue/package.json vue/package-lock.json ./
RUN npm ci
COPY vue .
RUN npm run build

FROM nginx:1.25-alpine AS production-stage
RUN rm /etc/nginx/conf.d/default.conf
COPY docker/vue/nginx.conf /etc/nginx/nginx.conf
COPY --from=build-stage /app/dist /usr/share/nginx/html
EXPOSE 19090
CMD ["nginx", "-g", "daemon off;"]
