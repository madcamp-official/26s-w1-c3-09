# ---- 빌드 단계: 타입체크 + Vite 프로덕션 빌드 ----
FROM node:22-alpine AS build
WORKDIR /app

# lockfile 기준 재현 가능한 설치 (소스만 바뀌면 이 레이어는 캐시 재사용)
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# .env는 이미지에 안 들어가므로(.dockerignore) 빌드타임 변수를 여기서 고정
#   - API 경로는 nginx가 같은 오리진에서 /api로 프록시
#   - MSW(목업)는 프로덕션에서 항상 꺼짐
ENV VITE_API_BASE_URL=/api
ENV VITE_ENABLE_MSW=false
RUN npm run build

# ---- 실행 단계: nginx로 정적 서빙 + API 프록시 ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
