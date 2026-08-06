FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG API_BASE_URL=/api
ENV API_BASE_URL=$API_BASE_URL
RUN npm run generate

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_HOST=server
EXPOSE 80
