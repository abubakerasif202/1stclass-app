FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json tsconfig*.json vite.config.ts index.html ./
RUN npm ci

COPY src ./src
ARG VITE_API_BASE_URL=https://staging-api.1stclassexpress.com.au
ARG VITE_SSE_URL=https://staging-api.1stclassexpress.com.au/v1/events/stream
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_SSE_URL=$VITE_SSE_URL

RUN npm run build

FROM nginx:alpine AS runner
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD wget -qO- http://127.0.0.1:8080/health || exit 1
CMD ["nginx", "-g", "daemon off;"]
